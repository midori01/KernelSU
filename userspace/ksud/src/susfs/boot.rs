use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use super::config::{CmdlineMode, HideMountsMode, KstatMode, SusfsConfig, load_config};
use super::ops;
use crate::defs::SUSFS_DIR;

static EARLY_APPLIED: AtomicBool = AtomicBool::new(false);

pub const BOOT_RECORD_FILE: &str = "boot.json";
pub const CMDLINE_SPOOF_FILE: &str = "cmdline_spoof";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BootRecord {
    pub time: u64,
    pub applied: usize,
    pub failed: usize,
    pub errors: Vec<BootErrorItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BootErrorItem {
    pub item: String,
    pub error: String,
}

impl BootRecord {
    pub fn new() -> Self {
        let time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        Self {
            time,
            applied: 0,
            failed: 0,
            errors: Vec::new(),
        }
    }

    pub fn load_or_new() -> Self {
        let path = get_boot_record_path();
        std::fs::read_to_string(&path)
            .ok()
            .and_then(|content| serde_json::from_str::<Self>(&content).ok())
            .unwrap_or_else(Self::new)
    }

    pub const fn record_success(&mut self, _item: &str) {
        self.applied += 1;
    }

    pub fn record_failure(&mut self, item: &str, error: &str) {
        self.failed += 1;
        self.errors.push(BootErrorItem {
            item: item.to_string(),
            error: error.to_string(),
        });
    }

    pub fn save(&self) {
        let path = get_boot_record_path();
        let parent = path.parent().unwrap_or_else(|| Path::new("."));
        if !parent.exists() {
            let _ = std::fs::create_dir_all(parent);
            let _ = std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
        }
        if let Ok(bytes) = serde_json::to_vec_pretty(self) {
            let tmp = parent.join(".boot.json.tmp");
            if std::fs::write(&tmp, bytes).is_ok() {
                let _ = std::fs::rename(tmp, path);
            }
        }
    }
}

use std::os::unix::fs::PermissionsExt;

pub fn get_boot_record_path() -> PathBuf {
    Path::new(SUSFS_DIR).join(BOOT_RECORD_FILE)
}

pub fn get_cmdline_spoof_path() -> PathBuf {
    Path::new(SUSFS_DIR).join(CMDLINE_SPOOF_FILE)
}

pub fn rewrite_cmdline(input: &str) -> String {
    let mut out_tokens = Vec::new();
    let mut found_state = false;
    let mut found_device = false;

    for token in input.split_whitespace() {
        if token.starts_with("androidboot.verifiedbooterror")
            || token.starts_with("androidboot.verifyerrorpart")
        {
            continue;
        }
        if token.starts_with("androidboot.verifiedbootstate=") {
            out_tokens.push("androidboot.verifiedbootstate=green".to_string());
            found_state = true;
        } else if token.starts_with("androidboot.vbmeta.device_state=") {
            out_tokens.push("androidboot.vbmeta.device_state=locked".to_string());
            found_device = true;
        } else {
            out_tokens.push(token.to_string());
        }
    }

    if !found_state {
        out_tokens.push("androidboot.verifiedbootstate=green".to_string());
    }
    if !found_device {
        out_tokens.push("androidboot.vbmeta.device_state=locked".to_string());
    }

    out_tokens.join(" ")
}

pub fn rewrite_bootconfig(input: &str) -> String {
    let mut out_lines = Vec::new();
    let mut found_state = false;
    let mut found_device = false;
    let mut found_bootreason = false;

    for line in input.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("androidboot.verifiedbooterror")
            || trimmed.starts_with("androidboot.verifyerrorpart")
        {
            continue;
        }
        if trimmed.starts_with("androidboot.vbmeta.device_state") {
            out_lines.push("androidboot.vbmeta.device_state = \"locked\"".to_string());
            found_device = true;
        } else if trimmed.starts_with("androidboot.verifiedbootstate") {
            out_lines.push("androidboot.verifiedbootstate = \"green\"".to_string());
            found_state = true;
        } else if trimmed.starts_with("androidboot.bootreason") {
            out_lines.push("androidboot.bootreason = \"reboot\"".to_string());
            found_bootreason = true;
        } else {
            out_lines.push(line.to_string());
        }
    }

    if !found_state {
        out_lines.push("androidboot.verifiedbootstate = \"green\"".to_string());
    }
    if !found_device {
        out_lines.push("androidboot.vbmeta.device_state = \"locked\"".to_string());
    }
    if !found_bootreason {
        out_lines.push("androidboot.bootreason = \"reboot\"".to_string());
    }

    out_lines.join("\n")
}

