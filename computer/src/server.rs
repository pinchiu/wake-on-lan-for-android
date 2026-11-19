//! UDP transport adapter: authenticates datagrams, hands the bare command to [`Dispatcher`],
//! and logs results.
//!
//! This module is the only part of the daemon aware of "UDP 9877"; protocol syntax is in
//! [`crate::action`], command authentication is in [`crate::auth`], and power execution is in
//! [`crate::power`]. If MQTT or HTTP channels are added in the future, only a similarly
//! lightweight adapter is needed.

use std::fmt;
use std::net::{Ipv4Addr, SocketAddr, UdpSocket};
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

use crate::MAX_COMMAND_BYTES;
use crate::auth::{self, ReplayGuard};
use crate::dispatch::{DispatchError, Dispatcher};
use crate::logging::Logger;
use crate::net;
use crate::power::PowerController;

const ERROR_BACKOFF: Duration = Duration::from_millis(250);

/// Windows `WSAEMSGSIZE`: an oversized datagram was truncated instead of delivered.
const WSAEMSGSIZE: i32 = 10040;

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
///
/// IPv4 is required; IPv6 is best effort. Both sockets feed the same [`Dispatcher`] and the
/// same [`ReplayGuard`], so an envelope replayed over the other address family is still
/// rejected and `net::is_trusted_v6` is actually exercised in production.
pub fn serve<P: PowerController + Sync>(
    dispatcher: &Dispatcher<P>,
    port: u16,
    secret: &str,
    logger: &Logger,
) -> Result<(), ServerError> {
    let ipv4_address = SocketAddr::from((Ipv4Addr::UNSPECIFIED, port));
    let socket = UdpSocket::bind(ipv4_address).map_err(|error| {
        ServerError::new(format!(
            "failed to bind UDP {ipv4_address}: {error} (port may already be in use)"
        ))
    })?;

    let ipv6_socket = match bind_ipv6_v6only(port) {
        Ok(socket) => Some(socket),
        Err(error) => {
            logger.record(
                "warning",
                &format!(
                    "IPv6 listener unavailable on UDP port {port}: {error} (continuing with IPv4 only)"
                ),
            );
            None
        }
    };

    if secret.trim().is_empty() {
        logger.record(
            "warning",
            "no command secret configured; new public sources need it, LAN/Tailscale do not",
        );
    }
    match &ipv6_socket {
        Some(_) => logger.record(
            "server_start",
            &format!(
                "successfully bound UDP {ipv4_address} and [::]:{port}, listening for remote commands..."
            ),
        ),
        None => logger.record(
            "server_start",
            &format!(
                "successfully bound UDP {ipv4_address}, listening for authenticated remote commands..."
            ),
        ),
    }

    let replay = Mutex::new(ReplayGuard::new());
    // A captured envelope from before this process started is a replay, even though its
    // timestamp is still inside the ±5 minute window (e.g. a reboot envelope after boot).
    let not_before = auth::unix_now();

    thread::scope(|scope| {
        if let Some(ipv6_socket) = ipv6_socket.as_ref() {
            scope.spawn(|| {
                receive_loop(ipv6_socket, dispatcher, secret, &replay, not_before, logger);
            });
        }
        receive_loop(&socket, dispatcher, secret, &replay, not_before, logger);
    });

    Ok(())
}

