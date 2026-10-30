//! 日誌輸出：主控台 + 檔案，附帶大小輪替與控制字元清理。
//!
//! 介面只有 [`Logger::record`]。檔案路徑、時間戳、輪替與執行緒同步都藏在
//! 實作裡；模組本身從不 panic，日誌失敗也不會影響電源服務。

use std::borrow::Cow;
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

/// 日誌檔超過此大小時輪替為 `*.1`。
pub const DEFAULT_MAX_LOG_BYTES: u64 = 5 * 1024 * 1024;

/// [`Logger`] 的可調整選項（測試會關閉主控台輸出並縮小上限）。
#[derive(Debug, Clone)]
pub struct LoggerOptions {
    pub path: Option<PathBuf>,
    pub max_bytes: u64,
    pub echo_stdout: bool,
}

impl Default for LoggerOptions {
    fn default() -> Self {
        Self {
            path: None,
            max_bytes: DEFAULT_MAX_LOG_BYTES,
            echo_stdout: true,
        }
    }
}

/// 執行緒安全的事件記錄器。
pub struct Logger {
    options: LoggerOptions,
    rotation_lock: Mutex<()>,
}

impl Logger {
    /// 建立寫入 `path` 的記錄器。
    pub fn new(path: PathBuf) -> Self {
        Self::with_options(LoggerOptions {
            path: Some(path),
            ..LoggerOptions::default()
        })
    }

    /// 不輸出任何內容的記錄器（測試或停用日誌時使用）。
    pub fn disabled() -> Self {
        Self::with_options(LoggerOptions {
            echo_stdout: false,
            ..LoggerOptions::default()
        })
    }

    pub fn with_options(options: LoggerOptions) -> Self {
        Self {
            options,
            rotation_lock: Mutex::new(()),
        }
    }

    pub fn path(&self) -> Option<&Path> {
        self.options.path.as_deref()
    }

    /// 記錄一筆事件。分類與訊息中的換行等控制字元會先被置換，
    /// 避免遠端封包偽造額外的日誌行。
    pub fn record(&self, category: &str, message: &str) {
        let line = format_line(&local_timestamp(), category, message);

        if self.options.echo_stdout {
            println!("{line}");
        }

        let Some(path) = self.options.path.as_deref() else {
            return;
        };

        let _guard = self
            .rotation_lock
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());

        if let Err(error) = append_line(path, &line, self.options.max_bytes)
            && self.options.echo_stdout
        {
            eprintln!("[pc_onoff] 無法寫入日誌 {}: {error}", path.display());
        }
    }
}

/// 產生一行日誌；時間戳由呼叫端提供，讓格式化本身可被測試。
fn format_line(timestamp: &str, category: &str, message: &str) -> String {
    format!(
        "[{}] [{}] {}",
        sanitize(timestamp),
        sanitize(category),
        sanitize(message)
    )
}

/// 將控制字元（含 `\r`、`\n`、NUL）替換為空白。
fn sanitize(input: &str) -> Cow<'_, str> {
    if input.chars().any(char::is_control) {
        Cow::Owned(
            input
                .chars()
                .map(|c| if c.is_control() { ' ' } else { c })
                .collect(),
        )
    } else {
        Cow::Borrowed(input)
    }
}

fn append_line(path: &Path, line: &str, max_bytes: u64) -> io::Result<()> {
    if let Some(parent) = path.parent()
        && !parent.as_os_str().is_empty()
    {
        fs::create_dir_all(parent)?;
    }

    rotate_if_needed(path, line.len() as u64 + 1, max_bytes)?;

    let mut file = OpenOptions::new().create(true).append(true).open(path)?;
    writeln!(file, "{line}")
}

fn rotate_if_needed(path: &Path, incoming_bytes: u64, max_bytes: u64) -> io::Result<()> {
    if max_bytes == 0 {
        return Ok(());
    }

    let current_bytes = match fs::metadata(path) {
        Ok(metadata) => metadata.len(),
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error),
    };

    if current_bytes == 0 || current_bytes + incoming_bytes <= max_bytes {
        return Ok(());
    }

    let rotated = rotated_path(path);
    let _ = fs::remove_file(&rotated);
    fs::rename(path, rotated)
}

fn rotated_path(path: &Path) -> PathBuf {
    let mut name = path.file_name().unwrap_or_default().to_os_string();
    name.push(".1");
    path.with_file_name(name)
}

struct CivilTime {
    year: u16,
    month: u16,
    day: u16,
    hour: u16,
    minute: u16,
    second: u16,
}

fn local_timestamp() -> String {
    match local_civil_time() {
        Some(time) => format!(
            "{:04}-{:02}-{:02} {:02}:{:02}:{:02}",
            time.year, time.month, time.day, time.hour, time.minute, time.second
        ),
        None => "未知時間".to_owned(),
    }
}

#[cfg(windows)]
fn local_civil_time() -> Option<CivilTime> {
    #[repr(C)]
    #[derive(Default)]
    #[allow(dead_code)] // 欄位順序必須符合 Win32 SYSTEMTIME 版面
    struct SystemTimeWin {
        year: u16,
        month: u16,
        day_of_week: u16,
        day: u16,
        hour: u16,
        minute: u16,
        second: u16,
        milliseconds: u16,
    }

    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn GetLocalTime(system_time: *mut SystemTimeWin);
    }

    let mut time = SystemTimeWin::default();
    unsafe { GetLocalTime(&mut time) };

    if time.year == 0 {
        return None;
    }

    Some(CivilTime {
        year: time.year,
        month: time.month,
        day: time.day,
        hour: time.hour,
        minute: time.minute,
        second: time.second,
    })
}

