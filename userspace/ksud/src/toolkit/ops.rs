use super::config::{self, Uname};
use super::real;
use crate::defs::{ADB_DIR, MODULE_DIR};
use crate::feature_id::FeatureId;
use crate::{ksu_uapi as uapi, ksucalls};
use anyhow::{Context, Result, ensure};
use serde::Serialize;
use std::collections::BTreeMap;
use std::ffi::CStr;
use std::fmt;
use std::fs;
use std::io;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

const CHANGE_MANAGER_UID: u32 = 10006;
const GET_SULOG_DUMP_V2: u32 = 10010;
const CHANGE_KSUVER: u32 = 10011;
const CHANGE_SPOOF_UNAME: u32 = 10012;
const CHANGE_KSUFLAGS: u32 = 10013;
const UMOUNT_GETSIZE: u8 = 107;
const UMOUNT_GETLIST: u8 = 108;
const SULOG_ENTRIES: usize = 250;

#[derive(Debug)]
pub(super) struct OverrideUnavailable(&'static str);

impl fmt::Display for OverrideUnavailable {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} override unavailable", self.0)
    }
}

impl std::error::Error for OverrideUnavailable {}

unsafe fn extension(cmd: u32, value: u32, slot: *mut u64) -> bool {
    ksucalls::with_toolkit_svc_call(|| unsafe {
        libc::syscall(libc::SYS_reboot, uapi::KSU_INSTALL_MAGIC1, cmd, value, slot);
    });
    unsafe { slot.read() == slot as u64 }
}

fn override_value(cmd: u32, value: u32, item: &'static str) -> Result<()> {
    let mut slot = 0;
    ensure!(
        unsafe { extension(cmd, value, &raw mut slot) },
        OverrideUnavailable(item)
    );
    Ok(())
}

pub(super) fn manager_uid() -> Result<u32> {
    let mut cmd = uapi::ksu_get_manager_appid_cmd { appid: 0 };
    ksucalls::ksuctl(uapi::KSU_IOCTL_GET_MANAGER_APPID, &raw mut cmd)?;
    Ok(cmd.appid)
}

pub(super) const fn valid_manager_uid(uid: u32) -> bool {
    matches!(uid, 10001..=19999)
}

pub(super) fn validate_uid(uid: u32) -> Result<()> {
    ensure!(
        valid_manager_uid(uid),
        "manager UID must be in 10001..19999"
    );
    Ok(())
}

pub(super) fn set_uid(uid: u32) -> Result<()> {
    validate_uid(uid)?;
    override_value(CHANGE_MANAGER_UID, uid, "manager UID")
}

pub(super) fn set_version(version: u32) -> Result<()> {
    if version != 0 {
        real::capture();
    }
    override_value(CHANGE_KSUVER, version, "KernelSU version")
}

pub(super) fn set_flags(flags: u32) -> Result<()> {
    if flags != 0 {
        real::capture();
    }
    override_value(CHANGE_KSUFLAGS, flags, "KernelSU flags")
}

pub(super) fn toggle_flags(flags: u32) -> Result<()> {
    let mask = uapi::KSU_GET_INFO_FLAG_LKM
        | uapi::KSU_GET_INFO_FLAG_LATE_LOAD
        | uapi::KSU_GET_INFO_FLAG_PR_BUILD;
    let current = ksucalls::get_info_fresh()?.flags;
    set_flags((current & !mask) | (flags & mask) | uapi::KSU_GET_INFO_FLAG_MANAGER)
}

pub(super) fn current_uname() -> Result<Uname> {
    let mut uname: libc::utsname = unsafe { std::mem::zeroed() };
    if unsafe { libc::uname(&raw mut uname) } != 0 {
        return Err(io::Error::last_os_error()).context("read uname");
    }
    Ok(Uname {
        release: unsafe { CStr::from_ptr(uname.release.as_ptr()) }
            .to_string_lossy()
            .into_owned(),
        version: unsafe { CStr::from_ptr(uname.version.as_ptr()) }
            .to_string_lossy()
            .into_owned(),
    })
}

