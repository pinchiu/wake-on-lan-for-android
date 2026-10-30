//! 開機自動啟動的註冊與查詢。
//!
//! Windows 使用 `HKCU\...\Run`（免管理員權限）；Unix 使用
//! `~/.config/autostart` 與 `systemd --user`。所有平台差異都藏在
//! [`is_enabled`]／[`set_enabled`] 之後，呼叫端只看到布林值與錯誤。

use std::fmt;

/// 開機自動啟動設定失敗。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AutostartError {
    message: String,
}

impl AutostartError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for AutostartError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for AutostartError {}

/// 目前使用者是否已註冊本程式為開機自動啟動。
pub fn is_enabled() -> bool {
    #[cfg(windows)]
    {
        windows::is_enabled()
    }
    #[cfg(not(windows))]
    {
        unix::is_enabled()
    }
}

/// 啟用或解除開機自動啟動。
pub fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
    #[cfg(windows)]
    {
        windows::set_enabled(enabled)
    }
    #[cfg(not(windows))]
    {
        unix::set_enabled(enabled)
    }
}

#[cfg(windows)]
mod windows {
    use std::env;
    use std::path::Path;

    use super::AutostartError;
    use crate::platform;

    const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
    const RUN_NAME: &str = "WakeOnLanPcOnOff";

    pub(super) fn is_enabled() -> bool {
        let Ok(executable) = env::current_exe() else {
            return false;
        };
        let Ok(output) = platform::command("reg")
            .args(["query", RUN_KEY, "/v", RUN_NAME])
            .output()
        else {
            return false;
        };
        if !output.status.success() {
            return false;
        }

        contains_executable(&String::from_utf8_lossy(&output.stdout), &executable)
    }

    pub(super) fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
        let executable = env::current_exe()
            .map_err(|error| AutostartError::new(format!("無法取得目前執行檔路徑: {error}")))?;

        if enabled {
            let value = format!("\"{}\"", executable.display());
            let output = platform::command("reg")
                .args([
                    "add", RUN_KEY, "/v", RUN_NAME, "/t", "REG_SZ", "/d", &value, "/f",
                ])
                .output()
                .map_err(|error| AutostartError::new(format!("無法執行 reg add: {error}")))?;

            if !output.status.success() {
                let stderr = String::from_utf8_lossy(&output.stderr);
                let stderr = stderr.trim();
                return Err(AutostartError::new(if stderr.is_empty() {
                    "寫入 Windows 開機啟動登錄項失敗".to_owned()
                } else {
                    format!("寫入 Windows 開機啟動登錄項失敗: {stderr}")
                }));
            }
        } else {
            let output = platform::command("reg")
                .args(["delete", RUN_KEY, "/v", RUN_NAME, "/f"])
                .output()
                .map_err(|error| AutostartError::new(format!("無法執行 reg delete: {error}")))?;

            // 值原本就不存在時 reg delete 會回傳失敗，這不算錯誤。
            if !output.status.success() && is_enabled() {
                return Err(AutostartError::new("移除 Windows 開機啟動登錄項失敗"));
            }
        }

        Ok(())
    }

    fn contains_executable(registry_output: &str, executable: &Path) -> bool {
        let needle = executable.to_string_lossy().to_ascii_lowercase();
        registry_output.to_ascii_lowercase().contains(&needle)
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn matches_the_registered_path_case_insensitively() {
            let output = "HKEY_CURRENT_USER\\Software\\...\\Run\n    WakeOnLanPcOnOff    REG_SZ    \"C:\\Tools\\PC_ONOFF.EXE\"\n";
            assert!(contains_executable(
                output,
                Path::new(r"C:\Tools\pc_onoff.exe")
            ));
        }

        #[test]
        fn does_not_match_a_different_executable() {
            let output = "    WakeOnLanPcOnOff    REG_SZ    \"C:\\Old\\pc_onoff.exe\"\n";
            assert!(!contains_executable(
                output,
                Path::new(r"C:\New\pc_onoff.exe")
            ));
        }

        #[test]
        fn ignores_unrelated_output() {
            assert!(!contains_executable(
                "",
                Path::new(r"C:\Tools\pc_onoff.exe")
            ));
        }
    }
}

#[cfg(not(windows))]
mod unix {
    use std::env;
    use std::fs;
    use std::path::{Path, PathBuf};

    use super::AutostartError;
    use crate::platform;

    const SERVICE_NAME: &str = "pc_onoff.service";
    const DESKTOP_NAME: &str = "pc_onoff.desktop";

    pub(super) fn is_enabled() -> bool {
        let Some(home) = home_dir() else {
            return false;
        };

        if desktop_path(&home).exists() {
            return true;
        }

        platform::command("systemctl")
            .args(["--user", "is-enabled", SERVICE_NAME])
            .output()
            .map(|output| output.status.success())
            .unwrap_or(false)
    }

    pub(super) fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
        let home = home_dir().ok_or_else(|| AutostartError::new("找不到 HOME 環境變數"))?;
        let executable = env::current_exe()
            .map_err(|error| AutostartError::new(format!("無法取得執行檔路徑: {error}")))?;

        let desktop = desktop_path(&home);
        let service = service_path(&home);

