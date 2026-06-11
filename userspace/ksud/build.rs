use std::{env, fs::File, io::Write, path::Path, process::Command};

fn get_git_version() -> Result<(u32, String), std::io::Error> {
    let output = Command::new("git")
        .args(["rev-list", "--count", "HEAD"])
        .output()?;

    let output = output.stdout;
    let git_count: u32 = String::from_utf8(output)
        .expect("Failed to read git count stdout")
        .trim()
        .parse()
        .map_err(|_| std::io::Error::other("Failed to parse git count"))?;

    let version_code = env::var("MANAGER_VERSION_CODE")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(30000 + 700 + git_count);

    let version_name = env::var("MANAGER_VERSION_NAME")
        .ok()
        .unwrap_or_else(|| {
            String::from_utf8(
                Command::new("git")
                    .args(["describe", "--tags", "--abbrev=0"])
                    .output()
                    .map(|o| o.stdout)
                    .unwrap_or_default(),
            )
            .map(|s| s.trim().trim_start_matches('v').to_string())
            .unwrap_or_else(|_| "0.0.0".to_string())
        });

    let version_name = format!("{}-{}-midori", version_name, version_code);
    Ok((version_code, version_name))
}

fn configure_bindgen() {
    let bindings = bindgen::Builder::default()
        .header("src/android/uapi/ksu_uapi.h")
        .clang_args(["-x", "c++", "-I../../"])
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .expect("Unable to generate bindings");

    let out_path = std::path::PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");
}

fn main() {
    let (code, name) = match get_git_version() {
        Ok((code, name)) => (code, name),
        Err(_) => {
            println!("cargo:warning=Failed to get git version, using 0.0.0");
            (0, "0.0.0".to_string())
        }
    };
    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    let out_dir = Path::new(&out_dir);
    File::create(Path::new(out_dir).join("VERSION_CODE"))
        .expect("Failed to create VERSION_CODE")
        .write_all(code.to_string().as_bytes())
        .expect("Failed to write VERSION_CODE");

    File::create(Path::new(out_dir).join("VERSION_NAME"))
        .expect("Failed to create VERSION_NAME")
        .write_all(name.trim().as_bytes())
        .expect("Failed to write VERSION_NAME");

    let target_os = env::var("CARGO_CFG_TARGET_OS").expect("CARGO_CFG_TARGET_OS not set");
    if target_os == "android" {
        configure_bindgen();
    }
}