pub(super) fn set_uname(uname: &Uname) -> Result<()> {
    uname.validate()?;
    if uname.release != "default" || uname.version != "default" {
        real::capture();
    }
    let mut buf = [0_u8; 130];
    let release = uname.release.as_bytes();
    let version = uname.version.as_bytes();
    buf[..release.len()].copy_from_slice(release);
    buf[release.len() + 1..release.len() + 1 + version.len()].copy_from_slice(version);
    let ptr = buf.as_ptr() as u64;
    let mut slot = (&raw const ptr) as u64;
    ensure!(
        unsafe { extension(CHANGE_SPOOF_UNAME, 0, &raw mut slot) },
        OverrideUnavailable("uname")
    );
    Ok(())
}

#[derive(Serialize)]
pub(super) struct SulogEntry {
    pub uid: u32,
    pub sym: char,
    pub time: u32,
}

#[derive(Serialize)]
pub(super) struct Sulog {
    pub uptime: u32,
    pub now: u64,
    pub entries: Vec<SulogEntry>,
}

pub(super) fn epoch_seconds() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

pub(super) fn sulog() -> Result<Sulog> {
    let mut index = 0_u32;
    let mut uptime = 0_u32;
    let mut entries = [[0_u32; 2]; SULOG_ENTRIES];
    let mut args = [
        (&raw mut index) as u64,
        entries.as_mut_ptr() as u64,
        (&raw mut uptime) as u64,
    ];
    ensure!(
        unsafe { extension(GET_SULOG_DUMP_V2, 0, args.as_mut_ptr()) },
        "sulog dump unavailable"
    );
    let start = index as usize % SULOG_ENTRIES;
    let entries = entries[start..]
        .iter()
        .chain(&entries[..start])
        .filter_map(|&[time, data]| {
            let sym = (data >> 24) as u8;
            (sym != 0).then_some(SulogEntry {
                uid: data & 0xFF_FFFF,
                sym: char::from(sym),
                time,
            })
        })
        .collect();
    Ok(Sulog {
        uptime,
        now: epoch_seconds(),
        entries,
    })
}

fn umount_ioctl(mode: u8, arg: u64) -> Result<()> {
    let mut cmd = uapi::ksu_add_try_umount_cmd {
        arg,
        flags: 0,
        mode,
    };
    ksucalls::ksuctl(uapi::KSU_IOCTL_ADD_TRY_UMOUNT, &raw mut cmd)?;
    Ok(())
}

pub(super) fn umount_size() -> Result<usize> {
    // Spare room for a 64-bit kernel serving a compat caller.
    let mut size = [0_usize; 2];
    umount_ioctl(UMOUNT_GETSIZE, size.as_mut_ptr() as u64)?;
    Ok(size[0])
}

pub(super) fn umount_list() -> Result<Vec<String>> {
    umount_bytes().map(|bytes| umount_paths(&bytes))
}

pub(super) fn umount_bytes() -> Result<Vec<u8>> {
    read_umount_bytes(umount_size()?)
}

fn umount_paths(bytes: &[u8]) -> Vec<String> {
    bytes
        .split(|&byte| byte == 0)
        .filter(|path| !path.is_empty())
        .map(|path| String::from_utf8_lossy(path).into_owned())
        .collect()
}

