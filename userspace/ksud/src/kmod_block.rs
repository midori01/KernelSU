use android_bootimg::cpio::Cpio;
use android_bootimg::parser::BootImage;
use anyhow::{Context, Result};
use clap::Subcommand;
use serde::Serialize;
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

use crate::{boot_patch, defs, ksucalls};

const KERNEL_MODULES: [&str; 2] = ["ksu", "kernelsu"];
const BLOCKED_MODULES_MARKERS: [&str; 2] = [
    "module_blacklist: blocking modules: ",
    "module_blacklist: blocked modules: ",
];
// ReSukiSU prints its "blocked modules" line only when its insmod hooks are built
// (tracepoint or manual), so it also implies the guard.
const GUARD_VARIANT_AVAILABLE_MARKERS: [&str; 3] = [
    "ksu_unhook_syscall_init_module: kthread init!",
    "module_blacklist: syscall hooked",
    "module_blacklist: blocked modules: ",
];

#[derive(Subcommand, Debug)]
pub enum KmodBlockCommands {
    /// Show saved and effective module blocklist state
    Status,

    /// Save and apply a module blocklist, or clear it with an empty value
    Set {
        #[arg(long)]
        modules: String,
    },
}

#[allow(clippy::struct_excessive_bools)]
#[derive(Serialize)]
struct StatusOutput {
    saved: Vec<String>,
    effective: String,
    guard_available: bool,
    guard_armed: bool,
    is_lkm: bool,
    first_stage: Vec<String>,
    first_stage_known: bool,
    first_stage_dependents: BTreeMap<String, Vec<String>>,
    needs_reboot: bool,
    log_ok: bool,
}

fn load_saved_modules() -> Vec<String> {
    let Ok(content) = std::fs::read(defs::KMOD_BLOCK_MODULES_PATH) else {
        return Vec::new();
    };
    parse_saved_modules(&String::from_utf8_lossy(&content))
}

fn parse_saved_modules(content: &str) -> Vec<String> {
    content
        .trim()
        .split(',')
        .filter_map(|entry| {
            let entry = entry.trim();
            (!entry.is_empty()).then(|| entry.to_owned())
        })
        .collect()
}

fn normalize_module_name(name: &str) -> Option<String> {
    let name = Path::new(name).file_name()?.to_str()?;
    let name = name.strip_suffix(".ko").unwrap_or(name);
    Some(name.replace('-', "_"))
}

fn parse_modules_load(content: &str) -> BTreeSet<String> {
    content
        .lines()
        .filter_map(|line| normalize_module_name(line.trim()))
        .collect()
}

fn parse_modules_blocklist(content: &str) -> BTreeSet<String> {
    content
        .lines()
        .filter_map(|line| {
            let mut fields = line.split_ascii_whitespace();
            (fields.next()? == "blocklist")
                .then(|| normalize_module_name(fields.next()?))
                .flatten()
        })
        .collect()
}

#[allow(clippy::struct_excessive_bools)]
struct BootLogInfo {
    effective: String,
    guard_available: bool,
    guard_armed: bool,
    log_ok: bool,
    has_effective: bool,
}

fn parse_boot_log(content: &str) -> BootLogInfo {
    let mut effective = String::new();
    let mut has_effective = false;
    for line in content.lines() {
        for marker in BLOCKED_MODULES_MARKERS {
            if let Some((_, value)) = line.split_once(marker) {
                value.trim().clone_into(&mut effective);
                has_effective = true;
            }
        }
    }

    let log_ok = first_log_timestamp(content).is_some_and(|seconds| seconds < 1.0)
        && content.contains("Linux version ");
    let variant_markers = log_ok
        && GUARD_VARIANT_AVAILABLE_MARKERS
            .iter()
            .any(|marker| content.contains(marker));
    BootLogInfo {
        effective,
        guard_available: variant_markers
            || log_ok && content.contains("module_blacklist: guard available"),
        guard_armed: variant_markers || log_ok && content.contains("module_blacklist: guard armed"),
        log_ok,
        has_effective,
    }
}

fn first_log_timestamp(content: &str) -> Option<f64> {
    for line in content.lines() {
        let Some(start) = line.find('[') else {
            continue;
        };
        let Some(offset) = line[start + 1..].find(']') else {
            continue;
        };
        let end = start + 1 + offset;
        if let Ok(seconds) = line[start + 1..end].trim().parse() {
            return Some(seconds);
        }
    }
    None
}

