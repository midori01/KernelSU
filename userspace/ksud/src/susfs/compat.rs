use std::path::Path;

use super::abi::{
    APP_STR_BUF_SIZE, CMD_SUSFS_ADD_OPEN_REDIRECT, CMD_SUSFS_ADD_SUS_KSTAT,
    CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY, CMD_SUSFS_ADD_SUS_MAP, CMD_SUSFS_ADD_SUS_PATH,
    CMD_SUSFS_ADD_SUS_PATH_LOOP, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING, CMD_SUSFS_ENABLE_LOG,
    CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS, CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG,
    CMD_SUSFS_SET_PRIORITY_UID_FILTER, CMD_SUSFS_SET_UNAME, CMD_SUSFS_SHOW_ENABLED_FEATURES,
    CMD_SUSFS_SHOW_VARIANT, CMD_SUSFS_SHOW_VERSION, CMD_SUSFS_UPDATE_SUS_KSTAT,
    ERR_CMD_NOT_SUPPORTED, SusfsError, UID_NON_APP_PROC, UID_UMOUNTED_PROC,
};
use super::config::SusKstatStatic;
use super::ops;

pub fn print_help() {
    print!(
        "usage: ksu_susfs <CMD> [CMD options]\n  <CMD>:\n    add_sus_path </path/of/file_or_directory>\n      |--> Added path and all its sub-paths will be hidden for umounted app process from several syscalls\n      |--> Please be reminded that if the target path has upper mounts then make sure the proper layer is added, otherwise it may not be effective for the target process.\n      * Important Notes *\n      - Only effective for umounted process with uid >= 10000\n\n    add_sus_path_loop </path/of/file_or_directory>\n      |--> The only difference to add_sus_path is that the added sus_path via this cli will be flagged as SUS_PATH again for the app process when it is being spawned by zygote and marked umounted\n      |--> Also it does not check if the path is existed or not, instead it checks for empty string only, so be careful what to add.\n      * Important Notes *\n      - Only effective for umounted process with uid >= 10000\n\n    hide_sus_mnts_for_non_su_procs <0|1>\n      |--> 0 -> DO NOT hide sus mounts for non-su processes\n      |--> 1 -> hide all sus mounts for non-su processes\n      * Important Notes *\n      - It is set to 0 in kernel by default, but it is a MUST now to have it enabled all the time\n        because of the zygote_next exploit. It is also required if kernel_umount is disabled in KSU manager\n\n    add_sus_kstat_statically </path/of/file_or_directory> <ino> <dev> <nlink> <size> <atime> <atime_nsec> <mtime> <mtime_nsec> <ctime> <ctime_nsec> <blocks> <blksize>\n      |--> Use 'stat' tool to find the format:\n               ino -> %i, dev -> %d, nlink -> %h, atime -> %X, mtime -> %Y, ctime -> %Z\n               size -> %s, blocks -> %b, blksize -> %B\n      |--> e.g., ksu_susfs add_sus_kstat_statically '/system/addon.d' '1234' '1234' '2' '223344'\\\n                    'default' 'default'\n      * Important Notes *\n      - Effective for all processes with uid >= 10000\n\n    add_sus_kstat </path/of/file_or_directory>\n      |--> Add the desired path BEFORE it gets bind mounted or overlayed, this is used for storing original stat info in kernel memory\n      |--> This command must be completed with <update_sus_kstat> later after the added path is bind mounted or overlayed\n      * Important Notes *\n      - Effective for all processes with uid >= 10000\n\n    update_sus_kstat </path/of/file_or_directory>\n      |--> Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure\n      |--> This updates the target ino, but size and blocks are remained the same as current stat\n      * Important Notes *\n      - Effective for all processes with uid >= 10000\n\n    update_sus_kstat_full_clone </path/of/file_or_directory>\n      |--> Add the desired path you have added before via <add_sus_kstat> to complete the kstat spoofing procedure\n      |--> This updates the target ino only, other stat members are remained the same as the original stat\n      * Important Notes *\n      - Effective for all processes with uid >= 10000\n\n    add_sus_map </path/to/actual/library>\n      |--> added real file path which gets mmapped will be hidden from /proc/self/[maps|smaps|smaps_rollup|map_files|mem|pagemap]\n      |--> e.g., add_sus_map '/data/adb/modules/my_module/zygisk/arm64-v8a.so'\n      * Important Notes *\n      - It does NOT support hiding for anon memory.\n      - It does NOT hide any inline hooks or plt hooks cause by the injected library itself\n      - It may not be able to evade detections by apps that implement a good injection detection\n      - Only effective for umounted process with uid >= 10000\n\n    set_priority_uid_filter <app_package_names_separated_by_comma>\n      |--> The uids of the added apps will be invisible for the apps that are abusing the getpriority/setpriority syscall,\n      * Important Notes *\n      - The uids of the apps are resolved from their data paths, hence they are always up to date\n      - Only effective for all processes with uid >= 10000\n\n    set_uname <release> <version>\n      |--> Spoof uname for all processes, set string to 'default' to imply the function to use original string\n      |--> NOTE: only 'release' and <version> are spoofed as others are no longer needed\n      |--> e.g., set_uname '4.9.337-g3291538446b7' '#1 SMP PREEMPT Mon Oct 6 16:50:48 UTC 2025'\n\n    set_cmdline_or_bootconfig </path/to/fake_cmdline_file/or/fake_bootconfig_file>\n      |--> Spoof the output of /proc/cmdline (non-gki) or /proc/bootconfig (gki) from a text file\n\n    enable_log <0|1>\n      |--> 0: disable susfs log in kernel\n      |--> 1: enable susfs log in kernel\n\n    enable_avc_log_spoofing <0|1>\n      |--> 0: disable spoofing the sus tcontext 'su' shown in avc log in kernel\n      |--> 1: enable spoofing the sus tcontext 'su' with 'u:r:priv_app:s0:c512,c768' shown in avc log in kernel\n      * Important Notes *\n      - It is set to '0' by default in kernel\n      - Enabling this may sometimes make developers hard to identify the cause when they are debugging with some permission or selinux issues, so users are advised to disable this when doing so.\n\n    add_open_redirect </target/path> </redirected/path> <uid_scheme>\n      |--> Redirect the target path to be opened with user defined path and pre-defined uid scheme\n      |--> <uid_scheme>\n             |--> 0: Effective for non-app processes (uid < 10000)\n             |--> 1: Effective for non-su processes of which uid is 0 (All root process but not with su domain)\n             |--> 2: Effective for non-su processes (Use it carefully!)\n             |--> 3: Effective for processes that are marked umounted with uid >= 10000 (Use it carefully!)\n             |--> 4: Effective for processes that are marked umounted (include most of the init spawned process, use it carefully!)\n      * Important Notes *\n      - Both target_pathname and redirected_pathname must be existed before they can be added to open_redirect\n      - Users have to take care of the selinux permission of both target_pathname and redirected_pathname by themselves\n      - Only effective for current process that matches the pre-defined uid scheme\n\n    show <version|enabled_features|variant>\n      |--> version: show the current susfs version implemented in kernel\n      |--> enabled_features: show the current implemented susfs features in kernel\n      |--> variant: show the current variant: GKI or NON-GKI\n"
    );
}