        if enabled {
            write_file(&desktop, &desktop_entry(&executable))?;
            write_file(&service, &service_unit(&executable))?;

            let _ = platform::command("systemctl")
                .args(["--user", "daemon-reload"])
                .output();
            let _ = platform::command("systemctl")
                .args(["--user", "enable", SERVICE_NAME])
                .output();
        } else {
            if service.exists() {
                let _ = platform::command("systemctl")
                    .args(["--user", "disable", "--now", SERVICE_NAME])
                    .output();
                remove_file(&service)?;
                let _ = platform::command("systemctl")
                    .args(["--user", "daemon-reload"])
                    .output();
            }
            if desktop.exists() {
                remove_file(&desktop)?;
            }
        }

        Ok(())
    }

    fn home_dir() -> Option<PathBuf> {
        env::var_os("HOME").map(PathBuf::from)
    }

    fn desktop_path(home: &Path) -> PathBuf {
        home.join(".config/autostart").join(DESKTOP_NAME)
    }

    fn service_path(home: &Path) -> PathBuf {
        home.join(".config/systemd/user").join(SERVICE_NAME)
    }

    fn write_file(path: &Path, content: &str) -> Result<(), AutostartError> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                AutostartError::new(format!("無法建立目錄 {}: {error}", parent.display()))
            })?;
        }
        fs::write(path, content)
            .map_err(|error| AutostartError::new(format!("無法寫入 {}: {error}", path.display())))
    }

    fn remove_file(path: &Path) -> Result<(), AutostartError> {
        fs::remove_file(path)
            .map_err(|error| AutostartError::new(format!("無法刪除 {}: {error}", path.display())))
    }

    fn desktop_entry(executable: &Path) -> String {
        format!(
            "[Desktop Entry]\n\
             Type=Application\n\
             Name=Wake-on-LAN PC Control\n\
             Comment=Remote shutdown, reboot, sleep, hibernate and lock helper\n\
             Exec={}\n\
             Terminal=false\n\
             X-GNOME-Autostart-enabled=true\n",
            desktop_exec(executable)
        )
    }

    fn service_unit(executable: &Path) -> String {
        format!(
            "[Unit]\n\
             Description=Wake-on-LAN PC Control Service\n\
             After=network.target\n\
             \n\
             [Service]\n\
             Type=simple\n\
             ExecStart={}\n\
             Restart=always\n\
             \n\
             [Install]\n\
             WantedBy=default.target\n",
            systemd_exec(executable)
        )
    }

    /// XDG Desktop Entry 的 Exec 需要雙引號跳脫，`%` 是欄位代碼必須加倍。
    fn desktop_exec(executable: &Path) -> String {
        let path = executable.to_string_lossy();
        let mut escaped = String::with_capacity(path.len() + 2);
        escaped.push('"');
        for character in path.chars() {
            match character {
                '\\' => escaped.push_str("\\\\"),
                '"' => escaped.push_str("\\\""),
                '`' => escaped.push_str("\\`"),
                '$' => escaped.push_str("\\$"),
                '%' => escaped.push_str("%%"),
                _ => escaped.push(character),
            }
        }
        escaped.push('"');
        escaped
    }

    /// systemd unit 的 ExecStart 同樣使用雙引號，`%` 需以 `%%` 表示。
    fn systemd_exec(executable: &Path) -> String {
        let path = executable.to_string_lossy();
        let mut escaped = String::with_capacity(path.len() + 2);
        escaped.push('"');
        for character in path.chars() {
            match character {
                '\\' => escaped.push_str("\\\\"),
                '"' => escaped.push_str("\\\""),
                '%' => escaped.push_str("%%"),
                '$' => escaped.push_str("$$"),
                _ => escaped.push(character),
            }
        }
        escaped.push('"');
        escaped
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn desktop_entry_quotes_paths_with_spaces() {
            let entry = desktop_entry(Path::new("/opt/my apps/pc_onoff"));
            assert!(entry.contains("Exec=\"/opt/my apps/pc_onoff\""));
            assert!(entry.contains("X-GNOME-Autostart-enabled=true"));
        }

        #[test]
        fn desktop_entry_escapes_field_codes() {
            let entry = desktop_entry(Path::new("/opt/100%/pc_onoff"));
            assert!(entry.contains("Exec=\"/opt/100%%/pc_onoff\""));
        }

        #[test]
        fn service_unit_escapes_specifiers_and_variables() {
            let unit = service_unit(Path::new("/opt/100%/pc_onoff"));
            assert!(unit.contains("ExecStart=\"/opt/100%%/pc_onoff\""));
            assert!(unit.contains("Restart=always"));
        }

        #[test]
        fn service_unit_quotes_paths_with_spaces() {
            let unit = service_unit(Path::new("/opt/my apps/pc_onoff"));
            assert!(unit.contains("ExecStart=\"/opt/my apps/pc_onoff\""));
        }

        #[test]
        fn chooses_standard_user_paths() {
            let home = Path::new("/home/tester");
            assert_eq!(
                desktop_path(home),
                Path::new("/home/tester/.config/autostart/pc_onoff.desktop")
            );
            assert_eq!(
                service_path(home),
                Path::new("/home/tester/.config/systemd/user/pc_onoff.service")
            );
        }
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn error_messages_are_visible_to_callers() {
        let error = super::AutostartError::new("測試失敗");
        assert_eq!(error.to_string(), "測試失敗");
    }
}