fn apply_early_stage(config: &SusfsConfig) {
    let mut record = BootRecord::new();
    record.save();

    // 1. Log
    match ops::set_log(config.log) {
        Ok(()) => record.record_success("log"),
        Err(e) => record.record_failure("log", &e.to_string()),
    }

    // 2. AVC log spoofing
    match ops::set_avc_log_spoofing(config.avc_log_spoofing) {
        Ok(()) => record.record_success("avc"),
        Err(e) => record.record_failure("avc", &e.to_string()),
    }

    // 3. Hide SUS mounts
    let hide_mounts_val = matches!(
        config.hide_sus_mounts,
        HideMountsMode::Always | HideMountsMode::Boot
    );
    match ops::hide_sus_mnts_for_non_su_procs(hide_mounts_val) {
        Ok(()) => record.record_success("hide_mounts"),
        Err(e) => record.record_failure("hide_mounts", &e.to_string()),
    }

    // 4. Uname
    if !config.uname.release.is_empty() || !config.uname.version.is_empty() {
        match ops::set_uname(&config.uname.release, &config.uname.version) {
            Ok(()) => record.record_success("uname"),
            Err(e) => record.record_failure("uname", &e.to_string()),
        }
    }

    // 5. Cmdline
    match config.cmdline.mode {
        CmdlineMode::Off => {}
        CmdlineMode::Auto => {
            let spoof_res = (|| -> anyhow::Result<()> {
                let bootconfig_path = Path::new("/proc/bootconfig");
                let cmdline_path = Path::new("/proc/cmdline");
                let content = if bootconfig_path.exists() {
                    let raw = std::fs::read_to_string(bootconfig_path)?;
                    rewrite_bootconfig(&raw)
                } else if cmdline_path.exists() {
                    let raw = std::fs::read_to_string(cmdline_path)?;
                    rewrite_cmdline(&raw)
                } else {
                    anyhow::bail!("neither /proc/bootconfig nor /proc/cmdline exists");
                };

                let spoof_file = get_cmdline_spoof_path();
                let parent = spoof_file.parent().unwrap_or_else(|| Path::new("."));
                if !parent.exists() {
                    std::fs::create_dir_all(parent)?;
                    let _ =
                        std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
                }
                std::fs::write(&spoof_file, content.as_bytes())?;
                ops::set_cmdline_or_bootconfig_from_content(content.as_bytes())
                    .map_err(|e| anyhow::anyhow!("{e}"))?;
                Ok(())
            })();

            match spoof_res {
                Ok(()) => record.record_success("cmdline auto"),
                Err(e) => record.record_failure("cmdline auto", &e.to_string()),
            }
        }
        CmdlineMode::File => {
            let p = Path::new(&config.cmdline.file);
            match ops::set_cmdline_or_bootconfig_from_file(p) {
                Ok(()) => record.record_success(&format!("cmdline file {}", p.display())),
                Err(e) => {
                    record.record_failure(&format!("cmdline file {}", p.display()), &e.to_string());
                }
            }
        }
    }

    // 6. Sus kstat: static entries add_sus_kstat_statically, dynamic/full_clone entries add_sus_kstat
    for kstat in &config.sus_kstats {
        let p = Path::new(&kstat.path);
        match kstat.mode {
            KstatMode::Static => {
                let empty_static = super::config::SusKstatStatic::default();
                let s = kstat.r#static.as_ref().unwrap_or(&empty_static);
                match ops::add_sus_kstat_statically(p, s) {
                    Ok(()) => record.record_success(&format!("kstat_static {}", kstat.path)),
                    Err(e) => {
                        record.record_failure(
                            &format!("kstat_static {}", kstat.path),
                            &e.to_string(),
                        );
                    }
                }
            }
            KstatMode::Dynamic | KstatMode::FullClone => match ops::add_sus_kstat(p) {
                Ok(()) => record.record_success(&format!("kstat_early {}", kstat.path)),
                Err(e) => {
                    record.record_failure(&format!("kstat_early {}", kstat.path), &e.to_string());
                }
            },
        }
    }

    record.save();
    EARLY_APPLIED.store(true, Ordering::SeqCst);
}