fn load_boot_log() -> BootLogInfo {
    let path = Path::new(defs::LOG_DIR).join("dmesg.log");
    std::fs::read_to_string(path).map_or_else(
        |_| BootLogInfo {
            effective: String::new(),
            guard_available: false,
            guard_armed: false,
            log_ok: false,
            has_effective: false,
        },
        |content| parse_boot_log(&content),
    )
}

fn cpio_text(cpio: &Cpio, name: &str) -> Result<Option<String>> {
    let Some(entry) = cpio.entry_by_name(name) else {
        return Ok(None);
    };
    let data = entry.data().unwrap_or_default();
    if data.is_empty() {
        return Ok(None);
    }
    let content = std::str::from_utf8(data)
        .with_context(|| format!("{name} is not valid UTF-8"))?
        .to_owned();
    Ok(Some(content))
}

#[derive(Default)]
struct FirstStageScan {
    modules: BTreeSet<String>,
    dependencies: Vec<String>,
    has_load: bool,
}

struct FirstStageSummary {
    known: bool,
    modules: Vec<String>,
    dependents: BTreeMap<String, Vec<String>>,
}

fn scan_first_stage(cpios: &[Cpio]) -> Result<FirstStageScan> {
    let mut modules = BTreeSet::new();
    let mut blocked = BTreeSet::new();
    let mut dependencies = Vec::new();
    let mut has_load = false;
    for cpio in cpios {
        for name in [
            "lib/modules/modules.load",
            "lib/modules/modules.load.recovery",
        ] {
            has_load |= cpio.exists(name);
            if let Some(content) = cpio_text(cpio, name)? {
                modules.extend(parse_modules_load(&content));
            }
        }
        if let Some(content) = cpio_text(cpio, "lib/modules/modules.blocklist")? {
            blocked.extend(parse_modules_blocklist(&content));
        }
        if let Some(content) = cpio_text(cpio, "lib/modules/modules.dep")? {
            dependencies.push(content);
        }
    }
    modules.retain(|name| !blocked.contains(name));
    Ok(FirstStageScan {
        modules,
        dependencies,
        has_load,
    })
}

fn image_cpios(path: &Path) -> Result<Vec<Cpio>> {
    let data = boot_patch::map_file(path)?;
    let image = BootImage::parse(&data)?;
    let ramdisk = image
        .get_blocks()
        .get_ramdisk()
        .context("boot image has no ramdisk")?;
    let mut cpios = Vec::new();
    if ramdisk.is_vendor_ramdisk() {
        for fragment in ramdisk.iter_vendor_ramdisk() {
            let mut content = Vec::new();
            fragment.dump(&mut content, false)?;
            if !content.is_empty() {
                cpios.push(Cpio::load_from_data(&content)?);
            }
        }
    } else {
        let mut content = Vec::new();
        ramdisk.dump(&mut content, false)?;
        if !content.is_empty() {
            cpios.push(Cpio::load_from_data(&content)?);
        }
    }
    Ok(cpios)
}

fn scan_partition_first_stage(image: &Path) -> Result<FirstStageScan> {
    scan_first_stage(&image_cpios(image)?)
}

fn parse_modules_dep(content: &str) -> BTreeMap<String, BTreeSet<String>> {
    let mut graph = BTreeMap::<String, BTreeSet<String>>::new();
    for line in content.lines() {
        let Some((dependent, dependencies)) = line.split_once(':') else {
            continue;
        };
        let Some(dependent) = normalize_module_name(dependent.trim()) else {
            continue;
        };
        let dependencies = dependencies
            .split_ascii_whitespace()
            .filter_map(normalize_module_name)
            .collect::<BTreeSet<_>>();
        if !dependencies.is_empty() {
            graph.entry(dependent).or_default().extend(dependencies);
        }
    }
    graph
}