#[cfg(unix)]
fn local_civil_time() -> Option<CivilTime> {
    use std::os::raw::{c_char, c_int, c_long};

    // 與 glibc / musl / macOS 的 struct tm 版面一致；後兩個欄位是 BSD 擴充，
    // localtime_r 會實際寫入，因此必須保留空間。
    #[repr(C)]
    #[allow(dead_code)]
    struct CTime {
        sec: c_int,
        min: c_int,
        hour: c_int,
        mday: c_int,
        mon: c_int,
        year: c_int,
        wday: c_int,
        yday: c_int,
        isdst: c_int,
        gmtoff: c_long,
        zone: *const c_char,
    }

    unsafe extern "C" {
        fn time(now: *mut c_long) -> c_long;
        fn localtime_r(now: *const c_long, result: *mut CTime) -> *mut CTime;
    }

    let mut now: c_long = 0;
    let mut tm: CTime = unsafe { std::mem::zeroed() };

    let converted = unsafe {
        if time(&mut now) == -1 {
            false
        } else {
            !localtime_r(&now, &mut tm).is_null()
        }
    };

    if !converted || tm.year < 0 || tm.mon < 0 || tm.mday < 1 {
        return None;
    }

    Some(CivilTime {
        year: (tm.year + 1900) as u16,
        month: (tm.mon + 1) as u16,
        day: tm.mday as u16,
        hour: tm.hour as u16,
        minute: tm.min as u16,
        second: tm.sec as u16,
    })
}

#[cfg(not(any(windows, unix)))]
fn local_civil_time() -> Option<CivilTime> {
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::TempDir;

    #[test]
    fn formats_a_complete_line() {
        assert_eq!(
            format_line("2026-10-30 12:34:56", "網路接收", "指令: shutdown"),
            "[2026-10-30 12:34:56] [網路接收] 指令: shutdown"
        );
    }

    #[test]
    fn sanitizes_embedded_control_characters() {
        assert_eq!(sanitize("shut\ndown\r\0"), "shut down  ");
        assert_eq!(sanitize("正常字串"), "正常字串");
    }

    #[test]
    fn prevents_log_forgery_through_packet_payload() {
        let line = format_line(
            "2026-10-30 12:34:56",
            "警告",
            "未知指令 'x\n[2026-10-30 12:34:56] [執行指令] shutdown'",
        );
        assert_eq!(line.lines().count(), 1, "惡意封包不可產生第二行日誌");
    }

    #[test]
    fn appends_records_to_the_log_file() {
        let dir = TempDir::new("log-append");
        let path = dir.path().join("pc_onoff.log");
        let logger = Logger::with_options(LoggerOptions {
            path: Some(path.clone()),
            max_bytes: 4096,
            echo_stdout: false,
        });

        logger.record("服務啟動", "第一行");
        logger.record("執行指令", "第二行");

        let content = fs::read_to_string(&path).expect("讀取日誌");
        let lines: Vec<_> = content.lines().collect();
        assert_eq!(lines.len(), 2);
        assert!(lines[0].contains("[服務啟動] 第一行"));
        assert!(lines[1].contains("[執行指令] 第二行"));
        assert!(lines[0].starts_with('['));
    }

    #[test]
    fn creates_missing_parent_directories() {
        let dir = TempDir::new("log-nested");
        let path = dir.path().join("nested").join("pc_onoff.log");
        let logger = Logger::with_options(LoggerOptions {
            path: Some(path.clone()),
            max_bytes: 4096,
            echo_stdout: false,
        });

        logger.record("測試", "內容");
        assert!(path.exists());
    }

    #[test]
    fn rotates_the_log_when_it_grows_too_large() {
        let dir = TempDir::new("log-rotate");
        let path = dir.path().join("pc_onoff.log");
        let logger = Logger::with_options(LoggerOptions {
            path: Some(path.clone()),
            max_bytes: 100,
            echo_stdout: false,
        });

        let message = "x".repeat(80);
        logger.record("測試", &message);
        logger.record("測試", &message);

        let rotated = rotated_path(&path);
        assert!(rotated.exists(), "超過上限時應輪替為 .1");
        assert_eq!(
            fs::read_to_string(&path).expect("目前日誌").lines().count(),
            1
        );
        assert_eq!(
            fs::read_to_string(&rotated)
                .expect("輪替日誌")
                .lines()
                .count(),
            1
        );
    }

    #[test]
    fn disabled_logger_writes_nothing() {
        let logger = Logger::disabled();
        assert!(logger.path().is_none());
        logger.record("測試", "不應輸出到檔案");
    }

    #[test]
    fn timestamp_has_the_expected_shape() {
        let timestamp = local_timestamp();
        if timestamp != "未知時間" {
            assert_eq!(timestamp.len(), 19, "格式應為 YYYY-MM-DD HH:MM:SS");
            assert_eq!(&timestamp[4..5], "-");
            assert_eq!(&timestamp[10..11], " ");
        }
    }
}
