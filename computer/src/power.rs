//! System power operations seam (interface).
//!
//! [`PowerController`] is the only place in the daemon that executes actual shutdowns or reboots.
//! The higher-level [`crate::dispatch::Dispatcher`] only depends on this interface, allowing unit
//! tests to inject mock implementations and verify dispatch logic without affecting the host system.

use std::fmt;

use crate::action::PowerAction;

/// Reasons why a power operation failed.
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

/// Execute a system power action.
pub trait PowerController {
    fn execute(&self, action: PowerAction) -> Result<(), PowerError>;
}

/// Real system implementation:
/// - Windows uses Win32 API and system commands.
/// - macOS uses osascript/pmset/CGSession.
/// - Linux uses systemctl/loginctl and fallback commands.
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
            // /f is required when the session is locked; otherwise Windows returns
            // error 1271 ("The machine is locked and cannot be shut down without the force option").
            PowerAction::Shutdown => run("shutdown", &["/s", "/f", "/t", "0"]),
            PowerAction::Reboot => run("shutdown", &["/r", "/f", "/t", "0"]),
            PowerAction::Lock => lock_workstation(),
            PowerAction::Sleep => {
                // Lock workstation first to avoid exposing desktop upon wakeup.
                let _ = lock_workstation();
                // SetSuspendState(0, 0, 0): sleep, non-force, preserve Wake-on-LAN events.
                let result = unsafe { SetSuspendState(0, 0, 0) };
                if result == 0 {
                    Err(PowerError::new(
                        "SetSuspendState call failed; system may not support S3 sleep",
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
                        // Some systems disable shutdown /h but have hibernation enabled; fallback to API.
                        let result = unsafe { SetSuspendState(1, 0, 0) };
                        if result == 0 {
                            Err(PowerError::new(format!("hibernation failed: {primary}")))
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
            Err(PowerError::new("LockWorkStation call failed"))
        } else {
            Ok(())
        }
    }

    fn run(program: &str, args: &[&str]) -> Result<(), PowerError> {
        let output = platform::command(program)
            .args(args)
            .output()
            .map_err(|e| PowerError::new(format!("cannot execute {program}: {e}")))?;

        if output.status.success() {
            return Ok(());
        }

        let stderr = String::from_utf8_lossy(&output.stderr);
        let detail = stderr.trim();
        let detail = if detail.is_empty() {
            format!("exit code {:?}", output.status.code())
        } else {
            detail.to_owned()
        };
        Err(PowerError::new(format!(
            "{program} execution failed ({detail})"
        )))
    }
}

#[cfg(not(windows))]
mod unix {
    use super::{PowerAction, PowerError};
    use crate::platform;

    pub(super) fn execute(action: PowerAction) -> Result<(), PowerError> {
        let candidates = candidates_for(action);
        run_first(candidates)
    }

    pub fn candidates_for(
        action: PowerAction,
    ) -> &'static [(&'static str, &'static [&'static str])] {
        #[cfg(target_os = "macos")]
        {
            macos_candidates(action)
        }
        #[cfg(not(target_os = "macos"))]
        {
            linux_candidates(action)
        }
    }

    #[cfg_attr(target_os = "macos", allow(dead_code))]
    pub fn linux_candidates(
        action: PowerAction,
    ) -> &'static [(&'static str, &'static [&'static str])] {
        match action {
            PowerAction::Shutdown => &[("systemctl", &["poweroff"]), ("shutdown", &["-h", "now"])],
            PowerAction::Reboot => &[("systemctl", &["reboot"]), ("reboot", &[])],
            PowerAction::Sleep => &[("systemctl", &["suspend"]), ("pm-suspend", &[])],
            PowerAction::Hibernate => &[("systemctl", &["hibernate"]), ("pm-hibernate", &[])],
            PowerAction::Lock => &[
                ("loginctl", &["lock-session"]),
                ("xdg-screensaver", &["lock"]),
            ],
        }
    }

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    pub fn macos_candidates(
        action: PowerAction,
    ) -> &'static [(&'static str, &'static [&'static str])] {
        match action {
            PowerAction::Shutdown => &[
                (
                    "osascript",
                    &["-e", "tell app \"System Events\" to shut down"],
                ),
                ("shutdown", &["-h", "now"]),
            ],
            PowerAction::Reboot => &[
                (
                    "osascript",
                    &["-e", "tell app \"System Events\" to restart"],
                ),
                ("shutdown", &["-r", "now"]),
                ("reboot", &[]),
            ],
            PowerAction::Sleep => &[
                ("pmset", &["sleepnow"]),
                ("osascript", &["-e", "tell app \"System Events\" to sleep"]),
            ],
            PowerAction::Hibernate => &[
                ("pmset", &["sleepnow"]),
                ("osascript", &["-e", "tell app \"System Events\" to sleep"]),
            ],
            PowerAction::Lock => &[
                (
                    "/System/Library/CoreServices/Menu Extras/User.menu/Contents/Resources/CGSession",
                    &["-suspend"],
                ),
                ("pmset", &["displaysleepnow"]),
                (
                    "osascript",
                    &[
                        "-e",
                        "tell application \"System Events\" to keystroke \"q\" using {control down, command down}",
                    ],
                ),
            ],
        }
    }

    /// Try candidate commands in sequence. Return Ok on the first success; report error only if all fail.
    fn run_first(candidates: &[(&str, &[&str])]) -> Result<(), PowerError> {
        let mut failures = Vec::new();

        for (program, args) in candidates {
            match platform::command(program).args(*args).output() {
                Ok(output) if output.status.success() => return Ok(()),
                Ok(output) => {
                    let stderr = String::from_utf8_lossy(&output.stderr);
                    let stderr = stderr.trim();
                    if stderr.is_empty() {
                        failures.push(format!("{program}: exit code {:?}", output.status.code()));
                    } else {
                        failures.push(format!("{program}: {stderr}"));
                    }
                }
                Err(error) => failures.push(format!("{program}: {error}")),
            }
        }

        Err(PowerError::new(format!(
            "all power commands failed ({})",
            failures.join("; ")
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(not(windows))]
    #[test]
    fn candidates_are_non_empty_for_all_actions_on_linux_and_macos() {
        let actions = [
            PowerAction::Shutdown,
            PowerAction::Reboot,
            PowerAction::Sleep,
            PowerAction::Hibernate,
            PowerAction::Lock,
        ];

        for action in actions {
            let linux = unix::linux_candidates(action);
            assert!(
                !linux.is_empty(),
                "Linux candidates cannot be empty: {action:?}"
            );
            for (prog, args) in linux {
                assert!(!prog.is_empty());
                let _ = args;
            }

            let macos = unix::macos_candidates(action);
            assert!(
                !macos.is_empty(),
                "macOS candidates cannot be empty: {action:?}"
            );
            for (prog, args) in macos {
                assert!(!prog.is_empty());
                let _ = args;
            }
        }
    }
}
