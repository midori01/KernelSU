use super::{config, ops};
use crate::defs::TOOLKIT_DIR;
use crate::{feature_id::FeatureId, ksucalls};
use anyhow::{Context, Result, bail, ensure};
use libc::c_long;
use serde::{Deserialize, Serialize};
use std::fs::{self, File, OpenOptions};
use std::io;
use std::os::fd::AsRawFd;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::Command;

pub(super) const ITERATIONS: u32 = 500_000;
const FEATURES: [FeatureId; 3] = [FeatureId::Sulog, FeatureId::AdbRoot, FeatureId::SuCompat];

#[derive(Serialize, Deserialize)]
pub(super) struct Sample {
    pub test: usize,
    pub syscall: String,
    pub ns: u64,
    pub tracked: bool,
}

#[derive(Serialize, Deserialize)]
pub(super) struct Round {
    pub sucompat: bool,
    pub total_ns: u64,
    pub results: Vec<Sample>,
}

#[derive(Serialize)]
pub(super) struct Report {
    kernel: String,
    cpu: u32,
    mode: &'static str,
    freq_mhz: Option<u32>,
    iterations: u32,
    seccomp: i32,
    rounds: [Round; 2],
}

struct Frequency {
    path: PathBuf,
    value: String,
    mode: u32,
    applied: bool,
}

impl Frequency {
    fn pin(&mut self, khz: u32) -> Result<u32> {
        self.applied = true;
        fs::set_permissions(&self.path, fs::Permissions::from_mode(self.mode | 0o200))?;
        fs::write(&self.path, format!("{khz}\n"))?;
        fs::set_permissions(&self.path, fs::Permissions::from_mode(0o444))?;
        Ok(fs::read_to_string(&self.path)?.trim().parse::<u32>()? / 1000)
    }

    fn restore(&mut self) -> Result<()> {
        let restore_value =
            fs::set_permissions(&self.path, fs::Permissions::from_mode(self.mode | 0o200))
                .and_then(|()| fs::write(&self.path, &self.value));
        let restore_mode = fs::set_permissions(&self.path, fs::Permissions::from_mode(self.mode));
        restore_value
            .and(restore_mode)
            .context("restore CPU frequency and mode")?;
        self.applied = false;
        Ok(())
    }
}

#[derive(Default)]
struct Restore {
    freq: Option<Frequency>,
    features: [Option<u64>; 3],
}

impl Restore {
    fn restore(&mut self) -> Result<()> {
        let mut errors = Vec::new();
        for (id, saved) in FEATURES.into_iter().zip(&mut self.features).rev() {
            if let Some(value) = *saved {
                match ksucalls::set_feature(id as u32, value) {
                    Ok(()) => *saved = None,
                    Err(err) => errors.push(format!("restore {}: {err}", id.name())),
                }
            }
        }
        if let Some(freq) = &mut self.freq
            && freq.applied
            && let Err(err) = freq.restore()
        {
            errors.push(format!("{err:#}"));
        }
        ensure!(errors.is_empty(), "{}", errors.join(", "));
        Ok(())
    }
}

impl Drop for Restore {
    fn drop(&mut self) {
        if let Err(err) = self.restore() {
            log::error!("toolkit bench: {err:#}");
        }
    }
}

fn frequency(cpu: u32, stable: bool) -> Result<Option<(Frequency, u32)>> {
    let dir = PathBuf::from(format!("/sys/devices/system/cpu/cpu{cpu}/cpufreq"));
    let path = dir.join("scaling_max_freq");
    let read = || -> Result<_> {
        let available = fs::read_to_string(dir.join("scaling_available_frequencies"))?;
        let value = fs::read_to_string(&path)?;
        let mode = fs::metadata(&path)?.permissions().mode() & 0o7777;
        Ok((available, value, mode))
    };
    let (available, value, mode) = match read() {
        Err(err)
            if err
                .downcast_ref::<io::Error>()
                .is_some_and(|e| e.kind() == io::ErrorKind::NotFound) =>
        {
            return Ok(None);
        }
        result => result.context("read cpufreq settings")?,
    };
    let mut available = available
        .split_whitespace()
        .map(str::parse::<u32>)
        .collect::<Result<Vec<_>, _>>()?;
    available.sort_unstable();
    let khz = if stable {
        available.first()
    } else {
        available.last()
    };
    Ok(khz.copied().map(|khz| {
        (
            Frequency {
                path,
                value,
                mode,
                applied: false,
            },
            khz,
        )
    }))
}

pub(super) fn seccomp() -> i32 {
    unsafe { libc::syscall(libc::SYS_prctl, libc::PR_GET_SECCOMP, 0, 0, 0, 0) as i32 }
}

fn wait_child(pid: libc::pid_t) -> io::Result<i32> {
    let mut status = 0;
    loop {
        if unsafe { libc::waitpid(pid, &raw mut status, 0) } == pid {
            return Ok(status);
        }
        let err = io::Error::last_os_error();
        if err.kind() != io::ErrorKind::Interrupted {
            return Err(err);
        }
    }
}

