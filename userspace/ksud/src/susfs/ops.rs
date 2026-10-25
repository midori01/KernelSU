use std::ffi::CString;
use std::os::unix::ffi::OsStrExt;
use std::path::{Path, PathBuf};

use super::abi::{
    AvcLogSpoofing, CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_KSTAT,
    CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_ADD_SUS_MAP, CMD_SUSFS_ADD_SUS_PATH,
    CMD_SUSFS_ADD_SUS_PATH_LOOP, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, CMD_SUSFS_ENABLE_LOG,
    CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG,
    CMD_SUSFS_SET_PRIORITY_UID_FILTER, CMD_SUSFS_SET_UNAME, CMD_SUSFS_SHOW_ENABLED_FEATURES,
    CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION, CMD_SUSFS_UPDATE_SUS_KSTAT, HideSusMnts,
    KSTAT_AUTO_SPOOF, KSTAT_AUTO_SPOOF_FULL_CLONE, KSTAT_SPOOF_ATIME_TV_NSEC,
    KSTAT_SPOOF_ATIME_TV_SEC, KSTAT_SPOOF_BLKSIZE, KSTAT_SPOOF_BLOCKS, KSTAT_SPOOF_CTIME_TV_NSEC,
    KSTAT_SPOOF_CTIME_TV_SEC, KSTAT_SPOOF_DEV, KSTAT_SPOOF_INO, KSTAT_SPOOF_MTIME_TV_NSEC,
    KSTAT_SPOOF_MTIME_TV_SEC, KSTAT_SPOOF_NLINK, KSTAT_SPOOF_SIZE, PriorityUidFilter,
    SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE, SpoofCmdlineOrBootconfig, SusMap, SusPath,
    SusfsEnabledFeatures, SusfsError, SusfsLog, SusfsOpenRedirect, SusfsSusKstat, SusfsUname,
    SusfsVariant, SusfsVersion, UID_NON_APP_PROC, UID_UMOUNTED_PROC, call,
};
use super::config::SusKstatStatic;

fn copy_to_c_buffer(src: &str, dest: &mut [libc::c_char]) -> Result<(), SusfsError> {
    let bytes = src.as_bytes();
    if bytes.len() >= dest.len() {
        return Err(SusfsError::Errno(libc::ENAMETOOLONG));
    }
    for (i, &b) in bytes.iter().enumerate() {
        dest[i] = b as libc::c_char;
    }
    dest[bytes.len()] = 0;
    Ok(())
}

fn canonicalize_path(path: &Path) -> Result<PathBuf, SusfsError> {
    std::fs::canonicalize(path).map_err(|e| {
        let errno = e.raw_os_error().unwrap_or(libc::ENOENT);
        SusfsError::Errno(errno)
    })
}

fn path_to_cstring(path: &Path) -> Result<CString, SusfsError> {
    CString::new(path.as_os_str().as_bytes()).map_err(|_| SusfsError::Errno(libc::EINVAL))
}

fn get_file_stat(path: &Path) -> Result<libc::stat, SusfsError> {
    let c_path = path_to_cstring(path)?;
    let mut sb: libc::stat = unsafe { std::mem::zeroed() };
    let res = unsafe { libc::stat(c_path.as_c_str().as_ptr(), std::ptr::addr_of_mut!(sb)) };
    if res != 0 {
        let errno = std::io::Error::last_os_error()
            .raw_os_error()
            .unwrap_or(libc::ENOENT);
        return Err(SusfsError::Errno(errno));
    }
    Ok(sb)
}

fn parse_c_string(buf: &[libc::c_char]) -> String {
    let len = buf.iter().position(|&c| c == 0).unwrap_or(buf.len());
    // SAFETY: FFI c_char buffer conversion to u8
    #[allow(clippy::unnecessary_cast)]
    let bytes: Vec<u8> = buf[..len].iter().map(|&c| c as u8).collect();
    String::from_utf8_lossy(&bytes).to_string()
}

pub fn is_available() -> bool {
    get_version().is_ok_and(|v| !v.trim().is_empty())
}

pub fn get_source() -> &'static str {
    if !is_available() {
        "none"
    } else if Path::new("/sys/module/susfs_guard_lkm").exists() {
        "lkm"
    } else {
        "builtin"
    }
}

pub fn get_version() -> Result<String, SusfsError> {
    let mut req = SusfsVersion::default();
    call(CMD_SUSFS_SHOW_VERSION, &mut req)?;
    Ok(parse_c_string(&req.susfs_version))
}