fn build_first_stage_dependents(
    modules: &BTreeSet<String>,
    dependency_contents: &[String],
) -> BTreeMap<String, Vec<String>> {
    let mut graph = BTreeMap::<String, BTreeSet<String>>::new();
    for content in dependency_contents {
        for (dependent, dependencies) in parse_modules_dep(content) {
            graph.entry(dependent).or_default().extend(dependencies);
        }
    }

    let mut dependents = BTreeMap::<String, BTreeSet<String>>::new();
    for dependent in modules {
        let mut pending = graph.get(dependent).cloned().unwrap_or_default();
        let mut visited = BTreeSet::from([dependent.clone()]);
        while let Some(dependency) = pending.pop_first() {
            if !visited.insert(dependency.clone()) {
                continue;
            }
            dependents
                .entry(dependency.clone())
                .or_default()
                .insert(dependent.clone());
            if let Some(next) = graph.get(&dependency) {
                pending.extend(next.iter().cloned());
            }
        }
    }
    dependents
        .into_iter()
        .map(|(module, dependents)| (module, dependents.into_iter().collect()))
        .collect()
}

fn assemble_first_stage(
    vendor_results: impl IntoIterator<Item = Result<FirstStageScan>>,
    boot_image: Option<&Path>,
) -> FirstStageSummary {
    let mut known = false;
    let mut failed = false;
    let mut modules = BTreeSet::new();
    let mut dependencies = Vec::new();
    for result in vendor_results {
        let Ok(scan) = result else {
            failed = true;
            continue;
        };
        known |= scan.has_load;
        modules.extend(scan.modules);
        dependencies.extend(scan.dependencies);
    }

    if modules.is_empty()
        && let Some(boot_image) = boot_image
    {
        match scan_partition_first_stage(boot_image) {
            Ok(scan) => {
                known |= scan.has_load;
                modules.extend(scan.modules);
                dependencies.extend(scan.dependencies);
            }
            Err(_) => failed = true,
        }
    }

    let dependents = build_first_stage_dependents(&modules, &dependencies);
    FirstStageSummary {
        known: known && !failed,
        modules: modules.into_iter().collect(),
        dependents,
    }
}

fn load_first_stage() -> FirstStageSummary {
    let suffix = boot_patch::get_slot_suffix(false);
    let vendor_results = ["vendor_boot", "vendor_kernel_boot"]
        .into_iter()
        .filter_map(|name| {
            let image = PathBuf::from(format!("/dev/block/by-name/{name}{suffix}"));
            image.exists().then(|| scan_partition_first_stage(&image))
        })
        .collect::<Vec<_>>();
    let boot = PathBuf::from(format!("/dev/block/by-name/boot{suffix}"));
    let boot = boot.exists().then_some(boot);
    assemble_first_stage(vendor_results, boot.as_deref())
}

fn cmdline_block_modules(cmdline: &str) -> Option<String> {
    cmdline.split_ascii_whitespace().find_map(|token| {
        token
            .strip_prefix("kernelsu.block_modules=")
            .map(ToOwned::to_owned)
    })
}

fn effective_block_modules(
    log: &BootLogInfo,
    is_lkm: bool,
    cmdline_modules: Option<String>,
) -> String {
    if !is_lkm && !log.has_effective {
        cmdline_modules.unwrap_or_else(|| log.effective.clone())
    } else {
        log.effective.clone()
    }
}

fn needs_reboot(saved: &[String], effective: &str, log_ok: bool, cmdline: Option<&str>) -> bool {
    if !log_ok {
        return false;
    }
    let cmdline_modules = cmdline
        .into_iter()
        .flat_map(str::split_ascii_whitespace)
        .filter_map(|token| token.strip_prefix("module_blacklist="))
        .flat_map(|list| list.split(','))
        .filter(|name| !name.is_empty())
        .collect::<BTreeSet<_>>();
    let saved_state = module_state(saved.iter().map(String::as_str));
    let effective_state = effective
        .split(',')
        .filter(|name| {
            !name.is_empty() && !KERNEL_MODULES.contains(name) && !cmdline_modules.contains(name)
        })
        .collect();
    saved_state != effective_state
}

fn module_state<'a>(modules: impl Iterator<Item = &'a str>) -> BTreeSet<&'a str> {
    modules
        .filter(|name| !name.is_empty() && !KERNEL_MODULES.contains(name))
        .collect()
}

