//! Single-instance daemon protection and background service shutdown.
//!
//! Windows uses named mutexes (`CreateMutexW`); Unix uses lock files with PID checks.
//! Callers interact via [`SingleInstance::acquire`], [`request_stop`], and result types.

use std::fmt;

#[cfg(not(windows))]
use crate::paths;

#[cfg(not(windows))]
const LOCK_FILE_NAME: &str = "pc_onoff.lock";

/// Reasons why acquiring an instance lock failed.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InstanceError {
    /// Another daemon is already holding the instance lock.
    AlreadyRunning,
    /// Cannot create mutex or lock file (permissions, disk error, etc.).
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
            Self::AlreadyRunning => f.write_str("another pc_onoff instance is already running"),
            Self::Unavailable { reason } => f.write_str(reason),
        }
    }
}

impl std::error::Error for InstanceError {}

/// Result of requesting a background service to stop.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StopOutcome {
    /// Stop signal has been sent.
    Signaled,
    /// No running service was found.
    NotRunning,
}

/// Errors when stopping the service (e.g. system command failed).
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

/// Holds the instance lock; automatically released when dropped.
pub struct SingleInstance {
    #[cfg(windows)]
    _guard: windows::HeldMutex,
    #[cfg(not(windows))]
    _guard: unix::LockFile,
}

impl SingleInstance {
    /// Attempt to acquire the instance lock.
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

/// Request running background service to stop.
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

/// Check if a daemon instance is currently holding the lock.
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
                return Err(InstanceError::unavailable("cannot create named mutex"));
            }

            // Must read GetLastError immediately after CreateMutexW without calling other APIs.
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
            .map_err(|error| StopError::new(format!("cannot get executable path: {error}")))?;
        let image_name = executable
            .file_name()
            .ok_or_else(|| StopError::new("cannot get executable name"))?
            .to_string_lossy()
            .into_owned();

        // Filter by image name while excluding current process (--stop process itself has same executable name).
        let image_filter = format!("IMAGENAME eq {image_name}");
        let self_filter = format!("PID ne {}", std::process::id());

        let output = platform::command("taskkill")
            .args(["/F", "/FI", &image_filter, "/FI", &self_filter])
            .output()
            .map_err(|error| StopError::new(format!("cannot execute taskkill: {error}")))?;

        match output.status.code() {
            Some(0) => Ok(StopOutcome::Signaled),
            // taskkill returns 128 when no matching processes were found.
            Some(128) => Ok(StopOutcome::NotRunning),
            Some(code) => Err(StopError::new(format!(
                "taskkill execution failed (exit code {code})"
            ))),
            None => Err(StopError::new("taskkill terminated by system")),
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
            let first = HeldMutex::acquire_named(&name).expect("first mutex acquire");
            assert_eq!(
                HeldMutex::acquire_named(&name).err(),
                Some(InstanceError::AlreadyRunning),
                "second acquire must report already running"
            );

            drop(first);
            assert!(
                HeldMutex::acquire_named(&name).is_ok(),
                "should acquire again after drop"
            );
        }

        #[test]
        fn reports_whether_an_instance_is_running() {
            let name = unique_name();
            assert!(
                !is_running_named(&name),
                "should report not running before creation"
            );

            let guard = HeldMutex::acquire_named(&name).expect("acquire mutex");
            assert!(
                is_running_named(&name),
                "should report running while holding lock"
            );

            drop(guard);
            assert!(
                !is_running_named(&name),
                "should report not running after drop"
            );
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
                        "cannot create lockfile directory {}: {error}",
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
                        // Stale lockfile from previous crash.
                        let _ = fs::remove_file(path);
                    }
                    Err(error) => {
                        return Err(InstanceError::unavailable(format!(
                            "cannot create lockfile {}: {error}",
                            path.display()
                        )));
                    }
                }
            }

            Err(InstanceError::unavailable(format!(
                "cannot create lockfile {}",
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
            .map_err(|error| StopError::new(format!("cannot send stop signal: {error}")))?;

        if output.status.success() {
            Ok(StopOutcome::Signaled)
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stderr = stderr.trim();
            if stderr.is_empty() {
                Err(StopError::new(format!(
                    "cannot send TERM signal to PID {pid}"
                )))
            } else {
                Err(StopError::new(format!(
                    "cannot send TERM signal to PID {pid}: {stderr}"
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

            let first = LockFile::acquire(&path).expect("first lockfile acquire");
            assert_eq!(
                LockFile::acquire(&path).err(),
                Some(InstanceError::AlreadyRunning),
                "second acquire must report already running"
            );

            drop(first);
            assert!(
                !path.exists(),
                "lockfile should be deleted after guard drop"
            );
            assert!(
                LockFile::acquire(&path).is_ok(),
                "should acquire again after drop"
            );
        }

        #[test]
        fn stale_lock_file_is_replaced() {
            let dir = TempDir::new("instance-stale");
            let path = dir.path().join("pc_onoff.lock");
            fs::write(&path, "999999999").expect("create stale lockfile");

            assert!(
                LockFile::acquire(&path).is_ok(),
                "non-existent PID should be treated as stale lock"
            );
        }

        #[test]
        fn reports_whether_an_instance_is_running() {
            let dir = TempDir::new("instance-running");
            let path = dir.path().join("pc_onoff.lock");
            assert!(
                !is_running(&path),
                "should report not running when no lockfile exists"
            );

            let guard = LockFile::acquire(&path).expect("acquire lockfile");
            assert!(
                is_running(&path),
                "should report running when lockfile points to alive PID"
            );

            drop(guard);
            assert!(
                !is_running(&path),
                "should report not running after guard drop"
            );
        }
    }
}