fn probe(syscall: c_long, [first, second, third, fourth, fifth]: [c_long; 5]) -> bool {
    let pid = unsafe { libc::fork() };
    if pid == 0 {
        unsafe {
            libc::signal(libc::SIGSYS, libc::SIG_DFL);
            let ret = libc::syscall(syscall, first, second, third, fourth, fifth);
            let unsupported =
                ret == -1 && io::Error::last_os_error().raw_os_error() == Some(libc::ENOSYS);
            libc::_exit(i32::from(unsupported));
        }
    }
    pid > 0
        && wait_child(pid)
            .is_ok_and(|status| libc::WIFEXITED(status) && libc::WEXITSTATUS(status) == 0)
}

pub(super) fn highest_allowed_cpu() -> Result<u32> {
    let mut affinity = unsafe { std::mem::zeroed::<libc::cpu_set_t>() };
    if unsafe { libc::sched_getaffinity(0, size_of_val(&affinity), &raw mut affinity) } != 0 {
        return Err(io::Error::last_os_error()).context("read CPU affinity");
    }
    (0..libc::CPU_SETSIZE)
        .rev()
        .find(|&cpu| unsafe { libc::CPU_ISSET(cpu, &affinity) })
        .map(|cpu| cpu as u32)
        .context("no allowed CPUs")
}

fn pin_process(cpu: u32) -> Result<()> {
    ensure!(cpu < libc::CPU_SETSIZE as u32, "invalid CPU {cpu}");
    let mut affinity = unsafe { std::mem::zeroed::<libc::cpu_set_t>() };
    unsafe { libc::CPU_SET(cpu as usize, &mut affinity) };
    if unsafe { libc::sched_setaffinity(0, size_of_val(&affinity), &raw const affinity) } != 0 {
        return Err(io::Error::last_os_error()).with_context(|| format!("pin to CPU {cpu}"));
    }
    if unsafe { libc::setpriority(libc::PRIO_PROCESS, 0, -20) } != 0 {
        return Err(io::Error::last_os_error()).context("set benchmark priority");
    }
    Ok(())
}

fn time_ns() -> Result<u64> {
    let mut time = [0 as c_long; 2];
    if unsafe {
        libc::syscall(
            libc::SYS_clock_gettime,
            libc::CLOCK_MONOTONIC_RAW,
            time.as_mut_ptr(),
        )
    } != 0
    {
        return Err(io::Error::last_os_error()).context("read benchmark clock");
    }
    Ok(time[0] as u64 * 1_000_000_000 + time[1] as u64)
}

fn measure(
    test: usize,
    name: &str,
    syscall: c_long,
    [first, second, third, fourth, fifth]: [c_long; 5],
    tracked: bool,
) -> Result<Sample> {
    let start = time_ns()?;
    for _ in 0..ITERATIONS {
        unsafe { libc::syscall(syscall, first, second, third, fourth, fifth) };
    }
    let ns = (time_ns()? - start) / u64::from(ITERATIONS);
    Ok(Sample {
        test,
        syscall: name.to_owned(),
        ns,
        tracked,
    })
}

pub(super) fn round(cpu: u32, sucompat: bool, legacy: bool) -> Result<Round> {
    let at = c_long::from(libc::AT_FDCWD);
    let devnull = c"/dev/null".as_ptr() as c_long;
    let has_execveat = probe(libc::SYS_execveat, [at, devnull, 0, 0, 0]);
    #[cfg(target_arch = "aarch64")]
    let has_faccessat2 = probe(libc::SYS_faccessat2, [at, devnull, 0, 0, 0]);
    let pin = pin_process(cpu);
    if !legacy {
        pin?;
    }
    let mut warm = 0;
    for i in 0..50_000 {
        warm = std::hint::black_box(warm + i);
    }

    #[cfg(target_pointer_width = "32")]
    let stat_syscall = libc::SYS_fstatat64;
    #[cfg(target_arch = "x86_64")]
    let stat_syscall = libc::SYS_newfstatat;
    #[cfg(any(target_arch = "aarch64", target_arch = "riscv64"))]
    let stat_syscall: c_long = 79; // Android libc omits __NR_newfstatat here.
    // Keep enough aligned space for the kernel's stat or stat64 output.
    let mut stat = [0_u64; 32];
    let stat = stat.as_mut_ptr() as c_long;
    let notsu = c"/system/bin/su_".as_ptr();
    let paths = [0, devnull, notsu as c_long, unsafe { notsu.add(3) }
        as c_long];
    let mut results = Vec::new();
    for (test, path) in (1..).zip(paths) {
        results.push(measure(
            test,
            "execve",
            libc::SYS_execve,
            [path, 0, 0, 0, 0],
            true,
        )?);
        if has_execveat {
            results.push(measure(
                test,
                "execveat",
                libc::SYS_execveat,
                [at, path, 0, 0, 0],
                true,
            )?);
        }
        results.push(measure(
            test,
            "newfstatat",
            stat_syscall,
            [at, path, stat, c_long::from(libc::AT_SYMLINK_NOFOLLOW), 0],
            true,
        )?);
        results.push(measure(
            test,
            "faccessat",
            libc::SYS_faccessat,
            [at, path, 0, 0, 0],
            true,
        )?);
        #[cfg(target_arch = "aarch64")]
        if has_faccessat2 {
            results.push(measure(
                test,
                "faccessat2",
                libc::SYS_faccessat2,
                [at, path, 0, 0, 0],
                false,
            )?);
        }
        #[cfg(target_pointer_width = "32")]
        results.push(measure(
            test,
            "access",
            libc::SYS_access,
            [path, 0, 0, 0, 0],
            false,
        )?);
    }
    let total_ns = results
        .iter()
        .filter(|test| test.tracked)
        .map(|test| test.ns)
        .sum();
    Ok(Round {
        sucompat,
        total_ns,
        results,
    })
}