fn status() -> Result<()> {
    let saved = load_saved_modules();
    let log = load_boot_log();
    let is_lkm = ksucalls::is_lkm();
    let cmdline = std::fs::read_to_string("/proc/cmdline").ok();
    let effective = effective_block_modules(
        &log,
        is_lkm,
        cmdline.as_deref().and_then(cmdline_block_modules),
    );

    let needs_reboot = needs_reboot(&saved, &effective, log.log_ok, cmdline.as_deref());
    let first_stage = load_first_stage();
    let output = StatusOutput {
        saved,
        effective,
        guard_available: log.guard_available,
        guard_armed: log.guard_armed,
        is_lkm,
        first_stage: first_stage.modules,
        first_stage_known: first_stage.known,
        first_stage_dependents: first_stage.dependents,
        needs_reboot,
        log_ok: log.log_ok,
    };
    println!("{}", serde_json::to_string(&output)?);
    Ok(())
}

fn set(input: &str) -> Result<()> {
    let modules = if input.is_empty() {
        Vec::new()
    } else {
        input
            .split(',')
            .map(boot_patch::parse_block_module)
            .collect::<Result<Vec<_>>>()?
    };
    boot_patch::validate_block_modules(&modules)?;

    if ksucalls::is_lkm() {
        boot_patch::apply_lkm_block_modules(&modules)?;
    } else {
        boot_patch::apply_builtin_block_modules(&modules)?;
    }

    std::fs::create_dir_all(defs::WORKING_DIR)?;
    std::fs::write(defs::KMOD_BLOCK_MODULES_PATH, modules.join(","))
        .with_context(|| format!("save {}", defs::KMOD_BLOCK_MODULES_PATH))?;
    println!("Reboot is required.");
    Ok(())
}

pub fn run(command: KmodBlockCommands) -> Result<()> {
    match command {
        KmodBlockCommands::Status => status(),
        KmodBlockCommands::Set { modules } => set(&modules),
    }
}

#[cfg(test)]
mod tests {
    use super::{
        StatusOutput, assemble_first_stage, build_first_stage_dependents, cmdline_block_modules,
        effective_block_modules, first_log_timestamp, needs_reboot, parse_boot_log,
        parse_modules_blocklist, parse_modules_load, parse_saved_modules, scan_first_stage,
    };
    use android_bootimg::cpio::{Cpio, CpioEntry};
    use std::collections::{BTreeMap, BTreeSet};

    #[test]
    fn parses_guard_and_effective_modules_from_boot_log() {
        let log = "\
<6>[    0.000000] Linux version 6.12.0-test (builder)
<6>[    3.55] KernelSU: module_blacklist: guard available
<6>[    3.56] KernelSU: module_blacklist: blocking modules: first
<6>[    3.57] KernelSU: module_blacklist: guard armed
<6>[    3.58] KernelSU: module_blacklist: blocking modules: second,third
";
        let info = parse_boot_log(log);
        assert_eq!(info.effective, "second,third");
        assert!(info.guard_available);
        assert!(info.guard_armed);
        assert!(info.log_ok);
        assert!(info.has_effective);
    }

    #[test]
    fn parses_xx_guard_markers() {
        let log = "\
<6>[    0.000000] Linux version 6.12.0-test (builder)
<6>[    0.99] KernelSU: Initialized on late-load
<6>[    1.00] KernelSU: ksu_unhook_syscall_init_module: kthread init!
<6>[    1.01] KernelSU: module_blacklist: blocking modules: xx_mod
";
        let info = parse_boot_log(log);
        assert_eq!(info.effective, "xx_mod");
        assert!(info.guard_available);
        assert!(info.guard_armed);
        assert!(info.log_ok);
    }

    #[test]
    fn parses_resukisu_guard_markers() {
        let log = "\
<6>[    0.000000] Linux version 6.12.0-test (builder)
<6>[    1.00] KernelSU: module_blacklist: syscall hooked
<6>[    1.01] KernelSU: module_blacklist: blocked modules: resuki_mod
";
        let info = parse_boot_log(log);
        assert_eq!(info.effective, "resuki_mod");
        assert!(info.guard_available);
        assert!(info.guard_armed);
        assert!(info.log_ok);
    }

    #[test]
    fn empty_boot_log_has_no_guard_or_modules() {
        let info = parse_boot_log("");
        assert!(info.effective.is_empty());
        assert!(!info.guard_available);
        assert!(!info.guard_armed);
        assert!(!info.log_ok);
        assert!(!info.has_effective);
    }

