//! Hot install: make a freshly installed module live without a reboot.
//!
//! A module opts in from customize.sh with `MODULE_HOT_INSTALL_REQUEST=true` (and optionally
//! `MODULE_HOT_RUN_SCRIPT=<script>`). installer.sh records the request in a marker file inside
//! the staged module dir; after the install script succeeds ksud moves the staged dir over the
//! live one, applies sepolicy.rule and runs the requested script. Anything that only happens at
//! boot (mounting system/, system.prop, post-fs-data.sh, ...) still waits for the next boot.
//!
//! The manager can also ask for an automatic hot install of modules that did not opt in. It is
//! only done when nothing in the module waits for boot, so the result is what a reboot would
//! give; everything else keeps the regular update-on-reboot path.

use crate::{
    assets, defs,
    module::{ScriptWait, exec_script_with},
    sepolicy,
    utils::{ensure_dir_exists, is_safe_mode, switch_mnt_ns},
};

use anyhow::{Context, Result, bail, ensure};
use log::{info, warn};

use std::{
    collections::HashSet,
    fs::{File, canonicalize, remove_dir_all, remove_file, rename},
    io::ErrorKind,
    os::unix::{fs::MetadataExt, process::CommandExt},
    path::{Component, Path, PathBuf},
    process::{Command, Stdio},
    time::{Instant, SystemTime, UNIX_EPOCH},
};

pub struct Request {
    run_script: Option<String>,
}

/// Consume the hot install marker left in the staged module dir, if any.
pub fn take_request(staged_dir: &Path) -> Option<Request> {
    let marker = staged_dir.join(defs::HOT_INSTALL_MARKER);
    let content = std::fs::read_to_string(&marker).ok()?;
    if let Err(e) = remove_file(&marker) {
        warn!("Failed to remove {}: {e}", marker.display());
    }
    let script = content.trim();
    Some(Request {
        run_script: (!script.is_empty()).then(|| script.to_owned()),
    })
}

/// Resolve the run script to a path relative to the module dir. A missing script is skipped,
/// like the metamodule implementations do.
fn check_run_script(staged_dir: &Path, script: &str) -> Result<Option<PathBuf>> {
    let mut components = Path::new(script).components().peekable();
    while components.next_if_eq(&Component::CurDir).is_some() {}
    let relative: PathBuf = components.collect();
    if relative.as_os_str().is_empty()
        || !relative
            .components()
            .all(|c| matches!(c, Component::Normal(_)))
    {
        bail!("invalid MODULE_HOT_RUN_SCRIPT '{script}'");
    }

    let path = staged_dir.join(&relative);
    if path.symlink_metadata().is_err() {
        println!("! MODULE_HOT_RUN_SCRIPT '{script}' not found, skipping it");
        return Ok(None);
    }
    // Symlinks may only point to files inside the module
    let resolved = canonicalize(&path)?;
    if !resolved.is_file() || !resolved.starts_with(canonicalize(staged_dir)?) {
        bail!("MODULE_HOT_RUN_SCRIPT '{script}' is not a file inside the module");
    }
    Ok(Some(relative))
}

fn check(module_id: &str, is_metamodule: bool, request: &Request) -> Result<Option<PathBuf>> {
    if is_metamodule {
        bail!("metamodules are always applied at boot");
    }
    if is_safe_mode() {
        bail!("modules are disabled in safe mode");
    }
    let Some(script) = &request.run_script else {
        return Ok(None);
    };
    check_run_script(&Path::new(defs::MODULE_UPDATE_DIR).join(module_id), script)
}

/// Move the live module dir out of the way. Running processes and existing mounts may still
/// reference it, so it is only deleted on the next boot.
fn stash(live_dir: &Path, module_id: &str) -> Result<PathBuf> {
    ensure_dir_exists(defs::HOT_INSTALL_TRASH_DIR)?;
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or_default();
    let target = Path::new(defs::HOT_INSTALL_TRASH_DIR).join(format!("{module_id}.{nanos}"));
    // Never delete the live dir here: on failure the regular update path still needs it
    rename(live_dir, &target).with_context(|| format!("Failed to move {}", live_dir.display()))?;
    Ok(target)
}

