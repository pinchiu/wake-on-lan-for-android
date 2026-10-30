//! 命令列入口與行程生命週期。
//!
//! `main` 只負責附加主控台與呼叫 [`run`]；參數解析、狀態查詢、日誌檢視、
//! 開機自啟管理與常駐啟動都在這裡組合底層模組。

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

/// 使用者要求的操作。
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

/// 命令列參數錯誤。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CliError {
    UnknownArgument(String),
    TooManyArguments,
}

impl fmt::Display for CliError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnknownArgument(argument) => write!(f, "未知的參數 '{argument}'"),
            Self::TooManyArguments => f.write_str("一次只能指定一個參數"),
        }
    }
}

impl std::error::Error for CliError {}

impl CliCommand {
    /// 解析命令列參數（不含執行檔路徑）。無參數代表常駐啟動。
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

/// 執行命令列並回傳行程結束碼。
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
            eprintln!("參數錯誤: {error}");
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

    println!("Wake-on-LAN 電腦端服務狀態:");
    println!("  本機 IP:       {}", server::local_ipv4());
    println!("  監聽連接埠:    {} (UDP)", config.udp_port);
    println!(
        "  背景服務:      {}",
        if instance::is_running() {
            "執行中"
        } else {
            "未執行"
        }
    );
    println!(
        "  開機自動啟動:  {}",
        if autostart_enabled {
            "已啟用"
        } else {
            "未啟用"
        }
    );
    println!("  設定檔案:      {}", config_path.display());
    println!("  日誌檔案:      {}", log_path.display());
    0
}

fn show_logs() -> i32 {
    let path = paths::log_path();

    let Ok(content) = fs::read_to_string(&path) else {
        println!("尚未產生日誌檔案: {}", path.display());
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
            println!("已送出停止指令，背景服務將結束。");
            0
        }
        Ok(StopOutcome::NotRunning) => {
            println!("目前沒有正在執行的背景服務。");
            0
        }
        Err(error) => {
            eprintln!("停止服務失敗: {error}");
            1
        }
    }
}

fn set_autostart(enabled: bool) -> i32 {
    if let Err(error) = autostart::set_enabled(enabled) {
        eprintln!("{error}");
        return 1;
    }

    // 只有作業系統端設定成功後才同步 config.ini，避免兩邊狀態不一致。
    let config_path = paths::config_path();
    let mut config = AppConfig::load(&config_path);
    config.autostart = enabled;
    if let Err(error) = config.save(&config_path) {
        eprintln!("警告: 無法更新設定檔 {}: {error}", config_path.display());
    }

    if enabled {
        println!("開機自動啟動設定成功。");
    } else {
        println!("開機自動啟動已解除。");
    }
    0
}

fn run_daemon() -> i32 {
    let logger = Logger::new(paths::log_path());

    // 守護鎖必須存活到函式結束；重複雙擊時直接安靜退出。
    let _instance = match SingleInstance::acquire() {
        Ok(instance) => instance,
        Err(InstanceError::AlreadyRunning) => {
            logger.record("常駐檢查", "已檢測到背景實例正在執行中，結束重複啟動。");
            return 0;
        }
        Err(error) => {
            logger.record("啟動錯誤", &format!("無法取得單一實例鎖: {error}"));
            return 1;
        }
    };

    let config_path = paths::config_path();
    let config = AppConfig::load(&config_path);

    if !config_path.exists()
        && let Err(error) = config.save(&config_path)
    {
        logger.record("警告", &format!("無法建立預設設定檔: {error}"));
    }

    if config.autostart {
        match autostart::set_enabled(true) {
            Ok(()) => logger.record("設定", "開機自動啟動已確認。"),
            Err(error) => logger.record("警告", &format!("開機自動啟動設定失敗: {error}")),
        }
    } else {
        logger.record("設定", "設定檔已停用開機自動啟動，略過登錄。");
    }

    logger.record(
        "服務啟動",
        &format!("pc_onoff 背景常駐服務啟動 (PID: {})", std::process::id()),
    );
    logger.record(
        "本機資訊",
        &format!(
            "區域網路 IP: {} (請於手機端填寫此 IP)",
            server::local_ipv4()
        ),
    );

    let dispatcher = Dispatcher::new(SystemPower);
    match server::serve(&dispatcher, config.udp_port, &logger) {
        Ok(()) => {
            logger.record("服務停止", "UDP 監聽服務已結束。");
            0
        }
        Err(error) => {
            logger.record("啟動錯誤", &error.to_string());
            1
        }
    }
}

fn print_help() {
    println!("Wake-on-LAN 電腦端遠端控制常駐程式");
    println!();
    println!("用法:");
    println!("  pc_onoff.exe              背景常駐執行，並依設定註冊開機自啟 (預設)");
    println!("  pc_onoff.exe --status     檢視目前運作與開機自啟狀態");
    println!("  pc_onoff.exe --log        檢視最新日誌記錄");
    println!("  pc_onoff.exe --stop       停止正在背景執行的服務");
    println!("  pc_onoff.exe --install    啟用開機自動啟動");
    println!("  pc_onoff.exe --uninstall  解除開機自動啟動");
    println!("  pc_onoff.exe --help       顯示本說明");
    println!();
    println!("遠端指令 (UDP, 大小寫不拘):");
    println!("  shutdown | reboot | sleep | hibernate | lock");
    println!("  也接受 SHUTDOWN:<IP> 形式，:<IP> 部分會被忽略。");
    println!();
    println!(
        "設定檔: {} (udp_port, autostart)",
        paths::config_path().display()
    );
    println!("日誌檔: {}", paths::log_path().display());
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
