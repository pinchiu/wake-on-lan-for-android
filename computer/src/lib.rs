//! Wake-on-LAN 電腦端常駐守護程式。
//!
//! 模組依賴方向（由內而外）：
//!
//! ```text
//! action (指令語法) ──► dispatch (深模組：解析 + 執行 + 結構化結果)
//!        ▲                    ▲
//!        │                    │
//!     power (系統電源 seam)   server (UDP 傳輸適配器)
//!                                 ▲
//!                                 │
//!                          cli (命令列 / 生命週期)
//! ```
//!
//! `dispatch` 只依賴 [`power::PowerController`] 介面，不直接呼叫作業系統，
//! 因此可用假實作在單元測試中完整驗證，不需真的關機或重啟。

pub mod action;
pub mod autostart;
pub mod cli;
pub mod config;
pub mod dispatch;
pub mod instance;
pub mod logging;
pub mod paths;
pub mod platform;
pub mod power;
pub mod server;

/// 應用程式名稱，同時作為資料目錄與互斥鎖名稱的一部分。
pub const APP_NAME: &str = "pc_onoff";

/// 預設 UDP 監聽連接埠（與 Android 端協定一致）。
pub const DEFAULT_UDP_PORT: u16 = 9877;

/// 監聽所有網路介面。
pub const LISTEN_IP: &str = "0.0.0.0";

/// 單一 UDP 指令封包的最大長度。
pub const MAX_COMMAND_BYTES: usize = 1024;

#[cfg(test)]
pub(crate) mod test_support {
    use std::path::{Path, PathBuf};
    use std::sync::atomic::{AtomicU64, Ordering};

    static COUNTER: AtomicU64 = AtomicU64::new(0);

    /// 測試用暫存目錄，離開作用域時自動刪除。
    pub struct TempDir {
        path: PathBuf,
    }

    impl TempDir {
        pub fn new(label: &str) -> Self {
            let id = COUNTER.fetch_add(1, Ordering::Relaxed);
            let path = std::env::temp_dir().join(format!(
                "pc_onoff-test-{}-{}-{}",
                std::process::id(),
                label,
                id
            ));
            std::fs::create_dir_all(&path).expect("建立測試暫存目錄失敗");
            Self { path }
        }

        pub fn path(&self) -> &Path {
            &self.path
        }
    }

    impl Drop for TempDir {
        fn drop(&mut self) {
            let _ = std::fs::remove_dir_all(&self.path);
        }
    }
}