pub fn get_variant() -> Result<String, SusfsError> {
    let mut req = SusfsVariant::default();
    call(CMD_SUSFS_SHOW_VARIANT, &mut req)?;
    Ok(parse_c_string(&req.susfs_variant))
}

pub fn get_enabled_features() -> Result<Vec<String>, SusfsError> {
    let mut req = Box::new(SusfsEnabledFeatures::default());
    call(CMD_SUSFS_SHOW_ENABLED_FEATURES, &mut *req)?;
    let raw = parse_c_string(&req.enabled_features);
    let features: Vec<String> = raw
        .lines()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(ToOwned::to_owned)
        .collect();
    Ok(features)
}

pub fn add_sus_path(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let real_str = real.to_string_lossy();
    if real_str.is_empty() {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let mut req = SusPath::default();
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;
    call(CMD_SUSFS_ADD_SUS_PATH, &mut req)
}

pub fn add_sus_path_loop(path: &str) -> Result<(), SusfsError> {
    if path.is_empty() {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let mut req = SusPath::default();
    copy_to_c_buffer(path, &mut req.target_pathname)?;
    call(CMD_SUSFS_ADD_SUS_PATH_LOOP, &mut req)
}

pub fn hide_sus_mnts_for_non_su_procs(enabled: bool) -> Result<(), SusfsError> {
    let mut req = HideSusMnts {
        enabled,
        _pad: [0; 3],
        err: 0,
    };
    call(CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, &mut req)
}

pub fn add_sus_map(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let real_str = real.to_string_lossy();
    if real_str.is_empty() {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let mut req = SusMap::default();
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;
    call(CMD_SUSFS_ADD_SUS_MAP, &mut req)
}

/// Keeps the packages the kernel will accept for the priority uid filter: a name made of
/// [A-Za-z0-9._] and a data dir owned by an app uid (10000-19999). A single bad entry
/// fails the whole kernel call, so everything else is dropped (and logged) beforehand.
pub fn filterable_packages(pkgs: &[String]) -> Vec<String> {
    use std::os::unix::fs::MetadataExt;

    pkgs.iter()
        .filter(|pkg| {
            let valid_name = !pkg.is_empty()
                && pkg.len() <= 255
                && pkg.as_str() != "."
                && pkg.as_str() != ".."
                && pkg
                    .bytes()
                    .all(|c| c.is_ascii_alphanumeric() || c == b'.' || c == b'_');
            let app_uid = valid_name
                && std::fs::metadata(format!("/data/data/{pkg}"))
                    .is_ok_and(|m| (10000..=19999).contains(&m.uid()));
            if !app_uid {
                log::warn!("SUSFS uid filter: skipping {pkg} (not installed or not an app uid)");
            }
            app_uid
        })
        .cloned()
        .collect()
}

pub fn set_priority_uid_filter(pkgs: &[String]) -> Result<(), SusfsError> {
    if pkgs.is_empty() {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let joined = pkgs.join(",");
    let mut req = PriorityUidFilter::new_boxed();
    copy_to_c_buffer(&joined, &mut req.app_strings)?;
    call(CMD_SUSFS_SET_PRIORITY_UID_FILTER, &mut *req)
}

pub fn add_open_redirect(
    target: &Path,
    redirect: &Path,
    uid_scheme: i32,
) -> Result<(), SusfsError> {
    if !(UID_NON_APP_PROC..=UID_UMOUNTED_PROC).contains(&uid_scheme) {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let real_target = canonicalize_path(target)?;
    let real_redirect = canonicalize_path(redirect)?;
    let target_str = real_target.to_string_lossy();
    let redirect_str = real_redirect.to_string_lossy();

    let mut req = SusfsOpenRedirect::default();
    copy_to_c_buffer(&target_str, &mut req.target_pathname)?;
    copy_to_c_buffer(&redirect_str, &mut req.redirected_pathname)?;
    req.uid_scheme = uid_scheme;
    call(CMD_SUSFS_ADD_OPEN_REDIRECT, &mut req)
}

fn copy_from_stat_to_sus_kstat(info: &mut SusfsSusKstat, sb: &libc::stat) {
    info.spoofed_ino = sb.st_ino;
    info.spoofed_dev = sb.st_dev;
    info.spoofed_nlink = sb.st_nlink as libc::c_uint;
    info.spoofed_size = sb.st_size;
    info.spoofed_atime_tv_sec = sb.st_atime;
    info.spoofed_atime_tv_nsec = sb.st_atime_nsec as libc::c_ulong;
    info.spoofed_mtime_tv_sec = sb.st_mtime;
    info.spoofed_mtime_tv_nsec = sb.st_mtime_nsec as libc::c_ulong;
    info.spoofed_ctime_tv_sec = sb.st_ctime;
    info.spoofed_ctime_tv_nsec = sb.st_ctime_nsec as libc::c_ulong;
    info.spoofed_blksize = libc::c_long::from(sb.st_blksize);
    info.spoofed_blocks = sb.st_blocks;
}

pub fn add_sus_kstat(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let sb = get_file_stat(&real)?;
    let real_str = real.to_string_lossy();

    let mut req = SusfsSusKstat::default();
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;
    req.is_statically = false;
    req.target_ino = sb.st_ino;
    req.flags |= KSTAT_AUTO_SPOOF;
    copy_from_stat_to_sus_kstat(&mut req, &sb);

    call(CMD_SUSFS_ADD_SUS_KSTAT, &mut req)
}

pub fn update_sus_kstat(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let sb = get_file_stat(&real)?;
    let real_str = real.to_string_lossy();

    let mut req = SusfsSusKstat::default();
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;
    req.is_statically = false;
    req.target_ino = sb.st_ino;
    req.flags |= KSTAT_AUTO_SPOOF;
    copy_from_stat_to_sus_kstat(&mut req, &sb);

    call(CMD_SUSFS_UPDATE_SUS_KSTAT, &mut req)
}

pub fn update_sus_kstat_full_clone(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let sb = get_file_stat(&real)?;
    let real_str = real.to_string_lossy();

    let mut req = SusfsSusKstat::default();
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;
    req.is_statically = false;
    req.target_ino = sb.st_ino;
    req.flags |= KSTAT_AUTO_SPOOF_FULL_CLONE;
    copy_from_stat_to_sus_kstat(&mut req, &sb);

    call(CMD_SUSFS_UPDATE_SUS_KSTAT, &mut req)
}

#[allow(clippy::field_reassign_with_default)]
pub fn compute_kstat_static(sb: &libc::stat, s: &SusKstatStatic) -> (i32, SusfsSusKstat) {
    let mut req = SusfsSusKstat::default();
    req.is_statically = true;
    req.target_ino = sb.st_ino;

    let mut flags = 0i32;

    req.spoofed_ino = s.ino.map_or(sb.st_ino, |v| {
        flags |= KSTAT_SPOOF_INO;
        v as libc::c_ulong
    });

    req.spoofed_dev = s.dev.map_or(sb.st_dev, |v| {
        flags |= KSTAT_SPOOF_DEV;
        v as libc::c_ulong
    });

    req.spoofed_nlink = s.nlink.map_or(sb.st_nlink as libc::c_uint, |v| {
        flags |= KSTAT_SPOOF_NLINK;
        v as libc::c_uint
    });

    req.spoofed_size = s.size.map_or(sb.st_size, |v| {
        flags |= KSTAT_SPOOF_SIZE;
        v
    });

    req.spoofed_atime_tv_sec = s.atime.map_or(sb.st_atime, |v| {
        flags |= KSTAT_SPOOF_ATIME_TV_SEC;
        v as libc::c_long
    });

    req.spoofed_atime_tv_nsec = s.atime_nsec.map_or(sb.st_atime_nsec as libc::c_ulong, |v| {
        flags |= KSTAT_SPOOF_ATIME_TV_NSEC;
        v as libc::c_ulong
    });

    req.spoofed_mtime_tv_sec = s.mtime.map_or(sb.st_mtime, |v| {
        flags |= KSTAT_SPOOF_MTIME_TV_SEC;
        v as libc::c_long
    });

    req.spoofed_mtime_tv_nsec = s.mtime_nsec.map_or(sb.st_mtime_nsec as libc::c_ulong, |v| {
        flags |= KSTAT_SPOOF_MTIME_TV_NSEC;
        v as libc::c_ulong
    });

    req.spoofed_ctime_tv_sec = s.ctime.map_or(sb.st_ctime, |v| {
        flags |= KSTAT_SPOOF_CTIME_TV_SEC;
        v as libc::c_long
    });

    req.spoofed_ctime_tv_nsec = s.ctime_nsec.map_or(sb.st_ctime_nsec as libc::c_ulong, |v| {
        flags |= KSTAT_SPOOF_CTIME_TV_NSEC;
        v as libc::c_ulong
    });

    req.spoofed_blocks = s.blocks.map_or(sb.st_blocks, |v| {
        flags |= KSTAT_SPOOF_BLOCKS;
        v
    });

    req.spoofed_blksize = s.blksize.map_or_else(
        || libc::c_long::from(sb.st_blksize),
        |v| {
            flags |= KSTAT_SPOOF_BLKSIZE;
            v as libc::c_long
        },
    );

    req.flags = flags;
    (flags, req)
}

pub fn add_sus_kstat_statically(path: &Path, s: &SusKstatStatic) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let sb = get_file_stat(&real)?;
    let real_str = real.to_string_lossy();

    let (_flags, mut req) = compute_kstat_static(&sb, s);
    copy_to_c_buffer(&real_str, &mut req.target_pathname)?;

    call(CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, &mut req)
}

pub fn set_log(enabled: bool) -> Result<(), SusfsError> {
    let mut req = SusfsLog {
        enabled,
        _pad: [0; 3],
        err: 0,
    };
    call(CMD_SUSFS_ENABLE_LOG, &mut req)
}

pub fn set_avc_log_spoofing(enabled: bool) -> Result<(), SusfsError> {
    let mut req = AvcLogSpoofing {
        enabled,
        _pad: [0; 3],
        err: 0,
    };
    call(CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, &mut req)
}

pub fn set_uname(release: &str, version: &str) -> Result<(), SusfsError> {
    let mut req = SusfsUname::default();
    let rel = if release.is_empty() {
        "default"
    } else {
        release
    };
    let ver = if version.is_empty() {
        "default"
    } else {
        version
    };
    copy_to_c_buffer(rel, &mut req.release)?;
    copy_to_c_buffer(ver, &mut req.version)?;
    call(CMD_SUSFS_SET_UNAME, &mut req)
}

pub fn set_cmdline_or_bootconfig_from_content(content: &[u8]) -> Result<(), SusfsError> {
    if content.len() >= SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE {
        return Err(SusfsError::Errno(libc::EINVAL));
    }
    let mut req = Box::new(SpoofCmdlineOrBootconfig::default());
    for (i, &b) in content.iter().enumerate() {
        req.fake_cmdline_or_bootconfig[i] = b as libc::c_char;
    }
    req.fake_cmdline_or_bootconfig[content.len()] = 0;
    call(CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG, &mut *req)
}

pub fn set_cmdline_or_bootconfig_from_file(path: &Path) -> Result<(), SusfsError> {
    let real = canonicalize_path(path)?;
    let content = std::fs::read(&real).map_err(|e| {
        let errno = e.raw_os_error().unwrap_or(libc::EIO);
        SusfsError::Errno(errno)
    })?;
    set_cmdline_or_bootconfig_from_content(&content)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compute_kstat_static_flags() {
        let mut sb: libc::stat = unsafe { std::mem::zeroed() };
        sb.st_ino = 100;
        sb.st_dev = 200;
        sb.st_nlink = 1;
        sb.st_size = 500;
        sb.st_atime = 1000;
        sb.st_atime_nsec = 10;
        sb.st_mtime = 2000;
        sb.st_mtime_nsec = 20;
        sb.st_ctime = 3000;
        sb.st_ctime_nsec = 30;
        sb.st_blocks = 8;
        sb.st_blksize = 4096;

        let empty = SusKstatStatic::default();
        let (flags, req) = compute_kstat_static(&sb, &empty);
        assert_eq!(flags, 0);
        assert_eq!(req.spoofed_ino, 100);
        assert_eq!(req.spoofed_dev, 200);
        assert_eq!(req.spoofed_size, 500);

        let custom = SusKstatStatic {
            ino: Some(999),
            size: Some(1234),
            mtime: Some(5555),
            ..Default::default()
        };
        let (flags, req) = compute_kstat_static(&sb, &custom);
        let expected_flags = KSTAT_SPOOF_INO | KSTAT_SPOOF_SIZE | KSTAT_SPOOF_MTIME_TV_SEC;
        assert_eq!(flags, expected_flags);
        assert_eq!(req.spoofed_ino, 999);
        assert_eq!(req.spoofed_dev, 200);
        assert_eq!(req.spoofed_size, 1234);
        assert_eq!(req.spoofed_mtime_tv_sec, 5555);
        assert_eq!(req.spoofed_mtime_tv_nsec, 20);
    }
}
