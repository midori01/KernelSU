use super::config::{Key, lock, save};
use super::ops::Paths;
use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize, Deserialize)]
pub(super) struct BootRecord {
    time: u64,
    applied: usize,
    failed: usize,
    pub errors: Vec<BootErrorItem>,
}

#[derive(Serialize, Deserialize)]
pub(super) struct BootErrorItem {
    item: String,
    error: String,
}

impl BootRecord {
    fn failure(&mut self, item: &str, err: &anyhow::Error) {
        log::warn!("TCP tuning {item}: {err:#}");
        self.failed += 1;
        self.errors.push(BootErrorItem {
            item: item.to_owned(),
            error: format!("{err:#}"),
        });
    }
}

pub(super) fn apply(paths: &Paths, nonblocking: bool) -> Result<()> {
    let _guard = lock(&paths.data, nonblocking)?;
    let mut record = BootRecord {
        time: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs(),
        applied: 0,
        failed: 0,
        errors: Vec::new(),
    };
    match (paths.capture_baseline(), paths.config()) {
        (Ok(baseline), Ok(config)) => {
            let module = paths.module_enabled();
            for key in Key::ALL {
                if key == Key::Congestion && module {
                    continue;
                }
                if let Some(value) = config.selected(key) {
                    match paths.apply_key(key, &value, &baseline) {
                        Ok(()) => record.applied += 1,
                        Err(err) => record.failure(key.name(), &err),
                    }
                }
            }
        }
        (Err(err), _) => record.failure("baseline", &err),
        (_, Err(err)) => record.failure("config", &err),
    }
    save(&paths.data, "boot.json", &record)
}

pub fn on_post_fs_data() {
    if crate::utils::is_safe_mode() {
        log::info!("TCP tuning skipped post-fs-data in safe mode");
        return;
    }
    if let Err(err) = apply(&Paths::device(), true) {
        log::warn!("TCP tuning post-fs-data: {err:#}");
    }
}