fn swap(module_id: &str) -> Result<()> {
    let staged_dir = Path::new(defs::MODULE_UPDATE_DIR).join(module_id);
    let live_dir = Path::new(defs::MODULE_DIR).join(module_id);

    let stashed = if live_dir.exists() {
        Some(stash(&live_dir, module_id)?)
    } else {
        None
    };

    if let Err(e) = rename(&staged_dir, &live_dir) {
        // Put the old dir back so the regular update-on-reboot path still works
        if let Some(old) = stashed
            && let Err(e) = rename(&old, &live_dir)
        {
            warn!("Failed to restore {}: {e}", live_dir.display());
        }
        return Err(e).with_context(|| format!("Failed to move {}", staged_dir.display()));
    }
    Ok(())
}

/// Try to apply the staged module right away. Returns false if the caller should fall back to
/// the regular update-on-reboot path.
pub fn apply(module_id: &str, is_metamodule: bool, request: &Request) -> bool {
    println!("- Hot install requested");

    let run_script = match check(module_id, is_metamodule, request)
        .and_then(|script| swap(module_id).map(|()| script))
    {
        Ok(script) => script,
        Err(e) => {
            println!("- Hot install skipped: {e:#}");
            println!("- Reboot to apply the module");
            return false;
        }
    };
    info!("Module {module_id} hot installed");
    mark_unknown(module_id);

    let live_dir = Path::new(defs::MODULE_DIR).join(module_id);

    // Rules are additive, so loading the new ones now is safe; stale ones go away on reboot
    let rule_file = live_dir.join("sepolicy.rule");
    if rule_file.exists() && sepolicy::apply_file(&rule_file).is_err() {
        println!("! Failed to apply sepolicy.rule");
    }

    if !live_dir.join("skip_mount").exists()
        && ["system", "vendor", "product", "system_ext", "odm"]
            .iter()
            .any(|partition| live_dir.join(partition).is_dir())
    {
        println!("- Note: partition files are mounted on the next boot");
    }

    if live_dir.join(defs::MODULE_PREINIT_DIR).is_dir() {
        println!("- Note: preinit actions run from the next boot");
    }

    if let Some(script) = run_script {
        // The installer usually runs in the manager's mount namespace; boot scripts run in
        // init's, so mounts made by the script must land there too
        if let Err(e) = switch_mnt_ns(1) {
            warn!("Failed to switch to init mount namespace: {e}");
        }
        println!("- Running {}", script.display());
        let deadline = Instant::now() + defs::HOT_INSTALL_SCRIPT_TIMEOUT;
        let result = exec_script_with(
            live_dir.join(script),
            ScriptWait::Until(deadline),
            |command| {
                // Output goes to the installer's pipe, which the manager closes once the install
                // ends; ignore SIGPIPE so background processes started here keep running
                command.stdin(Stdio::null());
                unsafe {
                    command.pre_exec(|| {
                        libc::signal(libc::SIGPIPE, libc::SIG_IGN);
                        Ok(())
                    });
                }
            },
        );
        if let Err(e) = result {
            println!("! {e:#}");
        }
    }

    true
}

/// Drop module dirs replaced by hot installs during the previous boot. The trash is renamed
/// first and deleted in the background so a large one does not hold up boot.
pub fn clean_trash() {
    let trash = Path::new(defs::HOT_INSTALL_TRASH_DIR);
    if !trash.exists() {
        return;
    }
    let old = Path::new(defs::WORKING_DIR).join("hot_install_trash.old");
    // Left over if the previous background removal did not finish
    if old.exists()
        && let Err(e) = remove_dir_all(&old)
    {
        warn!("Failed to clean {}: {e}", old.display());
    }
    if let Err(e) = rename(trash, &old) {
        warn!("Failed to move {}: {e}", trash.display());
        return;
    }
    let spawned = Command::new(assets::BUSYBOX_PATH)
        .args(["rm", "-rf"])
        .arg(&old)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn();
    if let Err(e) = spawned {
        warn!("Failed to spawn trash removal: {e}");
        if let Err(e) = remove_dir_all(&old) {
            warn!("Failed to clean {}: {e}", old.display());
        }
    }
}

