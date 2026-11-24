use super::{config, ops};
use crate::ksucalls;
use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize};
use std::fs;

const NAME: &str = "real.json";

#[derive(Deserialize, Serialize)]
pub struct Snapshot {
    boot_id: String,
    pub version: u32,
    pub flags: u32,
    pub release: String,
    uname_version: String,
}

pub(super) fn boot_id() -> Result<String> {
    let id = fs::read_to_string("/proc/sys/kernel/random/boot_id").context("read boot ID")?;
    ensure!(!id.trim().is_empty(), "empty boot ID");
    Ok(id.trim().to_owned())
}

pub fn load() -> Option<Snapshot> {
    let snapshot = config::load::<Snapshot>(NAME).ok()??;
    (snapshot.boot_id == boot_id().ok()?).then_some(snapshot)
}

pub(super) fn capture() {
    if let Err(err) = try_capture() {
        log::warn!("toolkit could not capture real kernel values: {err:#}");
    }
}

fn try_capture() -> Result<()> {
    let boot_id = boot_id()?;
    if config::load::<Snapshot>(NAME)?.is_some_and(|snapshot| snapshot.boot_id == boot_id) {
        return Ok(());
    }
    let info = ksucalls::get_info_fresh()?;
    let uname = ops::current_uname()?;
    config::save(
        NAME,
        &Snapshot {
            boot_id,
            version: info.version,
            flags: info.flags,
            release: uname.release,
            uname_version: uname.version,
        },
    )
}
