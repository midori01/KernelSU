use super::ops;
use crate::defs::{DISABLE_FILE_NAME, MODULE_DIR, REMOVE_FILE_NAME, TOOLKIT_DIR, WORKING_DIR};
use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::fs::{self, DirBuilder, File};
use std::io::ErrorKind;
use std::os::unix::fs::DirBuilderExt;
use std::path::Path;

const CONFIG: &str = "config.json";
const LEGACY: [&str; 4] = [".manager_uid", ".manager_version", ".uname", ".umount_list"];

#[derive(Default, Deserialize, Serialize)]
#[serde(default)]
pub(super) struct Config {
    pub manager_uid: Option<u32>,
    pub manager_package: Option<String>,
    pub ksu_version: Option<u32>,
    pub uname: Option<Uname>,
    pub umount: Vec<String>,
    pub legacy_imported: bool,
}

#[derive(Clone, Deserialize, Serialize)]
pub(super) struct Uname {
    pub release: String,
    pub version: String,
}

impl Uname {
    pub fn validate(&self) -> Result<()> {
        for field in [&self.release, &self.version] {
            ensure!(field.len() <= 64, "uname fields must be at most 64 bytes");
            ensure!(!field.contains('\0'), "uname fields must not contain NUL");
        }
        Ok(())
    }
}

pub(super) fn load<T: DeserializeOwned>(name: &str) -> Result<Option<T>> {
    let bytes = match fs::read(Path::new(TOOLKIT_DIR).join(name)) {
        Err(err) if err.kind() == ErrorKind::NotFound => return Ok(None),
        result => result.with_context(|| format!("read {name}"))?,
    };
    serde_json::from_slice(&bytes)
        .map(Some)
        .with_context(|| format!("parse {name}"))
}

pub(super) fn read() -> Result<Config> {
    Ok(load(CONFIG)?.unwrap_or_default())
}

fn prepare(value: &impl Serialize) -> Result<tempfile::NamedTempFile> {
    let mut tmp = tempfile::NamedTempFile::new_in(TOOLKIT_DIR)?;
    serde_json::to_writer_pretty(&mut tmp, value)?;
    tmp.as_file().sync_all()?;
    Ok(tmp)
}

fn persist(tmp: tempfile::NamedTempFile, name: &str) -> Result<()> {
    tmp.persist(Path::new(TOOLKIT_DIR).join(name))
        .with_context(|| format!("save {name}"))?;
    Ok(())
}

pub(super) fn save(name: &str, value: &impl Serialize) -> Result<()> {
    persist(prepare(value)?, name)
}

pub(super) fn lock() -> Result<File> {
    lock_dir(true)
}

pub(super) fn status_lock() -> Result<File> {
    lock_dir(false)
}

fn lock_dir(create: bool) -> Result<File> {
    let file = match File::open(TOOLKIT_DIR) {
        Err(err) if err.kind() == ErrorKind::NotFound => {
            if create {
                DirBuilder::new()
                    .recursive(true)
                    .mode(0o700)
                    .create(WORKING_DIR)?;
            }
            let parent = File::open(WORKING_DIR)?;
            parent.lock().context("lock toolkit directory creation")?;
            match File::open(TOOLKIT_DIR) {
                Err(err) if err.kind() == ErrorKind::NotFound && create => {
                    DirBuilder::new().mode(0o700).create(TOOLKIT_DIR)?;
                    File::open(TOOLKIT_DIR)?
                }
                Err(err) if err.kind() == ErrorKind::NotFound => return Ok(parent),
                result => result?,
            }
        }
        result => result?,
    };
    file.lock().context("lock toolkit settings")?;
    Ok(file)
}

fn apply_and_save(cfg: &Config, apply: impl FnOnce() -> Result<()>) -> Result<()> {
    let tmp = prepare(cfg)?;
    apply()?;
    persist(tmp, CONFIG)
}

pub(super) fn update(
    change: impl FnOnce(&mut Config),
    apply: impl FnOnce() -> Result<()>,
) -> Result<()> {
    let _guard = lock()?;
    let mut cfg = read()?;
    change(&mut cfg);
    apply_and_save(&cfg, apply)
}

pub(super) fn edit(change: impl FnOnce(&mut Config)) -> Result<()> {
    update(change, || Ok(()))
}

fn manager_package(uid: u32) -> Result<String> {
    ops::validate_uid(uid)?;
    let mut matches = ops::packages()?
        .into_iter()
        .filter(|(_, appid)| *appid == uid);
    let (package, _) = matches
        .next()
        .with_context(|| format!("UID {uid} is not installed"))?;
    ensure!(
        matches.next().is_none(),
        "UID {uid} belongs to multiple packages"
    );
    Ok(package)
}

pub(super) fn save_manager(uid: Option<u32>) -> Result<()> {
    let _guard = lock()?;
    let mut cfg = read()?;
    cfg.manager_package = uid.map(manager_package).transpose()?;
    cfg.manager_uid = uid;
    save(CONFIG, &cfg)
}