fn read_umount_bytes(mut size: usize) -> Result<Vec<u8>> {
    let page = usize::try_from(unsafe { libc::sysconf(libc::_SC_PAGESIZE) })?;
    ensure!(page.is_power_of_two(), "invalid page size");
    let mut retries = 3;
    loop {
        if size == 0 {
            return Ok(Vec::new());
        }
        let end = size
            .checked_add(page - 1)
            .context("invalid umount list size")?
            & !(page - 1);
        let len = end.checked_add(page).context("invalid umount list size")?;
        let mut map = memmap2::MmapMut::map_anon(len)?;
        let base = map.as_mut_ptr();
        if unsafe { libc::mprotect(base.add(end).cast(), page, libc::PROT_NONE) } != 0 {
            return Err(io::Error::last_os_error()).context("protect umount list guard page");
        }
        let buf = unsafe { base.add(end - size) };
        let mut cmd = uapi::ksu_add_try_umount_cmd {
            arg: buf as u64,
            flags: 0,
            mode: UMOUNT_GETLIST,
        };
        match ksucalls::ksuctl_io(uapi::KSU_IOCTL_ADD_TRY_UMOUNT, &raw mut cmd) {
            Ok(_) => {
                let bytes = unsafe { std::slice::from_raw_parts(buf, size) };
                let used = bytes
                    .iter()
                    .rposition(|&byte| byte != 0)
                    .map_or(0, |last| last + 2);
                return Ok(bytes
                    .get(..used)
                    .context("unterminated umount list")?
                    .to_vec());
            }
            Err(err) if err.raw_os_error() == Some(libc::EFAULT) && retries > 0 => {
                retries -= 1;
                size = umount_size()?;
            }
            Err(err) => return Err(err).context("read active umount list"),
        }
    }
}

pub(super) fn active_umount_list() -> Result<Vec<String>> {
    umount_size().map_or_else(
        |_| Ok(Vec::new()),
        |size| read_umount_bytes(size).map(|bytes| umount_paths(&bytes)),
    )
}

pub(super) fn add_umount(path: &str) -> Result<()> {
    let path = validate_mount_path(path)?;
    if umount_bytes().is_ok_and(|bytes| {
        bytes
            .split(|&b| b == 0)
            .any(|entry| entry == path.as_bytes())
    }) {
        return Ok(());
    }
    ksucalls::umount_list_add(path, libc::MNT_DETACH as u32)
}

pub(super) fn del_umount(path: &str) -> Result<()> {
    let path = validate_mount_path(path)?;
    if umount_bytes().is_ok_and(|bytes| {
        !bytes
            .split(|&b| b == 0)
            .any(|entry| entry == path.as_bytes())
    }) {
        return Ok(());
    }
    ksucalls::umount_list_del(path)
}

pub(super) fn validate_mount_path(path: &str) -> Result<&str> {
    let path = path.trim();
    ensure!(
        path.starts_with('/') && path.len() < 256 && path != "/",
        "expected absolute mount path shorter than 256 bytes and different from /"
    );
    Ok(path)
}

pub(super) fn packages() -> Result<BTreeMap<String, u32>> {
    let content = fs::read_to_string("/data/system/packages.list").context("read packages.list")?;
    Ok(content
        .lines()
        .filter_map(|line| {
            let mut fields = line.split_whitespace();
            Some((fields.next()?.to_owned(), fields.next()?.parse().ok()?))
        })
        .collect())
}

#[derive(Serialize)]
pub(super) struct Candidate {
    package: String,
    uid: u32,
}

pub(super) fn candidates() -> Vec<Candidate> {
    let Some(abi) = crate::utils::getprop("ro.product.cpu.abi") else {
        return Vec::new();
    };
    let lib = match abi.as_str() {
        "arm64-v8a" => Path::new("lib/arm64/libksud.so"),
        "armeabi-v7a" => Path::new("lib/arm/libksud.so"),
        "x86_64" => Path::new("lib/x86_64/libksud.so"),
        _ => return Vec::new(),
    };
    let mut packages = packages().unwrap_or_default();
    let mut found = BTreeMap::new();
    let root = Path::new("/data/app");
    let mut dirs = vec![root.to_path_buf()];
    if let Ok(entries) = fs::read_dir(root) {
        dirs.extend(entries.flatten().filter_map(|entry| {
            entry
                .file_type()
                .is_ok_and(|kind| kind.is_dir())
                .then(|| entry.path())
        }));
    }
    for dir in dirs {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            if !entry.file_type().is_ok_and(|kind| kind.is_dir()) {
                continue;
            }
            let name = entry.file_name();
            if let Some((package, _)) = name.to_str().and_then(|s| s.split_once('-'))
                && packages.contains_key(package)
                && entry.path().join(lib).is_file()
                && let Some(uid) = packages.remove(package)
            {
                found.insert(package.to_owned(), uid);
            }
        }
    }
    found
        .into_iter()
        .map(|(package, uid)| Candidate { package, uid })
        .collect()
}

