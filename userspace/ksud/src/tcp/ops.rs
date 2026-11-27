use super::boot::{BootErrorItem, BootRecord};
use super::config::{Baseline, Config, Key, load, lock, save};
use anyhow::{Context, Result, ensure};
use serde::Serialize;
use std::collections::{BTreeMap, btree_map::Entry};
use std::fs::{self, File};
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};

pub(super) struct Paths {
    sysctl: PathBuf,
    pub data: PathBuf,
    module: PathBuf,
    selection: PathBuf,
}

#[derive(Serialize)]
pub(super) struct Status {
    available: Vec<String>,
    items: Vec<Item>,
    module: ModuleStatus,
    boot_errors: Vec<BootErrorItem>,
}

#[derive(Serialize)]
struct Item {
    key: &'static str,
    present: bool,
    current: Option<String>,
    system: Option<String>,
    selected: Option<String>,
    state: &'static str,
}

#[derive(Serialize)]
struct ModuleStatus {
    present: bool,
    enabled: bool,
    selection: Option<String>,
}

fn parse_selection(line: &str) -> Option<&str> {
    let mut lines = line.lines();
    let mut fields = lines.next()?.split_ascii_whitespace();
    let wanted = fields.nth(2)?;
    (fields.next().is_none() && lines.next().is_none()).then_some(wanted)
}

fn state(
    key: Key,
    current: Option<&str>,
    selected: Option<&str>,
    available: &[String],
    module: bool,
) -> &'static str {
    match selected {
        None if key == Key::Congestion && module => "module",
        None => "system",
        Some(name) if key == Key::Congestion && !available.iter().any(|alg| alg == name) => {
            "unavailable"
        }
        Some(_) if current == selected => "applied",
        Some(_) => "mismatch",
    }
}

fn read_value(path: &Path) -> Result<String> {
    Ok(fs::read_to_string(path)
        .with_context(|| format!("read {}", path.display()))?
        .trim()
        .to_owned())
}

impl Paths {
    pub fn device() -> Self {
        use crate::defs::{ADB_DIR, MODULE_DIR, TCP_DIR};
        Self {
            sysctl: PathBuf::from("/proc/sys"),
            data: PathBuf::from(TCP_DIR),
            module: Path::new(MODULE_DIR).join("tcp_congestion"),
            selection: Path::new(ADB_DIR).join("tcp_congestion/selection"),
        }
    }

    pub fn config(&self) -> Result<Config> {
        Ok(load(&self.data.join("config.json"))?.unwrap_or_default())
    }

    fn available(&self) -> Result<Vec<String>> {
        let path = self
            .sysctl
            .join("net/ipv4/tcp_available_congestion_control");
        let names = match fs::read_to_string(&path) {
            Err(err) if err.kind() == ErrorKind::NotFound => return Ok(Vec::new()),
            result => result.with_context(|| format!("read {}", path.display()))?,
        };
        Ok(names.split_ascii_whitespace().map(str::to_owned).collect())
    }

    pub fn module_enabled(&self) -> bool {
        self.module.is_dir() && !self.module_disabled()
    }

    fn module_disabled(&self) -> bool {
        self.module.join("disable").exists() || self.module.join("remove").exists()
    }

    fn selection(&self) -> Result<Option<String>> {
        match fs::read_to_string(&self.selection) {
            Ok(line) => Ok(parse_selection(&line).map(str::to_owned)),
            Err(err) if err.kind() == ErrorKind::NotFound => Ok(None),
            Err(err) => Err(err).context("read tcp_congestion selection"),
        }
    }

    fn baseline(&self) -> Result<Baseline> {
        let boot_id = read_value(&self.sysctl.join("kernel/random/boot_id"))?;
        ensure!(!boot_id.is_empty(), "empty boot_id");
        let previous: Option<Baseline> = match load(&self.data.join("baseline.json")) {
            Err(err) if err.is::<serde_json::Error>() => None,
            result => result?,
        };
        Ok(previous
            .filter(|base| base.boot_id == boot_id)
            .unwrap_or_else(|| Baseline {
                boot_id,
                values: BTreeMap::new(),
            }))
    }

    pub fn capture_baseline(&self) -> Result<Baseline> {
        let mut baseline = self.baseline()?;
        let mut changed = baseline.values.is_empty();
        for key in Key::ALL {
            if let Entry::Vacant(entry) = baseline.values.entry(key.name().to_owned())
                && let Ok(value) = read_value(&self.sysctl.join(key.sysctl()))
            {
                entry.insert(value);
                changed = true;
            }
        }
        if changed {
            save(&self.data, "baseline.json", &baseline)?;
        }
        Ok(baseline)
    }

