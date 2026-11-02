//! `config.ini` parsing and persistence.
//!
//! The parser is a pure function ([`AppConfig::parse`]) testable without file system access.
//! File I/O is handled by [`AppConfig::load`] / [`AppConfig::save`], writing atomically
//! via temporary file + rename to avoid partial writes during power loss or crashes.

use std::fmt;
use std::fs;
use std::path::{Path, PathBuf};

use crate::DEFAULT_UDP_PORT;

const CONFIG_HEADER: &str = "\
# Wake-on-LAN Computer Power Control Configuration
# udp_port: UDP listening port (default 9877)
# autostart: Launch automatically on system login (true / false)
";

/// Daemon configuration values.
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
    /// Parse configuration from INI text.
    ///
    /// Unknown keys, comments, blank lines, and malformed values are safely ignored,
    /// preserving defaults so older configs (e.g. containing `start_minimized`) load cleanly.
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

    /// Load configuration; returns default values if file does not exist or cannot be read.
    pub fn load(path: &Path) -> Self {
        match fs::read_to_string(path) {
            Ok(content) => Self::parse(&content),
            Err(_) => Self::default(),
        }
    }

    /// Atomically save configuration file.
    pub fn save(&self, path: &Path) -> Result<(), ConfigError> {
        if let Some(parent) = path.parent()
            && !parent.as_os_str().is_empty()
        {
            fs::create_dir_all(parent).map_err(|e| {
                ConfigError::new(format!(
                    "cannot create configuration directory {}: {e}",
                    parent.display()
                ))
            })?;
        }

        let temporary = temporary_path(path);
        fs::write(&temporary, self.render()).map_err(|e| {
            ConfigError::new(format!(
                "cannot write temporary configuration file {}: {e}",
                temporary.display()
            ))
        })?;

        fs::rename(&temporary, path).map_err(|e| {
            let _ = fs::remove_file(&temporary);
            ConfigError::new(format!(
                "cannot update configuration file {}: {e}",
                path.display()
            ))
        })
    }

    /// Render configuration file content.
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

/// Configuration access failure.
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
        assert!(!path.exists(), "loading should not have side effects");
    }

    #[test]
    fn save_and_load_round_trip() {
        let dir = TempDir::new("config-roundtrip");
        let path = dir.path().join("config.ini");
        let config = AppConfig {
            udp_port: 9123,
            autostart: false,
        };

        config.save(&path).expect("save config");
        assert_eq!(AppConfig::load(&path), config);

        let leftovers: Vec<_> = fs::read_dir(dir.path())
            .expect("read config dir")
            .filter_map(|entry| entry.ok())
            .filter(|entry| entry.file_name().to_string_lossy().contains(".tmp"))
            .collect();
        assert!(
            leftovers.is_empty(),
            "temp files should disappear after rename"
        );
    }

    #[test]
    fn save_overwrites_existing_file() {
        let dir = TempDir::new("config-overwrite");
        let path = dir.path().join("config.ini");
        fs::write(&path, "udp_port=1234\nautostart=false\n").expect("write old config");

        AppConfig {
            udp_port: 4321,
            autostart: true,
        }
        .save(&path)
        .expect("overwrite config");

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
