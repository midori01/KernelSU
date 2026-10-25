use std::collections::HashMap;
use std::path::Path;

use super::config::{
    CmdlineMode, HideMountsMode, KstatMode, OpenRedirectConfig, SusKstatConfig, SusKstatStatic,
    SusfsConfig,
};

fn strip_quotes(s: &str) -> String {
    let trimmed = s.trim();
    if (trimmed.starts_with('\'') && trimmed.ends_with('\''))
        || (trimmed.starts_with('"') && trimmed.ends_with('"'))
    {
        if trimmed.len() >= 2 {
            trimmed[1..trimmed.len() - 1].to_string()
        } else {
            String::new()
        }
    } else {
        trimmed.to_string()
    }
}

fn parse_sh_key_values(content: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        if let Some((k, v)) = trimmed.split_once('=') {
            map.insert(k.trim().to_string(), strip_quotes(v));
        }
    }
    map
}

fn parse_i64_val(v: &serde_json::Value) -> Option<i64> {
    match v {
        serde_json::Value::Number(n) => n.as_i64(),
        serde_json::Value::String(s) => {
            let trimmed = s.trim();
            if trimmed.is_empty() || trimmed.eq_ignore_ascii_case("default") {
                None
            } else {
                trimmed.parse::<i64>().ok()
            }
        }
        _ => None,
    }
}