/// Top-level module entries that only take effect at boot: partition dirs mounted by the
/// metamodule, Zygisk libraries, init and preinit hooks, early stage scripts and system.prop.
/// The partitions are the union of what meta-overlayfs, the magic mount ones, hybrid-mount,
/// mountify, zeromount and nomount pick up, plus every `my_*` and `*_dlkm` dir and the extra
/// dirs users configure (see [`BootOnly`]).
const BOOT_ONLY_ENTRIES: &[&str] = &[
    "system",
    "vendor",
    "product",
    "system_ext",
    "odm",
    "apex",
    "oem",
    "optics",
    "prism",
    "mi_ext",
    "cust",
    "zygisk",
    defs::MODULE_PREINIT_DIR,
    defs::MODULE_INIT_RC_DIR,
    "post-fs-data.sh",
    "post-mount.sh",
    "late-load.sh",
    "system.prop",
];

/// Configs of metamodules that mount extra top-level module dirs listed under `extra_mount`.
const EXTRA_MOUNT_CONFIGS: &[&str] = &[
    "/data/adb/hybrid-mount/config.toml",
    "/data/adb/magic_mount/config.toml",
];

/// Scripts whose processes keep running once started: the late boot stages, and the one a soft
/// reboot runs.
const RUNNING_SCRIPTS: &[&str] = &["service.sh", "boot-completed.sh", "emulated-soft-reboot.sh"];

/// Stages that run once boot is done, so running them now matches what a reboot would do.
const LATE_STAGE_SCRIPTS: &[&str] = &["service.sh", "boot-completed.sh"];

const SEPOLICY_RULE: &str = "sepolicy.rule";

/// Markers that decide whether and how a module is set up at boot.
const STATE_MARKERS: &[&str] = &[
    defs::DISABLE_FILE_NAME,
    defs::REMOVE_FILE_NAME,
    defs::UPDATE_FILE_NAME,
    "skip_mount",
];

/// Global dirs under /data/adb whose scripts and rc files run at boot or soft reboot.
const COMMON_BOOT_DIRS: &[&str] = &[
    "post-fs-data.d",
    "post-mount.d",
    "service.d",
    "boot-completed.d",
    "late-load.d",
    "emulated-soft-reboot.d",
    "initrc.d",
];

/// Which top-level module entries only take effect at boot.
#[derive(Default)]
struct BootOnly {
    extra: HashSet<String>,
}

impl BootOnly {
    fn load() -> Result<Self> {
        let mut extra = HashSet::new();
        for path in EXTRA_MOUNT_CONFIGS {
            let content = match std::fs::read_to_string(path) {
                Ok(content) => content,
                Err(e) if e.kind() == ErrorKind::NotFound => continue,
                Err(e) => return Err(e).with_context(|| format!("failed to read {path}")),
            };
            extra.extend(
                extra_mount_entries(&content)
                    .with_context(|| format!("cannot tell the extra_mount dirs in {path}"))?,
            );
        }
        Ok(Self { extra })
    }

    /// ColorOS style and dlkm partitions come and go, so any `my_*` or `*_dlkm` dir counts too.
    fn contains(&self, name: &str) -> bool {
        BOOT_ONLY_ENTRIES.contains(&name)
            || name.starts_with("my_")
            || name.ends_with("_dlkm")
            || self.extra.contains(name)
    }

    /// Entries of a module that decide what it does at boot.
    fn is_boot_relevant(&self, name: &str) -> bool {
        self.contains(name)
            || RUNNING_SCRIPTS.contains(&name)
            || name == SEPOLICY_RULE
            || STATE_MARKERS.contains(&name)
    }
}

/// Every name listed under an `extra_mount` key, commented out ones included. None when a key
/// is not followed by a plain list.
fn extra_mount_entries(content: &str) -> Option<Vec<String>> {
    const KEY: &str = "extra_mount";
    let mut names = Vec::new();
    let mut rest = content;
    while let Some(start) = rest.find(KEY) {
        rest = &rest[start + KEY.len()..];
        let list = rest
            .trim_start()
            .strip_prefix('=')?
            .trim_start()
            .strip_prefix('[')?;
        let end = list.find(']')?;
        let items: Vec<&str> = list[..end].split(',').map(str::trim).collect();
        for (index, item) in items.iter().enumerate() {
            // Only a trailing comma may leave an empty item
            if item.is_empty() && index == items.len() - 1 {
                continue;
            }
            // Anything but a plain quoted string may read differently to the metamodule
            let quote = item.chars().next().filter(|c| *c == '"' || *c == '\'')?;
            let name = item.strip_prefix(quote)?.strip_suffix(quote)?;
            // TOML escapes would decode to another name than the one read here
            if name.contains(quote) || name.contains('\\') {
                return None;
            }
            if let Some(name) = name.split('/').find(|part| !part.is_empty()) {
                names.push(name.to_owned());
            }
        }
        rest = &list[end..];
    }
    Some(names)
}