    #[test]
    fn wrapped_log_disables_guard_state() {
        let log = "\
<6>[ 3273.612345] Linux version 6.12.0-test (builder)
<6>[ 3274.00] KernelSU: module_blacklist: guard available
<6>[ 3275.00] KernelSU: module_blacklist: guard armed
<6>[ 3276.00] KernelSU: module_blacklist: blocking modules: stale
";
        let info = parse_boot_log(log);
        assert_eq!(info.effective, "stale");
        assert!(!info.guard_available);
        assert!(!info.guard_armed);
        assert!(!info.log_ok);
        assert!(info.has_effective);
    }

    #[test]
    fn skips_non_numeric_bracketed_log_prefixes() {
        let log = "[not-a-timestamp] ignored\n<6>[    0.250000] Linux version 6.12.0-test\n";
        assert_eq!(first_log_timestamp(log), Some(0.25));
    }

    #[test]
    fn extracts_block_modules_from_proc_cmdline() {
        assert_eq!(
            cmdline_block_modules("console=ttynull kernelsu.block_modules=one,two quiet"),
            Some("one,two".to_owned())
        );
        assert_eq!(cmdline_block_modules("console=ttynull quiet"), None);
    }

    #[test]
    fn cmdline_fallback_applies_only_to_builtin_without_log_value() {
        let log = parse_boot_log("<6>[    0.000000] Linux version 6.12.0-test\n");
        assert_eq!(
            effective_block_modules(&log, false, Some("fallback".to_owned())),
            "fallback"
        );
        assert_eq!(
            effective_block_modules(&log, true, Some("fallback".to_owned())),
            ""
        );

        let log = parse_boot_log(
            "<6>[    0.000000] Linux version 6.12.0-test\nKernelSU: module_blacklist: blocking modules: log_value\n",
        );
        assert_eq!(
            effective_block_modules(&log, false, Some("fallback".to_owned())),
            "log_value"
        );
    }

    #[test]
    fn needs_reboot_ignores_cmdline_module_blacklist() {
        let saved = vec!["saved".to_owned()];
        let effective = "saved,vendor_a,vendor_b,ksu,kernelsu";
        let cmdline = "console=ttynull module_blacklist=vendor_a quiet module_blacklist=vendor_b";

        assert!(!needs_reboot(&saved, effective, true, Some(cmdline)));
        assert!(needs_reboot(&saved, effective, true, None));
        assert!(!needs_reboot(&saved, effective, false, None));
    }

    #[test]
    fn trims_saved_module_list() {
        assert_eq!(
            parse_saved_modules("  alpha, beta ,\n gamma,, "),
            ["alpha".to_owned(), "beta".to_owned(), "gamma".to_owned()]
        );
    }

    #[test]
    fn normalizes_load_and_blocklist_names() {
        let mut modules = parse_modules_load(
            "lib/modules/foo-bar.ko\nlib/modules/kept.ko\nlib/modules/foo-bar.ko\n",
        );
        modules.extend(parse_modules_load("lib/modules/recovery.ko\n"));
        let blocked =
            parse_modules_blocklist("blocklist foo-bar.ko\nblocklist  recovery.ko\nignored\n");

        assert_eq!(
            blocked,
            BTreeSet::from(["foo_bar".to_owned(), "recovery".to_owned()])
        );
        modules.retain(|name| !blocked.contains(name));
        assert_eq!(modules.into_iter().collect::<Vec<_>>(), ["kept".to_owned()]);
    }

    #[test]
    fn scans_first_stage_union_from_cpios() {
        let mut first = Cpio::new();
        first
            .add(
                "lib/modules/modules.load",
                CpioEntry::regular(0o644, Box::new("first.ko")),
            )
            .unwrap();
        first
            .add(
                "lib/modules/modules.blocklist",
                CpioEntry::regular(0o644, Box::new("blocklist blocked.ko")),
            )
            .unwrap();
        let mut second = Cpio::new();
        second
            .add(
                "lib/modules/modules.load.recovery",
                CpioEntry::regular(0o644, Box::new("recovery-mod.ko\nblocked.ko")),
            )
            .unwrap();

        let scan = scan_first_stage(&[first, second]).unwrap();
        assert!(scan.has_load);
        assert_eq!(
            scan.modules.into_iter().collect::<Vec<_>>(),
            ["first".to_owned(), "recovery_mod".to_owned()]
        );
    }

