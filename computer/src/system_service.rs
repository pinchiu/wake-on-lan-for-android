//! System-wide Linux service (`/etc/systemd/system/pc_onoff.service`).
//!
//! User-level autostart (`systemd --user`, XDG autostart) only starts after the user logs
//! in, so a freshly booted machine sitting at the login screen ignores every command.
//! Installing the daemon as a root system service makes it start at boot and lets
//! `systemctl poweroff/reboot` succeed without an active graphical session or polkit prompt.

use std::fmt;

/// Unit name of the system-wide service.
pub const UNIT_NAME: &str = "pc_onoff.service";

/// Flag passed by the system unit; runs the daemon without touching user autostart.
pub const SERVICE_FLAG: &str = "--service";

/// System service management failure.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServiceError {
    message: String,
}

impl ServiceError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for ServiceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for ServiceError {}

/// Current state of the system-wide service.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ServiceState {
    NotInstalled,
    Inactive,
    Active,
    /// Not applicable on this operating system.
    Unsupported,
}

impl ServiceState {
    pub fn is_installed(self) -> bool {
        matches!(self, Self::Inactive | Self::Active)
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::NotInstalled => "not installed",
            Self::Inactive => "installed (not running)",
            Self::Active => "running",
            Self::Unsupported => "not supported on this OS",
        }
    }
}

/// Query whether the system service is installed and running.
pub fn state() -> ServiceState {
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux::state()
    }
    #[cfg(not(all(unix, not(target_os = "macos"))))]
    {
        ServiceState::Unsupported
    }
}

/// Install (or update) and start the system service. Requires root.
pub fn install() -> Result<String, ServiceError> {
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux::install()
    }
    #[cfg(not(all(unix, not(target_os = "macos"))))]
    {
        Err(ServiceError::new(
            "--install-system is only available on Linux",
        ))
    }
}

/// Stop and remove the system service. Requires root.
pub fn uninstall() -> Result<String, ServiceError> {
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        linux::uninstall()
    }
    #[cfg(not(all(unix, not(target_os = "macos"))))]
    {
        Err(ServiceError::new(
            "--uninstall-system is only available on Linux",
        ))
    }
}

#[cfg(all(unix, not(target_os = "macos")))]
mod linux {
    use std::fs;
    use std::path::{Path, PathBuf};

    use super::{SERVICE_FLAG, ServiceError, ServiceState, UNIT_NAME};
    use crate::autostart::linux as user_autostart;
    use crate::config::AppConfig;
    use crate::paths::DATA_DIR_ENV;
    use crate::platform;

    pub const UNIT_PATH: &str = "/etc/systemd/system/pc_onoff.service";
    pub const INSTALL_PATH: &str = "/usr/local/bin/pc_onoff";
    pub const DATA_DIR: &str = "/var/lib/pc_onoff";

    pub(super) fn state() -> ServiceState {
        if !Path::new(UNIT_PATH).exists() {
            return ServiceState::NotInstalled;
        }
        let active = platform::command("systemctl")
            .args(["is-active", "--quiet", UNIT_NAME])
            .status()
            .map(|status| status.success())
            .unwrap_or(false);
        if active {
            ServiceState::Active
        } else {
            ServiceState::Inactive
        }
    }