fn check_root() -> bool {
    unsafe { libc::getuid() == 0 }
}

fn prt_if_not_supported(err: i32, cmd: u32) {
    if err == ERR_CMD_NOT_SUPPORTED {
        println!("[-] CMD: '0x{cmd:x}', SUSFS operation not supported, please enable it in kernel");
    }
}

fn parse_static_field(arg: &str) -> Result<Option<i64>, ()> {
    if arg == "default" {
        Ok(None)
    } else {
        arg.parse::<i64>().map(Some).map_err(|_| ())
    }
}

pub fn run_compat_argv(argv: &[String]) -> anyhow::Result<()> {
    if argv.len() < 2 || argv[1] == "-h" || argv[1] == "--help" {
        print_help();
        std::process::exit(libc::EINVAL);
    }

    if !check_root() {
        println!("[-] Must run as root");
        std::process::exit(libc::EPERM);
    }

    let cmd = argv[1].as_str();

    match cmd {
        "add_sus_path" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            if argv[2].is_empty() {
                println!("[-] argv[2] is empty'");
                std::process::exit(libc::EINVAL);
            }
            match ops::add_sus_path(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_SUS_PATH);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "add_sus_path_loop" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            if argv[2].is_empty() {
                println!("[-] argv[2] is empty'");
                std::process::exit(libc::EINVAL);
            }
            match ops::add_sus_path_loop(&argv[2]) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_SUS_PATH_LOOP);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "hide_sus_mnts_for_non_su_procs" => {
            if argv.len() != 3 || (argv[2] != "0" && argv[2] != "1") {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            let enabled = argv[2] == "1";
            match ops::hide_sus_mnts_for_non_su_procs(enabled) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(
                        ERR_CMD_NOT_SUPPORTED,
                        CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS,
                    );
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "add_sus_map" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            if argv[2].is_empty() {
                println!("[-] argv[2] is empty'");
                std::process::exit(libc::EINVAL);
            }
            match ops::add_sus_map(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_SUS_MAP);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "set_priority_uid_filter" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            if argv[2].is_empty() {
                println!("[-] argv[2] is empty'");
                std::process::exit(libc::EINVAL);
            }
            if argv[2].len() >= APP_STR_BUF_SIZE {
                println!(
                    "[-] the app package names are too long, the maximum length is {}",
                    APP_STR_BUF_SIZE - 1
                );
                std::process::exit(libc::EINVAL);
            }
            let pkgs: Vec<String> = argv[2].split(',').map(String::from).collect();
            match ops::set_priority_uid_filter(&pkgs) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_SET_PRIORITY_UID_FILTER);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "add_open_redirect" => {
            if argv.len() != 5 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            if argv[2].is_empty() || argv[3].is_empty() {
                println!("[-] empty path");
                std::process::exit(libc::EINVAL);
            }
            let Ok(scope) = argv[4].parse::<i32>() else {
                print_help();
                std::process::exit(libc::EINVAL);
            };
            if !(UID_NON_APP_PROC..=UID_UMOUNTED_PROC).contains(&scope) {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::add_open_redirect(Path::new(&argv[2]), Path::new(&argv[3]), scope) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_OPEN_REDIRECT);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "add_sus_kstat_statically" => {
            if argv.len() != 15 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            let (
                Ok(ino),
                Ok(dev),
                Ok(nlink),
                Ok(size),
                Ok(atime),
                Ok(atime_nsec),
                Ok(mtime),
                Ok(mtime_nsec),
                Ok(ctime),
                Ok(ctime_nsec),
                Ok(blocks),
                Ok(blksize),
            ) = (
                parse_static_field(&argv[3]),
                parse_static_field(&argv[4]),
                parse_static_field(&argv[5]),
                parse_static_field(&argv[6]),
                parse_static_field(&argv[7]),
                parse_static_field(&argv[8]),
                parse_static_field(&argv[9]),
                parse_static_field(&argv[10]),
                parse_static_field(&argv[11]),
                parse_static_field(&argv[12]),
                parse_static_field(&argv[13]),
                parse_static_field(&argv[14]),
            )
            else {
                print_help();
                std::process::exit(libc::EINVAL);
            };

            let static_fields = SusKstatStatic {
                ino,
                dev,
                nlink,
                size,
                atime,
                atime_nsec,
                mtime,
                mtime_nsec,
                ctime,
                ctime_nsec,
                blocks,
                blksize,
            };

            match ops::add_sus_kstat_statically(Path::new(&argv[2]), &static_fields) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "add_sus_kstat" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::add_sus_kstat(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ADD_SUS_KSTAT);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "update_sus_kstat" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::update_sus_kstat(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_UPDATE_SUS_KSTAT);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "update_sus_kstat_full_clone" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::update_sus_kstat_full_clone(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_UPDATE_SUS_KSTAT);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "enable_log" => {
            if argv.len() != 3 || (argv[2] != "0" && argv[2] != "1") {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            let enabled = argv[2] == "1";
            match ops::set_log(enabled) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ENABLE_LOG);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "set_cmdline_or_bootconfig" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::set_cmdline_or_bootconfig_from_file(Path::new(&argv[2])) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(
                        ERR_CMD_NOT_SUPPORTED,
                        CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG,
                    );
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "enable_avc_log_spoofing" => {
            if argv.len() != 3 || (argv[2] != "0" && argv[2] != "1") {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            let enabled = argv[2] == "1";
            match ops::set_avc_log_spoofing(enabled) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        "show" => {
            if argv.len() != 3 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match argv[2].as_str() {
                "version" => match ops::get_version() {
                    Ok(v) => {
                        println!("{v}");
                        std::process::exit(0);
                    }
                    Err(SusfsError::Unsupported) => {
                        prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_SHOW_VERSION);
                        std::process::exit(ERR_CMD_NOT_SUPPORTED);
                    }
                    Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
                },
                "variant" => match ops::get_variant() {
                    Ok(v) => {
                        println!("{v}");
                        std::process::exit(0);
                    }
                    Err(SusfsError::Unsupported) => {
                        prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_SHOW_VARIANT);
                        std::process::exit(ERR_CMD_NOT_SUPPORTED);
                    }
                    Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
                },
                "enabled_features" => match ops::get_enabled_features() {
                    Ok(features) => {
                        for f in features {
                            println!("{f}");
                        }
                        std::process::exit(0);
                    }
                    Err(SusfsError::Unsupported) => {
                        prt_if_not_supported(
                            ERR_CMD_NOT_SUPPORTED,
                            CMD_SUSFS_SHOW_ENABLED_FEATURES,
                        );
                        std::process::exit(ERR_CMD_NOT_SUPPORTED);
                    }
                    Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
                },
                _ => {
                    print_help();
                    std::process::exit(libc::EINVAL);
                }
            }
        }
        "set_uname" => {
            if argv.len() != 4 {
                print_help();
                std::process::exit(libc::EINVAL);
            }
            match ops::set_uname(&argv[2], &argv[3]) {
                Ok(()) => std::process::exit(0),
                Err(SusfsError::Unsupported) => {
                    prt_if_not_supported(ERR_CMD_NOT_SUPPORTED, CMD_SUSFS_SET_UNAME);
                    std::process::exit(ERR_CMD_NOT_SUPPORTED);
                }
                Err(SusfsError::Errno(e)) => std::process::exit(e.abs()),
            }
        }
        _ => {
            print_help();
            std::process::exit(libc::EINVAL);
        }
    }
}