    #[test]
    fn unions_partitions_after_applying_their_own_blocklists() {
        let mut vendor_boot = Cpio::new();
        vendor_boot
            .add(
                "lib/modules/modules.load",
                CpioEntry::regular(0o644, Box::new("first_a.ko\nshared.ko")),
            )
            .unwrap();
        vendor_boot
            .add(
                "lib/modules/modules.blocklist",
                CpioEntry::regular(0o644, Box::new("blocklist first_a.ko")),
            )
            .unwrap();

        let mut vendor_kernel_boot = Cpio::new();
        vendor_kernel_boot
            .add(
                "lib/modules/modules.load",
                CpioEntry::regular(0o644, Box::new("first_a.ko\nsecond_b.ko\nshared.ko")),
            )
            .unwrap();

        let first = scan_first_stage(&[vendor_boot]).unwrap();
        let second = scan_first_stage(&[vendor_kernel_boot]).unwrap();
        let summary = assemble_first_stage([Ok(first), Ok(second)], None);
        assert!(summary.known);
        assert_eq!(
            summary.modules,
            [
                "first_a".to_owned(),
                "second_b".to_owned(),
                "shared".to_owned()
            ]
        );
    }

    #[test]
    fn first_stage_known_is_false_on_parse_failure() {
        let summary = assemble_first_stage([Err(anyhow::anyhow!("invalid boot image"))], None);
        assert!(!summary.known);
        assert!(summary.modules.is_empty());
        assert!(summary.dependents.is_empty());
    }

    #[test]
    fn first_stage_known_is_false_on_partial_failure() {
        let mut cpio = Cpio::new();
        cpio.add(
            "lib/modules/modules.load",
            CpioEntry::regular(0o644, Box::new("lib/modules/kept.ko\n")),
        )
        .unwrap();
        let scan = scan_first_stage(&[cpio]).unwrap();
        let summary =
            assemble_first_stage([Ok(scan), Err(anyhow::anyhow!("invalid boot image"))], None);
        assert!(!summary.known);
        assert_eq!(summary.modules, ["kept".to_owned()]);
    }

    #[test]
    fn builds_transitive_normalized_module_dependents() {
        let modules = BTreeSet::from(["app_a".to_owned(), "mid_b".to_owned()]);
        let dependencies = vec![
            "lib/modules/app-a.ko: lib/modules/mid-b.ko".to_owned(),
            "lib/modules/mid-b.ko: lib/modules/root-c.ko\nlib/modules/other.ko: lib/modules/root-c.ko"
                .to_owned(),
        ];
        assert_eq!(
            build_first_stage_dependents(&modules, &dependencies),
            BTreeMap::from([
                ("mid_b".to_owned(), vec!["app_a".to_owned()]),
                (
                    "root_c".to_owned(),
                    vec!["app_a".to_owned(), "mid_b".to_owned()]
                )
            ])
        );
    }

    #[test]
    fn cyclic_dependencies_do_not_list_module_as_its_own_dependent() {
        let modules = BTreeSet::from(["a".to_owned(), "b".to_owned()]);
        let dependencies = vec!["a.ko: b.ko\nb.ko: a.ko".to_owned()];
        assert_eq!(
            build_first_stage_dependents(&modules, &dependencies),
            BTreeMap::from([
                ("a".to_owned(), vec!["b".to_owned()]),
                ("b".to_owned(), vec!["a".to_owned()]),
            ])
        );
    }

    #[test]
    fn resukisu_manual_hook_blocked_line_implies_guard() {
        let info = parse_boot_log(
            "<6>[    0.000000] Linux version 6.12.0-test\nKernelSU: module_blacklist: blocked modules: vr,kernelsu\n",
        );
        assert!(info.guard_available);
        assert!(info.guard_armed);
        assert_eq!(info.effective, "vr,kernelsu");
    }

    #[test]
    fn status_json_contract_has_expected_fields() {
        let output = StatusOutput {
            saved: Vec::new(),
            effective: String::new(),
            guard_available: false,
            guard_armed: false,
            is_lkm: false,
            first_stage: Vec::new(),
            first_stage_known: false,
            first_stage_dependents: BTreeMap::new(),
            needs_reboot: false,
            log_ok: false,
        };
        assert_eq!(
            serde_json::to_value(output).unwrap(),
            serde_json::json!({
                "saved": [],
                "effective": "",
                "guard_available": false,
                "guard_armed": false,
                "is_lkm": false,
                "first_stage": [],
                "first_stage_known": false,
                "first_stage_dependents": {},
                "needs_reboot": false,
                "log_ok": false
            })
        );
    }
}