    pub(super) fn install() -> Result<String, ServiceError> {
        require_root("--install-system")?;

        let current = std::env::current_exe()
            .map_err(|error| ServiceError::new(format!("cannot get executable path: {error}")))?;
        let target = Path::new(INSTALL_PATH);
        install_binary(&current, target)?;

        fs::create_dir_all(DATA_DIR)
            .map_err(|error| ServiceError::new(format!("cannot create {DATA_DIR}: {error}")))?;
        let invoking_home = invoking_user_home();
        migrate_user_config(invoking_home.as_deref(), &current);

        fs::write(UNIT_PATH, unit_file(target))
            .map_err(|error| ServiceError::new(format!("cannot write {UNIT_PATH}: {error}")))?;

        // Roll back the unit if systemd cannot take it, so user launches keep working.
        if let Err(error) =
            systemctl(&["daemon-reload"]).and_then(|()| systemctl(&["enable", UNIT_NAME]))
        {
            let _ = fs::remove_file(UNIT_PATH);
            let _ = systemctl(&["daemon-reload"]);
            return Err(error);
        }

        // The user-level instance would otherwise keep UDP 9877 busy. Keep its unit files
        // until the system service is proven healthy, so a failed install can roll back.
        let user_stopped = stop_user_instance(invoking_home.as_deref());

        if let Err(error) = systemctl(&["restart", UNIT_NAME]) {
            rollback_install(invoking_home.as_deref(), user_stopped);
            return Err(error);
        }
        if let Err(error) = wait_until_healthy() {
            rollback_install(invoking_home.as_deref(), user_stopped);
            return Err(error);
        }

        let mut notes = Vec::new();
        if let Some(note) = remove_user_autostart_files(invoking_home.as_deref()) {
            notes.push(note);
        }

        let mut message = format!(
            "System service installed: {UNIT_PATH}\n\
             Binary:  {INSTALL_PATH}\n\
             Config:  {DATA_DIR}/config.ini\n\
             Log:     {DATA_DIR}/pc_onoff.log\n\
             pc_onoff now starts at boot and accepts commands before anyone logs in."
        );
        for note in notes {
            message.push('\n');
            message.push_str(&note);
        }
        Ok(message)
    }

    pub(super) fn uninstall() -> Result<String, ServiceError> {
        require_root("--uninstall-system")?;

        if Path::new(UNIT_PATH).exists() {
            let _ = systemctl(&["disable", "--now", UNIT_NAME]);
            fs::remove_file(UNIT_PATH).map_err(|error| {
                ServiceError::new(format!("cannot remove {UNIT_PATH}: {error}"))
            })?;
            let _ = systemctl(&["daemon-reload"]);
        }

        let installed = Path::new(INSTALL_PATH);
        // Linux allows unlinking the running executable; do not leave the installed
        // binary behind when the user runs `sudo pc_onoff --uninstall-system`.
        if installed.exists() {
            let _ = fs::remove_file(installed);
        }

        Ok(format!(
            "System service removed. Data in {DATA_DIR} was kept.\n\
             Run pc_onoff (without sudo) to re-enable per-user autostart."
        ))
    }

    fn require_root(flag: &str) -> Result<(), ServiceError> {
        if platform::is_root() {
            return Ok(());
        }
        let exe = platform::launcher_executable()
            .map(|path| path.display().to_string())
            .unwrap_or_else(|_| "pc_onoff".to_owned());
        Err(ServiceError::new(format!(
            "{flag} needs root privileges. Run:\n  sudo \"{exe}\" {flag}"
        )))
    }

    /// Copy the real ELF binary (also works from inside an AppImage mount) to a
    /// root-owned location that exists before any home directory is mounted.
    fn install_binary(source: &Path, target: &Path) -> Result<(), ServiceError> {
        use std::io::Write;
        use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};

        // Never follow a symlink at the installed path: `rename` would replace it, but the
        // canonicalize shortcut below could otherwise accept a user-controlled target.
        if fs::symlink_metadata(target).is_ok_and(|metadata| metadata.file_type().is_symlink()) {
            return Err(ServiceError::new(format!(
                "refusing to install over symlink {}",
                target.display()
            )));
        }
        if source.canonicalize().ok() == target.canonicalize().ok() {
            return Ok(());
        }

