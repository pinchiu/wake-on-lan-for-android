//! File location resolution: prefers next to the executable, falling back to user data directory if unwritable.
//!
//! [`choose_file`] determines location based on injected directory parameters for easy testing,
//! avoiding legacy approaches that probed directories by creating actual configuration files.

use std::env;
use std::fs::OpenOptions;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use crate::APP_NAME;

const CONFIG_FILE_NAME: &str = "config.ini";
const LOG_FILE_NAME: &str = "pc_onoff.log";

static PROBE_COUNTER: AtomicU64 = AtomicU64::new(0);

/// Configuration file path.
pub fn config_path() -> PathBuf {
    choose_file(
        CONFIG_FILE_NAME,
        executable_dir().as_deref(),
        &app_data_dir(),
    )
}

/// Log file path.
pub fn log_path() -> PathBuf {
    choose_file(LOG_FILE_NAME, executable_dir().as_deref(), &app_data_dir())
}

/// User application data directory
/// (Windows: `%LOCALAPPDATA%\pc_onoff`; macOS: `~/Library/Application Support/pc_onoff`; Linux/Unix: `~/.config/pc_onoff`).
pub fn app_data_dir() -> PathBuf {
    #[cfg(windows)]
    if let Some(local_app_data) = env::var_os("LOCALAPPDATA") {
        return PathBuf::from(local_app_data).join(APP_NAME);
    }

    #[cfg(target_os = "macos")]
    if let Some(home) = env::var_os("HOME") {
        return PathBuf::from(home)
            .join("Library/Application Support")
            .join(APP_NAME);
    }

    #[cfg(all(not(windows), not(target_os = "macos")))]
    if let Some(home) = env::var_os("HOME") {
        return PathBuf::from(home).join(".config").join(APP_NAME);
    }

    env::current_dir().unwrap_or_else(|_| PathBuf::from("."))
}

fn executable_dir() -> Option<PathBuf> {
    env::current_exe()
        .ok()
        .and_then(|exe| exe.parent().map(Path::to_path_buf))
}

/// If a file already exists next to the executable, or fallback has no file and executable dir is writable,
/// use the executable directory; otherwise use fallback directory (preserves settings across binary relocations).
fn choose_file(file_name: &str, executable_dir: Option<&Path>, fallback_dir: &Path) -> PathBuf {
    if let Some(directory) = executable_dir {
        let candidate = directory.join(file_name);
        let fallback_candidate = fallback_dir.join(file_name);
        if candidate.exists() || (!fallback_candidate.exists() && is_directory_writable(directory))
        {
            return candidate;
        }
    }
    fallback_dir.join(file_name)
}

/// Detect directory writability by creating and deleting a temporary probe file.
fn is_directory_writable(directory: &Path) -> bool {
    let id = PROBE_COUNTER.fetch_add(1, Ordering::Relaxed);
    let probe = directory.join(format!(".{APP_NAME}.probe.{}.{id}", std::process::id()));

    match OpenOptions::new().create_new(true).write(true).open(&probe) {
        Ok(file) => {
            drop(file);
            let _ = std::fs::remove_file(&probe);
            true
        }
        Err(_) => false,
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use super::*;
    use crate::test_support::TempDir;

    #[test]
    fn existing_file_next_to_executable_wins_even_if_directory_would_fallback() {
        let dir = TempDir::new("paths-existing");
        let fallback = dir.path().join("fallback");
        let path = dir.path().join("config.ini");
        fs::write(&path, "udp_port=9000").expect("create config file");

        assert_eq!(choose_file("config.ini", Some(dir.path()), &fallback), path);
    }

    #[test]
    fn writable_executable_directory_wins_when_no_file_exists() {
        let dir = TempDir::new("paths-writable");
        let fallback = dir.path().join("fallback");

        assert_eq!(
            choose_file("pc_onoff.log", Some(dir.path()), &fallback),
            dir.path().join("pc_onoff.log")
        );
    }

    #[test]
    fn existing_fallback_file_is_not_ignored_after_moving_the_executable() {
        let dir = TempDir::new("paths-fallback-existing");
        let fallback = dir.path().join("fallback");
        fs::create_dir_all(&fallback).expect("create fallback dir");
        fs::write(fallback.join("config.ini"), "udp_port=9000").expect("create fallback config");

        assert_eq!(
            choose_file("config.ini", Some(dir.path()), &fallback),
            fallback.join("config.ini")
        );
    }

    #[test]
    fn missing_executable_directory_falls_back() {
        let dir = TempDir::new("paths-missing");
        let missing = dir.path().join("does-not-exist");
        let fallback = dir.path().join("fallback");

        assert_eq!(
            choose_file("pc_onoff.log", Some(&missing), &fallback),
            fallback.join("pc_onoff.log")
        );
        assert!(!missing.exists());
    }

    #[test]
    fn probing_a_directory_leaves_no_files_behind() {
        let dir = TempDir::new("paths-probe");
        assert!(is_directory_writable(dir.path()));

        let entries = fs::read_dir(dir.path()).expect("read dir").count();
        assert_eq!(entries, 0, "probe file should be removed");
    }

    #[test]
    fn app_data_dir_is_under_the_platform_config_root() {
        let path = app_data_dir();
        assert!(path.ends_with(APP_NAME) || path == Path::new("."));

        #[cfg(windows)]
        if let Some(root) = env::var_os("LOCALAPPDATA") {
            assert!(path.starts_with(PathBuf::from(root)));
        }

        #[cfg(target_os = "macos")]
        if let Some(home) = env::var_os("HOME") {
            assert!(path.starts_with(PathBuf::from(home).join("Library/Application Support")));
        }

        #[cfg(all(not(windows), not(target_os = "macos")))]
        if let Some(home) = env::var_os("HOME") {
            assert!(path.starts_with(PathBuf::from(home).join(".config")));
        }
    }
}
