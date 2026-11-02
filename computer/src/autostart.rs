//! Autostart registration and query.
//!
//! Windows uses `HKCU\...\Run` (no admin privileges required); Linux uses
//! `~/.config/autostart` and `systemd --user`; macOS uses `~/Library/LaunchAgents` (launchd).
//! All platform nuances are encapsulated behind [`is_enabled`] / [`set_enabled`].

use std::fmt;

/// Autostart configuration failure.
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

/// Check whether the current user has registered this program for autostart on login.
pub fn is_enabled() -> bool {
    #[cfg(windows)]
    {
        windows::is_enabled()
    }
    #[cfg(target_os = "macos")]
    {
        macos::is_enabled()
    }
    #[cfg(all(not(windows), not(target_os = "macos")))]
    {
        linux::is_enabled()
    }
}

/// Enable or disable autostart.
pub fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
    #[cfg(windows)]
    {
        windows::set_enabled(enabled)
    }
    #[cfg(target_os = "macos")]
    {
        macos::set_enabled(enabled)
    }
    #[cfg(all(not(windows), not(target_os = "macos")))]
    {
        linux::set_enabled(enabled)
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
            .map_err(|error| AutostartError::new(format!("cannot get executable path: {error}")))?;

        if enabled {
            let value = format!("\"{}\"", executable.display());
            let output = platform::command("reg")
                .args([
                    "add", RUN_KEY, "/v", RUN_NAME, "/t", "REG_SZ", "/d", &value, "/f",
                ])
                .output()
                .map_err(|error| AutostartError::new(format!("cannot execute reg add: {error}")))?;

            if !output.status.success() {
                let stderr = String::from_utf8_lossy(&output.stderr);
                let stderr = stderr.trim();
                return Err(AutostartError::new(if stderr.is_empty() {
                    "failed to write Windows autostart registry entry".to_owned()
                } else {
                    format!("failed to write Windows autostart registry entry: {stderr}")
                }));
            }
        } else {
            let output = platform::command("reg")
                .args(["delete", RUN_KEY, "/v", RUN_NAME, "/f"])
                .output()
                .map_err(|error| {
                    AutostartError::new(format!("cannot execute reg delete: {error}"))
                })?;

            // If the value did not exist in the first place, reg delete returns an error which is not fatal.
            if !output.status.success() && is_enabled() {
                return Err(AutostartError::new(
                    "failed to remove Windows autostart registry entry",
                ));
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

#[cfg(any(all(not(windows), not(target_os = "macos")), test))]
pub(crate) mod linux {
    use std::env;
    use std::fs;
    use std::path::{Path, PathBuf};

    use super::AutostartError;
    use crate::platform;

    const SERVICE_NAME: &str = "pc_onoff.service";
    const DESKTOP_NAME: &str = "pc_onoff.desktop";

    #[cfg_attr(target_os = "macos", allow(dead_code))]
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

    #[cfg_attr(target_os = "macos", allow(dead_code))]
    pub(super) fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
        let home =
            home_dir().ok_or_else(|| AutostartError::new("HOME environment variable not found"))?;
        let executable = env::current_exe()
            .map_err(|error| AutostartError::new(format!("cannot get executable path: {error}")))?;

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
                AutostartError::new(format!(
                    "cannot create directory {}: {error}",
                    parent.display()
                ))
            })?;
        }
        fs::write(path, content).map_err(|error| {
            AutostartError::new(format!("cannot write {}: {error}", path.display()))
        })
    }

    fn remove_file(path: &Path) -> Result<(), AutostartError> {
        fs::remove_file(path).map_err(|error| {
            AutostartError::new(format!("cannot remove {}: {error}", path.display()))
        })
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

    /// XDG Desktop Entry Exec requires double quote escaping; `%` is a field code and must be escaped.
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

    /// systemd unit ExecStart also quotes paths; `%` is escaped as `%%`.
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

#[cfg(any(target_os = "macos", test))]
pub(crate) mod macos {
    use std::env;
    use std::fs;
    use std::path::{Path, PathBuf};

    use super::AutostartError;
    use crate::platform;

    pub const LABEL: &str = "com.wakeonlan.pc_onoff";
    pub const PLIST_NAME: &str = "com.wakeonlan.pc_onoff.plist";

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    pub(super) fn is_enabled() -> bool {
        let Some(home) = home_dir() else {
            return false;
        };

        plist_path(&home).exists()
    }

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    pub(super) fn set_enabled(enabled: bool) -> Result<(), AutostartError> {
        let home =
            home_dir().ok_or_else(|| AutostartError::new("HOME environment variable not found"))?;
        let executable = env::current_exe()
            .map_err(|error| AutostartError::new(format!("cannot get executable path: {error}")))?;

        let plist = plist_path(&home);

        if enabled {
            write_file(&plist, &launch_agent_plist(&executable))?;
            let _ = platform::command("launchctl")
                .args(["load", &plist.to_string_lossy()])
                .output();
        } else if plist.exists() {
            let _ = platform::command("launchctl")
                .args(["unload", &plist.to_string_lossy()])
                .output();
            remove_file(&plist)?;
        }

        Ok(())
    }

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    fn home_dir() -> Option<PathBuf> {
        env::var_os("HOME").map(PathBuf::from)
    }

    pub fn plist_path(home: &Path) -> PathBuf {
        home.join("Library/LaunchAgents").join(PLIST_NAME)
    }

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    fn write_file(path: &Path, content: &str) -> Result<(), AutostartError> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                AutostartError::new(format!(
                    "cannot create directory {}: {error}",
                    parent.display()
                ))
            })?;
        }
        fs::write(path, content).map_err(|error| {
            AutostartError::new(format!("cannot write {}: {error}", path.display()))
        })
    }

    #[cfg_attr(not(target_os = "macos"), allow(dead_code))]
    fn remove_file(path: &Path) -> Result<(), AutostartError> {
        fs::remove_file(path).map_err(|error| {
            AutostartError::new(format!("cannot remove {}: {error}", path.display()))
        })
    }

    fn xml_escape(input: &str) -> String {
        let mut escaped = String::with_capacity(input.len());
        for character in input.chars() {
            match character {
                '&' => escaped.push_str("&amp;"),
                '<' => escaped.push_str("&lt;"),
                '>' => escaped.push_str("&gt;"),
                '"' => escaped.push_str("&quot;"),
                '\'' => escaped.push_str("&apos;"),
                _ => escaped.push(character),
            }
        }
        escaped
    }

    pub fn launch_agent_plist(executable: &Path) -> String {
        let escaped = xml_escape(&executable.to_string_lossy());
        format!(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
             <!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n\
             <plist version=\"1.0\">\n\
             <dict>\n\
             \t<key>Label</key>\n\
             \t<string>{LABEL}</string>\n\
             \t<key>ProgramArguments</key>\n\
             \t<array>\n\
             \t\t<string>{escaped}</string>\n\
             \t</array>\n\
             \t<key>RunAtLoad</key>\n\
             \t<true/>\n\
             \t<key>ProcessType</key>\n\
             \t<string>Interactive</string>\n\
             </dict>\n\
             </plist>\n"
        )
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn launch_agent_plist_contains_label_and_executable() {
            let plist = launch_agent_plist(Path::new("/Applications/pc_onoff"));
            assert!(plist.contains("<string>com.wakeonlan.pc_onoff</string>"));
            assert!(plist.contains("<string>/Applications/pc_onoff</string>"));
            assert!(plist.contains("<key>RunAtLoad</key>"));
            assert!(plist.contains("<true/>"));
        }

        #[test]
        fn launch_agent_plist_escapes_xml_special_characters() {
            let plist = launch_agent_plist(Path::new("/Applications/Test & App <1>/\"pc_onoff\""));
            assert!(plist.contains("/Applications/Test &amp; App &lt;1&gt;/&quot;pc_onoff&quot;"));
            assert!(!plist.contains("Test & App"));
        }

        #[test]
        fn chooses_standard_macos_launch_agents_path() {
            let home = Path::new("/Users/tester");
            assert_eq!(
                plist_path(home),
                Path::new("/Users/tester/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist")
            );
        }
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn error_messages_are_visible_to_callers() {
        let error = super::AutostartError::new("test failure");
        assert_eq!(error.to_string(), "test failure");
    }
}