/// Receive loop shared by the IPv4 and (optional) IPv6 sockets.
fn receive_loop<P: PowerController>(
    socket: &UdpSocket,
    dispatcher: &Dispatcher<P>,
    secret: &str,
    replay: &Mutex<ReplayGuard>,
    not_before: u64,
    logger: &Logger,
) {
    let mut buffer = [0u8; MAX_COMMAND_BYTES];
    loop {
        match socket.recv_from(&mut buffer) {
            Ok((size, source)) => {
                // Hold the replay mutex only for authentication: OS power commands run
                // after it is released, so a slow shutdown cannot block other datagrams.
                let command = {
                    let mut replay = replay
                        .lock()
                        .unwrap_or_else(|poisoned| poisoned.into_inner());
                    authenticate_datagram(
                        &buffer[..size],
                        source,
                        secret,
                        &mut replay,
                        not_before,
                        true,
                        logger,
                    )
                };
                if let Some(command) = command {
                    dispatch(dispatcher, &command, source, logger);
                }
            }
            Err(error) => {
                // On Windows, an oversized datagram is truncated and reported as
                // WSAEMSGSIZE. It can never carry a valid envelope, so reject it at once
                // instead of sleeping and retrying aggressively.
                if error.raw_os_error() == Some(WSAEMSGSIZE) {
                    logger.record(
                        "auth_rejected",
                        "oversized datagram truncated by the socket (malformed)",
                    );
                    continue;
                }
                logger.record(
                    "network_error",
                    &format!("error receiving UDP data: {error}"),
                );
                thread::sleep(ERROR_BACKOFF);
            }
        }
    }
}

/// Bind `[::]:{port}` with `IPV6_V6ONLY=1` so it can coexist with the IPv4 socket on
/// platforms where dual-stack sockets grab the IPv4 port too (Linux default).
#[cfg(target_os = "linux")]
fn bind_ipv6_v6only(port: u16) -> std::io::Result<UdpSocket> {
    use std::ffi::c_void;
    use std::os::fd::FromRawFd;

    #[repr(C)]
    struct SockaddrIn6 {
        family: u16,
        port: u16,
        flowinfo: u32,
        address: [u8; 16],
        scope_id: u32,
    }

    unsafe extern "C" {
        fn socket(domain: i32, socket_type: i32, protocol: i32) -> i32;
        fn setsockopt(
            socket: i32,
            level: i32,
            option_name: i32,
            option_value: *const c_void,
            option_length: u32,
        ) -> i32;
        fn bind(socket: i32, address: *const SockaddrIn6, length: u32) -> i32;
        fn close(socket: i32) -> i32;
    }

    const AF_INET6: i32 = 10;
    const SOCK_DGRAM: i32 = 2;
    const SOCK_CLOEXEC: i32 = 0o2000000;
    const IPPROTO_IPV6: i32 = 41;
    const IPV6_V6ONLY: i32 = 26;

    let descriptor = unsafe { socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0) };
    if descriptor < 0 {
        return Err(std::io::Error::last_os_error());
    }

    let enabled: i32 = 1;
    let configured = unsafe {
        setsockopt(
            descriptor,
            IPPROTO_IPV6,
            IPV6_V6ONLY,
            std::ptr::from_ref(&enabled).cast::<c_void>(),
            std::mem::size_of_val(&enabled) as u32,
        )
    };
    if configured != 0 {
        let error = std::io::Error::last_os_error();
        unsafe { close(descriptor) };
        return Err(error);
    }

    let address = SockaddrIn6 {
        family: AF_INET6 as u16,
        port: port.to_be(),
        flowinfo: 0,
        address: [0u8; 16],
        scope_id: 0,
    };
    let bound = unsafe {
        bind(
            descriptor,
            std::ptr::from_ref(&address),
            std::mem::size_of_val(&address) as u32,
        )
    };
    if bound != 0 {
        let error = std::io::Error::last_os_error();
        unsafe { close(descriptor) };
        return Err(error);
    }

    // SAFETY: `descriptor` is a fresh, owned UDP socket that is not used anywhere else.
    Ok(unsafe { UdpSocket::from_raw_fd(descriptor) })
}

#[cfg(not(target_os = "linux"))]
fn bind_ipv6_v6only(port: u16) -> std::io::Result<UdpSocket> {
    // Windows enables IPV6_V6ONLY by default; on other platforms a dual-stack bind may fail
    // because the IPv4 socket already owns the port. Callers treat failure as non-fatal.
    UdpSocket::bind((std::net::Ipv6Addr::UNSPECIFIED, port))
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
    handle_datagram_since(
        dispatcher, payload, source, secret, replay, 0, false, logger,
    );
}

