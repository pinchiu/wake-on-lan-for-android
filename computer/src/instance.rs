//! 單一實例守護與停止背景服務。
//!
//! Windows 以具名互斥鎖（`CreateMutexW`）判定；Unix 以鎖定檔記錄 PID。
//! 呼叫端只看到 [`SingleInstance::acquire`]、[`request_stop`] 與其結果型別。

use std::fmt;

#[cfg(not(windows))]
use crate::paths;

#[cfg(not(windows))]
const LOCK_FILE_NAME: &str = "pc_onoff.lock";

/// 取得實例鎖失敗的原因。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InstanceError {
    /// 已有另一個守護程式持有實例鎖。
    AlreadyRunning,
    /// 無法建立互斥鎖或鎖定檔（權限、磁碟等問題）。
    Unavailable { reason: String },
}

impl InstanceError {
    fn unavailable(reason: impl Into<String>) -> Self {
        Self::Unavailable {
            reason: reason.into(),
        }
    }
}

impl fmt::Display for InstanceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::AlreadyRunning => f.write_str("已有另一個 pc_onoff 實例正在執行"),
            Self::Unavailable { reason } => f.write_str(reason),
        }
    }
}

impl std::error::Error for InstanceError {}

/// 停止背景服務的結果。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StopOutcome {
    /// 已送出停止訊號。
    Signaled,
    /// 目前沒有執行中的服務。
    NotRunning,
}

/// 停止服務時發生的錯誤（例如無法執行系統命令）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StopError {
    message: String,
}

impl StopError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for StopError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for StopError {}

/// 持有實例鎖；離開作用域時自動釋放。
pub struct SingleInstance {
    #[cfg(windows)]
    _guard: windows::HeldMutex,
    #[cfg(not(windows))]
    _guard: unix::LockFile,
}

impl SingleInstance {
    /// 嘗試取得實例鎖。
    pub fn acquire() -> Result<Self, InstanceError> {
        #[cfg(windows)]
        {
            windows::HeldMutex::acquire().map(|_guard| Self { _guard })
        }
        #[cfg(not(windows))]
        {
            unix::LockFile::acquire(&lock_path()).map(|_guard| Self { _guard })
        }
    }
}

/// 要求背景服務停止。
pub fn request_stop() -> Result<StopOutcome, StopError> {
    #[cfg(windows)]
    {
        windows::request_stop()
    }
    #[cfg(not(windows))]
    {
        unix::request_stop(&lock_path())
    }
}

/// 目前是否有守護程式持有實例鎖。
pub fn is_running() -> bool {
    #[cfg(windows)]
    {
        windows::is_running()
    }
    #[cfg(not(windows))]
    {
        unix::is_running(&lock_path())
    }
}

#[cfg(not(windows))]
fn lock_path() -> std::path::PathBuf {
    paths::app_data_dir().join(LOCK_FILE_NAME)
}

#[cfg(windows)]
mod windows {
    use std::ffi::c_void;
    use std::ptr;

    use super::{InstanceError, StopError, StopOutcome};
    use crate::platform;

