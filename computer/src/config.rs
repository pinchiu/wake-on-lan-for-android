//! `config.ini` 的解析與持久化。
//!
//! 解析器是純函式（[`AppConfig::parse`]），可以在不碰檔案系統的情況下測試；
//! 檔案存取則由 [`AppConfig::load`]／[`AppConfig::save`] 負責，寫入採
//! 「暫存檔 + 更名」以避免停電或當機造成設定檔半截。

use std::fmt;
use std::fs;
use std::path::{Path, PathBuf};

use crate::DEFAULT_UDP_PORT;

const CONFIG_HEADER: &str = "\
# Wake-on-LAN 電腦端電源控制設定
# udp_port: UDP 監聽連接埠 (預設 9877)
# autostart: 是否隨系統登入自動啟動 (true / false)
";

/// 守護程式的設定值。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AppConfig {
    pub udp_port: u16,
    pub autostart: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            udp_port: DEFAULT_UDP_PORT,
            autostart: true,
        }
    }
}

impl AppConfig {
    /// 從 INI 文字解析設定。
    ///
    /// 未知的鍵、註解、空行與格式錯誤的數值都會被忽略，保留預設值，
    /// 因此舊版設定檔（例如含 `start_minimized`）仍可安全載入。
    pub fn parse(content: &str) -> Self {
        let mut config = Self::default();

        for line in content.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with(';') {
                continue;
            }

            let Some((key, value)) = line.split_once('=') else {
                continue;
            };

            match key.trim().to_ascii_lowercase().as_str() {
                "port" | "udp_port" => {
                    if let Ok(port) = value.trim().parse::<u16>()
                        && port != 0
                    {
                        config.udp_port = port;
                    }
                }
                "autostart" => config.autostart = parse_bool(value.trim()),
                _ => {}
            }
        }

        config
    }

    /// 載入設定；檔案不存在或無法讀取時回傳預設值（不會寫入檔案）。
    pub fn load(path: &Path) -> Self {
        match fs::read_to_string(path) {
            Ok(content) => Self::parse(&content),
            Err(_) => Self::default(),
        }
    }

    /// 以原子方式寫入設定檔。
    pub fn save(&self, path: &Path) -> Result<(), ConfigError> {
        if let Some(parent) = path.parent()
            && !parent.as_os_str().is_empty()
        {
            fs::create_dir_all(parent).map_err(|e| {
                ConfigError::new(format!("無法建立設定目錄 {}: {e}", parent.display()))
            })?;
        }

        let temporary = temporary_path(path);
        fs::write(&temporary, self.render()).map_err(|e| {
            ConfigError::new(format!("無法寫入暫存設定檔 {}: {e}", temporary.display()))
        })?;

        fs::rename(&temporary, path).map_err(|e| {
            let _ = fs::remove_file(&temporary);
            ConfigError::new(format!("無法更新設定檔 {}: {e}", path.display()))
        })
    }

    /// 產生設定檔內容。
    pub fn render(&self) -> String {
        format!(
            "{CONFIG_HEADER}udp_port={}\nautostart={}\n",
            self.udp_port, self.autostart
        )
    }
}

fn parse_bool(value: &str) -> bool {
    matches!(
        value.to_ascii_lowercase().as_str(),
        "true" | "1" | "yes" | "on"
    )
}

fn temporary_path(path: &Path) -> PathBuf {
    let mut name = path.file_name().unwrap_or_default().to_os_string();
    name.push(format!(".{}.tmp", std::process::id()));
    path.with_file_name(name)
}

/// 設定檔存取失敗。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConfigError {
    message: String,
}

impl ConfigError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for ConfigError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for ConfigError {}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::TempDir;

    #[test]
    fn defaults_match_the_wire_protocol() {
        let config = AppConfig::default();
        assert_eq!(config.udp_port, DEFAULT_UDP_PORT);
        assert!(config.autostart);
    }

    #[test]
    fn parses_both_port_key_names() {
        assert_eq!(AppConfig::parse("udp_port=9000").udp_port, 9000);
        assert_eq!(AppConfig::parse("port=9001").udp_port, 9001);
    }

    #[test]
    fn ignores_invalid_values_comments_and_unknown_keys() {
        let content = "\
# comment
; another comment
start_minimized=true
udp_port=0
port=not-a-number
autostart=maybe
";
        let config = AppConfig::parse(content);
        assert_eq!(config.udp_port, DEFAULT_UDP_PORT);
        assert!(!config.autostart);
    }

    #[test]
    fn parses_boolean_spellings() {
        for value in ["true", "TRUE", "1", "yes", "on", "On"] {
            assert!(AppConfig::parse(&format!("autostart={value}")).autostart);
        }
        for value in ["false", "FALSE", "0", "no", "off", ""] {
            assert!(!AppConfig::parse(&format!("autostart={value}")).autostart);
        }
    }

    #[test]
    fn tolerates_crlf_and_extra_whitespace() {
        let config = AppConfig::parse("  udp_port = 8123 \r\n autostart = false \r\n");
        assert_eq!(config.udp_port, 8123);
        assert!(!config.autostart);
    }

    #[test]
    fn load_returns_defaults_without_creating_a_file() {
        let dir = TempDir::new("config-missing");
        let path = dir.path().join("config.ini");

        assert_eq!(AppConfig::load(&path), AppConfig::default());
        assert!(!path.exists(), "載入不應有建立檔案的副作用");
    }

    #[test]
    fn save_and_load_round_trip() {
        let dir = TempDir::new("config-roundtrip");
        let path = dir.path().join("config.ini");
        let config = AppConfig {
            udp_port: 9123,
            autostart: false,
        };

        config.save(&path).expect("儲存設定");
        assert_eq!(AppConfig::load(&path), config);

        let leftovers: Vec<_> = fs::read_dir(dir.path())
            .expect("讀取設定目錄")
            .filter_map(|entry| entry.ok())
            .filter(|entry| entry.file_name().to_string_lossy().contains(".tmp"))
            .collect();
        assert!(leftovers.is_empty(), "暫存檔應在更名後消失");
    }

    #[test]
    fn save_overwrites_existing_file() {
        let dir = TempDir::new("config-overwrite");
        let path = dir.path().join("config.ini");
        fs::write(&path, "udp_port=1234\nautostart=false\n").expect("寫入舊設定");

        AppConfig {
            udp_port: 4321,
            autostart: true,
        }
        .save(&path)
        .expect("覆寫設定");

        let reloaded = AppConfig::load(&path);
        assert_eq!(reloaded.udp_port, 4321);
        assert!(reloaded.autostart);
    }

    #[test]
    fn render_is_parseable_by_our_own_parser() {
        let config = AppConfig {
            udp_port: 9877,
            autostart: true,
        };
        assert_eq!(AppConfig::parse(&config.render()), config);
    }
}