pub(super) fn switch_manager(uid: u32, save_uid: bool, fallback: Option<u32>) -> Result<()> {
    ops::validate_uid(uid)?;
    let _guard = lock()?;
    let mut cfg = read()?;
    let target = if save_uid { Some(uid) } else { fallback };
    if target.is_none() && cfg.manager_uid.is_none_or(|saved| saved == uid) {
        return ops::set_uid(uid);
    }
    cfg.manager_package = target.map(manager_package).transpose()?;
    cfg.manager_uid = target;
    let previous = ops::manager_uid()
        .ok()
        .filter(|&uid| ops::valid_manager_uid(uid));
    let tmp = prepare(&cfg)?;
    ops::set_uid(uid)?;
    if let Err(err) = persist(tmp, CONFIG) {
        if let Some(previous) = previous
            && previous != uid
        {
            ops::set_uid(previous)
                .with_context(|| format!("{err:#}; restore manager UID {previous}"))?;
        }
        return Err(err);
    }
    Ok(())
}

pub(super) fn module_dir() -> &'static Path {
    Path::new(const_format::concatcp!(MODULE_DIR, "ksu_toolkit"))
}

pub(super) fn module_present(path: &Path) -> bool {
    path.is_dir() && !path.join(REMOVE_FILE_NAME).exists()
}

pub(super) fn module_enabled(path: &Path) -> bool {
    module_present(path) && !path.join(DISABLE_FILE_NAME).exists()
}

pub(super) fn legacy_exists() -> bool {
    LEGACY
        .iter()
        .any(|name| Path::new(WORKING_DIR).join(name).exists())
}

fn read_legacy(name: &str) -> Result<Option<String>> {
    match fs::read_to_string(Path::new(WORKING_DIR).join(name)) {
        Err(err) if err.kind() == ErrorKind::NotFound => Ok(None),
        result => result.map(Some).with_context(|| format!("read {name}")),
    }
}

fn legacy_number(name: &str) -> Result<Option<u32>> {
    let Some(content) = read_legacy(name)? else {
        return Ok(None);
    };
    content
        .lines()
        .next()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .map(str::parse)
        .transpose()
        .with_context(|| format!("parse {name}"))
}

pub(super) fn import_module() -> Result<()> {
    let _guard = lock()?;
    let module = module_dir();
    let present = module_present(module);
    let mut cfg = read()?;
    if !cfg.legacy_imported {
        ensure!(
            present || legacy_exists(),
            "ksu_toolkit module and legacy settings are missing"
        );
        merge_legacy(&mut cfg)?;
        cfg.legacy_imported = !present;
        save(CONFIG, &cfg)?;
    }
    if present && module_present(module) {
        match File::create_new(module.join(DISABLE_FILE_NAME)) {
            Err(err) if err.kind() == ErrorKind::AlreadyExists => return Ok(()),
            Err(err) if err.kind() == ErrorKind::NotFound && !module_present(module) => {}
            result => return result.map(drop).context("disable ksu_toolkit module"),
        }
    }
    if !cfg.legacy_imported {
        cfg.legacy_imported = true;
        save(CONFIG, &cfg)?;
    }
    Ok(())
}

fn merge_legacy(cfg: &mut Config) -> Result<()> {
    if cfg.manager_uid.is_none() {
        cfg.manager_uid = legacy_number(LEGACY[0])?;
        if let Some(uid) = cfg.manager_uid {
            ops::validate_uid(uid)?;
            cfg.manager_package = manager_package(uid).ok();
        }
    }
    if cfg.ksu_version.is_none() {
        cfg.ksu_version = legacy_number(LEGACY[1])?.filter(|&n| n != 0);
    }
    if cfg.uname.is_none()
        && let Some(content) = read_legacy(LEGACY[2])?
    {
        let mut release = None;
        let mut version = None;
        for line in content.lines().map(str::trim) {
            let Some((key, value)) = line.split_once('=') else {
                continue;
            };
            let value = value.trim();
            let value = match value.chars().next() {
                Some(quote @ ('"' | '\'')) => value
                    .strip_prefix(quote)
                    .and_then(|s| s.strip_suffix(quote)),
                _ => Some(value),
            };
            if let Some(value) = value {
                match key {
                    "RELEASE" => release = Some(value.to_owned()),
                    "VERSION" => version = Some(value.to_owned()),
                    _ => {}
                }
            }
        }
        if release.is_some() || version.is_some() {
            let uname = Uname {
                release: release.unwrap_or_default(),
                version: version.unwrap_or_default(),
            };
            uname.validate()?;
            cfg.uname = Some(uname);
        }
    }
    if let Some(content) = read_legacy(LEGACY[3])? {
        for path in content.lines().map(str::trim) {
            if !path.is_empty()
                && !path.starts_with('#')
                && !cfg.umount.iter().any(|entry| entry == path)
            {
                cfg.umount.push(ops::validate_mount_path(path)?.to_owned());
            }
        }
    }
    Ok(())
}
