use anyhow::Result;
use clap::Subcommand;
use serde::Serialize;
use std::path::{Path, PathBuf};

use super::abi::SusfsError;
use super::boot::{BootRecord, get_boot_record_path};
use super::config::{
    CmdlineConfig, CmdlineMode, HideMountsMode, KstatMode, OpenRedirectConfig, SusKstatConfig,
    SusKstatStatic, SusfsConfig, UnameConfig, load_config, save_config, save_config_to_path,
};
use super::ops;

#[derive(Subcommand, Debug)]
pub enum SusfsCommands {
    /// Show SUSFS status
    Status,

    /// Show current SUSFS configuration
    Config,

    /// Enable SUSFS boot apply master switch
    Enable,

    /// Disable SUSFS boot apply master switch
    Disable,

    /// Modify SUSFS settings
    Set {
        #[command(subcommand)]
        command: SetCommand,
    },

    /// Add a SUSFS rule
    Add {
        #[command(subcommand)]
        command: AddCommand,
    },

    /// Remove a SUSFS rule
    Remove {
        #[command(subcommand)]
        command: RemoveCommand,
    },

    /// Export SUSFS configuration
    Export {
        /// Target file path (prints to stdout if omitted)
        file: Option<PathBuf>,
    },

    /// Import SUSFS configuration from JSON file
    Import {
        /// Source file path
        file: PathBuf,
    },

    /// Import configuration from legacy susfs4ksu module
    ImportModule,

    /// Reset SUSFS configuration to defaults
    Reset,

    /// Raw ksu_susfs argv command
    #[command(disable_help_flag = true)]
    Raw {
        #[arg(trailing_var_arg = true, allow_hyphen_values = true, num_args = 0..)]
        args: Vec<String>,
    },
}

#[derive(Subcommand, Debug)]
pub enum SetCommand {
    /// Enable or disable SUSFS kernel logging
    Log {
        #[arg(value_parser = ["on", "off"])]
        state: String,
    },

    /// Enable or disable AVC log spoofing
    Avc {
        #[arg(value_parser = ["on", "off"])]
        state: String,
    },

    /// Configure SUS mounts hiding mode
    HideMounts {
        #[arg(value_parser = ["off", "always", "boot"])]
        mode: String,
    },

    /// Set kernel uname spoofing or reset to defaults
    Uname {
        #[arg(long)]
        reset: bool,
        release: Option<String>,
        version: Option<String>,
    },

    /// Configure boot parameters spoofing
    Cmdline {
        #[command(subcommand)]
        command: CmdlineSubcommand,
    },

    /// Set priority UID filter packages (comma-separated list, empty string to clear)
    UidFilter {
        #[arg(default_value = "")]
        pkgs: String,
    },
}

#[derive(Subcommand, Debug)]
pub enum CmdlineSubcommand {
    /// Automatic cmdline / bootconfig spoofing (locked bootloader & green verified boot)
    Auto,
    /// Disable cmdline / bootconfig spoofing
    Off,
    /// Custom cmdline file
    File { path: PathBuf },
}

#[derive(Subcommand, Debug)]
pub enum AddCommand {
    /// Add SUS path
    Path {
        path: PathBuf,
        #[arg(long)]
        r#loop: bool,
    },

    /// Add SUS kstat rule
    Kstat {
        path: PathBuf,
        #[arg(long, conflicts_with_all = ["full_clone", "r#static"])]
        dynamic: bool,
        #[arg(long, conflicts_with_all = ["dynamic", "r#static"])]
        full_clone: bool,
        #[arg(long, num_args = 0..=1, default_missing_value = "")]
        r#static: Option<String>,
    },

    /// Add SUS map rule
    Map { path: PathBuf },

    /// Add open redirect rule
    Redirect {
        target: PathBuf,
        redirect: PathBuf,
        #[arg(value_parser = clap::value_parser!(i32))]
        scope: i32,
    },
}

#[derive(Subcommand, Debug)]
pub enum RemoveCommand {
    /// Remove SUS path
    Path { path: PathBuf },

    /// Remove SUS kstat rule
    Kstat { path: PathBuf },