pub fn import_from_module(base_dir: &Path, config: &mut SusfsConfig) -> usize {
    let mut count = 0;

    // 1. config.sh
    if let Ok(content) = std::fs::read_to_string(base_dir.join("config.sh")) {
        let kv = parse_sh_key_values(&content);

        if let Some(v) = kv.get("susfs_log") {
            if v == "1" {
                config.log = true;
                count += 1;
            } else if v == "0" {
                config.log = false;
                count += 1;
            }
        }

        if let Some(v) = kv.get("avc_log_spoofing") {
            if v == "1" {
                config.avc_log_spoofing = true;
                count += 1;
            } else if v == "0" {
                config.avc_log_spoofing = false;
                count += 1;
            }
        }

        if let Some(v) = kv.get("hide_sus_mnts_for_all_or_non_su_procs") {
            match v.as_str() {
                "0" => {
                    config.hide_sus_mounts = HideMountsMode::Off;
                    count += 1;
                }
                "1" => {
                    config.hide_sus_mounts = HideMountsMode::Always;
                    count += 1;
                }
                "2" => {
                    config.hide_sus_mounts = HideMountsMode::Boot;
                    count += 1;
                }
                _ => {}
            }
        }

        let spoof_uname = kv.get("spoof_uname").map_or("0", String::as_str);
        if spoof_uname != "0" {
            if let Some(ver) = kv
                .get("kernel_version")
                .filter(|v| !v.eq_ignore_ascii_case("default") && !v.is_empty())
            {
                config.uname.release.clone_from(ver);
                count += 1;
            }
            if let Some(build) = kv
                .get("kernel_build")
                .filter(|b| !b.eq_ignore_ascii_case("default") && !b.is_empty())
            {
                config.uname.version.clone_from(build);
                count += 1;
            }
        }

        let spoof_cmdline = kv.get("spoof_cmdline").map_or("0", String::as_str);
        if spoof_cmdline != "0" {
            config.cmdline.mode = CmdlineMode::Auto;
            count += 1;
        }
    }

    // 2. sus_path.txt (loop = false)
    if let Ok(content) = std::fs::read_to_string(base_dir.join("sus_path.txt")) {
        for line in content.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            if let Some(token) = trimmed.split_whitespace().next() {
                config.add_or_replace_path(token.to_string(), false);
                count += 1;
            }
        }
    }

    // 3. sus_path_loop.txt (loop = true)
    if let Ok(content) = std::fs::read_to_string(base_dir.join("sus_path_loop.txt")) {
        for line in content.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            if let Some(token) = trimmed.split_whitespace().next() {
                config.add_or_replace_path(token.to_string(), true);
                count += 1;
            }
        }
    }

    // 4. sus_maps.txt
    if let Ok(content) = std::fs::read_to_string(base_dir.join("sus_maps.txt")) {
        for line in content.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            if let Some(token) = trimmed.split_whitespace().next() {
                config.add_map(token.to_string());
                count += 1;
            }
        }
    }

    // 5. sus_open_redirect.txt
    if let Ok(content) = std::fs::read_to_string(base_dir.join("sus_open_redirect.txt")) {
        for line in content.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            let tokens: Vec<&str> = trimmed.split_whitespace().collect();
            if tokens.len() >= 2 {
                let target = tokens[0].to_string();
                let redirect = tokens[1].to_string();
                let scope = if tokens.len() >= 4 {
                    tokens[3].parse::<i32>().unwrap_or(2)
                } else {
                    2
                };
                config.add_or_replace_redirect(OpenRedirectConfig {
                    target,
                    redirect,
                    scope,
                });
                count += 1;
            }
        }
    }

    // 6. sus_kstat_statically.json
    let kstat_content =
        std::fs::read_to_string(base_dir.join("sus_kstat_statically.json")).unwrap_or_default();
    if let Ok(serde_json::Value::Array(items)) = serde_json::from_str(&kstat_content) {
        for item in items {
            if let serde_json::Value::Object(map) = item {
                let path = match map.get("path").and_then(serde_json::Value::as_str) {
                    Some(p) if !p.is_empty() => p.to_string(),
                    _ => continue,
                };

                let static_obj = SusKstatStatic {
                    ino: map.get("ino").and_then(parse_i64_val),
                    dev: map.get("dev").and_then(parse_i64_val),
                    nlink: map.get("nlink").and_then(parse_i64_val),
                    size: map.get("size").and_then(parse_i64_val),
                    atime: map.get("atime").and_then(parse_i64_val),
                    atime_nsec: map.get("atime_nsec").and_then(parse_i64_val),
                    mtime: map.get("mtime").and_then(parse_i64_val),
                    mtime_nsec: map.get("mtime_nsec").and_then(parse_i64_val),
                    ctime: map.get("ctime").and_then(parse_i64_val),
                    ctime_nsec: map.get("ctime_nsec").and_then(parse_i64_val),
                    blocks: map.get("blocks").and_then(parse_i64_val),
                    blksize: map.get("blksize").and_then(parse_i64_val),
                };

                config.add_or_replace_kstat(SusKstatConfig {
                    path,
                    mode: KstatMode::Static,
                    r#static: Some(static_obj),
                });
                count += 1;
            }
        }
    }

    count
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn test_import_from_module() {
        let dir = tempdir().unwrap();
        let p = dir.path();

        let config_sh = r"
susfs_log=0
avc_log_spoofing=1
hide_sus_mnts_for_all_or_non_su_procs=2
spoof_uname=1
kernel_version='6.1.0-custom'
kernel_build='default'
spoof_cmdline=1
";
        std::fs::write(p.join("config.sh"), config_sh).unwrap();

        let sus_path_txt = "# comment\n/system/addon.d\n/vendor/bin/install-recovery.sh 1\n";
        std::fs::write(p.join("sus_path.txt"), sus_path_txt).unwrap();

        let sus_path_loop_txt = "/sdcard/TWRP\n/sdcard/MT2 15\n";
        std::fs::write(p.join("sus_path_loop.txt"), sus_path_loop_txt).unwrap();

        let sus_maps_txt = "# comment\n/system/fonts/Roboto-Regular.ttf\n";
        std::fs::write(p.join("sus_maps.txt"), sus_maps_txt).unwrap();

        let sus_open_redirect_txt =
            "# comment\n/system/etc/hosts /data/local/tmp/hosts 0 3\n/target /redir\n";
        std::fs::write(p.join("sus_open_redirect.txt"), sus_open_redirect_txt).unwrap();

        let sus_kstat_json = r#"[
            {
                "path": "/system/etc/hosts",
                "ino": 1234,
                "dev": "default",
                "size": "5678"
            }
        ]"#;
        std::fs::write(p.join("sus_kstat_statically.json"), sus_kstat_json).unwrap();

        let mut config = SusfsConfig::default();
        let imported = import_from_module(p, &mut config);

        assert_eq!(imported, 13);
        assert!(!config.log);
        assert!(config.avc_log_spoofing);
        assert_eq!(config.hide_sus_mounts, HideMountsMode::Boot);
        assert_eq!(config.uname.release, "6.1.0-custom");
        assert_eq!(config.uname.version, "");
        assert_eq!(config.cmdline.mode, CmdlineMode::Auto);

        assert_eq!(config.sus_paths.len(), 4);
        assert_eq!(config.sus_paths[0].path, "/system/addon.d");
        assert!(!config.sus_paths[0].r#loop);
        assert_eq!(config.sus_paths[2].path, "/sdcard/TWRP");
        assert!(config.sus_paths[2].r#loop);

        assert_eq!(config.sus_maps.len(), 1);
        assert_eq!(config.sus_maps[0], "/system/fonts/Roboto-Regular.ttf");

        assert_eq!(config.open_redirects.len(), 2);
        assert_eq!(config.open_redirects[0].target, "/system/etc/hosts");
        assert_eq!(config.open_redirects[0].redirect, "/data/local/tmp/hosts");
        assert_eq!(config.open_redirects[0].scope, 3);
        assert_eq!(config.open_redirects[1].scope, 2); // default uid scheme

        assert_eq!(config.sus_kstats.len(), 1);
        assert_eq!(config.sus_kstats[0].path, "/system/etc/hosts");
        let stat = config.sus_kstats[0].r#static.as_ref().unwrap();
        assert_eq!(stat.ino, Some(1234));
        assert_eq!(stat.dev, None);
        assert_eq!(stat.size, Some(5678));
    }
}
