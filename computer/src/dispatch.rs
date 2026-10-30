//! 深模組：將一個原始指令轉換為實際的電源操作。
//!
//! 呼叫者只需要知道 [`Dispatcher::dispatch`]；語法解析、別名、錯誤分類與
//! 電源控制器呼叫都藏在介面之後。這個模組不寫日誌、不碰 socket，
//! 只回傳結構化結果，讓每個傳輸適配器自行決定如何呈現。

use std::fmt;

use crate::action::{ParseError, PowerAction};
use crate::power::{PowerController, PowerError};

/// `dispatch` 失敗的兩種情形：指令被拒絕，或動作執行失敗。
#[derive(Debug, PartialEq, Eq)]
pub enum DispatchError {
    /// 語法無法辨識，沒有執行任何動作。
    Rejected(ParseError),
    /// 語法正確，但系統電源操作失敗。
    Execution {
        action: PowerAction,
        failure: PowerError,
    },
}

impl fmt::Display for DispatchError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Rejected(error) => error.fmt(f),
            Self::Execution { action, failure } => {
                write!(f, "{}執行失敗: {failure}", action.label())
            }
        }
    }
}

impl std::error::Error for DispatchError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Rejected(error) => Some(error),
            Self::Execution { failure, .. } => Some(failure),
        }
    }
}

impl From<ParseError> for DispatchError {
    fn from(error: ParseError) -> Self {
        Self::Rejected(error)
    }
}

/// 指令分派器。
pub struct Dispatcher<P: PowerController> {
    power: P,
}

impl<P: PowerController> Dispatcher<P> {
    pub fn new(power: P) -> Self {
        Self { power }
    }

    /// 解析並執行 `raw`，回傳實際執行的動作。
    pub fn dispatch(&self, raw: &str) -> Result<PowerAction, DispatchError> {
        let action = PowerAction::parse(raw)?;
        self.power
            .execute(action)
            .map_err(|failure| DispatchError::Execution { action, failure })?;
        Ok(action)
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use super::*;

    #[derive(Default)]
    struct FakePower {
        executed: Mutex<Vec<PowerAction>>,
        fail: bool,
    }

    impl FakePower {
        fn failing() -> Self {
            Self {
                executed: Mutex::new(Vec::new()),
                fail: true,
            }
        }

        fn executed(&self) -> Vec<PowerAction> {
            self.executed.lock().expect("lock").clone()
        }
    }

    impl PowerController for FakePower {
        fn execute(&self, action: PowerAction) -> Result<(), PowerError> {
            self.executed.lock().expect("lock").push(action);
            if self.fail {
                Err(PowerError::new("模擬失敗"))
            } else {
                Ok(())
            }
        }
    }

    #[test]
    fn dispatches_each_action_to_the_controller() {
        let dispatcher = Dispatcher::new(FakePower::default());

        for action in PowerAction::ALL {
            assert_eq!(dispatcher.dispatch(action.keyword()), Ok(action));
        }

        assert_eq!(dispatcher.power.executed(), PowerAction::ALL);
    }

    #[test]
    fn strips_wire_format_before_dispatching() {
        let dispatcher = Dispatcher::new(FakePower::default());

        assert_eq!(
            dispatcher.dispatch("  SHUTDOWN:192.168.1.100 \r\n"),
            Ok(PowerAction::Shutdown)
        );
        assert_eq!(dispatcher.power.executed(), vec![PowerAction::Shutdown]);
    }

    #[test]
    fn rejected_commands_never_reach_the_controller() {
        let dispatcher = Dispatcher::new(FakePower::default());

        assert!(matches!(
            dispatcher.dispatch(""),
            Err(DispatchError::Rejected(ParseError::Empty))
        ));
        assert!(matches!(
            dispatcher.dispatch("explode"),
            Err(DispatchError::Rejected(ParseError::Unknown { .. }))
        ));
        assert!(dispatcher.power.executed().is_empty());
    }

    #[test]
    fn execution_failures_name_the_action() {
        let dispatcher = Dispatcher::new(FakePower::failing());

        match dispatcher.dispatch("sleep") {
            Err(DispatchError::Execution { action, failure }) => {
                assert_eq!(action, PowerAction::Sleep);
                assert_eq!(failure.to_string(), "模擬失敗");
            }
            other => panic!("預期執行失敗，實際得到 {other:?}"),
        }
        assert_eq!(dispatcher.power.executed(), vec![PowerAction::Sleep]);
    }

    #[test]
    fn display_messages_are_human_readable() {
        let rejected = DispatchError::Rejected(ParseError::Unknown {
            received: "explode".to_owned(),
        });
        assert_eq!(rejected.to_string(), "未知指令 'explode'");

        let failed = DispatchError::Execution {
            action: PowerAction::Hibernate,
            failure: PowerError::new("沒有休眠檔"),
        };
        assert_eq!(failed.to_string(), "休眠執行失敗: 沒有休眠檔");
    }
}
