//! Deep module: translates a raw command into actual system power execution.
//!
//! Callers only need to know [`Dispatcher::dispatch`]; syntax parsing, aliases,
//! error categorization, and power controller invocations are encapsulated behind the interface.
//! This module does not write logs or touch sockets; it returns structured results so each
//! transport adapter can present them appropriately.

use std::fmt;

use crate::action::{ParseError, PowerAction};
use crate::power::{PowerController, PowerError};

/// Two scenarios of `dispatch` failure: syntax rejected, or execution failed.
#[derive(Debug, PartialEq, Eq)]
pub enum DispatchError {
    /// Syntax was not recognized; no action was executed.
    Rejected(ParseError),
    /// Syntax is valid, but the system power execution failed.
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
                write!(f, "failed to execute {}: {failure}", action.label())
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

/// Command dispatcher.
pub struct Dispatcher<P: PowerController> {
    power: P,
}

impl<P: PowerController> Dispatcher<P> {
    pub fn new(power: P) -> Self {
        Self { power }
    }

    /// Parse and execute `raw`, returning the action that was performed.
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
                Err(PowerError::new("simulated failure"))
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
                assert_eq!(failure.to_string(), "simulated failure");
            }
            other => panic!("expected execution failure, got {other:?}"),
        }
        assert_eq!(dispatcher.power.executed(), vec![PowerAction::Sleep]);
    }

    #[test]
    fn display_messages_are_human_readable() {
        let rejected = DispatchError::Rejected(ParseError::Unknown {
            received: "explode".to_owned(),
        });
        assert_eq!(rejected.to_string(), "unknown command 'explode'");

        let failed = DispatchError::Execution {
            action: PowerAction::Hibernate,
            failure: PowerError::new("no hibernation file"),
        };
        assert_eq!(
            failed.to_string(),
            "failed to execute Hibernate: no hibernation file"
        );
    }
}
