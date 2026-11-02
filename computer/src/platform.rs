//! Operating system nuances: subprocess spawning, wide strings, console attachment.
//!
//! The interface here is intentionally minimal so other modules don't need scattered `#[cfg(windows)]`.

use std::ffi::OsStr;
use std::process::Command;

#[cfg(windows)]
mod windows_ffi {
    use std::ffi::c_void;

    #[link(name = "kernel32")]
    unsafe extern "system" {
        pub fn AttachConsole(process_id: u32) -> i32;
        pub fn CreateFileW(
            file_name: *const u16,
            desired_access: u32,
            share_mode: u32,
            security_attributes: *mut c_void,
            creation_disposition: u32,
            flags_and_attributes: u32,
            template_file: *mut c_void,
        ) -> *mut c_void;
        pub fn GetStdHandle(std_handle: u32) -> *mut c_void;
        pub fn SetStdHandle(std_handle: u32, handle: *mut c_void) -> i32;
    }

    pub const ATTACH_PARENT_PROCESS: u32 = u32::MAX;
    pub const GENERIC_READ: u32 = 0x8000_0000;
    pub const GENERIC_WRITE: u32 = 0x4000_0000;
    pub const FILE_SHARE_READ: u32 = 0x0000_0001;
    pub const FILE_SHARE_WRITE: u32 = 0x0000_0002;
    pub const OPEN_EXISTING: u32 = 3;
    pub const STD_OUTPUT_HANDLE: u32 = 0xFFFF_FFF5; // -11
    pub const STD_ERROR_HANDLE: u32 = 0xFFFF_FFF4; // -12
    pub const INVALID_HANDLE_VALUE: *mut c_void = usize::MAX as *mut c_void;
}

/// Create a subprocess command; on Windows automatically adds `CREATE_NO_WINDOW`
/// to prevent console popups when invoking `reg`, `shutdown`, `taskkill`, etc.
pub fn command(program: impl AsRef<OsStr>) -> Command {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;

        let mut command = Command::new(program);
        command.creation_flags(CREATE_NO_WINDOW);
        command
    }
    #[cfg(not(windows))]
    {
        Command::new(program)
    }
}

/// If the parent process has a console, attach output to it (convenient for CLI commands like `--status`).
///
/// Windows GUI subsystem binaries don't automatically receive stdout/stderr, so `CONOUT$` is assigned
/// to standard output/error after attaching. If the caller redirected output (e.g. `> status.txt`), the handle is preserved.
#[cfg(windows)]
pub fn attach_console() {
    use std::ptr;

    unsafe {
        if windows_ffi::AttachConsole(windows_ffi::ATTACH_PARENT_PROCESS) == 0 {
            return;
        }

        let stdout = windows_ffi::GetStdHandle(windows_ffi::STD_OUTPUT_HANDLE);
        if !stdout.is_null() && stdout != windows_ffi::INVALID_HANDLE_VALUE {
            return;
        }

        let console_name = wide("CONOUT$");
        let console = windows_ffi::CreateFileW(
            console_name.as_ptr(),
            windows_ffi::GENERIC_READ | windows_ffi::GENERIC_WRITE,
            windows_ffi::FILE_SHARE_READ | windows_ffi::FILE_SHARE_WRITE,
            ptr::null_mut(),
            windows_ffi::OPEN_EXISTING,
            0,
            ptr::null_mut(),
        );

        if console == windows_ffi::INVALID_HANDLE_VALUE {
            return;
        }

        windows_ffi::SetStdHandle(windows_ffi::STD_OUTPUT_HANDLE, console);
        windows_ffi::SetStdHandle(windows_ffi::STD_ERROR_HANDLE, console);
    }
}

/// Convert a string to NUL-terminated UTF-16 for Win32 `...W` APIs.
#[cfg(windows)]
pub fn wide(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}

#[cfg(test)]
mod tests {
    #[cfg(windows)]
    #[test]
    fn wide_strings_are_nul_terminated() {
        let wide = super::wide("pc");
        assert_eq!(wide, vec![b'p' as u16, b'c' as u16, 0]);
    }
}