        // Unique, create_new file in the target directory: no predictable path to pre-create,
        // no symlink following, and the rename stays atomic on the same filesystem.
        let file_name = target
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("pc_onoff");
        let temporary = target.with_file_name(format!(
            ".{file_name}.new.{}.{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|duration| duration.as_nanos())
                .unwrap_or(0)
        ));
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o700)
            .open(&temporary)
            .map_err(|error| {
                ServiceError::new(format!(
                    "cannot create temporary install file {}: {error}",
                    temporary.display()
                ))
            })?;

        let result = (|| -> Result<(), ServiceError> {
            let bytes = fs::read(source).map_err(|error| {
                ServiceError::new(format!("cannot read {}: {error}", source.display()))
            })?;
            file.write_all(&bytes).map_err(|error| {
                ServiceError::new(format!("cannot write {}: {error}", temporary.display()))
            })?;
            file.sync_all().map_err(|error| {
                ServiceError::new(format!("cannot sync {}: {error}", temporary.display()))
            })?;
            drop(file);
            fs::set_permissions(&temporary, fs::Permissions::from_mode(0o755)).map_err(
                |error| ServiceError::new(format!("cannot chmod {}: {error}", temporary.display())),
            )?;
            fs::rename(&temporary, target).map_err(|error| {
                ServiceError::new(format!("cannot install {}: {error}", target.display()))
            })
        })();

        if result.is_err() {
            let _ = fs::remove_file(&temporary);
        }
        result
    }

    /// Carry over a custom `udp_port` and the command secret from the invoking user's config
    /// on first install.
    fn migrate_user_config(home: Option<&Path>, current_exe: &Path) {
        let target = Path::new(DATA_DIR).join("config.ini");
        if target.exists() {
            return;
        }
        let mut candidates: Vec<PathBuf> = Vec::new();
        if let Some(directory) = current_exe.parent() {
            candidates.push(directory.join("config.ini"));
        }
        if let Some(home) = home {
            candidates.push(home.join(".config/pc_onoff/config.ini"));
        }
        let user_config = candidates
            .iter()
            .find(|path| path.exists())
            .map(|path| AppConfig::load(path))
            .unwrap_or_default();
        let config = AppConfig {
            udp_port: user_config.udp_port,
            autostart: false,
            secret: user_config.secret,
        };
        let _ = config.save(&target);
    }

    /// Stop the invoking user's `systemd --user` instance (and any manual daemon) without
    /// deleting its unit files, so a failed install can start it again.
    fn stop_user_instance(home: Option<&Path>) -> bool {
        let Some(home) = home else {
            return false;
        };
        let user = std::env::var("SUDO_USER")
            .ok()
            .filter(|user| !user.is_empty());

        if let Some(user) = &user {
            // `.host` is required to address the local machine's user manager.
            let machine = format!("{user}@.host");
            let _ = platform::command("systemctl")
                .args([
                    "--user",
                    "--machine",
                    &machine,
                    "disable",
                    "--now",
                    user_autostart::SERVICE_NAME,
                ])
                .output();
        }

        let lock = home.join(".config/pc_onoff/pc_onoff.lock");
        if let Some(pid) = fs::read_to_string(&lock)
            .ok()
            .and_then(|content| content.trim().parse::<u32>().ok())
            .filter(|&pid| is_users_pc_onoff(pid, home))
        {
            let _ = platform::command("kill")
                .args(["-TERM", &pid.to_string()])
                .output();
            // Wait for the old daemon to release UDP 9877 before `systemctl restart`
            // starts the system unit, otherwise the install fails with EADDRINUSE.
            for _ in 0..40 {
                if !is_users_pc_onoff(pid, home) {
                    break;
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
        }
        true
    }

    /// Delete the invoking user's autostart entries after the system service is healthy.
    fn remove_user_autostart_files(home: Option<&Path>) -> Option<String> {
        let home = home?;
        let mut removed = false;
        for path in [
            user_autostart::desktop_path(home),
            user_autostart::service_path(home),
            user_autostart::service_wants_link(home),
        ] {
            if fs::symlink_metadata(&path).is_ok() && fs::remove_file(&path).is_ok() {
                removed = true;
            }
        }
        removed.then(|| {
            format!(
                "Removed per-user autostart in {} (the system service replaces it).",
                home.display()
            )
        })
    }

    /// `systemctl restart` succeeds as soon as the unit is queued for a `Type=simple`
    /// service, so verify the daemon actually stayed up with a live MainPID.
    fn wait_until_healthy() -> Result<(), ServiceError> {
        for _ in 0..25 {
            if state() == ServiceState::Active && main_pid_running() {
                return Ok(());
            }
            std::thread::sleep(std::time::Duration::from_millis(200));
        }
        Err(ServiceError::new(format!(
            "{UNIT_NAME} did not stay running after installation; check `sudo systemctl status {UNIT_NAME}` \
             and `sudo journalctl -u {UNIT_NAME}`"
        )))
    }

    fn main_pid_running() -> bool {
        let output = platform::command("systemctl")
            .args(["show", "-p", "MainPID", "--value", UNIT_NAME])
            .output();
        let Some(output) = output.ok().filter(|output| output.status.success()) else {
            return false;
        };
        let pid = String::from_utf8_lossy(&output.stdout)
            .trim()
            .parse::<u32>();
        pid.is_ok_and(|pid| pid > 0 && Path::new(&format!("/proc/{pid}")).exists())
    }

    /// Remove the unit and restart the user instance if the system service could not stay up.
    fn rollback_install(home: Option<&Path>, user_stopped: bool) {
        let _ = fs::remove_file(UNIT_PATH);
        let _ = systemctl(&["disable", "--now", UNIT_NAME]);
        let _ = systemctl(&["daemon-reload"]);
        if user_stopped && home.is_some() {
            let user = std::env::var("SUDO_USER")
                .ok()
                .filter(|user| !user.is_empty());
            if let Some(user) = user {
                let machine = format!("{user}@.host");
                let _ = platform::command("systemctl")
                    .args([
                        "--user",
                        "--machine",
                        &machine,
                        "enable",
                        "--now",
                        user_autostart::SERVICE_NAME,
                    ])
                    .output();
            }
        }
    }

    /// The lock file is user-writable, so only signal a `pc_onoff` process owned by the
    /// same user that owns `home`.
    fn is_users_pc_onoff(pid: u32, home: &Path) -> bool {
        use std::os::unix::fs::MetadataExt;
        if pid <= 1 {
            return false;
        }
        let proc_dir = PathBuf::from(format!("/proc/{pid}"));
        // `comm` is truncated to 15 bytes, so the published standalone binary
        // (`pc_onoff-linux-x86_64`) shows up as `pc_onoff-linux-x`. Resolve the
        // executable instead and fall back to `comm` only when /proc/exe is unreadable.
        let is_pc_onoff = fs::read_link(proc_dir.join("exe"))
            .ok()
            .as_deref()
            .and_then(Path::file_name)
            .and_then(|name| name.to_str())
            .is_some_and(is_pc_onoff_executable)
            || fs::read_to_string(proc_dir.join("comm"))
                .is_ok_and(|comm| is_pc_onoff_executable(comm.trim()));
        let same_owner = match (fs::metadata(&proc_dir), fs::metadata(home)) {
            (Ok(process), Ok(home)) => process.uid() == home.uid() && process.uid() != 0,
            _ => false,
        };
        is_pc_onoff && same_owner
    }

    /// Executable names shipped by this project: `pc_onoff` (AppImage / installed copy)
    /// and `pc_onoff-linux-x86_64` (standalone release). Nothing else may be signalled.
    fn is_pc_onoff_executable(name: &str) -> bool {
        name == "pc_onoff" || name.starts_with("pc_onoff-")
    }

    fn invoking_user_home() -> Option<PathBuf> {
        let user = std::env::var("SUDO_USER")
            .ok()
            .filter(|user| !user.is_empty())?;
        if user == "root" {
            return None;
        }
        if let Some(home) = fs::read_to_string("/etc/passwd")
            .ok()
            .and_then(|passwd| home_from_passwd(&passwd, &user))
        {
            return Some(home);
        }
        // LDAP / SSSD / systemd-homed accounts are only visible through NSS.
        let output = platform::command("getent")
            .args(["passwd", &user])
            .output()
            .ok()
            .filter(|output| output.status.success())?;
        home_from_passwd(&String::from_utf8_lossy(&output.stdout), &user)
    }

    pub fn home_from_passwd(passwd: &str, user: &str) -> Option<PathBuf> {
        passwd.lines().find_map(|line| {
            let fields: Vec<&str> = line.split(':').collect();
            (fields.len() >= 6 && fields[0] == user && !fields[5].is_empty())
                .then(|| PathBuf::from(fields[5]))
        })
    }

    fn systemctl(args: &[&str]) -> Result<(), ServiceError> {
        let output = platform::command("systemctl")
            .args(args)
            .output()
            .map_err(|error| ServiceError::new(format!("cannot execute systemctl: {error}")))?;
        if output.status.success() {
            return Ok(());
        }
        let stderr = String::from_utf8_lossy(&output.stderr);
        Err(ServiceError::new(format!(
            "systemctl {} failed: {}",
            args.join(" "),
            stderr.trim()
        )))
    }

    pub fn unit_file(executable: &Path) -> String {
        format!(
            "[Unit]\n\
             Description=Wake-on-LAN PC Control Service (system-wide, active before login)\n\
             Wants=network-online.target\n\
             After=network-online.target\n\
             \n\
             [Service]\n\
             Type=simple\n\
             ExecStart={} {SERVICE_FLAG}\n\
             Environment={DATA_DIR_ENV}={DATA_DIR}\n\
             StateDirectory=pc_onoff\n\
             Restart=on-failure\n\
             RestartSec=5\n\
             NoNewPrivileges=true\n\
             PrivateTmp=true\n\
             ProtectSystem=strict\n\
             ProtectHome=true\n\
             ProtectKernelTunables=true\n\
             ProtectKernelModules=true\n\
             ProtectKernelLogs=true\n\
             ProtectControlGroups=true\n\
             ProtectClock=true\n\
             ProtectHostname=true\n\
             RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX AF_NETLINK\n\
             RestrictNamespaces=true\n\
             RestrictRealtime=true\n\
             RestrictSUIDSGID=true\n\
             LockPersonality=true\n\
             MemoryDenyWriteExecute=true\n\
             CapabilityBoundingSet=CAP_KILL CAP_SYS_BOOT\n\
             UMask=0077\n\
             \n\
             [Install]\n\
             WantedBy=multi-user.target\n",
            user_autostart::systemd_exec(executable)
        )
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn unit_starts_at_boot_without_a_login_session() {
            let unit = unit_file(Path::new(INSTALL_PATH));
            assert!(unit.contains("WantedBy=multi-user.target"));
            assert!(unit.contains("ExecStart=\"/usr/local/bin/pc_onoff\" --service"));
            assert!(unit.contains("Environment=PC_ONOFF_DATA_DIR=/var/lib/pc_onoff"));
            assert!(unit.contains("After=network-online.target"));
            assert!(!unit.contains("User="), "must run as root to power off");
            assert!(unit.contains("NoNewPrivileges=true"));
            assert!(unit.contains("ProtectSystem=strict"));
            assert!(unit.contains("ProtectHome=true"));
            assert!(unit.contains("MemoryDenyWriteExecute=true"));
            assert!(unit.contains("RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX AF_NETLINK"));
        }

        #[test]
        fn finds_home_directory_in_passwd() {
            let passwd = "root:x:0:0:root:/root:/bin/bash\n\
                          scott:x:1000:1000:Scott,,,:/home/scott:/bin/bash\n";
            assert_eq!(
                home_from_passwd(passwd, "scott"),
                Some(PathBuf::from("/home/scott"))
            );
            assert_eq!(home_from_passwd(passwd, "nobody"), None);
        }

        #[test]
        fn recognises_published_executable_names_only() {
            assert!(is_pc_onoff_executable("pc_onoff"));
            assert!(is_pc_onoff_executable("pc_onoff-linux-x86_64"));
            assert!(is_pc_onoff_executable("pc_onoff-linux-x"));
            assert!(!is_pc_onoff_executable("pc_onoffd"));
            assert!(!is_pc_onoff_executable("bash"));
        }

        #[test]
        fn install_binary_replaces_file_and_rejects_symlink() {
            use std::os::unix::fs::{PermissionsExt, symlink};

            use crate::test_support::TempDir;

            let dir = TempDir::new("install-binary");
            let source = dir.path().join("source");
            fs::write(&source, b"new-binary").expect("write source");
            let target = dir.path().join("pc_onoff");
            fs::write(&target, b"old").expect("write target");

            install_binary(&source, &target).expect("install binary");
            assert_eq!(fs::read(&target).expect("read target"), b"new-binary");
            let mode = fs::metadata(&target)
                .expect("metadata")
                .permissions()
                .mode()
                & 0o777;
            assert_eq!(mode, 0o755);

            let link = dir.path().join("link");
            symlink(&source, &link).expect("create symlink");
            assert!(
                install_binary(&source, &link).is_err(),
                "must refuse to install over a symlink"
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_inactive_and_active_count_as_installed() {
        assert!(ServiceState::Active.is_installed());
        assert!(ServiceState::Inactive.is_installed());
        assert!(!ServiceState::NotInstalled.is_installed());
        assert!(!ServiceState::Unsupported.is_installed());
    }
}
