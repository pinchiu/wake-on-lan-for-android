//! 遠端電源指令的語法解析。
//!
//! 這是守護程式與 Android 用戶端之間的協定邊界：一份 `&str` 進來，
//! 一個經過驗證的 [`PowerAction`] 出去。所有大小寫、空白與分隔符的
//! 容錯都集中在這裡，傳輸通道（UDP、未來的 MQTT／Webhook）無需重複實作。

use std::fmt;

/// 守護程式支援的電源動作。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum PowerAction {
    Shutdown,
    Reboot,
    Sleep,
    Hibernate,
    Lock,
}

impl PowerAction {
    /// 所有支援的動作，供說明文件或未來 UI 列舉使用。
    pub const ALL: [Self; 5] = [
        Self::Shutdown,
        Self::Reboot,
        Self::Sleep,
        Self::Hibernate,
        Self::Lock,
    ];

    /// 協定上的標準關鍵字（小寫）。
    pub const fn keyword(self) -> &'static str {
        match self {
            Self::Shutdown => "shutdown",
            Self::Reboot => "reboot",
            Self::Sleep => "sleep",
            Self::Hibernate => "hibernate",
            Self::Lock => "lock",
        }
    }

    /// 日誌與訊息使用的中文名稱。
    pub const fn label(self) -> &'static str {
        match self {
            Self::Shutdown => "關機",
            Self::Reboot => "重啟",
            Self::Sleep => "睡眠",
            Self::Hibernate => "休眠",
            Self::Lock => "鎖定螢幕",
        }
    }

    /// 將一個 UDP 封包內容解析為動作。
    ///
    /// 容許：
    /// * 前後空白（`"  shutdown  "`）
    /// * 任意大小寫（`"ShUtDoWn"`）
    /// * `:` 或 `,` 之後的目標資訊，例如 Android 端送出的
    ///   `"SHUTDOWN:192.168.1.100"`（本程式只收已送達的封包，目標資訊會被忽略）
    /// * 常見別名：`poweroff`、`restart`、`suspend`、`standby`
    pub fn parse(raw: &str) -> Result<Self, ParseError> {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            return Err(ParseError::Empty);
        }

        let keyword = trimmed.split([':', ',']).next().unwrap_or_default().trim();

        if keyword.is_empty() {
            return Err(ParseError::Unknown {
                received: trimmed.to_owned(),
            });
        }

        match keyword.to_ascii_lowercase().as_str() {
            "shutdown" | "poweroff" => Ok(Self::Shutdown),
            "reboot" | "restart" => Ok(Self::Reboot),
            "sleep" | "suspend" | "standby" => Ok(Self::Sleep),
            "hibernate" => Ok(Self::Hibernate),
            "lock" => Ok(Self::Lock),
            _ => Err(ParseError::Unknown {
                received: keyword.to_owned(),
            }),
        }
    }
}

impl fmt::Display for PowerAction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.keyword())
    }
}

/// 指令無法被辨識的原因。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    /// 封包內容為空或只有空白。
    Empty,
    /// 動詞不在支援清單中。
    Unknown { received: String },
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Empty => f.write_str("收到空白指令"),
            Self::Unknown { received } => write!(f, "未知指令 '{received}'"),
        }
    }
}

impl std::error::Error for ParseError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_each_supported_action() {
        for action in PowerAction::ALL {
            assert_eq!(PowerAction::parse(action.keyword()), Ok(action));
        }
    }

    #[test]
    fn parsing_is_case_insensitive() {
        assert_eq!(PowerAction::parse("ShUtDoWn"), Ok(PowerAction::Shutdown));
        assert_eq!(PowerAction::parse("REBOOT"), Ok(PowerAction::Reboot));
    }

    #[test]
    fn ignores_target_payload_after_colon_or_comma() {
        assert_eq!(
            PowerAction::parse("shutdown:192.168.1.100"),
            Ok(PowerAction::Shutdown)
        );
        assert_eq!(
            PowerAction::parse("sleep,192.168.1.100"),
            Ok(PowerAction::Sleep)
        );
        assert_eq!(
            PowerAction::parse("reboot:fe80::1"),
            Ok(PowerAction::Reboot)
        );
    }

    #[test]
    fn ignores_surrounding_and_delimiter_whitespace() {
        assert_eq!(
            PowerAction::parse("  shutdown  "),
            Ok(PowerAction::Shutdown)
        );
        assert_eq!(
            PowerAction::parse(" sleep : 192.168.1.50 "),
            Ok(PowerAction::Sleep)
        );
        assert_eq!(
            PowerAction::parse(" reboot , 192.168.1.50 "),
            Ok(PowerAction::Reboot)
        );
    }

    #[test]
    fn accepts_common_aliases() {
        assert_eq!(PowerAction::parse("poweroff"), Ok(PowerAction::Shutdown));
        assert_eq!(PowerAction::parse("restart"), Ok(PowerAction::Reboot));
        assert_eq!(PowerAction::parse("suspend"), Ok(PowerAction::Sleep));
        assert_eq!(PowerAction::parse("standby"), Ok(PowerAction::Sleep));
    }

    #[test]
    fn rejects_empty_input() {
        assert_eq!(PowerAction::parse(""), Err(ParseError::Empty));
        assert_eq!(PowerAction::parse("   \r\n"), Err(ParseError::Empty));
        assert_eq!(
            PowerAction::parse(":"),
            Err(ParseError::Unknown {
                received: ":".to_owned()
            })
        );
    }

    #[test]
    fn rejects_unknown_commands_and_names_the_token() {
        assert_eq!(
            PowerAction::parse("explode:192.168.1.1"),
            Err(ParseError::Unknown {
                received: "explode".to_owned()
            })
        );
    }

    #[test]
    fn labels_and_keywords_are_stable() {
        assert_eq!(PowerAction::Sleep.to_string(), "sleep");
        assert_eq!(PowerAction::Lock.label(), "鎖定螢幕");
    }
}
