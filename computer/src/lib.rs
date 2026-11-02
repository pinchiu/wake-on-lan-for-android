//! Wake-on-LAN computer power control daemon.
//!
//! Module dependency direction (inside out):
//!
//! ```text
//! action (command syntax) ──► dispatch (deep module: parse + execute + structured result)
//!        ▲                          ▲
//!        │                          │
//!     power (system power seam)   server (UDP transport adapter)
//!                                       ▲
//!                                       │
//!                                cli (command line / lifecycle)
//! ```
//!
//! `dispatch` only depends on the [`power::PowerController`] interface and does not
//! directly invoke OS APIs. It can be fully verified in unit tests using mock implementations
//! without actually shutting down or rebooting the machine.

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

/// Application name, also used as part of data directory and mutex names.
pub const APP_NAME: &str = "pc_onoff";

/// Default UDP listening port (matches the Android client protocol).
pub const DEFAULT_UDP_PORT: u16 = 9877;

/// Listen on all network interfaces.
pub const LISTEN_IP: &str = "0.0.0.0";

/// Maximum length of a single UDP command packet.
pub const MAX_COMMAND_BYTES: usize = 1024;

#[cfg(test)]
pub(crate) mod test_support {
    use std::path::{Path, PathBuf};
    use std::sync::atomic::{AtomicU64, Ordering};

    static COUNTER: AtomicU64 = AtomicU64::new(0);

    /// Temporary directory for testing; automatically deleted when dropped.
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
            std::fs::create_dir_all(&path).expect("failed to create test temporary directory");
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