#[cfg(test)]
#[allow(clippy::too_many_arguments)]
fn handle_datagram_since<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    payload: &[u8],
    source: SocketAddr,
    secret: &str,
    replay: &mut ReplayGuard,
    not_before: u64,
    trust_private: bool,
    logger: &Logger,
) {
    if let Some(command) = authenticate_datagram(
        payload,
        source,
        secret,
        replay,
        not_before,
        trust_private,
        logger,
    ) {
        dispatch(dispatcher, &command, source, logger);
    }
}

/// Authenticate one datagram and return the command to execute.
///
/// Callers that share a [`ReplayGuard`] hold its mutex only while this function runs; the
/// actual OS power operation is dispatched afterwards, outside the lock.
fn authenticate_datagram(
    payload: &[u8],
    source: SocketAddr,
    secret: &str,
    replay: &mut ReplayGuard,
    not_before: u64,
    trust_private: bool,
    logger: &Logger,
) -> Option<String> {
    let raw = String::from_utf8_lossy(payload);

    // Trusted LAN / Tailscale sources need no HMAC. Public sources sign every command;
    // the apps store the secret once and sign automatically, so the user never re-enters it.
    let trusted_network = trust_private && net::is_trusted_source(source.ip());
    if trusted_network {
        let command = auth::command(&raw).unwrap_or_else(|| raw.trim());
        if command.is_empty() {
            logger.record("auth_rejected", &format!("from {source}: empty command"));
            return None;
        }
        logger.record(
            "network",
            &format!(
                "from {source} len: {} bytes, command: '{command}' (trusted network)",
                payload.len()
            ),
        );
        return Some(command.to_owned());
    }

    // Public sources sign every command. Rejected envelopes are never echoed into the log:
    // they contain a signature.
    let command = match auth::verify_since(&raw, secret, auth::unix_now(), not_before, replay) {
        Ok(command) => command,
        Err(error) => {
            logger.record(
                "auth_rejected",
                &format!(
                    "from {source}: authentication rejected ({})",
                    error.reason()
                ),
            );
            return None;
        }
    };

    logger.record(
        "network",
        &format!(
            "from {source} len: {} bytes, command: '{command}'",
            payload.len()
        ),
    );

    Some(command)
}