    /// Remove SUS map rule
    Map { path: PathBuf },

    /// Remove open redirect rule
    Redirect { target: PathBuf },
}

#[derive(Serialize)]
struct StatusOutput {
    available: bool,
    version: String,
    variant: String,
    features: Vec<String>,
    source: String,
    enabled: bool,
    module_conflict: bool,
    last_boot: Option<BootRecord>,
}

fn print_mutation_success(live: bool, reboot: bool) {
    println!(r#"{{"ok":true,"live":{live},"reboot":{reboot}}}"#);
}

fn print_import_module_success(imported: usize) {
    println!(r#"{{"ok":true,"live":false,"reboot":true,"imported":{imported}}}"#);
}

fn print_failure_and_exit(err_msg: &str, unsupported: bool) -> ! {
    let escaped_err = serde_json::to_string(err_msg).unwrap_or_else(|_| format!("\"{err_msg}\""));
    println!(r#"{{"ok":false,"error":{escaped_err},"unsupported":{unsupported}}}"#);
    std::process::exit(1);
}

fn handle_live_res(res: Result<(), SusfsError>) {
    if let Err(e) = res {
        match e {
            SusfsError::Unsupported => {
                print_failure_and_exit("SUSFS operation not supported in this kernel", true);
            }
            SusfsError::Errno(code) => {
                let reason = std::io::Error::from_raw_os_error(code.abs());
                print_failure_and_exit(&format!("SUSFS kernel error {code}: {reason}"), false);
            }
        }
    }
}

// A broken config must never be silently replaced by defaults on the next write
fn load_config_or_exit() -> SusfsConfig {
    load_config().unwrap_or_else(|e| print_failure_and_exit(&format!("{e:#}"), false))
}

pub fn parse_kstat_static_pairs(input: &str) -> std::result::Result<SusKstatStatic, String> {
    let mut s = SusKstatStatic::default();
    if input.trim().is_empty() {
        return Ok(s);
    }
    let parts = input.split([',', ' ']);
    for part in parts {
        let trimmed = part.trim();
        if trimmed.is_empty() {
            continue;
        }
        let Some((k, v)) = trimmed.split_once('=') else {
            return Err(format!(
                "invalid static pair '{trimmed}', expected key=value"
            ));
        };
        let val = v
            .trim()
            .parse::<i64>()
            .map_err(|_| format!("invalid numeric value in '{trimmed}'"))?;
        match k.trim() {
            "ino" => s.ino = Some(val),
            "dev" => s.dev = Some(val),
            "nlink" => s.nlink = Some(val),
            "size" => s.size = Some(val),
            "atime" => s.atime = Some(val),
            "atime_nsec" => s.atime_nsec = Some(val),
            "mtime" => s.mtime = Some(val),
            "mtime_nsec" => s.mtime_nsec = Some(val),
            "ctime" => s.ctime = Some(val),
            "ctime_nsec" => s.ctime_nsec = Some(val),
            "blocks" => s.blocks = Some(val),
            "blksize" => s.blksize = Some(val),
            other => return Err(format!("unknown static kstat field '{other}'")),
        }
    }
    Ok(s)
}

pub fn run_susfs(cmd: SusfsCommands) -> Result<()> {
    match cmd {
        SusfsCommands::Status => {
            let config = load_config().unwrap_or_default();
            let available = ops::is_available();
            let (version, variant, features, source) = if available {
                (
                    ops::get_version().unwrap_or_default(),
                    ops::get_variant().unwrap_or_default(),
                    ops::get_enabled_features().unwrap_or_default(),
                    ops::get_source().to_string(),
                )
            } else {
                (String::new(), String::new(), Vec::new(), "none".to_string())
            };

            let mod_dir = Path::new("/data/adb/modules/susfs4ksu");
            let module_conflict = mod_dir.is_dir()
                && !mod_dir.join("disable").exists()
                && !mod_dir.join("remove").exists();

            let last_boot_path = get_boot_record_path();
            let last_boot = if last_boot_path.exists() {
                std::fs::read_to_string(&last_boot_path)
                    .ok()
                    .and_then(|c| serde_json::from_str::<BootRecord>(&c).ok())
            } else {
                None
            };

            let out = StatusOutput {
                available,
                version,
                variant,
                features,
                source,
                enabled: config.enabled,
                module_conflict,
                last_boot,
            };

            let json = serde_json::to_string(&out).unwrap_or_else(|_| "{}".to_string());
            println!("{json}");
            Ok(())
        }

        SusfsCommands::Config => {
            let config = match load_config() {
                Ok(c) => c,
                Err(e) => print_failure_and_exit(&format!("failed to load config: {e}"), false),
            };
            let json = serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string());
            println!("{json}");
            Ok(())
        }

        SusfsCommands::Enable => {
            let mut config = match load_config() {
                Ok(c) => c,
                Err(e) => print_failure_and_exit(&format!("failed to load config: {e}"), false),
            };
            config.enabled = true;
            if let Err(e) = save_config(&config) {
                print_failure_and_exit(&format!("failed to save config: {e}"), false);
            }
            print_mutation_success(false, false);
            Ok(())
        }

        SusfsCommands::Disable => {
            let mut config = match load_config() {
                Ok(c) => c,
                Err(e) => print_failure_and_exit(&format!("failed to load config: {e}"), false),
            };
            config.enabled = false;
            if let Err(e) = save_config(&config) {
                print_failure_and_exit(&format!("failed to save config: {e}"), false);
            }
            print_mutation_success(false, false);
            Ok(())
        }

        SusfsCommands::Set { command } => match command {
            SetCommand::Log { state } => {
                let val = state == "on";
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                handle_live_res(ops::set_log(val));
                let mut config = load_config_or_exit();
                config.log = val;
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            SetCommand::Avc { state } => {
                let val = state == "on";
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                handle_live_res(ops::set_avc_log_spoofing(val));
                let mut config = load_config_or_exit();
                config.avc_log_spoofing = val;
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            SetCommand::HideMounts { mode } => {
                let (hide_mode, live_val) = match mode.as_str() {
                    "off" => (HideMountsMode::Off, false),
                    "always" => (HideMountsMode::Always, true),
                    "boot" => (HideMountsMode::Boot, true),
                    _ => print_failure_and_exit("invalid hide-mounts mode", false),
                };
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                handle_live_res(ops::hide_sus_mnts_for_non_su_procs(live_val));
                let mut config = load_config_or_exit();
                config.hide_sus_mounts = hide_mode;
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            SetCommand::Uname {
                reset,
                release,
                version,
            } => {
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                let mut config = load_config_or_exit();
                if reset {
                    handle_live_res(ops::set_uname("default", "default"));
                    config.uname = UnameConfig::default();
                } else {
                    let rel = release.unwrap_or_default();
                    let ver = version.unwrap_or_default();
                    handle_live_res(ops::set_uname(&rel, &ver));
                    config.uname = UnameConfig {
                        release: rel,
                        version: ver,
                    };
                }
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            SetCommand::Cmdline { command } => match command {
                CmdlineSubcommand::Auto => {
                    if !ops::is_available() {
                        print_failure_and_exit("SUSFS is not available", true);
                    }
                    let bootconfig = Path::new("/proc/bootconfig");
                    let cmdline = Path::new("/proc/cmdline");
                    let content = if bootconfig.exists() {
                        let raw = std::fs::read_to_string(bootconfig)
                            .map_err(|e| {
                                print_failure_and_exit(
                                    &format!("failed to read /proc/bootconfig: {e}"),
                                    false,
                                )
                            })
                            .unwrap();
                        super::boot::rewrite_bootconfig(&raw)
                    } else if cmdline.exists() {
                        let raw = std::fs::read_to_string(cmdline)
                            .map_err(|e| {
                                print_failure_and_exit(
                                    &format!("failed to read /proc/cmdline: {e}"),
                                    false,
                                )
                            })
                            .unwrap();
                        super::boot::rewrite_cmdline(&raw)
                    } else {
                        print_failure_and_exit(
                            "neither /proc/bootconfig nor /proc/cmdline exists",
                            false,
                        );
                    };

                    let spoof_path = super::boot::get_cmdline_spoof_path();
                    if let Some(parent) = spoof_path.parent() {
                        let _ = std::fs::create_dir_all(parent);
                    }
                    if let Err(e) = std::fs::write(&spoof_path, content.as_bytes()) {
                        print_failure_and_exit(
                            &format!("failed to write cmdline spoof file: {e}"),
                            false,
                        );
                    }

                    handle_live_res(ops::set_cmdline_or_bootconfig_from_content(
                        content.as_bytes(),
                    ));

                    let mut config = load_config_or_exit();
                    config.cmdline = CmdlineConfig {
                        mode: CmdlineMode::Auto,
                        file: String::new(),
                    };
                    if let Err(e) = save_config(&config) {
                        print_failure_and_exit(&format!("failed to save config: {e}"), false);
                    }
                    print_mutation_success(true, false);
                    Ok(())
                }

                CmdlineSubcommand::File { path } => {
                    if !ops::is_available() {
                        print_failure_and_exit("SUSFS is not available", true);
                    }
                    handle_live_res(ops::set_cmdline_or_bootconfig_from_file(&path));
                    let mut config = load_config_or_exit();
                    config.cmdline = CmdlineConfig {
                        mode: CmdlineMode::File,
                        file: path.to_string_lossy().to_string(),
                    };
                    if let Err(e) = save_config(&config) {
                        print_failure_and_exit(&format!("failed to save config: {e}"), false);
                    }
                    print_mutation_success(true, false);
                    Ok(())
                }

                CmdlineSubcommand::Off => {
                    let mut config = load_config_or_exit();
                    config.cmdline = CmdlineConfig {
                        mode: CmdlineMode::Off,
                        file: String::new(),
                    };
                    if let Err(e) = save_config(&config) {
                        print_failure_and_exit(&format!("failed to save config: {e}"), false);
                    }
                    print_mutation_success(false, true);
                    Ok(())
                }
            },

            SetCommand::UidFilter { pkgs } => {
                let trimmed = pkgs.trim();
                let mut config = load_config_or_exit();

                if trimmed.is_empty() {
                    config.uid_filter.clear();
                    if let Err(e) = save_config(&config) {
                        print_failure_and_exit(&format!("failed to save config: {e}"), false);
                    }
                    print_mutation_success(false, true);
                    return Ok(());
                }

                let pkg_list: Vec<String> = trimmed
                    .split(',')
                    .map(str::trim)
                    .filter(|s| !s.is_empty())
                    .map(ToString::to_string)
                    .collect();

                let installed_pkgs = ops::filterable_packages(&pkg_list);

                let live = if installed_pkgs.is_empty() {
                    false
                } else {
                    if !ops::is_available() {
                        print_failure_and_exit("SUSFS is not available", true);
                    }
                    handle_live_res(ops::set_priority_uid_filter(&installed_pkgs));
                    true
                };

                config.set_uid_filter(pkg_list);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(live, false);
                Ok(())
            }
        },

        SusfsCommands::Add { command } => match command {
            AddCommand::Path { path, r#loop } => {
                let path_str = path.to_string_lossy().to_string();
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                if r#loop {
                    handle_live_res(ops::add_sus_path_loop(&path_str));
                } else {
                    handle_live_res(ops::add_sus_path(&path));
                }
                let mut config = load_config_or_exit();
                config.add_or_replace_path(path_str, r#loop);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            AddCommand::Kstat {
                path,
                dynamic: _,
                full_clone,
                r#static,
            } => {
                let path_str = path.to_string_lossy().to_string();
                let mut config = load_config_or_exit();

                let (kstat_cfg, live, reboot) = if let Some(static_pairs) = r#static {
                    let parsed_static = match parse_kstat_static_pairs(&static_pairs) {
                        Ok(s) => s,
                        Err(e) => print_failure_and_exit(&e, false),
                    };
                    if !ops::is_available() {
                        print_failure_and_exit("SUSFS is not available", true);
                    }
                    handle_live_res(ops::add_sus_kstat_statically(&path, &parsed_static));
                    (
                        SusKstatConfig {
                            path: path_str,
                            mode: KstatMode::Static,
                            r#static: Some(parsed_static),
                        },
                        true,
                        false,
                    )
                } else {
                    let mode = if full_clone {
                        KstatMode::FullClone
                    } else {
                        KstatMode::Dynamic
                    };
                    (
                        SusKstatConfig {
                            path: path_str,
                            mode,
                            r#static: None,
                        },
                        false,
                        true,
                    )
                };

                config.add_or_replace_kstat(kstat_cfg);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(live, reboot);
                Ok(())
            }

            AddCommand::Map { path } => {
                let path_str = path.to_string_lossy().to_string();
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                handle_live_res(ops::add_sus_map(&path));
                let mut config = load_config_or_exit();
                config.add_map(path_str);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }

            AddCommand::Redirect {
                target,
                redirect,
                scope,
            } => {
                let target_str = target.to_string_lossy().to_string();
                let redirect_str = redirect.to_string_lossy().to_string();
                if !ops::is_available() {
                    print_failure_and_exit("SUSFS is not available", true);
                }
                handle_live_res(ops::add_open_redirect(&target, &redirect, scope));
                let mut config = load_config_or_exit();
                config.add_or_replace_redirect(OpenRedirectConfig {
                    target: target_str,
                    redirect: redirect_str,
                    scope,
                });
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(true, false);
                Ok(())
            }
        },

        SusfsCommands::Remove { command } => match command {
            RemoveCommand::Path { path } => {
                let path_str = path.to_string_lossy();
                let mut config = load_config_or_exit();
                config.remove_path(&path_str);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(false, true);
                Ok(())
            }

            RemoveCommand::Kstat { path } => {
                let path_str = path.to_string_lossy();
                let mut config = load_config_or_exit();
                config.remove_kstat(&path_str);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(false, true);
                Ok(())
            }

            RemoveCommand::Map { path } => {
                let path_str = path.to_string_lossy();
                let mut config = load_config_or_exit();
                config.remove_map(&path_str);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(false, true);
                Ok(())
            }

            RemoveCommand::Redirect { target } => {
                let target_str = target.to_string_lossy();
                let mut config = load_config_or_exit();
                config.remove_redirect(&target_str);
                if let Err(e) = save_config(&config) {
                    print_failure_and_exit(&format!("failed to save config: {e}"), false);
                }
                print_mutation_success(false, true);
                Ok(())
            }
        },

        SusfsCommands::Export { file } => {
            let config = match load_config() {
                Ok(c) => c,
                Err(e) => print_failure_and_exit(&format!("failed to load config: {e}"), false),
            };
            if let Some(dest) = file {
                if let Err(e) = save_config_to_path(&config, &dest) {
                    print_failure_and_exit(
                        &format!("failed to export config to {}: {e}", dest.display()),
                        false,
                    );
                }
            } else {
                let json = serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string());
                println!("{json}");
            }
            Ok(())
        }

        SusfsCommands::Import { file } => {
            let config = match super::config::load_config_from_path(&file) {
                Ok(c) => c,
                Err(e) => print_failure_and_exit(
                    &format!("failed to import config from {}: {e}", file.display()),
                    false,
                ),
            };
            if let Err(e) = save_config(&config) {
                print_failure_and_exit(&format!("failed to save config: {e}"), false);
            }
            print_mutation_success(false, true);
            Ok(())
        }

        SusfsCommands::ImportModule => {
            let mut config = load_config_or_exit();
            let base_dir = Path::new("/data/adb/susfs4ksu");
            let imported = super::import::import_from_module(base_dir, &mut config);
            if let Err(e) = save_config(&config) {
                print_failure_and_exit(&format!("failed to save config: {e}"), false);
            }
            print_import_module_success(imported);
            Ok(())
        }

        SusfsCommands::Reset => {
            let default_cfg = SusfsConfig::default();
            if let Err(e) = save_config(&default_cfg) {
                print_failure_and_exit(&format!("failed to reset config: {e}"), false);
            }
            print_mutation_success(false, true);
            Ok(())
        }

        SusfsCommands::Raw { args } => {
            let mut full_argv = vec!["ksu_susfs".to_string()];
            full_argv.extend(args);
            super::compat::run_compat_argv(&full_argv)
        }
    }
}
