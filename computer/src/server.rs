//! UDP transport adapter: receives datagrams, hands them to [`Dispatcher`], and logs results.
//!
//! This module is the only part of the daemon aware of "UDP 9877"; protocol syntax is in
//! [`crate::action`], and power execution is in [`crate::power`]. If MQTT or HTTP channels
//! are added in the future, only a similarly lightweight adapter is needed.

use std::fmt;
use std::net::{SocketAddr, UdpSocket};
use std::thread;
use std::time::Duration;

use crate::dispatch::{DispatchError, Dispatcher};
use crate::logging::Logger;
use crate::power::PowerController;
use crate::{LISTEN_IP, MAX_COMMAND_BYTES};

const ERROR_BACKOFF: Duration = Duration::from_millis(250);

/// Server startup error.
#[derive(Debug)]
pub struct ServerError {
    message: String,
}

impl ServerError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for ServerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for ServerError {}

/// Bind UDP port and serve indefinitely until process terminates.
pub fn serve<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    port: u16,
    logger: &Logger,
) -> Result<(), ServerError> {
    let bind_address = format!("{LISTEN_IP}:{port}");
    let socket = UdpSocket::bind(&bind_address).map_err(|error| {
        ServerError::new(format!(
            "failed to bind UDP {bind_address}: {error} (port may already be in use)"
        ))
    })?;

    logger.record(
        "server_start",
        &format!("successfully bound UDP {bind_address}, listening for remote commands..."),
    );

    let mut buffer = [0u8; MAX_COMMAND_BYTES];
    loop {
        match socket.recv_from(&mut buffer) {
            Ok((size, source)) => handle_datagram(dispatcher, &buffer[..size], source, logger),
            Err(error) => {
                logger.record(
                    "network_error",
                    &format!("error receiving UDP data: {error}"),
                );
                thread::sleep(ERROR_BACKOFF);
            }
        }
    }
}

/// Handle a single UDP packet. Separated from loop for unit testability.
fn handle_datagram<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    payload: &[u8],
    source: SocketAddr,
    logger: &Logger,
) {
    let raw = String::from_utf8_lossy(payload);
    let command = raw.trim();

    logger.record(
        "network",
        &format!(
            "from {source} len: {} bytes, command: '{command}'",
            payload.len()
        ),
    );

    match dispatcher.dispatch(command) {
        Ok(action) => logger.record(
            "result",
            &format!(
                "source: {source} | {} executed successfully",
                action.label()
            ),
        ),
        Err(error) => {
            let category = match &error {
                DispatchError::Rejected(_) => "warning",
                DispatchError::Execution { .. } => "command_failed",
            };
            logger.record(category, &format!("source: {source} | {error}"));
        }
    }
}

/// Get local IPv4 address (for configuring in mobile app).
///
/// UDP `connect` does not actually send packets, allowing fast interface discovery even offline;
/// falls back to `127.0.0.1` on error.
pub fn local_ipv4() -> String {
    UdpSocket::bind("0.0.0.0:0")
        .and_then(|socket| {
            socket.connect("8.8.8.8:80")?;
            socket.local_addr()
        })
        .map(|address| address.ip().to_string())
        .unwrap_or_else(|_| "127.0.0.1".to_owned())
}

#[cfg(test)]
mod tests {
    use std::sync::{Arc, Mutex};

    use super::*;
    use crate::action::PowerAction;
    use crate::power::PowerError;

    #[derive(Default)]
    struct FakePower {
        executed: Arc<Mutex<Vec<PowerAction>>>,
    }

    impl FakePower {
        fn observed(&self) -> Arc<Mutex<Vec<PowerAction>>> {
            Arc::clone(&self.executed)
        }
    }

    impl PowerController for FakePower {
        fn execute(&self, action: PowerAction) -> Result<(), PowerError> {
            self.executed.lock().expect("lock").push(action);
            Ok(())
        }
    }

    fn source() -> SocketAddr {
        "192.168.1.50:54321".parse().expect("source address")
    }

    #[test]
    fn dispatches_valid_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(
            &dispatcher,
            "SHUTDOWN:192.168.1.100".as_bytes(),
            source(),
            &logger,
        );

        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Shutdown]);
    }

    #[test]
    fn ignores_empty_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, b"  \r\n", source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn ignores_unknown_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, b"self-destruct", source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn handles_invalid_utf8_without_panicking() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(&dispatcher, &[0xff, 0xfe, 0x00], source(), &logger);

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn local_ipv4_is_always_parseable() {
        let ip = local_ipv4();
        assert!(ip.parse::<std::net::IpAddr>().is_ok(), "got {ip}");
    }
}
