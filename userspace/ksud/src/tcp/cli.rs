use super::boot;
use super::config::Key;
use super::ops::Paths;
use anyhow::Result;
use clap::Subcommand;

#[derive(Subcommand, Debug)]
pub enum TcpCommands {
    /// Show TCP settings and module status as JSON
    Status,
    /// Select and apply a value
    Set { key: Key, value: String },
    /// Follow system values for one key or all keys
    Reset { key: String },
    /// Apply the saved settings and record boot errors
    Apply,
    /// Import the tcp_congestion module selection and disable the module
    ImportModule,
}

pub fn run_tcp(cmd: TcpCommands) -> Result<()> {
    let paths = Paths::device();
    match cmd {
        TcpCommands::Status => {
            println!("{}", serde_json::to_string(&paths.status())?);
            Ok(())
        }
        TcpCommands::Set { key, value } => paths.set(key, &value),
        TcpCommands::Reset { key } if key == "all" => paths.reset(&Key::ALL),
        TcpCommands::Reset { key } => paths.reset(&[key.parse()?]),
        TcpCommands::Apply => boot::apply(&paths, false),
        TcpCommands::ImportModule => paths.import_module(),
    }
}
