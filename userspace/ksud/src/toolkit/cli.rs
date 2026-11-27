use super::{bench, boot, config, ops, real};
use crate::ksucalls;
use anyhow::{Result, ensure};
use clap::Subcommand;
use serde::Serialize;
use serde_json::json;

#[derive(Debug, Subcommand)]
pub enum ToolkitCommands {
    /// Show toolkit status as JSON
    Status,
    Manager {
        #[command(subcommand)]
        command: Manager,
    },
    Version {
        #[command(subcommand)]
        command: Version,
    },
    Flags {
        #[command(subcommand)]
        command: Flags,
    },
    Uname {
        #[command(subcommand)]
        command: Uname,
    },
    Umount {
        #[command(subcommand)]
        command: Umount,
    },
    /// List mounts from PID 1 as JSON
    Mounts,
    /// Dump the kernel SU log as JSON
    Sulog,
    /// Compare syscall timings with SU compatibility enabled and disabled
    Bench {
        #[arg(long)]
        stable: bool,
        #[arg(long)]
        cpu: Option<u32>,
    },
    #[command(hide = true)]
    BenchWorker {
        #[arg(long)]
        cpu: u32,
    },
    /// Apply saved settings for a boot stage
    Apply { stage: boot::Stage },
    /// Import legacy settings and disable the toolkit module
    ImportModule,
}

#[derive(Debug, Subcommand)]
pub enum Manager {
    /// Switch manager and clear a different saved UID unless a boot manager is specified.
    Set {
        uid: u32,
        /// Keep this manager after reboot
        #[arg(long, conflicts_with = "fallback")]
        save: bool,
        /// Use this UID as the manager after reboot
        #[arg(long)]
        fallback: Option<u32>,
    },
    Save {
        uid: u32,
    },
    Unsave,
    /// Show the current manager UID and toolkit support as JSON
    Current,
}

#[derive(Debug, Subcommand)]
pub enum Version {
    Set {
        n: u32,
        #[arg(long)]
        save: bool,
    },
    Reset,
}

#[derive(Debug, Subcommand)]
pub enum Flags {
    Set { n: u32 },
    Reset,
}

#[derive(Debug, Subcommand)]
pub enum Uname {
    Set {
        release: String,
        version: String,
        #[arg(long)]
        save: bool,
    },
    Reset,
    SaveCurrent,
    Unsave,
}

#[derive(Debug, Subcommand)]
pub enum Umount {
    Add { path: String },
    Del { path: String },
    List,
}

fn print_json(value: &impl Serialize) -> Result<()> {
    println!("{}", serde_json::to_string(value)?);
    Ok(())
}

#[derive(Serialize)]
struct ManagerCurrent {
    current_uid: Option<u32>,
    toolkit: bool,
}

fn current_manager() -> ManagerCurrent {
    let current_uid = ops::manager_uid()
        .ok()
        .filter(|&uid| ops::valid_manager_uid(uid));
    let toolkit = current_uid.is_some_and(|uid| ops::set_uid(uid).is_ok());
    ManagerCurrent {
        current_uid,
        toolkit,
    }
}

fn status() -> Result<()> {
    let guard = config::status_lock()?;
    let cfg = config::read().unwrap_or_default();
    let boot = config::load::<boot::BootRecord>(boot::RECORD)
        .ok()
        .flatten()
        .unwrap_or_default();
    let info = crate::ksucalls::get_info_fresh()?;
    let uname = ops::current_uname()?;
    let manager = current_manager();
    let real = real::load();
    drop(guard);
    let module = config::module_dir();
    let mut status = json!({
        "kernel": { "version": info.version, "flags": info.flags, "release": uname.release, "uname_version": uname.version },
        "features": { "toolkit": manager.toolkit, "umount_list": ops::umount_size().is_ok(), "sulog": ops::sulog().is_ok() },
        "manager": { "current_uid": manager.current_uid, "saved_uid": cfg.manager_uid.filter(|_| cfg.manager_package.is_some()), "candidates": ops::candidates() },
        "version": { "saved": cfg.ksu_version },
        "uname": { "saved": cfg.uname },
        "umount": {
            "kernel_umount": ops::kernel_umount(),
            "saved": cfg.umount,
            "active": ops::umount_list().unwrap_or_default(),
            "provider": ops::umount_provider()
        },
        "module": {
            "present": config::module_present(module),
            "enabled": config::module_enabled(module),
            "legacy_config": !cfg.legacy_imported && config::legacy_exists()
        },
        "boot_errors": boot.errors
    });
    if let Some(real) = real {
        status["real"] =
            json!({ "version": real.version, "flags": real.flags, "release": real.release });
    }
    print_json(&status)
}