pub(super) fn zygisknext_enforces() -> bool {
    config::module_enabled(&Path::new(MODULE_DIR).join("zygisksu"))
        && Path::new(ADB_DIR)
            .join("zygisksu/denylist_enforce")
            .exists()
}

pub(super) fn umount_provider() -> &'static str {
    let Ok(modules) = fs::read_dir(MODULE_DIR) else {
        return "none";
    };
    let mut neo = false;
    let mut re = false;
    for module in modules.flatten() {
        let path = module.path();
        if !config::module_enabled(&path) {
            continue;
        }
        let name = module.file_name().to_string_lossy().to_ascii_lowercase();
        let mut next = name == "zygisksu" || name.contains("zygisknext");
        neo |= name.contains("neozygisk");
        re |= name.contains("rezygisk");
        if let Ok(props) = fs::read_to_string(path.join("module.prop")) {
            for line in props
                .lines()
                .filter(|line| line.starts_with("id=") || line.starts_with("name="))
            {
                let line = line.to_ascii_lowercase();
                next |= line.contains("zygisk next") || line.contains("zygisknext");
                neo |= line.contains("neozygisk");
                re |= line.contains("rezygisk");
            }
        }
        if next && zygisknext_status(&path).unwrap_or_else(zygisknext_enforces) {
            return "zygisknext";
        }
    }
    if neo {
        "neozygisk"
    } else if re {
        "rezygisk"
    } else {
        "none"
    }
}

fn zygisknext_status(module: &Path) -> Option<bool> {
    let output = Command::new(module.join("bin/zygiskd"))
        .arg("status")
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    String::from_utf8_lossy(&output.stdout)
        .lines()
        .find_map(|line| {
            let (_, value) = line.split_once("enforce_denylist")?;
            let n = value
                .split(|c: char| !c.is_ascii_digit())
                .find(|s| !s.is_empty())?
                .parse::<u32>()
                .ok()?;
            Some(n != 0)
        })
}

pub(super) fn kernel_umount() -> bool {
    ksucalls::get_feature(FeatureId::KernelUmount as u32).is_ok_and(|state| state.value != 0)
}

#[derive(Serialize)]
pub(super) struct MountEntry {
    mount_point: String,
    source: String,
    fs_type: String,
}

fn decode_mount(field: &[u8]) -> String {
    let mut out = Vec::with_capacity(field.len());
    let mut rest = field;
    while let [first, tail @ ..] = rest {
        if let [
            b'\\',
            a @ b'0'..=b'3',
            b @ b'0'..=b'7',
            c @ b'0'..=b'7',
            next @ ..,
        ] = rest
        {
            out.push((a - b'0') * 64 + (b - b'0') * 8 + (c - b'0'));
            rest = next;
        } else {
            out.push(*first);
            rest = tail;
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

pub(super) fn mounts() -> Result<Vec<MountEntry>> {
    let content = fs::read("/proc/1/mounts").context("read /proc/1/mounts")?;
    let mut mounts = BTreeMap::new();
    for line in content.split(|&b| b == b'\n') {
        let mut fields = line
            .split(u8::is_ascii_whitespace)
            .filter(|field| !field.is_empty());
        if let (Some(source), Some(point), Some(fs_type)) =
            (fields.next(), fields.next(), fields.next())
        {
            let mount_point = decode_mount(point);
            mounts.insert(
                mount_point.clone(),
                MountEntry {
                    mount_point,
                    source: decode_mount(source),
                    fs_type: decode_mount(fs_type),
                },
            );
        }
    }
    Ok(mounts.into_values().collect())
}
