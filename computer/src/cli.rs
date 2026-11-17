//! Command-line entry point and process lifecycle.
//!
//! `main` attaches the console and delegates to [`run`]; argument parsing, status queries,
//! log inspection, autostart management, and daemon startup are composed here.

use std::fmt;
use std::fs;
use std::path::{Path, PathBuf};

use crate::autostart;
use crate::config::AppConfig;
use crate::dispatch::Dispatcher;
use crate::instance::{self, InstanceError, SingleInstance, StopOutcome};
use crate::logging::Logger;
use crate::paths;
use crate::platform;
use crate::power::SystemPower;
use crate::server;
use crate::system_service::{self, SERVICE_FLAG};

const LOG_TAIL_LINES: usize = 25;
const SYSTEM_DATA_DIR: &str = "/var/lib/pc_onoff";

/// User requested operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CliCommand {
    Run,
    /// Run from the system service unit: never touches per-user autostart.
    RunService,
    Status,
    Logs,
    Stop,
    Install,
    Uninstall,
    InstallSystem,
    UninstallSystem,
    Help,
}

/// Command line argument error.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CliError {
    UnknownArgument(String),
    TooManyArguments,
}

impl fmt::Display for CliError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnknownArgument(argument) => write!(f, "unknown argument '{argument}'"),
            Self::TooManyArguments => f.write_str("only one argument may be specified at a time"),
        }
    }
}

impl std::error::Error for CliError {}

impl CliCommand {
    /// Parse command line arguments (excluding binary path). No arguments means run daemon.
    pub fn parse(args: &[String]) -> Result<Self, CliError> {
        let Some(argument) = args.first() else {
            return Ok(Self::Run);
        };

        if args.len() > 1 {
            return Err(CliError::TooManyArguments);
        }

        match argument.as_str() {
            "--status" => Ok(Self::Status),
            "--log" => Ok(Self::Logs),
            "--stop" => Ok(Self::Stop),
            "--install" | "-i" => Ok(Self::Install),
            "--uninstall" | "-u" => Ok(Self::Uninstall),
            "--install-system" => Ok(Self::InstallSystem),
            "--uninstall-system" => Ok(Self::UninstallSystem),
            SERVICE_FLAG => Ok(Self::RunService),
            "--help" | "-h" => Ok(Self::Help),
            other => Err(CliError::UnknownArgument(other.to_owned())),
        }
    }
}