pub fn run(cmd: ToolkitCommands) -> Result<()> {
    match cmd {
        ToolkitCommands::Status => status(),
        ToolkitCommands::Manager { command } => match command {
            Manager::Set {
                uid,
                save,
                fallback,
            } => config::switch_manager(uid, save, fallback),
            Manager::Save { uid } => config::save_manager(Some(uid)),
            Manager::Unsave => config::save_manager(None),
            Manager::Current => {
                let guard = config::status_lock()?;
                let manager = current_manager();
                drop(guard);
                print_json(&manager)
            }
        },
        ToolkitCommands::Version { command } => match command {
            Version::Set { n, save } => {
                ensure!(n != 0, "version must be greater than zero");
                config::update(
                    |cfg| cfg.ksu_version = save.then_some(n),
                    || ops::set_version(n),
                )
            }
            Version::Reset => config::update(|cfg| cfg.ksu_version = None, || ops::set_version(0)),
        },
        ToolkitCommands::Flags { command } => {
            let _guard = config::lock()?;
            match command {
                Flags::Set { n } => {
                    ensure!(n != 0, "flags must be greater than zero");
                    ops::toggle_flags(n)
                }
                Flags::Reset => ops::set_flags(0),
            }
        }
        ToolkitCommands::Uname { command } => match command {
            Uname::Set {
                release,
                version,
                save,
            } => {
                let uname = config::Uname { release, version };
                if save {
                    config::update(
                        |cfg| cfg.uname = Some(uname.clone()),
                        || ops::set_uname(&uname),
                    )
                } else {
                    let _guard = config::lock()?;
                    ops::set_uname(&uname)
                }
            }
            Uname::Reset => config::update(
                |cfg| cfg.uname = None,
                || {
                    ops::set_uname(&config::Uname {
                        release: "default".into(),
                        version: "default".into(),
                    })
                },
            ),
            Uname::SaveCurrent => {
                let uname = ops::current_uname()?;
                uname.validate()?;
                config::edit(|cfg| cfg.uname = Some(uname))
            }
            Uname::Unsave => config::edit(|cfg| cfg.uname = None),
        },
        ToolkitCommands::Umount { command } => match command {
            Umount::Add { path } => {
                let path = ops::validate_mount_path(&path)?;
                config::update(
                    |cfg| {
                        if !cfg.umount.iter().any(|entry| entry.trim() == path) {
                            cfg.umount.push(path.to_owned());
                        }
                    },
                    || ops::add_umount(path),
                )?;
                ksucalls::report_module_mounted();
                Ok(())
            }
            Umount::Del { path } => {
                let path = ops::validate_mount_path(&path)?;
                config::update(
                    |cfg| cfg.umount.retain(|entry| entry.trim() != path),
                    || ops::del_umount(path),
                )
            }
            Umount::List => print_json(
                &json!({ "saved": config::read()?.umount, "active": ops::active_umount_list()? }),
            ),
        },
        ToolkitCommands::Mounts => print_json(&ops::mounts()?),
        ToolkitCommands::Sulog => print_json(&ops::sulog()?),
        ToolkitCommands::Bench { stable, cpu } => print_json(&bench::run(stable, cpu)?),
        ToolkitCommands::BenchWorker { cpu } => print_json(&bench::run_worker(cpu)?),
        ToolkitCommands::Apply { stage } => boot::apply(stage),
        ToolkitCommands::ImportModule => config::import_module(),
    }
}