/// Whether `dir` has `name`; an error means it cannot be ruled out.
fn has_entry(dir: &Path, name: &str) -> Result<bool> {
    match dir.join(name).symlink_metadata() {
        Ok(_) => Ok(true),
        Err(e) if e.kind() == ErrorKind::NotFound => Ok(false),
        Err(e) => Err(e).with_context(|| format!("failed to check {}", dir.join(name).display())),
    }
}

/// The first top-level entry of `dir` that only takes effect at boot.
fn boot_only_entry(dir: &Path, boot_only: &BootOnly) -> Result<Option<String>> {
    let entries = match std::fs::read_dir(dir) {
        Ok(entries) => entries,
        Err(e) if e.kind() == ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(e).with_context(|| format!("failed to read {}", dir.display())),
    };
    for entry in entries {
        let entry = entry.with_context(|| format!("failed to read {}", dir.display()))?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if boot_only.contains(&name) {
            return Ok(Some(name));
        }
    }
    Ok(None)
}

/// Everything outside the module being installed that decides the next boot: the global boot
/// dirs, the boot relevant entries of the other modules, the metamodule's files and the
/// `extra_mount` configs. An installer that touches any of it leaves work for the boot.
#[derive(PartialEq, Eq)]
pub struct BootState(Vec<(PathBuf, String)>);

pub fn boot_state(module_id: &str) -> BootState {
    let mut state = Vec::new();
    let boot_only = BootOnly::load().unwrap_or_else(|e| {
        state.push((PathBuf::new(), format!("{e:#}")));
        BootOnly::default()
    });

    let mut roots: Vec<PathBuf> = COMMON_BOOT_DIRS
        .iter()
        .map(|dir| Path::new(defs::ADB_DIR).join(dir))
        .chain(EXTRA_MOUNT_CONFIGS.iter().map(PathBuf::from))
        .collect();
    match std::fs::read_dir(defs::MODULE_DIR) {
        Ok(modules) => {
            for module in modules {
                let Ok(module) = module else {
                    roots.push(PathBuf::from(defs::MODULE_DIR));
                    continue;
                };
                if module.file_name().to_str() == Some(module_id) {
                    continue;
                }
                let Ok(entries) = std::fs::read_dir(module.path()) else {
                    roots.push(module.path());
                    continue;
                };
                for entry in entries {
                    match entry {
                        Ok(entry)
                            if !boot_only
                                .is_boot_relevant(&entry.file_name().to_string_lossy()) => {}
                        Ok(entry) => roots.push(entry.path()),
                        Err(_) => roots.push(module.path()),
                    }
                }
            }
        }
        Err(_) => roots.push(PathBuf::from(defs::MODULE_DIR)),
    }
    roots.push(
        canonicalize(defs::METAMODULE_DIR).unwrap_or_else(|_| PathBuf::from(defs::METAMODULE_DIR)),
    );

    for root in &roots {
        walk_state(root, &mut state);
    }
    state.sort();
    BootState(state)
}

/// Record every path below `path` with its metadata; anything unreadable is recorded as such.
fn walk_state(path: &Path, state: &mut Vec<(PathBuf, String)>) {
    let metadata = match path.symlink_metadata() {
        Ok(metadata) => metadata,
        Err(e) => {
            state.push((path.to_owned(), format!("{:?}", e.kind())));
            return;
        }
    };
    let mtime = metadata
        .modified()
        .ok()
        .and_then(|time| time.duration_since(UNIX_EPOCH).ok())
        .map_or(0, |time| time.as_nanos());
    state.push((
        path.to_owned(),
        format!(
            "{:o} {}:{} {} {mtime}",
            metadata.mode(),
            metadata.uid(),
            metadata.gid(),
            metadata.len()
        ),
    ));
    if metadata.is_dir() {
        match std::fs::read_dir(path) {
            Ok(entries) => {
                for entry in entries {
                    match entry {
                        Ok(entry) => walk_state(&entry.path(), state),
                        Err(e) => state.push((path.join(""), format!("{:?}", e.kind()))),
                    }
                }
            }
            Err(e) => state.push((path.join(""), format!("{:?}", e.kind()))),
        }
    }
}