pub fn on_post_fs_data_early() {
    let _ = std::fs::remove_file(get_boot_record_path());

    let config = match load_config() {
        Ok(c) => c,
        Err(e) => {
            warn!("SUSFS failed to load config: {e}");
            return;
        }
    };

    if !config.enabled {
        info!("SUSFS disabled by config, skipping boot apply");
        return;
    }

    if !ops::is_available() {
        info!("SUSFS not available at early post-fs-data");
        EARLY_APPLIED.store(false, Ordering::SeqCst);
        return;
    }

    apply_early_stage(&config);
}

pub fn on_post_fs_data_late() {
    if EARLY_APPLIED.load(Ordering::SeqCst) {
        return;
    }

    let config = match load_config() {
        Ok(c) => c,
        Err(e) => {
            warn!("SUSFS failed to load config: {e}");
            return;
        }
    };

    if !config.enabled {
        return;
    }

    if !ops::is_available() {
        info!("SUSFS still not available at late post-fs-data");
        return;
    }

    info!("SUSFS detected at late post-fs-data, running early stage rules");
    apply_early_stage(&config);
}

pub fn on_boot_completed() {
    let config = match load_config() {
        Ok(c) => c,
        Err(e) => {
            warn!("SUSFS failed to load config: {e}");
            return;
        }
    };

    if !config.enabled {
        info!("SUSFS disabled by config, skipping boot-completed apply");
        return;
    }

    match crate::utils::create_daemon(true) {
        Ok(true) => {
            run_boot_completed_worker(&config);
            std::process::exit(0);
        }
        Ok(false) => {}
        Err(e) => warn!("SUSFS failed to start boot-completed worker: {e:#}"),
    }
}

