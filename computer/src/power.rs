//! 系統電源操作的 seam（介面）。
//!
//! [`PowerController`] 是整個守護程式唯一會真正關機／重啟的地方。
//! 上層的 [`crate::dispatch::Dispatcher`] 只認得這個介面，因此測試可以注入
//! 假實作，驗證指令分派而不會動到執行中的電腦。

use std::fmt;

use crate::action::PowerAction;

/// 電源操作失敗的原因。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PowerError {
    message: String,
}

impl PowerError {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for PowerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for PowerError {}

/// 執行一個電源動作。
pub trait PowerController {
    fn execute(&self, action: PowerAction) -> Result<(), PowerError>;
}

/// 真實系統實作：Windows 使用 Win32 API 與系統命令，其餘平台使用 systemctl 等命令。
#[derive(Debug, Default, Clone, Copy)]
pub struct SystemPower;

impl PowerController for SystemPower {
    fn execute(&self, action: PowerAction) -> Result<(), PowerError> {
        #[cfg(windows)]
        {
            windows::execute(action)
        }
        #[cfg(not(windows))]
        {
            unix::execute(action)
        }
    }
}

#[cfg(windows)]
mod windows {
    use super::{PowerAction, PowerError};
    use crate::platform;

    #[link(name = "user32")]
    unsafe extern "system" {
        fn LockWorkStation() -> i32;
    }

    #[link(name = "powrprof")]
    unsafe extern "system" {
        fn SetSuspendState(hibernate: u8, force: u8, wakeup_events_disabled: u8) -> u8;
    }

    pub(super) fn execute(action: PowerAction) -> Result<(), PowerError> {
        match action {
            PowerAction::Shutdown => run("shutdown", &["/s", "/t", "0"]),
            PowerAction::Reboot => run("shutdown", &["/r", "/t", "0"]),
            PowerAction::Lock => lock_workstation(),
            PowerAction::Sleep => {
                // 先鎖定工作站，避免喚醒後畫面停留在桌面。
                let _ = lock_workstation();
                // SetSuspendState(0, 0, 0)：睡眠、非強制、保留 Wake-on-LAN 喚醒事件。
                let result = unsafe { SetSuspendState(0, 0, 0) };
                if result == 0 {
                    Err(PowerError::new(
                        "SetSuspendState 呼叫失敗，系統可能不支援 S3 睡眠",
                    ))
                } else {
                    Ok(())
                }
            }
            PowerAction::Hibernate => {
                let _ = lock_workstation();
                match run("shutdown", &["/h"]) {
                    Ok(()) => Ok(()),
                    Err(primary) => {
                        // 部分系統停用 shutdown /h 但仍有休眠能力，改用原生 API。
                        let result = unsafe { SetSuspendState(1, 0, 0) };
                        if result == 0 {
                            Err(PowerError::new(format!("休眠失敗: {primary}")))
                        } else {
                            Ok(())
                        }
                    }
                }
            }
        }
    }

    fn lock_workstation() -> Result<(), PowerError> {
        let result = unsafe { LockWorkStation() };
        if result == 0 {
            Err(PowerError::new("LockWorkStation 呼叫失敗"))
        } else {
            Ok(())
        }
    }

    fn run(program: &str, args: &[&str]) -> Result<(), PowerError> {
        let output = platform::command(program)
            .args(args)
            .output()
            .map_err(|e| PowerError::new(format!("無法執行 {program}: {e}")))?;

        if output.status.success() {
            return Ok(());
        }

        let stderr = String::from_utf8_lossy(&output.stderr);
        let detail = stderr.trim();
        let detail = if detail.is_empty() {
            format!("結束碼 {:?}", output.status.code())
        } else {
            detail.to_owned()
        };
        Err(PowerError::new(format!("{program} 執行失敗 ({detail})")))
    }
}

#[cfg(not(windows))]
mod unix {
    use super::{PowerAction, PowerError};
    use crate::platform;

    pub(super) fn execute(action: PowerAction) -> Result<(), PowerError> {
        let candidates: &[(&str, &[&str])] = match action {
            PowerAction::Shutdown => &[("systemctl", &["poweroff"]), ("shutdown", &["-h", "now"])],
            PowerAction::Reboot => &[("systemctl", &["reboot"]), ("reboot", &[])],
            PowerAction::Sleep => &[("systemctl", &["suspend"]), ("pm-suspend", &[])],
            PowerAction::Hibernate => &[("systemctl", &["hibernate"]), ("pm-hibernate", &[])],
            PowerAction::Lock => &[
                ("loginctl", &["lock-session"]),
                ("xdg-screensaver", &["lock"]),
            ],
        };

        run_first(candidates)
    }

    /// 依序嘗試候選命令，第一個成功者即回傳；全部失敗才回報錯誤。
    fn run_first(candidates: &[(&str, &[&str])]) -> Result<(), PowerError> {
        let mut failures = Vec::new();

        for (program, args) in candidates {
            match platform::command(program).args(*args).output() {
                Ok(output) if output.status.success() => return Ok(()),
                Ok(output) => {
                    let stderr = String::from_utf8_lossy(&output.stderr);
                    let stderr = stderr.trim();
                    if stderr.is_empty() {
                        failures.push(format!("{program}: 結束碼 {:?}", output.status.code()));
                    } else {
                        failures.push(format!("{program}: {stderr}"));
                    }
                }
                Err(error) => failures.push(format!("{program}: {error}")),
            }
        }

        Err(PowerError::new(format!(
            "所有電源指令皆失敗 ({})",
            failures.join("; ")
        )))
    }
}