fn read_boot_id() -> Option<String> {
    let id = std::fs::read_to_string("/proc/sys/kernel/random/boot_id").ok()?;
    let id = id.trim();
    (!id.is_empty()).then(|| id.to_owned())
}

/// Record line prefix for modules whose effects in this session are unknown.
const UNKNOWN_PREFIX: &str = "?";
/// Last line of a complete record.
const RECORD_END: &str = ".";

#[derive(Default)]
struct ActiveRecord {
    /// Modules whose boot stages or automatic hot install ran in this session
    active: HashSet<String>,
    /// Modules that ran their own hot install script or an action in this session
    unknown: HashSet<String>,
}

/// Write the record in one go and end it with [`RECORD_END`], so a partial one is never read.
fn write_active_record(boot_id: &str, entries: &[&str]) -> Result<()> {
    let mut content = String::from(boot_id);
    for entry in entries {
        content.push('\n');
        content.push_str(entry);
    }
    content.push('\n');
    content.push_str(RECORD_END);
    let tmp = format!("{}.tmp", defs::ACTIVE_MODULES_FILE);
    std::fs::write(&tmp, content)?;
    rename(&tmp, defs::ACTIVE_MODULES_FILE)?;
    Ok(())
}

/// The entries of the record for this session, or None when it is missing, incomplete or from
/// another session.
fn read_active_entries() -> Option<Vec<String>> {
    let content = std::fs::read_to_string(defs::ACTIVE_MODULES_FILE).ok()?;
    let mut lines: Vec<&str> = content.lines().collect();
    if lines.pop()? != RECORD_END || lines.first().copied() != Some(read_boot_id()?.as_str()) {
        return None;
    }
    Some(lines[1..].iter().map(|line| (*line).to_owned()).collect())
}

fn read_active_record() -> Option<ActiveRecord> {
    let mut record = ActiveRecord::default();
    for entry in read_active_entries()? {
        match entry.strip_prefix(UNKNOWN_PREFIX) {
            Some(id) => record.unknown.insert(id.to_owned()),
            None => record.active.insert(entry),
        };
    }
    Some(record)
}

/// Record the modules whose boot stages run in this session (a boot or a soft reboot), so the
/// automatic hot install can tell what of a module is in effect.
pub fn record_active_modules() {
    // A stale record must not survive a failed write
    remove_file(defs::ACTIVE_MODULES_FILE).ok();
    let Some(boot_id) = read_boot_id() else {
        return;
    };
    let mut ids = Vec::new();
    let result = crate::module::foreach_active_module(|module| {
        let id = module
            .file_name()
            .and_then(|name| name.to_str())
            .with_context(|| format!("unexpected module dir {}", module.display()))?;
        ids.push(id.to_owned());
        Ok(())
    })
    .and_then(|()| {
        let entries: Vec<&str> = ids.iter().map(String::as_str).collect();
        write_active_record(&boot_id, &entries)
    });
    if let Err(e) = result {
        warn!("Failed to record active modules: {e:#}");
        remove_file(defs::ACTIVE_MODULES_FILE).ok();
    }
}

/// Replace the module's entry in the record. Dropping the whole record on failure is safe: then
/// nothing installed counts as known.
fn set_active_entry(module_id: &str, entry: &str) {
    let (Some(boot_id), Some(entries)) = (read_boot_id(), read_active_entries()) else {
        return;
    };
    let unknown = format!("{UNKNOWN_PREFIX}{module_id}");
    let mut kept: Vec<&str> = entries
        .iter()
        .map(String::as_str)
        .filter(|line| *line != module_id && *line != unknown)
        .collect();
    kept.push(entry);
    if let Err(e) = write_active_record(&boot_id, &kept) {
        warn!("Failed to update the active record for {module_id}: {e:#}");
        remove_file(defs::ACTIVE_MODULES_FILE).ok();
    }
}

