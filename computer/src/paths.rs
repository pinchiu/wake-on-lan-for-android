//! 檔案位置解析：優先放在執行檔旁，不可寫入時退回使用者資料目錄。
//!
//! [`choose_file`] 以注入的目錄參數決定位置，方便測試，也避免像舊版那樣
//! 用「建立真正的設定檔」來試探目錄是否可寫。

use std::env;
use std::fs::OpenOptions;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use crate::APP_NAME;

const CONFIG_FILE_NAME: &str = "config.ini";
const LOG_FILE_NAME: &str = "pc_onoff.log";

static PROBE_COUNTER: AtomicU64 = AtomicU64::new(0);

/// 設定檔路徑。
pub fn config_path() -> PathBuf {
    choose_file(
        CONFIG_FILE_NAME,
        executable_dir().as_deref(),
        &app_data_dir(),
    )
}

/// 日誌檔路徑。
pub fn log_path() -> PathBuf {
    choose_file(LOG_FILE_NAME, executable_dir().as_deref(), &app_data_dir())
}

/// 使用者資料目錄（Windows：`%LOCALAPPDATA%\pc_onoff`；Unix：`~/.config/pc_onoff`）。
pub fn app_data_dir() -> PathBuf {
    #[cfg(windows)]
    if let Some(local_app_data) = env::var_os("LOCALAPPDATA") {
        return PathBuf::from(local_app_data).join(APP_NAME);
    }

    #[cfg(not(windows))]
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

/// 若執行檔旁已有同名檔案，或備援目錄尚無設定且執行檔目錄可寫，就使用執行檔目錄；
/// 否則使用備援目錄（避免搬動執行檔後忽略既有的使用者設定）。
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

/// 以建立再刪除臨時檔的方式偵測目錄可寫性，不會留下探測檔。
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
        fs::write(&path, "udp_port=9000").expect("建立設定檔");

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
        fs::create_dir_all(&fallback).expect("建立備援目錄");
        fs::write(fallback.join("config.ini"), "udp_port=9000").expect("建立備援設定");

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

        let entries = fs::read_dir(dir.path()).expect("讀取目錄").count();
        assert_eq!(entries, 0, "探測檔應被刪除");
    }

    #[test]
    fn app_data_dir_is_under_the_platform_config_root() {
        let path = app_data_dir();
        assert!(path.ends_with(APP_NAME) || path == Path::new("."));

        #[cfg(windows)]
        if let Some(root) = env::var_os("LOCALAPPDATA") {
            assert!(path.starts_with(PathBuf::from(root)));
        }

        #[cfg(not(windows))]
        if let Some(home) = env::var_os("HOME") {
            assert!(path.starts_with(PathBuf::from(home).join(".config")));
        }
    }
}
