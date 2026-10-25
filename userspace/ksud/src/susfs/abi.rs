use std::fmt;

pub const KSU_INSTALL_MAGIC1: libc::c_ulong = 0xDEAD_BEEF;
pub const SUSFS_MAGIC: libc::c_ulong = 0xFAFA_FAFA;

pub const SUSFS_MAX_LEN_PATHNAME: usize = 256;
pub const SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE: usize = 8192;
pub const APP_STR_BUF_SIZE: usize = 65535;
pub const SUSFS_ENABLED_FEATURES_SIZE: usize = 8192;
pub const SUSFS_MAX_VERSION_BUFSIZE: usize = 16;
pub const SUSFS_MAX_VARIANT_BUFSIZE: usize = 16;
pub const NEW_UTS_LEN: usize = 64;

pub const ERR_CMD_NOT_SUPPORTED: i32 = 126;

// Commands
pub const CMD_SUSFS_ADD_SUS_PATH: u32 = 0x55550;
pub const CMD_SUSFS_ADD_SUS_PATH_LOOP: u32 = 0x55553;
pub const CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS: u32 = 0x55561;
pub const CMD_SUSFS_ADD_SUS_KSTAT: u32 = 0x55570;
pub const CMD_SUSFS_UPDATE_SUS_KSTAT: u32 = 0x55571;
pub const CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY: u32 = 0x55572;
pub const CMD_SUSFS_SET_UNAME: u32 = 0x55590;
pub const CMD_SUSFS_ENABLE_LOG: u32 = 0x555a0;
pub const CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG: u32 = 0x555b0;
pub const CMD_SUSFS_ADD_OPEN_REDIRECT: u32 = 0x555c0;
pub const CMD_SUSFS_SHOW_VERSION: u32 = 0x555e1;
pub const CMD_SUSFS_SHOW_ENABLED_FEATURES: u32 = 0x555e2;
pub const CMD_SUSFS_SHOW_VARIANT: u32 = 0x555e3;
pub const CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING: u32 = 0x60010;
pub const CMD_SUSFS_ADD_SUS_MAP: u32 = 0x60020;
pub const CMD_SUSFS_SET_PRIORITY_UID_FILTER: u32 = 0x60030;

// Kstat flags
pub const KSTAT_SPOOF_INO: i32 = 1 << 0;
pub const KSTAT_SPOOF_DEV: i32 = 1 << 1;
pub const KSTAT_SPOOF_NLINK: i32 = 1 << 2;
pub const KSTAT_SPOOF_SIZE: i32 = 1 << 3;
pub const KSTAT_SPOOF_ATIME_TV_SEC: i32 = 1 << 4;
pub const KSTAT_SPOOF_ATIME_TV_NSEC: i32 = 1 << 5;
pub const KSTAT_SPOOF_MTIME_TV_SEC: i32 = 1 << 6;
pub const KSTAT_SPOOF_MTIME_TV_NSEC: i32 = 1 << 7;
pub const KSTAT_SPOOF_CTIME_TV_SEC: i32 = 1 << 8;
pub const KSTAT_SPOOF_CTIME_TV_NSEC: i32 = 1 << 9;
pub const KSTAT_SPOOF_BLOCKS: i32 = 1 << 10;
pub const KSTAT_SPOOF_BLKSIZE: i32 = 1 << 11;
pub const KSTAT_AUTO_SPOOF: i32 = KSTAT_SPOOF_INO
    | KSTAT_SPOOF_DEV
    | KSTAT_SPOOF_ATIME_TV_SEC
    | KSTAT_SPOOF_ATIME_TV_NSEC
    | KSTAT_SPOOF_MTIME_TV_SEC
    | KSTAT_SPOOF_MTIME_TV_NSEC
    | KSTAT_SPOOF_CTIME_TV_SEC
    | KSTAT_SPOOF_CTIME_TV_NSEC
    | KSTAT_SPOOF_BLKSIZE
    | KSTAT_SPOOF_BLOCKS;
pub const KSTAT_AUTO_SPOOF_FULL_CLONE: i32 =
    KSTAT_AUTO_SPOOF | KSTAT_SPOOF_NLINK | KSTAT_SPOOF_SIZE;

// Open redirect UID schemes
#[allow(unused)]
pub const UID_NON_APP_PROC: i32 = 0;
#[allow(unused)]
pub const UID_ROOT_PROC_EXCEPT_SU_PROC: i32 = 1;
#[allow(unused)]
pub const UID_NON_SU_PROC: i32 = 2;
#[allow(unused)]
pub const UID_UMOUNTED_APP_PROC: i32 = 3;
#[allow(unused)]
pub const UID_UMOUNTED_PROC: i32 = 4;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SusfsError {
    Unsupported,
    Errno(i32),
}

impl fmt::Display for SusfsError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Unsupported => write!(f, "command not supported (err 126)"),
            Self::Errno(e) => write!(f, "susfs kernel error ({e})"),
        }
    }
}

impl std::error::Error for SusfsError {}

