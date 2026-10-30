//! 作業系統的細微差異集中於此：建立子程序、取得寬字元字串、附加主控台。
//!
//! 這裡的介面刻意保持極小，讓其他模組不必到處散落 `#[cfg(windows)]`。

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

/// 建立子程序命令；在 Windows 上自動加上 `CREATE_NO_WINDOW`，
/// 確保背景守護程式不會因呼叫 `reg`、`shutdown`、`taskkill` 而閃出黑色視窗。
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

/// 若父行程有主控台，將輸出附加其上（方便從終端機執行 `--status` 等指令）。
///
/// GUI 子系統的程式不會自動取得 `stdout`，因此附加後還要把 `CONOUT$` 設為
/// 標準輸出／錯誤；若呼叫端已重導向輸出（例如 `> status.txt`），則保留原句柄。
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

/// 將字串轉為以 NUL 結尾的 UTF-16，供 Win32 `...W` 系列 API 使用。
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