fn run_boot_completed_worker(config: &SusfsConfig) {
    if !ops::is_available() {
        info!("SUSFS not available at boot-completed");
        return;
    }

    // Wait for /sdcard/Android up to 120 s
    let start = Instant::now();
    let timeout = Duration::from_secs(120);
    while !Path::new("/sdcard/Android").is_dir() {
        if start.elapsed() >= timeout {
            warn!("SUSFS boot-completed timed out waiting for /sdcard/Android");
            break;
        }
        std::thread::sleep(Duration::from_millis(500));
    }

    // Leftovers cleanup if any sus_path
    if !config.sus_paths.is_empty() {
        let leftovers = [
            "/sdcard/..5.u.S",
            "/sdcard/Android/data/..5.u.S",
            "/sdcard/Android/media/..5.u.S",
        ];
        for path_str in leftovers {
            let p = Path::new(path_str);
            if p.exists() {
                if p.is_dir() {
                    let _ = std::fs::remove_dir_all(p);
                } else {
                    let _ = std::fs::remove_file(p);
                }
            }
        }
    }

    let mut record = BootRecord::load_or_new();

    // 1. sus_paths
    for sp in &config.sus_paths {
        let item_name = format!("sus_path {}", sp.path);
        let res = if sp.r#loop {
            ops::add_sus_path_loop(&sp.path)
        } else {
            ops::add_sus_path(Path::new(&sp.path))
        };
        match res {
            Ok(()) => record.record_success(&item_name),
            Err(e) => record.record_failure(&item_name, &e.to_string()),
        }
    }

    // 2. sus_maps
    for sm in &config.sus_maps {
        let item_name = format!("sus_map {sm}");
        match ops::add_sus_map(Path::new(sm)) {
            Ok(()) => record.record_success(&item_name),
            Err(e) => record.record_failure(&item_name, &e.to_string()),
        }
    }

    // 3. open_redirects
    for red in &config.open_redirects {
        let item_name = format!("open_redirect {} -> {}", red.target, red.redirect);
        match ops::add_open_redirect(Path::new(&red.target), Path::new(&red.redirect), red.scope) {
            Ok(()) => record.record_success(&item_name),
            Err(e) => record.record_failure(&item_name, &e.to_string()),
        }
    }

    // 4. dynamic / full_clone kstats
    for kstat in &config.sus_kstats {
        let p = Path::new(&kstat.path);
        match kstat.mode {
            KstatMode::Dynamic => {
                let item_name = format!("kstat_update {}", kstat.path);
                match ops::update_sus_kstat(p) {
                    Ok(()) => record.record_success(&item_name),
                    Err(e) => record.record_failure(&item_name, &e.to_string()),
                }
            }
            KstatMode::FullClone => {
                let item_name = format!("kstat_update_full_clone {}", kstat.path);
                match ops::update_sus_kstat_full_clone(p) {
                    Ok(()) => record.record_success(&item_name),
                    Err(e) => record.record_failure(&item_name, &e.to_string()),
                }
            }
            KstatMode::Static => {}
        }
    }

    // 5. uid_filter (installed app uids only)
    let installed_pkgs = ops::filterable_packages(&config.uid_filter);

    if !installed_pkgs.is_empty() {
        match ops::set_priority_uid_filter(&installed_pkgs) {
            Ok(()) => record.record_success("uid_filter"),
            Err(e) => record.record_failure("uid_filter", &e.to_string()),
        }
    }

    // 6. hide-mounts boot -> 0
    if config.hide_sus_mounts == HideMountsMode::Boot {
        match ops::hide_sus_mnts_for_non_su_procs(false) {
            Ok(()) => record.record_success("hide_mounts_unhide"),
            Err(e) => record.record_failure("hide_mounts_unhide", &e.to_string()),
        }
    }

    record.save();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rewrite_cmdline() {
        let input = "console=null androidboot.verifiedbootstate=orange androidboot.vbmeta.device_state=unlocked androidboot.verifiedbooterror=1 androidboot.verifyerrorpart=boot quiet";
        let output = rewrite_cmdline(input);
        assert!(!output.contains("verifiedbooterror"));
        assert!(!output.contains("verifyerrorpart"));
        assert!(output.contains("androidboot.verifiedbootstate=green"));
        assert!(output.contains("androidboot.vbmeta.device_state=locked"));
        assert!(output.contains("console=null"));
        assert!(output.contains("quiet"));
    }

    #[test]
    fn test_rewrite_bootconfig() {
        let input = "androidboot.bootreason = \"cold\"\nandroidboot.vbmeta.device_state = \"unlocked\"\nandroidboot.verifiedbootstate = \"orange\"\nandroidboot.verifiedbooterror = \"bad\"\nandroidboot.verifyerrorpart = \"boot\"\nother.config = \"value\"";
        let output = rewrite_bootconfig(input);
        assert!(!output.contains("verifiedbooterror"));
        assert!(!output.contains("verifyerrorpart"));
        assert!(output.contains("androidboot.bootreason = \"reboot\""));
        assert!(output.contains("androidboot.vbmeta.device_state = \"locked\""));
        assert!(output.contains("androidboot.verifiedbootstate = \"green\""));
        assert!(output.contains("other.config = \"value\""));
    }
}
