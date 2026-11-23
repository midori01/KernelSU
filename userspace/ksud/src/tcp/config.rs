use anyhow::{Context, Result, bail};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs::{self, DirBuilder, File};
use std::io::ErrorKind;
use std::os::unix::fs::DirBuilderExt;
use std::path::Path;
use std::str::FromStr;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Key {
    Congestion,
    SlowStartAfterIdle,
    MtuProbing,
    Ecn,
}

impl Key {
    pub const ALL: [Self; 4] = [
        Self::Congestion,
        Self::SlowStartAfterIdle,
        Self::MtuProbing,
        Self::Ecn,
    ];

    pub const fn name(self) -> &'static str {
        match self {
            Self::Congestion => "congestion",
            Self::SlowStartAfterIdle => "slow_start_after_idle",
            Self::MtuProbing => "mtu_probing",
            Self::Ecn => "ecn",
        }
    }

    pub const fn sysctl(self) -> &'static str {
        match self {
            Self::Congestion => "net/ipv4/tcp_congestion_control",
            Self::SlowStartAfterIdle => "net/ipv4/tcp_slow_start_after_idle",
            Self::MtuProbing => "net/ipv4/tcp_mtu_probing",
            Self::Ecn => "net/ipv4/tcp_ecn",
        }
    }

    pub fn validate(self, value: &str, available: &[String]) -> Result<()> {
        let valid = match self {
            Self::Congestion => available.iter().any(|name| name == value),
            Self::SlowStartAfterIdle => matches!(value, "0" | "1"),
            Self::MtuProbing | Self::Ecn => matches!(value, "0" | "1" | "2"),
        };
        if !valid {
            bail!("invalid or unavailable value {value:?} for {}", self.name());
        }
        Ok(())
    }
}

impl FromStr for Key {
    type Err = anyhow::Error;

    fn from_str(key: &str) -> Result<Self> {
        Self::ALL
            .into_iter()
            .find(|item| item.name() == key)
            .with_context(|| format!("unknown TCP key: {key}"))
    }
}

#[derive(Default, Serialize, Deserialize)]
pub(super) struct Config {
    pub congestion: Option<String>,
    slow_start_after_idle: Option<u8>,
    mtu_probing: Option<u8>,
    ecn: Option<u8>,
}

impl Config {
    pub fn selected(&self, key: Key) -> Option<String> {
        match key {
            Key::Congestion => return self.congestion.clone(),
            Key::SlowStartAfterIdle => self.slow_start_after_idle,
            Key::MtuProbing => self.mtu_probing,
            Key::Ecn => self.ecn,
        }
        .map(|value| value.to_string())
    }

    pub fn select(&mut self, key: Key, value: Option<&str>) -> Result<()> {
        match key {
            Key::Congestion => self.congestion = value.map(str::to_owned),
            Key::SlowStartAfterIdle => {
                self.slow_start_after_idle = value.map(str::parse).transpose()?;
            }
            Key::MtuProbing => self.mtu_probing = value.map(str::parse).transpose()?,
            Key::Ecn => self.ecn = value.map(str::parse).transpose()?,
        }
        Ok(())
    }
}

#[derive(Serialize, Deserialize)]
pub(super) struct Baseline {
    pub boot_id: String,
    pub values: BTreeMap<String, String>,
}

pub(super) fn load<T: DeserializeOwned>(path: &Path) -> Result<Option<T>> {
    let bytes = match fs::read(path) {
        Err(err) if err.kind() == ErrorKind::NotFound => return Ok(None),
        result => result.with_context(|| format!("read {}", path.display()))?,
    };
    serde_json::from_slice(&bytes)
        .map(Some)
        .with_context(|| format!("parse {}", path.display()))
}

pub(super) fn save(dir: &Path, name: &str, value: &impl Serialize) -> Result<()> {
    let mut tmp = tempfile::NamedTempFile::new_in(dir)?;
    serde_json::to_writer_pretty(&mut tmp, value)?;
    tmp.persist(dir.join(name))
        .with_context(|| format!("save {name}"))?;
    Ok(())
}

pub(super) fn lock(dir: &Path, nonblocking: bool) -> Result<File> {
    DirBuilder::new().recursive(true).mode(0o700).create(dir)?;
    let file = File::open(dir)?;
    if nonblocking {
        file.try_lock().context("lock TCP settings")?;
    } else {
        file.lock().context("lock TCP settings")?;
    }
    Ok(file)
}
