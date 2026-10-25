use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use crate::defs::SUSFS_DIR;

pub const CONFIG_FILE_NAME: &str = "config.json";

const fn default_schema() -> u32 {
    1
}

const fn default_true() -> bool {
    true
}

const fn default_false() -> bool {
    false
}

const fn default_hide_sus_mounts() -> HideMountsMode {
    HideMountsMode::Always
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum HideMountsMode {
    Off,
    Always,
    Boot,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
pub struct UnameConfig {
    #[serde(default)]
    pub release: String,
    #[serde(default)]
    pub version: String,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum CmdlineMode {
    #[default]
    Off,
    Auto,
    File,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
pub struct CmdlineConfig {
    #[serde(default)]
    pub mode: CmdlineMode,
    #[serde(default)]
    pub file: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SusPathConfig {
    pub path: String,
    #[serde(default)]
    pub r#loop: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "snake_case")]
pub enum KstatMode {
    #[default]
    Dynamic,
    FullClone,
    Static,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Default)]
pub struct SusKstatStatic {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ino: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub dev: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub nlink: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub atime: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub atime_nsec: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mtime: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mtime_nsec: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ctime: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ctime_nsec: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub blocks: Option<i64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub blksize: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SusKstatConfig {
    pub path: String,
    #[serde(default)]
    pub mode: KstatMode,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub r#static: Option<SusKstatStatic>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct OpenRedirectConfig {
    pub target: String,
    pub redirect: String,
    #[serde(default)]
    pub scope: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct SusfsConfig {
    #[serde(default = "default_schema")]
    pub schema: u32,
    #[serde(default = "default_true")]
    pub enabled: bool,
    #[serde(default = "default_true")]
    pub log: bool,
    #[serde(default = "default_false")]
    pub avc_log_spoofing: bool,
    #[serde(default = "default_hide_sus_mounts")]
    pub hide_sus_mounts: HideMountsMode,
    #[serde(default)]
    pub uname: UnameConfig,
    #[serde(default)]
    pub cmdline: CmdlineConfig,
    #[serde(default)]
    pub sus_paths: Vec<SusPathConfig>,
    #[serde(default)]
    pub sus_kstats: Vec<SusKstatConfig>,
    #[serde(default)]
    pub sus_maps: Vec<String>,
    #[serde(default)]
    pub open_redirects: Vec<OpenRedirectConfig>,
    #[serde(default)]
    pub uid_filter: Vec<String>,
}

impl Default for SusfsConfig {
    fn default() -> Self {
        Self {
            schema: 1,
            enabled: true,
            log: true,
            avc_log_spoofing: false,
            hide_sus_mounts: HideMountsMode::Always,
            uname: UnameConfig::default(),
            cmdline: CmdlineConfig::default(),
            sus_paths: Vec::new(),
            sus_kstats: Vec::new(),
            sus_maps: Vec::new(),
            open_redirects: Vec::new(),
            uid_filter: Vec::new(),
        }
    }
}

impl SusfsConfig {
    pub fn add_or_replace_path(&mut self, path: String, loop_mode: bool) {
        if let Some(pos) = self.sus_paths.iter().position(|p| p.path == path) {
            self.sus_paths[pos].r#loop = loop_mode;
        } else {
            self.sus_paths.push(SusPathConfig {
                path,
                r#loop: loop_mode,
            });
        }
    }

    pub fn remove_path(&mut self, path: &str) -> bool {
        if let Some(pos) = self.sus_paths.iter().position(|p| p.path == path) {
            self.sus_paths.remove(pos);
            true
        } else {
            false
        }
    }

    pub fn add_or_replace_kstat(&mut self, kstat: SusKstatConfig) {
        if let Some(pos) = self.sus_kstats.iter().position(|k| k.path == kstat.path) {
            self.sus_kstats[pos] = kstat;
        } else {
            self.sus_kstats.push(kstat);
        }
    }

    pub fn remove_kstat(&mut self, path: &str) -> bool {
        if let Some(pos) = self.sus_kstats.iter().position(|k| k.path == path) {
            self.sus_kstats.remove(pos);
            true
        } else {
            false
        }
    }

    pub fn add_map(&mut self, map_path: String) {
        if !self.sus_maps.iter().any(|m| m == &map_path) {
            self.sus_maps.push(map_path);
        }
    }

    pub fn remove_map(&mut self, map_path: &str) -> bool {
        if let Some(pos) = self.sus_maps.iter().position(|m| m == map_path) {
            self.sus_maps.remove(pos);
            true
        } else {
            false
        }
    }

    pub fn add_or_replace_redirect(&mut self, redirect: OpenRedirectConfig) {
        if let Some(pos) = self
            .open_redirects
            .iter()
            .position(|r| r.target == redirect.target)
        {
            self.open_redirects[pos] = redirect;
        } else {
            self.open_redirects.push(redirect);
        }
    }

    pub fn remove_redirect(&mut self, target: &str) -> bool {
        if let Some(pos) = self.open_redirects.iter().position(|r| r.target == target) {
            self.open_redirects.remove(pos);
            true
        } else {
            false
        }
    }

    pub fn set_uid_filter(&mut self, pkgs: Vec<String>) {
        let mut deduped = Vec::new();
        for p in pkgs {
            let trimmed = p.trim().to_string();
            if !trimmed.is_empty() && !deduped.contains(&trimmed) {
                deduped.push(trimmed);
            }
        }
        self.uid_filter = deduped;
    }
}

pub fn get_config_path() -> PathBuf {
    Path::new(SUSFS_DIR).join(CONFIG_FILE_NAME)
}

pub fn load_config() -> Result<SusfsConfig> {
    let path = get_config_path();
    if !path.exists() {
        return Ok(SusfsConfig::default());
    }
    load_config_from_path(&path)
}

pub fn load_config_from_path(path: &Path) -> Result<SusfsConfig> {
    let content = std::fs::read_to_string(path)
        .with_context(|| format!("failed to read config file at {}", path.display()))?;
    let config: SusfsConfig = serde_json::from_str(&content)
        .with_context(|| format!("failed to parse JSON from config at {}", path.display()))?;
    Ok(config)
}

pub fn save_config(config: &SusfsConfig) -> Result<()> {
    let dir = Path::new(SUSFS_DIR);
    if !dir.exists() {
        std::fs::create_dir_all(dir)
            .with_context(|| format!("failed to create directory {}", dir.display()))?;
        let _ = std::fs::set_permissions(dir, std::fs::Permissions::from_mode(0o700));
    }
    let target = dir.join(CONFIG_FILE_NAME);
    save_config_to_path(config, &target)
}

pub fn save_config_to_path(config: &SusfsConfig, target: &Path) -> Result<()> {
    let parent = target.parent().unwrap_or_else(|| Path::new("."));
    if !parent.exists() {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("failed to create dir {}", parent.display()))?;
        let _ = std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
    }

    let json_bytes = serde_json::to_vec_pretty(config).context("failed to serialize config")?;
    let tmp_path = parent.join(format!(
        ".{}.tmp",
        target
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("config")
    ));

    std::fs::write(&tmp_path, json_bytes)
        .with_context(|| format!("failed to write temp config file {}", tmp_path.display()))?;
    let _ = std::fs::set_permissions(&tmp_path, std::fs::Permissions::from_mode(0o600));

    std::fs::rename(&tmp_path, target)
        .with_context(|| format!("failed to rename temp file to {}", target.display()))?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_config_roundtrip_with_defaults() {
        let default_cfg = SusfsConfig::default();
        assert_eq!(default_cfg.schema, 1);
        assert!(default_cfg.enabled);
        assert!(default_cfg.log);
        assert!(!default_cfg.avc_log_spoofing);
        assert_eq!(default_cfg.hide_sus_mounts, HideMountsMode::Always);
        assert_eq!(default_cfg.uname.release, "");
        assert_eq!(default_cfg.uname.version, "");
        assert_eq!(default_cfg.cmdline.mode, CmdlineMode::Off);
        assert_eq!(default_cfg.sus_paths, []);
        assert_eq!(default_cfg.sus_kstats, []);
        assert_eq!(default_cfg.sus_maps, Vec::<String>::new());
        assert_eq!(default_cfg.open_redirects, []);
        assert_eq!(default_cfg.uid_filter, Vec::<String>::new());

        let json = serde_json::to_string(&default_cfg).unwrap();
        let parsed: SusfsConfig = serde_json::from_str(&json).unwrap();
        assert_eq!(default_cfg, parsed);
    }

    #[test]
    fn test_config_dedup_and_order() {
        let mut cfg = SusfsConfig::default();
        cfg.add_or_replace_path("/sdcard/TWRP".to_string(), false);
        cfg.add_or_replace_path("/sdcard/MT2".to_string(), true);
        assert_eq!(cfg.sus_paths.len(), 2);
        assert!(!cfg.sus_paths[0].r#loop);

        // Update existing path keeps order
        cfg.add_or_replace_path("/sdcard/TWRP".to_string(), true);
        assert_eq!(cfg.sus_paths.len(), 2);
        assert_eq!(cfg.sus_paths[0].path, "/sdcard/TWRP");
        assert!(cfg.sus_paths[0].r#loop);

        // Remove path
        assert!(cfg.remove_path("/sdcard/TWRP"));
        assert_eq!(cfg.sus_paths.len(), 1);
        assert_eq!(cfg.sus_paths[0].path, "/sdcard/MT2");
    }
}
