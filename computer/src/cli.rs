//! Command-line entry point and process lifecycle.
//!
//! `main` attaches the console and delegates to [`run`]; argument parsing, status queries,
//! log inspection, autostart management, and daemon startup are composed here.

use std::fmt;
use std::fs;

use crate::autostart;
use crate::config::AppConfig;
use crate::dispatch::Dispatcher;
use crate::instance::{self, InstanceError, SingleInstance, StopOutcome};
use crate::logging::Logger;
use crate::paths;
use crate::power::SystemPower;
use crate::server;

const LOG_TAIL_LINES: usize = 25;

/// User requested operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CliCommand {
    Run,
    Status,
    Logs,
    Stop,
    Install,
    Uninstall,
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
        Ok(CliCommand::Run) => run_daemon(),
        Err(error) => {
            eprintln!("Argument error: {error}");
            eprintln!();
            print_help();
            2
        }
    }
}

fn show_status() -> i32 {
    let config_path = paths::config_path();
    let log_path = paths::log_path();
    let config = AppConfig::load(&config_path);
    let autostart_enabled = autostart::is_enabled();

    println!("Wake-on-LAN Computer Power Control Status:");
    println!("  Local IP:           {}", server::local_ipv4());
    println!("  Listening Port:     {} (UDP)", config.udp_port);
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
    println!("  Config File:        {}", config_path.display());
    println!("  Log File:           {}", log_path.display());
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

fn run_daemon() -> i32 {
    let logger = Logger::new(paths::log_path());

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
    let config = AppConfig::load(&config_path);

    if !config_path.exists()
        && let Err(error) = config.save(&config_path)
    {
        logger.record(
            "warning",
            &format!("cannot create default config file: {error}"),
        );
    }

    if config.autostart {
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
    match server::serve(&dispatcher, config.udp_port, &logger) {
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
    println!("  {exe} --help       Show this help");
    println!();
    println!("Remote commands (UDP, case-insensitive):");
    println!("  shutdown | reboot | sleep | hibernate | lock");
    println!("  Also accepts SHUTDOWN:<IP> format; the :<IP> part is ignored.");
    println!();
    println!(
        "Config file: {} (udp_port, autostart)",
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