    const MUTEX_NAME: &str = r"Local\WakeOnLanPcOnOffMutex";
    const ERROR_ALREADY_EXISTS: u32 = 183;
    const SYNCHRONIZE: u32 = 0x0010_0000;

    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn CreateMutexW(
            attributes: *mut c_void,
            initial_owner: i32,
            name: *const u16,
        ) -> *mut c_void;
        fn OpenMutexW(desired_access: u32, inherit_handle: i32, name: *const u16) -> *mut c_void;
        fn CloseHandle(handle: *mut c_void) -> i32;
        fn GetLastError() -> u32;
    }

    pub(super) struct HeldMutex {
        handle: *mut c_void,
    }

    impl HeldMutex {
        pub(super) fn acquire() -> Result<Self, InstanceError> {
            Self::acquire_named(MUTEX_NAME)
        }

        pub(super) fn acquire_named(name: &str) -> Result<Self, InstanceError> {
            let wide_name = platform::wide(name);
            let handle = unsafe { CreateMutexW(ptr::null_mut(), 0, wide_name.as_ptr()) };

            if handle.is_null() {
                return Err(InstanceError::unavailable("無法建立具名互斥鎖"));
            }

            // 必須緊接在 CreateMutexW 之後讀取，中間不可再呼叫其他 Win32 API。
            if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
                unsafe {
                    CloseHandle(handle);
                }
                return Err(InstanceError::AlreadyRunning);
            }

            Ok(Self { handle })
        }
    }

    impl Drop for HeldMutex {
        fn drop(&mut self) {
            if !self.handle.is_null() {
                unsafe {
                    CloseHandle(self.handle);
                }
            }
        }
    }

    pub(super) fn is_running() -> bool {
        is_running_named(MUTEX_NAME)
    }

    fn is_running_named(name: &str) -> bool {
        let wide_name = platform::wide(name);
        let handle = unsafe { OpenMutexW(SYNCHRONIZE, 0, wide_name.as_ptr()) };
        if handle.is_null() {
            return false;
        }
        unsafe {
            CloseHandle(handle);
        }
        true
    }

    pub(super) fn request_stop() -> Result<StopOutcome, StopError> {
        let executable = std::env::current_exe()
            .map_err(|error| StopError::new(format!("無法取得執行檔路徑: {error}")))?;
        let image_name = executable
            .file_name()
            .ok_or_else(|| StopError::new("無法取得執行檔名稱"))?
            .to_string_lossy()
            .into_owned();

        // 以影像名稱篩選，但排除自己（--stop 程序本身也是同名的執行檔）。
        let image_filter = format!("IMAGENAME eq {image_name}");
        let self_filter = format!("PID ne {}", std::process::id());

        let output = platform::command("taskkill")
            .args(["/F", "/FI", &image_filter, "/FI", &self_filter])
            .output()
            .map_err(|error| StopError::new(format!("無法執行 taskkill: {error}")))?;

        match output.status.code() {
            Some(0) => Ok(StopOutcome::Signaled),
            // taskkill 在找不到符合條件的程序時回傳 128。
            Some(128) => Ok(StopOutcome::NotRunning),
            Some(code) => Err(StopError::new(format!("taskkill 執行失敗 (結束碼 {code})"))),
            None => Err(StopError::new("taskkill 被系統終止")),
        }
    }

    #[cfg(test)]
    mod tests {
        use std::sync::atomic::{AtomicU64, Ordering};

        use super::*;

        static COUNTER: AtomicU64 = AtomicU64::new(0);

        fn unique_name() -> String {
            let id = COUNTER.fetch_add(1, Ordering::Relaxed);
            format!(r"Local\pc_onoff-test-{}-{id}", std::process::id())
        }

        #[test]
        fn only_one_guard_can_hold_the_mutex() {
            let name = unique_name();
            let first = HeldMutex::acquire_named(&name).expect("第一次取得互斥鎖");
            assert_eq!(
                HeldMutex::acquire_named(&name).err(),
                Some(InstanceError::AlreadyRunning),
                "第二次取得必須回報已在執行"
            );

            drop(first);
            assert!(
                HeldMutex::acquire_named(&name).is_ok(),
                "釋放後應可再次取得"
            );
        }

        #[test]
        fn reports_whether_an_instance_is_running() {
            let name = unique_name();
            assert!(!is_running_named(&name), "尚未建立時應回報未執行");

            let guard = HeldMutex::acquire_named(&name).expect("取得互斥鎖");
            assert!(is_running_named(&name), "持有鎖時應回報執行中");

            drop(guard);
            assert!(!is_running_named(&name), "釋放後應回報未執行");
        }
    }
}

#[cfg(not(windows))]
mod unix {
    use std::fs::{self, OpenOptions};
    use std::io::{ErrorKind, Write};
    use std::path::{Path, PathBuf};

    use super::{InstanceError, StopError, StopOutcome};
    use crate::platform;

    pub(super) struct LockFile {
        path: PathBuf,
    }