/// After the module's own hot install script or action ran, what of it runs is unknown.
pub fn mark_unknown(module_id: &str) {
    set_active_entry(module_id, &format!("{UNKNOWN_PREFIX}{module_id}"));
}

/// Earlier versions of the module that hot installs replaced in this session.
fn replaced_versions(module_id: &str) -> Result<Vec<PathBuf>> {
    let prefix = format!("{module_id}.");
    let entries = match std::fs::read_dir(defs::HOT_INSTALL_TRASH_DIR) {
        Ok(entries) => entries,
        Err(e) if e.kind() == ErrorKind::NotFound => return Ok(Vec::new()),
        Err(e) => return Err(e).context("failed to read the hot install trash"),
    };
    let mut versions = Vec::new();
    for entry in entries {
        let entry = entry.context("failed to read the hot install trash")?;
        let is_version = entry.file_name().to_str().is_some_and(|name| {
            name.strip_prefix(&prefix)
                .is_some_and(|nanos| nanos.parse::<u128>().is_ok())
        });
        if is_version {
            versions.push(entry.path());
        }
    }
    Ok(versions)
}

#[derive(PartialEq, Eq)]
enum InEffect {
    /// Nothing of the module runs in this session
    No,
    /// Versions of the module ran their boot stages or an automatic hot install
    Yes,
    /// Its own hot install script or an action ran, or there is no complete record
    Unknown,
}

fn in_effect(module_id: &str) -> InEffect {
    let Ok(replaced) = replaced_versions(module_id) else {
        return InEffect::Unknown;
    };
    match read_active_record() {
        Some(record) if record.unknown.contains(module_id) => InEffect::Unknown,
        // A replaced version means some version went live in this session; requested hot
        // installs are marked unknown above, so this one was automatic
        Some(record) if record.active.contains(module_id) || !replaced.is_empty() => InEffect::Yes,
        Some(_) => InEffect::No,
        None => InEffect::Unknown,
    }
}

/// Make sure nothing that a version of the module in effect set up outlives the update:
/// running processes and mounts stay until a reboot.
fn ensure_nothing_left_running(module_id: &str, boot_only: &BootOnly) -> Result<()> {
    let mut versions = replaced_versions(module_id)?;
    versions.push(Path::new(defs::MODULE_DIR).join(module_id));
    for dir in &versions {
        if let Some(entry) = boot_only_entry(dir, boot_only)? {
            bail!("{entry} of the installed version stays in effect until a reboot");
        }
        for script in RUNNING_SCRIPTS {
            ensure!(
                !has_entry(dir, script)?,
                "what {script} of the installed version started keeps running until a reboot"
            );
        }
    }
    Ok(())
}

/// Top-level names in the zip, which still list files a metamodule installer moved away.
fn zip_top_level_entries(zip: &Path) -> Result<HashSet<String>> {
    let archive = zip::ZipArchive::new(File::open(zip)?)?;
    Ok(archive
        .file_names()
        .filter_map(|name| {
            name.split(['/', '\\'])
                .find(|part| !part.is_empty() && *part != "." && *part != "..")
        })
        .map(str::to_owned)
        .collect())
}

fn sepolicy_rules(dir: &Path) -> Result<HashSet<String>> {
    match std::fs::read_to_string(dir.join(SEPOLICY_RULE)) {
        Ok(content) => Ok(content
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty() && !line.starts_with('#'))
            .map(str::to_owned)
            .collect()),
        Err(e) if e.kind() == ErrorKind::NotFound => Ok(HashSet::new()),
        Err(e) => Err(e.into()),
    }
}