pub trait SusfsPayload {
    fn set_err(&mut self, err: i32);
    fn get_err(&self) -> i32;
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusPath {
    pub target_pathname: [libc::c_char; SUSFS_MAX_LEN_PATHNAME],
    pub err: libc::c_int,
}

impl Default for SusPath {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

impl SusfsPayload for SusPath {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct HideSusMnts {
    pub enabled: bool,
    pub _pad: [u8; 3],
    pub err: libc::c_int,
}

impl SusfsPayload for HideSusMnts {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusMap {
    pub target_pathname: [libc::c_char; SUSFS_MAX_LEN_PATHNAME],
    pub err: libc::c_int,
}

impl Default for SusMap {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

impl SusfsPayload for SusMap {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct AvcLogSpoofing {
    pub enabled: bool,
    pub _pad: [u8; 3],
    pub err: libc::c_int,
}

impl SusfsPayload for AvcLogSpoofing {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct SusfsLog {
    pub enabled: bool,
    pub _pad: [u8; 3],
    pub err: libc::c_int,
}

impl SusfsPayload for SusfsLog {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusfsUname {
    pub release: [libc::c_char; NEW_UTS_LEN + 1],
    pub version: [libc::c_char; NEW_UTS_LEN + 1],
    pub _pad: [u8; 2],
    pub err: libc::c_int,
}

impl Default for SusfsUname {
    fn default() -> Self {
        Self {
            release: [0; NEW_UTS_LEN + 1],
            version: [0; NEW_UTS_LEN + 1],
            _pad: [0; 2],
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsUname {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusfsOpenRedirect {
    pub target_pathname: [libc::c_char; SUSFS_MAX_LEN_PATHNAME],
    pub redirected_pathname: [libc::c_char; SUSFS_MAX_LEN_PATHNAME],
    pub uid_scheme: libc::c_int,
    pub err: libc::c_int,
}

impl Default for SusfsOpenRedirect {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            redirected_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            uid_scheme: 0,
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsOpenRedirect {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
pub struct SpoofCmdlineOrBootconfig {
    pub fake_cmdline_or_bootconfig: [libc::c_char; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    pub err: libc::c_int,
}

impl Default for SpoofCmdlineOrBootconfig {
    fn default() -> Self {
        Self {
            fake_cmdline_or_bootconfig: [0; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
            err: 0,
        }
    }
}

impl SusfsPayload for SpoofCmdlineOrBootconfig {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
pub struct PriorityUidFilter {
    pub app_strings: [libc::c_char; APP_STR_BUF_SIZE],
    pub _pad: [u8; 1],
    pub err: libc::c_int,
}

impl PriorityUidFilter {
    #[allow(clippy::cast_ptr_alignment)] // Layout::new::<Self>() guarantees correct alignment
    pub fn new_boxed() -> Box<Self> {
        // Allocate zeroed directly on the heap to avoid a 64KB stack frame
        unsafe {
            let layout = std::alloc::Layout::new::<Self>();
            let ptr = std::alloc::alloc_zeroed(layout).cast::<Self>();
            if ptr.is_null() {
                std::alloc::handle_alloc_error(layout);
            }
            Box::from_raw(ptr)
        }
    }
}

impl SusfsPayload for PriorityUidFilter {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusfsVersion {
    pub susfs_version: [libc::c_char; SUSFS_MAX_VERSION_BUFSIZE],
    pub err: libc::c_int,
}

impl Default for SusfsVersion {
    fn default() -> Self {
        Self {
            susfs_version: [0; SUSFS_MAX_VERSION_BUFSIZE],
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsVersion {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusfsVariant {
    pub susfs_variant: [libc::c_char; SUSFS_MAX_VARIANT_BUFSIZE],
    pub err: libc::c_int,
}

impl Default for SusfsVariant {
    fn default() -> Self {
        Self {
            susfs_variant: [0; SUSFS_MAX_VARIANT_BUFSIZE],
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsVariant {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
pub struct SusfsEnabledFeatures {
    pub enabled_features: [libc::c_char; SUSFS_ENABLED_FEATURES_SIZE],
    pub err: libc::c_int,
}

impl Default for SusfsEnabledFeatures {
    fn default() -> Self {
        Self {
            enabled_features: [0; SUSFS_ENABLED_FEATURES_SIZE],
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsEnabledFeatures {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct SusfsSusKstat {
    pub is_statically: bool,
    pub _pad1: [u8; 7],
    pub target_ino: libc::c_ulong,
    pub target_pathname: [libc::c_char; SUSFS_MAX_LEN_PATHNAME],
    pub spoofed_ino: libc::c_ulong,
    pub spoofed_dev: libc::c_ulong,
    pub spoofed_nlink: libc::c_uint,
    pub _pad2: [u8; 4],
    pub spoofed_size: libc::c_longlong,
    pub spoofed_atime_tv_sec: libc::c_long,
    pub spoofed_atime_tv_nsec: libc::c_ulong,
    pub spoofed_mtime_tv_sec: libc::c_long,
    pub spoofed_mtime_tv_nsec: libc::c_ulong,
    pub spoofed_ctime_tv_sec: libc::c_long,
    pub spoofed_ctime_tv_nsec: libc::c_ulong,
    pub spoofed_blocks: libc::c_longlong,
    pub spoofed_blksize: libc::c_long,
    pub flags: libc::c_int,
    pub err: libc::c_int,
}

impl Default for SusfsSusKstat {
    fn default() -> Self {
        Self {
            is_statically: false,
            _pad1: [0; 7],
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            spoofed_ino: 0,
            spoofed_dev: 0,
            spoofed_nlink: 0,
            _pad2: [0; 4],
            spoofed_size: 0,
            spoofed_atime_tv_sec: 0,
            spoofed_atime_tv_nsec: 0,
            spoofed_mtime_tv_sec: 0,
            spoofed_mtime_tv_nsec: 0,
            spoofed_ctime_tv_sec: 0,
            spoofed_ctime_tv_nsec: 0,
            spoofed_blocks: 0,
            spoofed_blksize: 0,
            flags: 0,
            err: 0,
        }
    }
}

impl SusfsPayload for SusfsSusKstat {
    fn set_err(&mut self, err: i32) {
        self.err = err;
    }
    fn get_err(&self) -> i32 {
        self.err
    }
}

pub fn call<T: SusfsPayload>(cmd: u32, arg: &mut T) -> Result<(), SusfsError> {
    arg.set_err(ERR_CMD_NOT_SUPPORTED);
    // Like ksu_susfs, only the err field written back by the kernel counts; the
    // syscall return value differs between SUSFS variants and the LKM
    unsafe {
        libc::syscall(
            libc::SYS_reboot,
            KSU_INSTALL_MAGIC1,
            SUSFS_MAGIC,
            libc::c_ulong::from(cmd),
            std::ptr::from_mut(arg).cast::<libc::c_void>(),
        );
    }
    match arg.get_err() {
        0 => Ok(()),
        ERR_CMD_NOT_SUPPORTED => Err(SusfsError::Unsupported),
        err => Err(SusfsError::Errno(err)),
    }
}

// Compile-time checks on 64-bit platforms
#[cfg(target_pointer_width = "64")]
const _: () = {
    assert!(std::mem::size_of::<SusPath>() == 260);
    assert!(std::mem::align_of::<SusPath>() == 4);

    assert!(std::mem::size_of::<HideSusMnts>() == 8);
    assert!(std::mem::align_of::<HideSusMnts>() == 4);

    assert!(std::mem::size_of::<SusMap>() == 260);
    assert!(std::mem::align_of::<SusMap>() == 4);

    assert!(std::mem::size_of::<AvcLogSpoofing>() == 8);
    assert!(std::mem::align_of::<AvcLogSpoofing>() == 4);

    assert!(std::mem::size_of::<SusfsLog>() == 8);
    assert!(std::mem::align_of::<SusfsLog>() == 4);

    assert!(std::mem::size_of::<SusfsUname>() == 136);
    assert!(std::mem::align_of::<SusfsUname>() == 4);

    assert!(std::mem::size_of::<SusfsOpenRedirect>() == 520);
    assert!(std::mem::align_of::<SusfsOpenRedirect>() == 4);

    assert!(std::mem::size_of::<SpoofCmdlineOrBootconfig>() == 8196);
    assert!(std::mem::align_of::<SpoofCmdlineOrBootconfig>() == 4);

    assert!(std::mem::size_of::<PriorityUidFilter>() == 65540);
    assert!(std::mem::align_of::<PriorityUidFilter>() == 4);

    assert!(std::mem::size_of::<SusfsVersion>() == 20);
    assert!(std::mem::align_of::<SusfsVersion>() == 4);

    assert!(std::mem::size_of::<SusfsVariant>() == 20);
    assert!(std::mem::align_of::<SusfsVariant>() == 4);

    assert!(std::mem::size_of::<SusfsEnabledFeatures>() == 8196);
    assert!(std::mem::align_of::<SusfsEnabledFeatures>() == 4);

    assert!(std::mem::size_of::<SusfsSusKstat>() == 376);
    assert!(std::mem::align_of::<SusfsSusKstat>() == 8);
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_struct_sizes() {
        assert_eq!(std::mem::size_of::<SusPath>(), 260);
        assert_eq!(std::mem::size_of::<HideSusMnts>(), 8);
        assert_eq!(std::mem::size_of::<SusMap>(), 260);
        assert_eq!(std::mem::size_of::<AvcLogSpoofing>(), 8);
        assert_eq!(std::mem::size_of::<SusfsLog>(), 8);
        assert_eq!(std::mem::size_of::<SusfsUname>(), 136);
        assert_eq!(std::mem::size_of::<SusfsOpenRedirect>(), 520);
        assert_eq!(std::mem::size_of::<SpoofCmdlineOrBootconfig>(), 8196);
        assert_eq!(std::mem::size_of::<PriorityUidFilter>(), 65540);
        assert_eq!(std::mem::size_of::<SusfsVersion>(), 20);
        assert_eq!(std::mem::size_of::<SusfsVariant>(), 20);
        assert_eq!(std::mem::size_of::<SusfsEnabledFeatures>(), 8196);
        assert_eq!(std::mem::size_of::<SusfsSusKstat>(), 376);
    }
}