    impl LockFile {
        pub(super) fn acquire(path: &Path) -> Result<Self, InstanceError> {
            if let Some(parent) = path.parent() {
                fs::create_dir_all(parent).map_err(|error| {
                    InstanceError::unavailable(format!(
                        "無法建立鎖定檔目錄 {}: {error}",
                        parent.display()
                    ))
                })?;
            }

            for _ in 0..2 {
                match OpenOptions::new().write(true).create_new(true).open(path) {
                    Ok(mut file) => {
                        let _ = writeln!(file, "{}", std::process::id());
                        return Ok(Self {
                            path: path.to_path_buf(),
                        });
                    }
                    Err(error) if error.kind() == ErrorKind::AlreadyExists => {
                        if read_pid(path).is_some_and(process_alive) {
                            return Err(InstanceError::AlreadyRunning);
                        }
                        // 上次異常結束留下的鎖定檔。
                        let _ = fs::remove_file(path);
                    }
                    Err(error) => {
                        return Err(InstanceError::unavailable(format!(
                            "無法建立鎖定檔 {}: {error}",
                            path.display()
                        )));
                    }
                }
            }

            Err(InstanceError::unavailable(format!(
                "無法建立鎖定檔 {}",
                path.display()
            )))
        }
    }

    impl Drop for LockFile {
        fn drop(&mut self) {
            let _ = fs::remove_file(&self.path);
        }
    }

    pub(super) fn is_running(path: &Path) -> bool {
        read_pid(path).is_some_and(process_alive)
    }

    pub(super) fn request_stop(path: &Path) -> Result<StopOutcome, StopError> {
        let Some(pid) = read_pid(path) else {
            return Ok(StopOutcome::NotRunning);
        };

        if !process_alive(pid) {
            let _ = fs::remove_file(path);
            return Ok(StopOutcome::NotRunning);
        }

        let output = platform::command("kill")
            .args(["-TERM", &pid.to_string()])
            .output()
            .map_err(|error| StopError::new(format!("無法傳送停止訊號: {error}")))?;

        if output.status.success() {
            Ok(StopOutcome::Signaled)
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stderr = stderr.trim();
            if stderr.is_empty() {
                Err(StopError::new(format!("無法傳送 TERM 訊號給 PID {pid}")))
            } else {
                Err(StopError::new(format!(
                    "無法傳送 TERM 訊號給 PID {pid}: {stderr}"
                )))
            }
        }
    }

    fn read_pid(path: &Path) -> Option<u32> {
        fs::read_to_string(path).ok()?.trim().parse().ok()
    }

    fn process_alive(pid: u32) -> bool {
        platform::command("kill")
            .args(["-0", &pid.to_string()])
            .output()
            .map(|output| output.status.success())
            .unwrap_or(false)
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use crate::test_support::TempDir;

        #[test]
        fn lock_file_enforces_single_instance_and_cleans_up() {
            let dir = TempDir::new("instance");
            let path = dir.path().join("pc_onoff.lock");

            let first = LockFile::acquire(&path).expect("第一次取得鎖定檔");
            assert_eq!(
                LockFile::acquire(&path).err(),
                Some(InstanceError::AlreadyRunning),
                "第二次取得必須回報已在執行"
            );

            drop(first);
            assert!(!path.exists(), "離開作用域後應刪除鎖定檔");
            assert!(LockFile::acquire(&path).is_ok(), "應可重新取得");
        }

        #[test]
        fn stale_lock_file_is_replaced() {
            let dir = TempDir::new("instance-stale");
            let path = dir.path().join("pc_onoff.lock");
            fs::write(&path, "999999999").expect("建立過期鎖定檔");

            assert!(
                LockFile::acquire(&path).is_ok(),
                "PID 不存在時應視為過期鎖定"
            );
        }

        #[test]
        fn reports_whether_an_instance_is_running() {
            let dir = TempDir::new("instance-running");
            let path = dir.path().join("pc_onoff.lock");
            assert!(!is_running(&path), "沒有鎖定檔時應回報未執行");

            let guard = LockFile::acquire(&path).expect("取得鎖定檔");
            assert!(is_running(&path), "鎖定檔指向存活 PID 時應回報執行中");

            drop(guard);
            assert!(!is_running(&path), "釋放後應回報未執行");
        }
    }
}
