#![cfg_attr(not(test), windows_subsystem = "windows")]

fn main() -> std::process::ExitCode {
    #[cfg(windows)]
    pc_onoff::platform::attach_console();

    let args: Vec<String> = std::env::args().skip(1).collect();
    std::process::ExitCode::from(pc_onoff::cli::run(args) as u8)
}