/// Execute command line and return process exit code.
pub fn run(args: Vec<String>) -> i32 {
    match CliCommand::parse(&args) {
        Ok(CliCommand::Help) => {
            print_help();
            0
        }
        Ok(CliCommand::Status) => show_status(),
        Ok(CliCommand::Logs) => show_logs(),
        Ok(CliCommand::Stop) => stop_service(),
        Ok(CliCommand::Install) => set_autostart(true),
        Ok(CliCommand::Uninstall) => set_autostart(false),
        Ok(CliCommand::InstallSystem) => report(system_service::install()),
        Ok(CliCommand::UninstallSystem) => report(system_service::uninstall()),
        Ok(CliCommand::Run) => run_daemon(DaemonMode::User),
        Ok(CliCommand::RunService) => run_daemon(DaemonMode::SystemService),
        Err(error) => {
            eprintln!("Argument error: {error}");
            eprintln!();
            print_help();
            2
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DaemonMode {
    User,
    SystemService,
}

fn report(result: Result<String, system_service::ServiceError>) -> i32 {
    match result {
        Ok(message) => {
            println!("{message}");
            0
        }
        Err(error) => {
            eprintln!("{error}");
            1
        }
    }
}

fn active_config_path() -> PathBuf {
    if system_service::state().is_installed() {
        let system = PathBuf::from(SYSTEM_DATA_DIR).join("config.ini");
        if system.exists() {
            return system;
        }
    }
    paths::config_path()
}

fn active_log_path(config_path: &Path) -> PathBuf {
    if config_path.parent() == Some(Path::new(SYSTEM_DATA_DIR)) {
        config_path.with_file_name("pc_onoff.log")
    } else {
        paths::log_path()
    }
}

fn is_system_service_config(path: &Path) -> bool {
    path.parent() == Some(Path::new(SYSTEM_DATA_DIR))
}

fn show_status() -> i32 {
    let config_path = active_config_path();
    let log_path = active_log_path(&config_path);
    let mut config = AppConfig::load(&config_path);
    // The system service config is root-only; an unprivileged status call must not write it
    // or invent a different user secret. Suggest `sudo pc_onoff --status` instead.
    let privileged_system_config = is_system_service_config(&config_path) && !platform::is_root();
    let secret_result = if privileged_system_config {
        Ok(())
    } else {
        config.ensure_secret(&config_path)
    };
    let autostart_enabled = autostart::is_enabled();

    println!("Wake-on-LAN Computer Power Control Status:");
    println!("  Local IP:           {}", server::local_ipv4());
    println!("  Listening Port:     {} (UDP)", config.udp_port);
    println!(
        "  Command Secret: {}",
        if config.secret.is_empty() {
            if privileged_system_config {
                "(run sudo pc_onoff --status to view)"
            } else {
                "(not configured)"
            }
        } else {
            config.secret.as_str()
        }
    );
    println!(
        "  Background Service: {}",
        if instance::is_running() {
            "running"
        } else {
            "stopped"
        }
    );
    println!(
        "  Autostart on Boot:  {}",
        if autostart_enabled {
            "enabled"
        } else {
            "disabled"
        }
    );
    let service_state = system_service::state();
    if service_state != system_service::ServiceState::Unsupported {
        println!("  System Service:     {}", service_state.label());
    }
    println!("  Config File:        {}", config_path.display());
    println!("  Log File:           {}", log_path.display());
    if service_state.is_installed() {
        println!(
            "  (System service uses /var/lib/pc_onoff/config.ini and pc_onoff.log; manage with `sudo systemctl status {}`)",
            system_service::UNIT_NAME
        );
    }
    if let Err(error) = secret_result {
        eprintln!(
            "Warning: failed to save the command secret to {}: {error}",
            config_path.display()
        );
    }
    0
}

fn show_logs() -> i32 {
    let path = paths::log_path();

    let Ok(content) = fs::read_to_string(&path) else {
        println!("Log file does not exist yet: {}", path.display());
        return 0;
    };

    let lines: Vec<&str> = content.lines().collect();
    let start = lines.len().saturating_sub(LOG_TAIL_LINES);
    for line in &lines[start..] {
        println!("{line}");
    }
    0
}

fn stop_service() -> i32 {
    if system_service::state() == system_service::ServiceState::Active {
        println!(
            "Note: the system service is running; stop it with `sudo systemctl stop {}`.",
            system_service::UNIT_NAME
        );
    }
    match instance::request_stop() {
        Ok(StopOutcome::Signaled) => {
            println!("Stop signal sent; background service will terminate.");
            0
        }
        Ok(StopOutcome::NotRunning) => {
            println!("No background service is currently running.");
            0
        }
        Err(error) => {
            eprintln!("Failed to stop service: {error}");
            1
        }
    }
}

fn set_autostart(enabled: bool) -> i32 {
    if let Err(error) = autostart::set_enabled(enabled) {
        eprintln!("{error}");
        return 1;
    }

    // Only update config.ini if OS-level configuration succeeded.
    let config_path = paths::config_path();
    let mut config = AppConfig::load(&config_path);
    config.autostart = enabled;
    if let Err(error) = config.save(&config_path) {
        eprintln!(
            "Warning: failed to update config file {}: {error}",
            config_path.display()
        );
    }

    if enabled {
        println!("Autostart on boot configured successfully.");
    } else {
        println!("Autostart on boot removed.");
    }
    0
}

fn run_daemon(mode: DaemonMode) -> i32 {
    let logger = Logger::new(paths::log_path());

    if mode == DaemonMode::User {
        match system_service::state() {
            system_service::ServiceState::Active => {
                logger.record(
                    "instance_check",
                    &format!(
                        "System service {} is active and handles remote commands; exiting user instance.",
                        system_service::UNIT_NAME
                    ),
                );
                return 0;
            }
            system_service::ServiceState::Inactive => {
                // Installed but stopped/crash-looping: let the user start a manual instance so
                // remote control is not lost; the unit would otherwise fight for UDP 9877.
                logger.record(
                    "instance_check",
                    &format!(
                        "System service {} is installed but not running; starting a user instance. \
                         Check `sudo systemctl status {}`.",
                        system_service::UNIT_NAME,
                        system_service::UNIT_NAME
                    ),
                );
            }
            system_service::ServiceState::NotInstalled
            | system_service::ServiceState::Unsupported => {}
        }
    }

    // Single-instance lock must remain in scope until function ends; quiet exit on duplicate launch.
    let _instance = match SingleInstance::acquire() {
        Ok(instance) => instance,
        Err(InstanceError::AlreadyRunning) => {
            logger.record(
                "instance_check",
                "Background instance is already running; exiting duplicate launch.",
            );
            return 0;
        }
        Err(error) => {
            logger.record(
                "startup_error",
                &format!("cannot acquire single-instance lock: {error}"),
            );
            return 1;
        }
    };

    let config_path = paths::config_path();
    let mut config = AppConfig::load(&config_path);

    if let Err(error) = config.ensure_secret(&config_path) {
        logger.record(
            "startup_error",
            &format!(
                "cannot save the command secret to {}: {error}",
                config_path.display()
            ),
        );
        eprintln!(
            "Cannot persist the command secret to {}: {error}\n             Refusing to start with an unsaved secret (clients could never pair).",
            config_path.display()
        );
        return 1;
    }

    if mode == DaemonMode::SystemService {
        logger.record(
            "config",
            "Running as system service; active at boot before any user logs in.",
        );
    } else if config.autostart {
        match autostart::set_enabled(true) {
            Ok(()) => logger.record("config", "Autostart on boot verified."),
            Err(error) => logger.record(
                "warning",
                &format!("failed to configure autostart on boot: {error}"),
            ),
        }
    } else {
        logger.record(
            "config",
            "Autostart disabled in configuration file; skipping registration.",
        );
    }

    if mode == DaemonMode::User
        && system_service::state() == system_service::ServiceState::NotInstalled
    {
        let exe = crate::platform::launcher_executable()
            .map(|path| path.display().to_string())
            .unwrap_or_else(|_| "pc_onoff".to_owned());
        logger.record(
            "hint",
            &format!(
                "Per-user autostart only runs after you log in. To accept shutdown/reboot \
                 commands right after boot (at the login screen), run once: sudo \"{exe}\" --install-system"
            ),
        );
    }

    logger.record(
        "service_start",
        &format!("pc_onoff daemon started (PID: {})", std::process::id()),
    );
    logger.record(
        "local_info",
        &format!(
            "Local IP: {} (enter this IP in mobile app)",
            server::local_ipv4()
        ),
    );

    let dispatcher = Dispatcher::new(SystemPower);
    match server::serve(&dispatcher, config.udp_port, &config.secret, &logger) {
        Ok(()) => {
            logger.record("service_stop", "UDP listener service terminated.");
            0
        }
        Err(error) => {
            logger.record("startup_error", &error.to_string());
            1
        }
    }
}

fn print_help() {
    let exe = if cfg!(windows) {
        "pc_onoff.exe"
    } else {
        "pc_onoff"
    };

    println!("Wake-on-LAN Computer Power Control Daemon");
    println!();
    println!("Usage:");
    println!("  {exe}              Run as background daemon and register autostart (default)");
    println!("  {exe} --status     View current runtime and autostart status");
    println!("  {exe} --log        View latest log entries");
    println!("  {exe} --stop       Stop running background daemon");
    println!("  {exe} --install    Enable autostart on boot");
    println!("  {exe} --uninstall  Disable autostart on boot");
    if cfg!(all(unix, not(target_os = "macos"))) {
        println!(
            "  sudo {exe} --install-system    Install as a system service (works before login)"
        );
        println!("  sudo {exe} --uninstall-system  Remove the system service");
    }
    println!("  {exe} --help       Show this help");
    println!();
    println!("Remote commands (UDP, case-insensitive):");
    println!("  shutdown | reboot | sleep | hibernate | lock");
    println!("  Also accepts SHUTDOWN:<IP> format; the :<IP> part is ignored.");
    println!();
    println!("Command authentication:");
    println!("  Every command must carry a signed envelope:");
    println!("  <command>|ts=<unix>|nonce=<16 hex>|sig=<64 hex>");
    println!("  Run `{exe} --status` to show the shared command secret to paste into the apps.");
    println!("  Unsigned commands are rejected; clocks must agree within 5 minutes.");
    println!();
    println!(
        "Config file: {} (udp_port, autostart, secret)",
        paths::config_path().display()
    );
    println!("Log file:    {}", paths::log_path().display());
}

#[cfg(test)]
mod tests {
    use super::*;

    fn args(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| (*value).to_owned()).collect()
    }

    #[test]
    fn no_arguments_means_run() {
        assert_eq!(CliCommand::parse(&[]), Ok(CliCommand::Run));
    }

    #[test]
    fn parses_every_supported_flag() {
        let cases = [
            ("--status", CliCommand::Status),
            ("--log", CliCommand::Logs),
            ("--stop", CliCommand::Stop),
            ("--install", CliCommand::Install),
            ("-i", CliCommand::Install),
            ("--uninstall", CliCommand::Uninstall),
            ("-u", CliCommand::Uninstall),
            ("--help", CliCommand::Help),
            ("-h", CliCommand::Help),
            ("--install-system", CliCommand::InstallSystem),
            ("--uninstall-system", CliCommand::UninstallSystem),
            ("--service", CliCommand::RunService),
        ];

        for (flag, expected) in cases {
            assert_eq!(CliCommand::parse(&args(&[flag])), Ok(expected));
        }
    }

    #[test]
    fn rejects_unknown_flags() {
        assert_eq!(
            CliCommand::parse(&args(&["--explode"])),
            Err(CliError::UnknownArgument("--explode".to_owned()))
        );
    }

    #[test]
    fn rejects_multiple_flags() {
        assert_eq!(
            CliCommand::parse(&args(&["--status", "--stop"])),
            Err(CliError::TooManyArguments)
        );
    }
}