fn dispatch<P: PowerController>(
    dispatcher: &Dispatcher<P>,
    command: &str,
    source: SocketAddr,
    logger: &Logger,
) {
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

    #[cfg(target_os = "linux")]
    #[test]
    fn ipv6_v6only_socket_binds_the_exact_ipv4_port() {
        let ipv4 = UdpSocket::bind((Ipv4Addr::UNSPECIFIED, 0)).expect("bind IPv4");
        let port = ipv4.local_addr().expect("IPv4 local address").port();

        match bind_ipv6_v6only(port) {
            Ok(ipv6) => {
                assert_eq!(
                    ipv6.local_addr().expect("IPv6 local address").port(),
                    port,
                    "IPv6 must bind the same port as IPv4 when IPV6_V6ONLY is set"
                );
            }
            Err(error) => {
                // IPv6 can be disabled in containers/CI: EAFNOSUPPORT (97) or
                // EADDRNOTAVAIL (99) means this environment cannot host an IPv6 socket.
                let code = error.raw_os_error();
                assert!(
                    matches!(code, Some(97) | Some(99)),
                    "unexpected IPv6 bind failure: {error}"
                );
            }
        }
    }

    #[test]
    fn local_ipv4_is_always_parseable() {
        let ip = local_ipv4();
        assert!(ip.parse::<std::net::IpAddr>().is_ok(), "got {ip}");
    }

    fn handle_trusted_datagram<P: PowerController>(
        dispatcher: &Dispatcher<P>,
        payload: &[u8],
        source: SocketAddr,
        secret: &str,
        replay: &mut ReplayGuard,
        logger: &Logger,
    ) {
        handle_datagram_since(dispatcher, payload, source, secret, replay, 0, true, logger);
    }

    #[test]
    fn trusted_networks_accept_bare_and_line_terminated_envelopes() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();
        let mut replay = ReplayGuard::new();

        handle_trusted_datagram(
            &dispatcher,
            b"SHUTDOWN:192.168.1.100\r\n",
            source(),
            SECRET,
            &mut replay,
            &logger,
        );

        let mut envelope = signed_envelope("REBOOT", SECRET);
        envelope.push_str("\r\n");
        handle_trusted_datagram(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut replay,
            &logger,
        );

        assert_eq!(
            *observed.lock().expect("lock"),
            vec![PowerAction::Shutdown, PowerAction::Reboot]
        );
    }

    #[test]
    fn public_signed_envelopes_tolerate_trailing_crlf() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let logger = Logger::disabled();

        let mut envelope = signed_envelope("SLEEP", SECRET);
        envelope.push_str("\r\n");
        handle_datagram(
            &dispatcher,
            envelope.as_bytes(),
            SocketAddr::from(([8, 8, 8, 8], 40000)),
            SECRET,
            &mut ReplayGuard::new(),
            &logger,
        );

        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Sleep]);
    }

    #[test]
    fn ipv6_tailscale_sources_are_trusted_but_public_ipv6_is_not() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-ipv6-trust");
        let mut replay = ReplayGuard::new();
        let tailscale: SocketAddr = "[fd7a:115c:a1e0::1]:41234".parse().expect("ULA source");
        let public: SocketAddr = "[2001:4860:4860::8888]:41234"
            .parse()
            .expect("public source");

        handle_trusted_datagram(
            &dispatcher,
            b"lock",
            tailscale,
            SECRET,
            &mut replay,
            &logger,
        );
        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Lock]);

        handle_trusted_datagram(&dispatcher, b"reboot", public, SECRET, &mut replay, &logger);
        assert_eq!(
            *observed.lock().expect("lock"),
            vec![PowerAction::Lock],
            "bare commands from a public IPv6 source must be rejected"
        );
        assert!(
            log_text(&path).contains("(missing)"),
            "log was: {}",
            log_text(&path)
        );

        let envelope = signed_envelope("HIBERNATE", SECRET);
        handle_trusted_datagram(
            &dispatcher,
            envelope.as_bytes(),
            public,
            SECRET,
            &mut replay,
            &logger,
        );
        assert_eq!(
            *observed.lock().expect("lock"),
            vec![PowerAction::Lock, PowerAction::Hibernate]
        );
    }

    #[test]
    fn pre_start_envelopes_are_rejected_without_poisoning_the_replay_guard() {
        let power = FakePower::default();
        let observed = power.observed();
        let dispatcher = Dispatcher::new(power);
        let (logger, _dir, path) = captured_logger("server-pre-start");
        let now = auth::unix_now();
        let envelope = auth::sign(
            "shutdown",
            SECRET,
            now.saturating_sub(100),
            &auth::generate_nonce(),
        )
        .expect("sign");
        let mut replay = ReplayGuard::new();

        handle_datagram_since(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut replay,
            now.saturating_sub(50),
            false,
            &logger,
        );

        assert!(observed.lock().expect("lock").is_empty());
        assert!(
            log_text(&path).contains("(pre_start_replay)"),
            "log was: {}",
            log_text(&path)
        );

        // The rejected packet must not have consumed the nonce: it is still accepted once
        // the pre-start cutoff no longer covers it.
        handle_datagram_since(
            &dispatcher,
            envelope.as_bytes(),
            source(),
            SECRET,
            &mut replay,
            0,
            false,
            &logger,
        );
        assert_eq!(*observed.lock().expect("lock"), vec![PowerAction::Shutdown]);
    }
}