fn lock_bench() -> Result<File> {
    let _guard = config::lock()?;
    let file = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(Path::new(TOOLKIT_DIR).join("bench.lock"))?;
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        let err = io::Error::last_os_error();
        ensure!(
            err.kind() != io::ErrorKind::WouldBlock,
            "benchmark already running"
        );
        return Err(err).context("lock toolkit benchmark");
    }
    Ok(file)
}

fn detach_supervisor() -> Result<()> {
    let _ = unsafe { libc::setsid() };
    for signal in [libc::SIGHUP, libc::SIGINT, libc::SIGTERM, libc::SIGPIPE] {
        if unsafe { libc::signal(signal, libc::SIG_IGN) } == libc::SIG_ERR {
            return Err(io::Error::last_os_error()).context("set benchmark signal disposition");
        }
    }
    Ok(())
}

pub(super) fn run_worker(cpu: u32) -> Result<Round> {
    let state = ksucalls::get_feature(FeatureId::SuCompat as u32)?;
    ensure!(state.supported, "su_compat is unavailable");
    round(cpu, state.value != 0, false)
}

fn run_round(cpu: u32, sucompat: bool) -> Result<Round> {
    let output = Command::new("/proc/self/exe")
        .args(["toolkit", "bench-worker", "--cpu"])
        .arg(cpu.to_string())
        .output()
        .context("start benchmark worker")?;
    ensure!(
        output.status.success(),
        "benchmark worker {}: {}",
        output.status,
        String::from_utf8_lossy(&output.stderr).trim()
    );
    let round: Round =
        serde_json::from_slice(&output.stdout).context("read benchmark worker result")?;
    ensure!(
        round.sucompat == sucompat,
        "su_compat changed during benchmark"
    );
    Ok(round)
}

pub(super) fn run(stable: bool, cpu: Option<u32>) -> Result<Report> {
    let _lock = lock_bench()?;
    detach_supervisor()?;
    let cpu = match cpu {
        Some(cpu) => cpu,
        None => highest_allowed_cpu()?,
    };
    ensure!(cpu < libc::CPU_SETSIZE as u32, "invalid CPU {cpu}");
    let kernel = ops::current_uname()?.release;
    let seccomp = seccomp();
    let states = FEATURES
        .map(|id| ksucalls::get_feature(id as u32).with_context(|| format!("read {}", id.name())));
    let [sulog, adb_root, sucompat] = states;
    let states = [sulog?, adb_root?, sucompat?];
    for (id, state) in FEATURES.into_iter().zip(states) {
        ensure!(
            state.supported && !state.forced,
            "{} cannot be changed",
            id.name()
        );
    }

    let mut restore = Restore::default();
    let result = (|| -> Result<Report> {
        let freq_mhz = if let Some((freq, khz)) = frequency(cpu, stable)? {
            Some(
                restore
                    .freq
                    .insert(freq)
                    .pin(khz)
                    .context("pin CPU frequency")?,
            )
        } else {
            None
        };
        for ((saved, id), state) in restore.features.iter_mut().zip(FEATURES).zip(states) {
            *saved = Some(state.value);
            ksucalls::set_feature(id as u32, u64::from(id == FeatureId::SuCompat))?;
        }
        let enabled = run_round(cpu, true)?;
        ksucalls::set_feature(FeatureId::SuCompat as u32, 0)?;
        let disabled = run_round(cpu, false)?;
        Ok(Report {
            kernel,
            cpu,
            mode: if stable { "stable" } else { "fast" },
            freq_mhz,
            iterations: ITERATIONS,
            seccomp,
            rounds: [enabled, disabled],
        })
    })();
    match (result, restore.restore()) {
        (Ok(report), Ok(())) => Ok(report),
        (Err(err), Ok(())) | (Ok(_), Err(err)) => Err(err),
        (Err(err), Err(restore)) => bail!("{err:#}, {restore:#}"),
    }
}