    pub fn status(&self) -> Status {
        let config = self.config().unwrap_or_default();
        let available = self.available().unwrap_or_default();
        let baseline = self.baseline().ok();
        let present = self.module.is_dir();
        let module = ModuleStatus {
            present,
            enabled: present && !self.module_disabled(),
            selection: if present {
                self.selection().unwrap_or_default()
            } else {
                None
            },
        };
        let items = Key::ALL
            .into_iter()
            .map(|key| {
                let path = self.sysctl.join(key.sysctl());
                let current = read_value(&path).ok();
                let selected = config.selected(key);
                Item {
                    key: key.name(),
                    present: current.is_some() || path.exists(),
                    state: state(
                        key,
                        current.as_deref(),
                        selected.as_deref(),
                        &available,
                        module.enabled,
                    ),
                    system: baseline
                        .as_ref()
                        .and_then(|base| base.values.get(key.name()))
                        .or(current.as_ref())
                        .cloned(),
                    current,
                    selected,
                }
            })
            .collect();
        let record = load::<BootRecord>(&self.data.join("boot.json"))
            .ok()
            .flatten();
        Status {
            available,
            items,
            module,
            boot_errors: record.map(|boot| boot.errors).unwrap_or_default(),
        }
    }

    fn validate(&self, key: Key, value: &str) -> Result<()> {
        if key == Key::Congestion {
            key.validate(value, &self.available()?)
        } else {
            key.validate(value, &[])
        }
    }

    fn write_verified(&self, key: Key, value: &str) -> Result<()> {
        let path = self.sysctl.join(key.sysctl());
        let mut file = File::options()
            .write(true)
            .truncate(true)
            .open(&path)
            .with_context(|| format!("open {}", path.display()))?;
        file.write_all(value.as_bytes())
            .with_context(|| format!("write {}", path.display()))?;
        let actual = read_value(&path)?;
        ensure!(
            actual == value,
            "{}: requested {value:?}, read back {actual:?}",
            key.name()
        );
        Ok(())
    }

    pub fn set(&self, key: Key, value: &str) -> Result<()> {
        let _guard = lock(&self.data, false)?;
        self.validate(key, value)?;
        let mut config = self.config()?;
        let baseline = self.capture_baseline()?;
        ensure!(
            baseline.values.contains_key(key.name()),
            "no baseline for {}",
            key.name()
        );
        let path = self.sysctl.join(key.sysctl());
        let previous = read_value(&path)?;
        config.select(key, Some(value))?;
        self.write_verified(key, value)?;
        if let Err(err) = save(&self.data, "config.json", &config) {
            if previous != value && read_value(&path).ok().as_deref() == Some(value) {
                self.write_verified(key, &previous)
                    .with_context(|| format!("{err:#}; restore {}", key.name()))?;
            }
            return Err(err);
        }
        Ok(())
    }

    pub fn apply_key(&self, key: Key, value: &str, baseline: &Baseline) -> Result<()> {
        let current = read_value(&self.sysctl.join(key.sysctl()))?;
        self.validate(key, value)?;
        if current != value {
            ensure!(
                baseline.values.contains_key(key.name()),
                "no baseline for {}",
                key.name()
            );
            self.write_verified(key, value)?;
        }
        Ok(())
    }

    pub fn reset(&self, keys: &[Key]) -> Result<()> {
        let _guard = lock(&self.data, false)?;
        let mut config = match self.config() {
            Err(err) if keys == Key::ALL && err.is::<serde_json::Error>() => Config::default(),
            result => result?,
        };
        let baseline = self.baseline().ok();
        let mut errors = Vec::new();
        for &key in keys {
            if !(key == Key::Congestion && self.module_enabled())
                && let Some(selected) = config.selected(key)
                && let Some(system) = baseline
                    .as_ref()
                    .and_then(|base| base.values.get(key.name()))
                && read_value(&self.sysctl.join(key.sysctl())).ok().as_ref() == Some(&selected)
                && let Err(err) = self.write_verified(key, system)
            {
                errors.push(format!("{err:#}"));
                continue;
            }
            config.select(key, None)?;
        }
        save(&self.data, "config.json", &config)?;
        ensure!(errors.is_empty(), "{}", errors.join("; "));
        Ok(())
    }

    pub fn import_module(&self) -> Result<()> {
        let _guard = lock(&self.data, false)?;
        ensure!(
            self.module_enabled(),
            "tcp_congestion module is not enabled"
        );
        let config = if let Some(wanted) = self.selection()?
            && self.available()?.contains(&wanted)
        {
            let mut config = self.config()?;
            config.congestion = Some(wanted);
            Some(config)
        } else {
            None
        };
        let disable = self.module.join("disable");
        File::create_new(&disable).context("disable tcp_congestion module")?;
        if let Some(config) = config
            && let Err(err) = save(&self.data, "config.json", &config)
        {
            fs::remove_file(&disable).with_context(|| format!("{err:#}; remove disable marker"))?;
            return Err(err);
        }
        Ok(())
    }
}
