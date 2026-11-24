use super::{
    bench,
    config::{self, Uname},
    ops,
};
use crate::ksucalls;
use anyhow::{Result, ensure};
use std::ffi::OsString;
use std::io::{self, Write};

const USAGE: &str = "Usage:\n./toolkit\n   --bench <? cpu>\n   --setuid <uid>\n   --getuid\n   --getlist\n   --getinfo\n   --sulog\n   --setver <? uint>\n   --setflags <? uint>\n   --fkuname \"6.18\" \"#0 SMP ...\"\n";

fn show_bench(cpu: Option<&str>) -> Result<()> {
    let cpu = cpu.map_or_else(bench::highest_allowed_cpu, |cpu| Ok(cpu.parse()?))?;
    let uname = ops::current_uname()?;
    let sucompat = unsafe {
        libc::syscall(
            libc::SYS_faccessat,
            libc::AT_FDCWD,
            c"/system/bin/su".as_ptr(),
            libc::F_OK,
        ) == 0
    };
    let root = if unsafe { libc::getuid() } == 0 {
        "yes"
    } else {
        "no"
    };
    let seccomp = match bench::seccomp() {
        0 => '0',
        1 => '1',
        2 => '2',
        _ => '?',
    };
    let round = bench::round(cpu, sucompat, true)?;
    println!("[+] kernel: {}", uname.release);
    println!(
        "[+] iterations: {:06} | core: {:02}",
        bench::ITERATIONS,
        cpu % 100
    );
    println!(
        "[+] sucompat: {} | seccomp: {seccomp} | root: {root}",
        u8::from(round.sucompat)
    );
    println!(
        "[!] tests:\n[1] NULL\n[2] /dev/null\n[3] /system/bin/su_\n[4] *unaligned*\n[*] Lower is better, * = untracked\n"
    );
    for result in round.results {
        let suffix = if result.tracked { "" } else { "*" };
        let label = format!("{}{suffix}:", result.syscall);
        println!(
            "[{}] {label:13}({:07} ns avg)",
            result.test,
            result.ns % 10_000_000
        );
    }
    println!("\n[+] total avgs:   {:09}", round.total_ns % 1_000_000_000);
    Ok(())
}

fn set_override(args: &[&str], apply: fn(u32) -> Result<()>) -> Result<()> {
    let value = if let [arg] = args {
        let n = arg.parse()?;
        ensure!(n != 0, "override must be greater than zero");
        n
    } else {
        0
    };
    let _guard = config::lock()?;
    apply(value)?;
    println!("ok");
    Ok(())
}

fn run_args(args: &[&str]) -> Result<()> {
    match args {
        ["--getuid"] => {
            let uid = ops::manager_uid()?;
            ops::validate_uid(uid)?;
            println!("{uid}");
        }
        ["--getlist"] => {
            let mut bytes = ops::umount_bytes()?;
            if bytes.is_empty() {
                eprintln!("list empty");
                std::process::exit(1);
            }
            for byte in &mut bytes {
                if *byte == 0 {
                    *byte = b'\n';
                }
            }
            io::stdout().write_all(&bytes)?;
        }
        ["--getinfo"] => {
            let info = ksucalls::get_info_fresh()?;
            println!(
                "ksuver: {:06}\nflags: {:06}\nfeatures: {:06}\nuapi: {:06}",
                info.version % 1_000_000,
                info.flags % 1_000_000,
                info.features % 1_000_000,
                info.uapi_version % 1_000_000
            );
        }
        ["--sulog"] => {
            let sulog = ops::sulog()?;
            println!("uptime: {:010}", sulog.uptime);
            for entry in sulog.entries {
                println!(
                    "sym: {} uid: {:06} time: {:010}",
                    entry.sym,
                    entry.uid % 1_000_000,
                    entry.time
                );
            }
        }
        ["--setuid", uid] => {
            let uid = uid.parse::<u32>()? % 100_000;
            config::switch_manager(uid, false, None)?;
            println!("ok");
        }
        ["--setver" | "--setflags", rest @ ..] if rest.len() <= 1 => {
            set_override(
                rest,
                if args[0] == "--setver" {
                    ops::set_version
                } else {
                    ops::set_flags
                },
            )?;
        }
        ["--fkuname", release, version] => {
            let _guard = config::lock()?;
            ops::set_uname(&Uname {
                release: (*release).to_owned(),
                version: (*version).to_owned(),
            })?;
            println!("ok");
        }
        ["--bench", rest @ ..] if rest.len() <= 1 => show_bench(rest.first().copied())?,
        _ => {
            eprint!("{USAGE}");
            std::process::exit(1);
        }
    }
    Ok(())
}

pub fn run() {
    let args: Vec<OsString> = std::env::args_os().skip(1).collect();
    let args = args
        .iter()
        .map(|arg| arg.to_str())
        .collect::<Option<Vec<_>>>();
    match args {
        Some(args) if run_args(&args).is_ok() => {}
        _ => {
            eprintln!("fail");
            std::process::exit(1);
        }
    }
}
