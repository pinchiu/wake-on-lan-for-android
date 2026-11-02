//! Remote power command syntax parsing.
//!
//! Protocol boundary between the daemon and Android clients: a `&str` comes in,
//! a validated [`PowerAction`] comes out. All fault tolerance for casing, whitespace,
//! and delimiters is centralized here, so transport layers (UDP, future MQTT/Webhooks)
//! don't need to re-implement parsing logic.

use std::fmt;

/// Power actions supported by the daemon.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum PowerAction {
    Shutdown,
    Reboot,
    Sleep,
    Hibernate,
    Lock,
}

impl PowerAction {
    /// All supported actions, for documentation or future UI enumeration.
    pub const ALL: [Self; 5] = [
        Self::Shutdown,
        Self::Reboot,
        Self::Sleep,
        Self::Hibernate,
        Self::Lock,
    ];

    /// Standard protocol keyword (lowercase).
    pub const fn keyword(self) -> &'static str {
        match self {
            Self::Shutdown => "shutdown",
            Self::Reboot => "reboot",
            Self::Sleep => "sleep",
            Self::Hibernate => "hibernate",
            Self::Lock => "lock",
        }
    }

    /// Human-readable label used in logging and notifications.
    pub const fn label(self) -> &'static str {
        match self {
            Self::Shutdown => "Shutdown",
            Self::Reboot => "Reboot",
            Self::Sleep => "Sleep",
            Self::Hibernate => "Hibernate",
            Self::Lock => "Lock Screen",
        }
    }

    /// Parse a UDP packet string into an action.
    ///
    /// Accepts:
    /// * Leading/trailing whitespace (`"  shutdown  "`)
    /// * Any case (`"ShUtDoWn"`)
    /// * Target info after `:` or `,` sent by Android clients, such as
    ///   `"SHUTDOWN:192.168.1.100"` (target info is safely ignored)
    /// * Common aliases: `poweroff`, `restart`, `suspend`, `standby`
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

/// Reasons why a command failed to parse.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    /// Packet was empty or contained only whitespace.
    Empty,
    /// Verb is not in the supported action list.
    Unknown { received: String },
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Empty => f.write_str("received empty command"),
            Self::Unknown { received } => write!(f, "unknown command '{received}'"),
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
        assert_eq!(PowerAction::Lock.label(), "Lock Screen");
    }
}