/// Decide whether applying the staged module now leaves the device as a reboot would.
fn check_auto(
    module_id: &str,
    is_metamodule: bool,
    zip: &Path,
    boot_state_before: &BootState,
) -> Result<()> {
    if is_metamodule {
        bail!("metamodules are always applied at boot");
    }
    if is_safe_mode() {
        bail!("modules are disabled in safe mode");
    }
    ensure!(
        boot_state(module_id) == *boot_state_before,
        "the installer changed what other modules or {} do at boot",
        defs::ADB_DIR
    );

    let staged_dir = Path::new(defs::MODULE_UPDATE_DIR).join(module_id);
    for marker in [defs::DISABLE_FILE_NAME, defs::REMOVE_FILE_NAME] {
        ensure!(
            !has_entry(&staged_dir, marker)?,
            "the module ships a {marker} marker"
        );
    }

    // The zip still lists files a metamodule installer moved away, the staged dir has files
    // that customize.sh created
    let boot_only = BootOnly::load()?;
    let zip_entries = zip_top_level_entries(zip).context("failed to read the module zip")?;
    if let Some(entry) = boot_only_entry(&staged_dir, &boot_only)?.or_else(|| {
        zip_entries
            .into_iter()
            .find(|name| boot_only.contains(name))
    }) {
        bail!("{entry} only takes effect at boot");
    }

    match in_effect(module_id) {
        // Like a first boot
        InEffect::No => Ok(()),
        InEffect::Unknown => bail!("cannot tell what of the installed version is running"),
        InEffect::Yes => {
            ensure_nothing_left_running(module_id, &boot_only)?;
            ensure!(
                sepolicy_rules(&Path::new(defs::MODULE_DIR).join(module_id))?
                    .is_subset(&sepolicy_rules(&staged_dir)?),
                "sepolicy.rule drops rules that stay loaded until a reboot"
            );
            Ok(())
        }
    }
}

fn run_late_stage_scripts(module_id: &str) {
    let live_dir = Path::new(defs::MODULE_DIR).join(module_id);
    for script in LATE_STAGE_SCRIPTS {
        let path = live_dir.join(script);
        if !path.exists() {
            continue;
        }
        println!("- Running {script}");
        // Give service.sh time to settle before boot-completed.sh, as boot does
        let wait = if *script == "service.sh" {
            ScriptWait::Until(Instant::now() + defs::HOT_INSTALL_SCRIPT_TIMEOUT)
        } else {
            ScriptWait::NoWait
        };
        let result = exec_script_with(&path, wait, |command| {
            // Same as the requested hot install script: keep background processes alive
            // once the installer's pipe closes
            command.stdin(Stdio::null());
            unsafe {
                command.pre_exec(|| {
                    libc::signal(libc::SIGPIPE, libc::SIG_IGN);
                    Ok(())
                });
            }
        });
        if let Err(e) = result {
            println!("! {e:#}");
        }
    }
}

/// Whether the install is still the installer's: it always leaves the module staged with an
/// update marker in the live dir. A metamodule that hot installs by itself swaps the dirs and
/// leaves a stub behind instead.
pub fn is_staged(module_id: &str) -> bool {
    Path::new(defs::MODULE_UPDATE_DIR).join(module_id).is_dir()
        && matches!(
            has_entry(
                &Path::new(defs::MODULE_DIR).join(module_id),
                defs::UPDATE_FILE_NAME
            ),
            Ok(true)
        )
}

/// Hot install a module that did not ask for it, when applying it now leaves the device as a
/// reboot would. Returns false if the caller should keep the regular update-on-reboot path.
pub fn apply_auto(
    module_id: &str,
    is_metamodule: bool,
    zip: &Path,
    boot_state_before: &BootState,
) -> bool {
    if !is_staged(module_id) {
        return false;
    }

    if let Err(e) = check_auto(module_id, is_metamodule, zip, boot_state_before) {
        println!("- Reboot to apply the module: {e:#}");
        return false;
    }

    // Nothing irreversible may happen before these succeed, the regular path stays intact.
    // Boot scripts run in init's mount namespace.
    if let Err(e) = switch_mnt_ns(1) {
        println!("- Reboot to apply the module: {e:#}");
        return false;
    }
    // Rules are additive and the module loads them at boot anyway
    let rule_file = Path::new(defs::MODULE_UPDATE_DIR)
        .join(module_id)
        .join(SEPOLICY_RULE);
    if rule_file.exists() && sepolicy::apply_file(&rule_file).is_err() {
        println!("- Reboot to apply the module: failed to load sepolicy.rule");
        return false;
    }
    if let Err(e) = swap(module_id) {
        println!("- Reboot to apply the module: {e:#}");
        return false;
    }
    println!("- Nothing in this module waits for boot, applied now");
    info!("Module {module_id} hot installed automatically");

    set_active_entry(module_id, module_id);
    run_late_stage_scripts(module_id);
    true
}
