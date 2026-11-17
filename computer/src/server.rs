//! UDP transport adapter: authenticates datagrams, hands the bare command to [`Dispatcher`],
//! and logs results.
//!
//! This module is the only part of the daemon aware of "UDP 9877"; protocol syntax is in
//! [`crate::action`], command authentication is in [`crate::auth`], and power execution is in
//! [`crate::power`]. If MQTT or HTTP channels are added in the future, only a similarly
//! lightweight adapter is needed.

use std::fmt;
use std::net::{SocketAddr, UdpSocket};
use std::thread;
use std::time::Duration;

use crate::auth::{self, ReplayGuard};
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
    secret: &str,
    logger: &Logger,
) -> Result<(), ServerError> {
    let bind_address = format!("{LISTEN_IP}:{port}");
    let socket = UdpSocket::bind(&bind_address).map_err(|error| {
        ServerError::new(format!(
            "failed to bind UDP {bind_address}: {error} (port may already be in use)"
        ))
    })?;

    if secret.trim().is_empty() {
        logger.record(
            "warning",
            "command secret is not configured; all remote commands will be rejected",
        );
    }
    logger.record(
        "server_start",
        &format!(
            "successfully bound UDP {bind_address}, listening for authenticated remote commands..."
        ),
    );

    let mut replay = ReplayGuard::new();
    // A captured envelope from before this process started is a replay, even though its
    // timestamp is still inside the ±5 minute window (e.g. a reboot envelope after boot).
    let not_before = auth::unix_now();
    let mut buffer = [0u8; MAX_COMMAND_BYTES];
    loop {
        match socket.recv_from(&mut buffer) {
            Ok((size, source)) => {
                handle_datagram_since(
                    dispatcher,
                    &buffer[..size],
                    source,
                    secret,
                    &mut replay,
                    not_before,
                    logger,
                );
            }
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

/// Handle a single UDP packet from tests (no pre-start cutoff).
#[cfg(test)]
fn handle_datagram<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    payload: &[u8],
    source: SocketAddr,
    secret: &str,
    replay: &mut ReplayGuard,
    logger: &Logger,
) {
    handle_datagram_since(dispatcher, payload, source, secret, replay, 0, logger);
}

fn handle_datagram_since<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    payload: &[u8],
    source: SocketAddr,
    secret: &str,
    replay: &mut ReplayGuard,
    not_before: u64,
    logger: &Logger,
) {
    let raw = String::from_utf8_lossy(payload);

    // Authenticate before parsing or logging the payload. Rejected envelopes are never
    // echoed into the log: they contain a signature, and logging only the reason keeps
    // malformed/hostile input out of the log stream.
    let command = match auth::verify(&raw, secret, auth::unix_now(), replay) {
        Ok(command) => command,
        Err(error) => {
            logger.record(
                "auth_rejected",
                &format!(
                    "from {source}: authentication rejected ({})",
                    error.reason()
                ),
            );
            return;
        }
    };

    if auth::timestamp(&raw).is_none_or(|timestamp| timestamp < not_before) {
        logger.record(
            "auth_rejected",
            &format!("from {source}: authentication rejected (pre_start_replay)"),
        );
        return;
    }

    logger.record(
        "network",
        &format!(
            "from {source} len: {} bytes, command: '{command}'",
            payload.len()
        ),
    );

    match dispatcher.dispatch(&command) {
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
    use std::fs;
    use std::path::{Path, PathBuf};
    use std::sync::{Arc, Mutex};

    use super::*;
    use crate::action::PowerAction;
    use crate::logging::LoggerOptions;
    use crate::power::PowerError;
    use crate::test_support::TempDir;

    const SECRET: &str = "0123456789abcdef0123456789abcdef";
    const OTHER_SECRET: &str = "fedcba9876543210fedcba9876543210";

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

    fn signed_envelope(command: &str, secret: &str) -> String {
        auth::sign(command, secret, auth::unix_now(), &auth::generate_nonce())
            .expect("sign envelope")
    }

    fn captured_logger(label: &str) -> (Logger, TempDir, PathBuf) {
        let dir = TempDir::new(label);
        let path = dir.path().join("pc_onoff.log");
        let logger = Logger::with_options(LoggerOptions {
            path: Some(path.clone()),
            max_bytes: 64 * 1024,
            echo_stdout: false,
        });
        (logger, dir, path)
    }

    fn log_text(path: &Path) -> String {
        fs::read_to_string(path).expect("read captured log")
    }

    #[test]
    fn signed_datagrams_are_dispatched() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();
        let envelope = signed_envelope("SHUTDOWN:192.168.1.100", SECRET);

        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Shutdown]);
    }

    #[test]
    fn unsigned_datagrams_are_rejected_and_logged_without_the_payload() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-unsigned");

        handle_datagram(
            &dispatcher,
            b"SHUTDOWN:192.168.1.100",
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        let log = log_text(&path);
        assert!(log.contains("[auth_rejected]"), "log was: {log}");
        assert!(log.contains("(missing)"), "log was: {log}");
        assert!(log.contains("192.168.1.50:54321"), "log was: {log}");
        assert!(
            !log.contains("SHUTDOWN:192.168.1.100"),
            "rejected payload must not be echoed into the log: {log}"
        );
    }

    #[test]
    fn wrong_secret_is_rejected() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-wrong-secret");
        let envelope = signed_envelope("shutdown", OTHER_SECRET);
        let signature = envelope
            .rsplit_once("sig=")
            .map(|(_, signature)| signature)
            .expect("signature");

        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        let log = log_text(&path);
        assert!(log.contains("(bad_signature)"), "log was: {log}");
        assert!(!log.contains(signature), "signature leaked into log: {log}");
        assert!(!log.contains(SECRET), "secret leaked into log: {log}");
    }

    #[test]
    fn replayed_datagrams_are_rejected() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-replay");
        let envelope = signed_envelope("reboot", SECRET);
        let mut replay = ReplayGuard::new();

        for _ in 0..2 {
            handle_datagram(
                &dispatcher,
                envelope.as_bytes(),
                source(),
                SECRET,
                &mut replay,
                &logger,
            );
        }

        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Reboot]);
        let log = log_text(&path);
        assert!(log.contains("(replayed)"), "log was: {log}");
    }

    #[test]
    fn expired_datagrams_are_rejected() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-expired");
        let timestamp = auth::unix_now().saturating_sub(auth::TIMESTAMP_WINDOW_SECONDS + 1);
        let envelope =
            auth::sign("shutdown", SECRET, timestamp, &auth::generate_nonce()).expect("sign");

        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        assert!(
            log_text(&path).contains("(expired)"),
            "log was: {}",
            log_text(&path)
        );
    }

    #[test]
    fn commands_are_rejected_when_the_daemon_has_no_secret() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-not-configured");
        let envelope = signed_envelope("shutdown", SECRET);

        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            "",
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        assert!(
            log_text(&path).contains("(not_configured)"),
            "log was: {}",
            log_text(&path)
        );
    }

    #[test]
    fn oversized_datagrams_are_rejected_as_malformed() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-oversized");
        let payload = vec![b'A'; auth::MAX_ENVELOPE_BYTES + 1];

        handle_datagram(
            &dispatcher,
            &payload,
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        assert!(
            log_text(&path).contains("(malformed)"),
            "log was: {}",
            log_text(&path)
        );
    }

    #[test]
    fn signed_unknown_commands_still_reach_the_dispatcher() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-unknown");
        let envelope = signed_envelope("self-destruct", SECRET);

        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        let log = log_text(&path);
        assert!(log.contains("unknown command"), "log was: {log}");
        assert!(
            !log.contains("auth_rejected"),
            "authenticated command must not be auth-rejected: {log}"
        );
    }

    #[test]
    fn ignores_empty_datagrams() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(
            &dispatcher,
            b"  \r\n",
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn handles_invalid_utf8_without_panicking() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        handle_datagram(
            &dispatcher,
            &[0xff, 0xfe, 0x00],
            source(),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
    }

    #[test]
    fn local_ipv4_is_always_parseable() {
        let ip = local_ipv4();
        assert!(ip.parse::<std::net::IpAddr>().is_ok(), "got {ip}");
    }
}
