use super::{config, ops, real};
use crate::ksucalls;
use anyhow::{Result, ensure};
use clap::ValueEnum;
use serde::{Deserialize, Serialize};

pub(super) const RECORD: &str = "boot.json";

#[derive(Clone, Copy, Debug, ValueEnum)]
pub enum Stage {
    PostFsData,
    Services,
    BootCompleted,
}

#[derive(Default, Deserialize, Serialize)]
pub(super) struct BootRecord {
    #[serde(default)]
    boot_id: String,
    time: u64,
    applied: usize,
    failed: usize,
    pub errors: Vec<BootError>,
}

#[derive(Deserialize, Serialize)]
pub(super) struct BootError {
    item: String,
    error: String,
}

impl BootRecord {
    fn record(&mut self, item: &str, result: Result<()>) {
        match result {
            Ok(()) => self.applied += 1,
            Err(err) if err.is::<ops::OverrideUnavailable>() => log::info!("toolkit {item}: {err}"),
            Err(err) => {
                log::warn!("toolkit {item}: {err:#}");
                self.failed += 1;
                self.errors.push(BootError {
                    item: item.to_owned(),
                    error: format!("{err:#}"),
                });
            }
        }
    }
}

pub(super) fn apply(stage: Stage) -> Result<()> {
    if crate::utils::is_safe_mode() {
        log::info!("toolkit skipped {stage:?} in safe mode");
        return Ok(());
    }
    if config::module_enabled(config::module_dir()) {
        return Ok(());
    }
    let _guard = config::lock()?;
    if config::module_enabled(config::module_dir()) {
        return Ok(());
    }

    let boot_id = real::boot_id()?;
    let mut record = config::load::<BootRecord>(RECORD)
        .ok()
        .flatten()
        .filter(|record| record.boot_id == boot_id)
        .unwrap_or_else(|| BootRecord {
            boot_id,
            ..BootRecord::default()
        });
    record.time = ops::epoch_seconds();
    let failures_before = record.failed;
    match config::read() {
        Ok(cfg) => match stage {
            Stage::PostFsData => {
                if let Some(uname) = cfg.uname {
                    record.record("uname", ops::set_uname(&uname));
                }
            }
            Stage::Services => {
                if let Some(uid) = cfg.manager_uid {
                    if let Some(package) = cfg.manager_package {
                        match ops::packages() {
                            Ok(packages) if packages.get(&package) == Some(&uid) => {
                                record.record("manager_uid", ops::set_uid(uid));
                            }
                            Ok(_) => log::info!(
                                "toolkit skipped manager UID {uid}: package {package} no longer matches"
                            ),
                            Err(err) => log::info!("toolkit skipped manager UID {uid}: {err:#}"),
                        }
                    } else {
                        log::info!("toolkit skipped manager UID {uid}: saved package is missing");
                    }
                }
                if let Some(version) = cfg.ksu_version {
                    record.record("ksu_version", ops::set_version(version));
                }
            }
            Stage::BootCompleted => {
                for path in &cfg.umount {
                    record.record(&format!("umount {path}"), ops::add_umount(path));
                }
                if !cfg.umount.is_empty() {
                    ksucalls::report_module_mounted();
                    if !ops::zygisknext_enforces() {
                        record.record(
                            "sepolicy",
                            crate::sepolicy::live_patch(
                                "allow zygote labeledfs filesystem unmount",
                            ),
                        );
                    }
                }
            }
        },
        Err(err) => record.record("config", Err(err)),
    }
    config::save(RECORD, &record)?;
    ensure!(
        record.failed == failures_before,
        "toolkit apply failed for {stage:?}; see boot.json"
    );
    Ok(())
}

fn on_stage(stage: Stage) {
    if let Err(err) = apply(stage) {
        log::warn!("toolkit {stage:?}: {err:#}");
    }
}

pub fn on_post_fs_data() {
    on_stage(Stage::PostFsData);
}

pub fn on_services() {
    on_stage(Stage::Services);
}

pub fn on_late_load_services() {
    log::info!("toolkit skips uname on late load");
    on_services();
}

pub fn on_boot_completed() {
    on_stage(Stage::BootCompleted);
}
