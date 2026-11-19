//! Opt-in local Frost dashboard (`--web-ui`).
//!
//! This is a foreground-only mode. It binds a loopback HTTP server, never acquires the
//! daemon's single-instance lock and never binds UDP 9877, so it cannot fight the running
//! daemon or the Linux system service. Power buttons forward the existing protocol keywords
//! as UDP datagrams to the local daemon, exactly like a mobile client would.
//!
//! The implementation intentionally uses only `std`: a tiny HTTP/1.1 request reader,
//! hand-written JSON serialization, and an embedded HTML/CSS/JS document. Routes are pure
//! functions so request parsing, action validation, and CSRF checks stay unit-testable.

use std::fmt;
use std::io::{self, Read, Write};
use std::net::{Ipv4Addr, TcpListener, TcpStream, UdpSocket};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::action::PowerAction;
use crate::auth;
use crate::autostart;
use crate::config::AppConfig;
use crate::instance;
use crate::paths;
use crate::server;
use crate::system_service;

/// Default loopback port for the dashboard.
pub const DEFAULT_WEB_PORT: u16 = 9878;

/// Optional environment override for the dashboard port.
pub const WEB_PORT_ENV: &str = "PC_ONOFF_WEB_PORT";

/// Maximum request head + body accepted from one connection (64 KB).
const MAX_REQUEST_BYTES: usize = 64 * 1024;

/// Maximum number of simultaneously handled dashboard connections.
const MAX_CONCURRENT_CONNECTIONS: usize = 32;

/// Maximum nesting depth accepted by the tiny JSON reader.
const MAX_JSON_DEPTH: usize = 32;

/// How much of the end of the log file `/api/log` reads (keeps polling cheap).
const LOG_TAIL_WINDOW_BYTES: u64 = 65_536;

/// Socket read/write timeout. Browsers may open speculative idle connections.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(5);

/// Last log lines included in `/api/status`.
const STATUS_LOG_LINES: usize = 8;

/// Upper bound for `/api/log?lines=N`.
pub const MAX_LOG_LINES: usize = 200;

/// Default number of log lines returned by `/api/log`.
const DEFAULT_LOG_LINES: usize = 40;

/// Embedded Frost dashboard. `include_str!` keeps the binary dependency-free.
const DASHBOARD_HTML: &str = include_str!("web/app.html");

/// Dashboard startup failure.
#[derive(Debug)]
pub struct WebError {
    message: String,
}

impl WebError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for WebError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for WebError {}

/// Effective dashboard port (`PC_ONOFF_WEB_PORT`, falling back to [`DEFAULT_WEB_PORT`]).
fn port() -> u16 {
    resolve_port(std::env::var(WEB_PORT_ENV).ok().as_deref())
}

fn resolve_port(value: Option<&str>) -> u16 {
    value
        .and_then(|value| value.trim().parse::<u16>().ok())
        .filter(|port| *port != 0)
        .unwrap_or(DEFAULT_WEB_PORT)
}

/// Bind the loopback dashboard and serve until the process is interrupted.
///
/// Prints the URL, opens the default browser best-effort, then accepts connections.
/// Each connection gets its own thread so a browser preconnect cannot stall the UI.
pub fn serve() -> Result<(), WebError> {
    let port = port();
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, port)).map_err(|error| {
        WebError::new(format!(
            "cannot bind dashboard http://127.0.0.1:{port}: {error} \
             (is another --web-ui already running?)"
        ))
    })?;

    println!("Dashboard: http://127.0.0.1:{port}");
    println!("Press Ctrl+C to stop.");
    open_browser(port);

    let active = Arc::new(AtomicUsize::new(0));
    for stream in listener.incoming() {
        match stream {
            Ok(stream) => spawn_connection(stream, port, &active),
            Err(error) => eprintln!("Dashboard: cannot accept connection: {error}"),
        }
    }

    Ok(())
}

/// Active connection counter; decrements when the handler finishes or its thread is dropped.
struct ConnectionGuard(Arc<AtomicUsize>);

impl Drop for ConnectionGuard {
    fn drop(&mut self) {
        self.0.fetch_sub(1, Ordering::SeqCst);
    }
}

/// Handle one accepted connection on its own thread, refusing work above the cap.
fn spawn_connection(stream: TcpStream, port: u16, active: &Arc<AtomicUsize>) {
    if active.load(Ordering::SeqCst) >= MAX_CONCURRENT_CONNECTIONS {
        let mut refused = stream;
        let _ = write_response(
            &mut refused,
            &Response::error(503, "too many concurrent connections"),
        );
        return;
    }

    let Ok(cloned) = stream.try_clone() else {
        let mut refused = stream;
        let _ = write_response(
            &mut refused,
            &Response::error(503, "cannot clone connection"),
        );
        return;
    };

    active.fetch_add(1, Ordering::SeqCst);
    let guard = ConnectionGuard(Arc::clone(active));
    let spawned = thread::Builder::new()
        .name("pc_onoff-web".to_owned())
        .spawn(move || {
            let _guard = guard;
            handle_connection(cloned, port);
        });

    if let Err(error) = spawned {
        // The closure (and its guard) is dropped by `spawn`, so the counter is already
        // decremented; report the failure on the original stream.
        let mut refused = stream;
        let _ = write_response(
            &mut refused,
            &Response::error(503, "cannot start a connection handler"),
        );
        eprintln!("Dashboard: cannot spawn connection handler: {error}");
    }
}

/// Open the dashboard in the platform browser. Failure is intentionally silent:
/// the URL is already printed, and headless machines simply do nothing.
fn open_browser(port: u16) {
    let url = format!("http://127.0.0.1:{port}");

    #[cfg(windows)]
    let mut command = {
        let mut command = crate::platform::command("cmd");
        command.args(["/C", "start", "", &url]);
        command
    };
    #[cfg(target_os = "macos")]
    let mut command = {
        let mut command = crate::platform::command("open");
        command.arg(&url);
        command
    };
    #[cfg(all(unix, not(target_os = "macos")))]
    let mut command = {
        let mut command = crate::platform::command("xdg-open");
        command.arg(&url);
        command
    };

    #[cfg(any(windows, target_os = "macos", unix))]
    {
        if let Ok(mut child) = command
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()
        {
            // Reap the browser launcher so it cannot linger as a zombie.
            let _ = thread::Builder::new()
                .name("pc_onoff-browser-reaper".to_owned())
                .spawn(move || {
                    let _ = child.wait();
                });
        }
    }
}

fn handle_connection(mut stream: TcpStream, port: u16) {
    let _ = stream.set_read_timeout(Some(REQUEST_TIMEOUT));
    let _ = stream.set_write_timeout(Some(REQUEST_TIMEOUT));

    let response = match read_http_request(&mut stream) {
        Ok(request) => respond(request, port),
        Err(ReadError::TooLarge) => Response::error(413, "request exceeds 64 KB"),
        Err(ReadError::Timeout) => Response::error(408, "request timed out"),
        Err(ReadError::BadRequest) => Response::error(400, "malformed HTTP request"),
    };

    let _ = write_response(&mut stream, &response);
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ReadError {
    BadRequest,
    TooLarge,
    Timeout,
}

fn read_http_request(stream: &mut TcpStream) -> Result<Request, ReadError> {
    let mut buffer = Vec::with_capacity(1024);
    let (header_end, delimiter_len) = loop {
        if let Some(boundary) = find_header_boundary(&buffer) {
            break boundary;
        }
        if buffer.len() >= MAX_REQUEST_BYTES {
            return Err(ReadError::TooLarge);
        }

        let mut chunk = [0u8; 2048];
        let read = stream.read(&mut chunk).map_err(map_read_error)?;
        if read == 0 {
            return Err(ReadError::BadRequest);
        }
        buffer.extend_from_slice(&chunk[..read]);
        if buffer.len() > MAX_REQUEST_BYTES {
            return Err(ReadError::TooLarge);
        }
    };

    let head_text =
        std::str::from_utf8(&buffer[..header_end]).map_err(|_| ReadError::BadRequest)?;
    let head = parse_request_head(head_text)?;
    let content_length = head.content_length()?;
    let body_start = header_end + delimiter_len;
    if body_start.saturating_add(content_length) > MAX_REQUEST_BYTES {
        return Err(ReadError::TooLarge);
    }

    let mut body = buffer[body_start..].to_vec();
    if body.len() > content_length {
        body.truncate(content_length);
    }

    while body.len() < content_length {
        let mut chunk = [0u8; 2048];
        let wanted = (content_length - body.len()).min(chunk.len());
        let read = stream.read(&mut chunk[..wanted]).map_err(map_read_error)?;
        if read == 0 {
            return Err(ReadError::BadRequest);
        }
        body.extend_from_slice(&chunk[..read]);
    }

    Ok(Request { head, body })
}

fn map_read_error(error: io::Error) -> ReadError {
    match error.kind() {
        io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut => ReadError::Timeout,
        _ => ReadError::BadRequest,
    }
}

/// Locate the blank line ending the request head; returns (index, delimiter length).
///
/// Both CRLFCRLF and LFLF are accepted, and the earliest terminator wins so a request
/// cannot smuggle a second boundary past the first one.
fn find_header_boundary(buffer: &[u8]) -> Option<(usize, usize)> {
    let crlf = buffer
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .map(|index| (index, 4));
    let lf = buffer
        .windows(2)
        .position(|window| window == b"\n\n")
        .map(|index| (index, 2));

    match (crlf, lf) {
        (Some(crlf), Some(lf)) if lf.0 < crlf.0 => Some(lf),
        (Some(crlf), _) => Some(crlf),
        (None, lf) => lf,
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct RequestHead {
    method: String,
    target: String,
    headers: Vec<(String, String)>,
}

impl RequestHead {
    fn header(&self, name: &str) -> Option<&str> {
        self.headers
            .iter()
            .find(|(header, _)| header == name)
            .map(|(_, value)| value.as_str())
    }

    fn content_length(&self) -> Result<usize, ReadError> {
        match self.header("content-length") {
            Some(value) => value
                .trim()
                .parse::<usize>()
                .map_err(|_| ReadError::BadRequest),
            None => Ok(0),
        }
    }
}

#[derive(Debug)]
struct Request {
    head: RequestHead,
    body: Vec<u8>,
}

fn parse_request_head(text: &str) -> Result<RequestHead, ReadError> {
    let mut lines = text
        .split('\n')
        .map(|line| line.strip_suffix('\r').unwrap_or(line));

    let request_line = lines.next().ok_or(ReadError::BadRequest)?;
    let mut parts = request_line.split_whitespace();
    let method = parts.next().ok_or(ReadError::BadRequest)?;
    let target = parts.next().ok_or(ReadError::BadRequest)?;
    let version = parts.next().ok_or(ReadError::BadRequest)?;
    if parts.next().is_some() || !version.starts_with("HTTP/1.") {
        return Err(ReadError::BadRequest);
    }

    let mut headers = Vec::new();
    for line in lines {
        if line.is_empty() {
            break;
        }
        let (name, value) = line.split_once(':').ok_or(ReadError::BadRequest)?;
        if name.is_empty() || name.chars().any(char::is_whitespace) {
            return Err(ReadError::BadRequest);
        }
        let name = name.to_ascii_lowercase();
        // Chunked bodies would need framing we deliberately do not implement, and duplicate
        // security-relevant headers are a classic request-smuggling primitive.
        if name == "transfer-encoding" {
            return Err(ReadError::BadRequest);
        }
        if matches!(
            name.as_str(),
            "host" | "origin" | "content-length" | "content-type"
        ) && headers.iter().any(|(existing, _)| existing == &name)
        {
            return Err(ReadError::BadRequest);
        }
        headers.push((name, value.trim().to_owned()));
        if headers.len() > 100 {
            return Err(ReadError::BadRequest);
        }
    }

    Ok(RequestHead {
        method: method.to_owned(),
        target: target.to_owned(),
        headers,
    })
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Route {
    Index,
    Status,
    Log(usize),
    Action,
    NotFound,
}

/// Pure route table: method + target (with optional query) to an endpoint.
fn route(method: &str, target: &str) -> Route {
    let (path, query) = target.split_once('?').unwrap_or((target, ""));
    match (method, path) {
        ("GET", "/") => Route::Index,
        ("GET", "/api/status") => Route::Status,
        ("GET", "/api/log") => Route::Log(log_lines_from_query(query)),
        ("POST", "/api/action") => Route::Action,
        _ => Route::NotFound,
    }
}

fn log_lines_from_query(query: &str) -> usize {
    query
        .split('&')
        .filter_map(|pair| pair.split_once('='))
        .find(|(name, _)| *name == "lines")
        .and_then(|(_, value)| value.parse::<usize>().ok())
        .map_or(DEFAULT_LOG_LINES, |lines| lines.clamp(1, MAX_LOG_LINES))
}

fn respond(request: Request, port: u16) -> Response {
    // Defense in depth against DNS rebinding, in addition to the Origin check below.
    if !host_allowed(request.head.header("host"), port) {
        return Response::error(403, "invalid Host header");
    }

    match route(&request.head.method, &request.head.target) {
        Route::Index => Response::html(DASHBOARD_HTML),
        Route::Status => Response::json(200, status_json()),
        Route::Log(lines) => Response::json(200, log_json(lines)),
        Route::Action => handle_action(&request, port),
        Route::NotFound => Response::error(404, "not found"),
    }
}

fn handle_action(request: &Request, port: u16) -> Response {
    let Some(content_type) = request.head.header("content-type") else {
        return Response::error(415, "Content-Type must be application/json");
    };
    if !is_json_content_type(content_type) {
        return Response::error(415, "Content-Type must be application/json");
    }

    if let Some(origin) = request.head.header("origin")
        && !origin_allowed(origin, port)
    {
        return Response::error(403, "cross-origin request rejected");
    }

    let action = match action_from_body(&request.body) {
        Ok(action) => action,
        Err(message) => return Response::error(400, &message),
    };

    let config_path = dashboard_config_path();
    let config = AppConfig::load(&config_path);
    // LAN/Tailscale needs no secret; a public port-forward signs every command with the
    // stored daemon secret.
    let wire = if config.secret.trim().is_empty() {
        action.keyword().to_owned()
    } else {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|duration| duration.as_secs())
            .unwrap_or(0);
        let nonce = auth::generate_nonce();
        match auth::sign(action.keyword(), &config.secret, now, &nonce) {
            Ok(envelope) => envelope,
            Err(error) => return Response::error(500, &format!("cannot sign command: {error}")),
        }
    };
    let udp_port = config.udp_port;
    match send_action(&wire, udp_port) {
        Ok(()) => Response::json(
            200,
            format!(
                "{{\"ok\":true,\"action\":{}}}",
                json_string(action.keyword())
            ),
        ),
        Err(error) => Response::error(
            502,
            &format!(
                "cannot send {} to 127.0.0.1:{udp_port}: {error}",
                action.keyword()
            ),
        ),
    }
}

fn action_from_body(body: &[u8]) -> Result<PowerAction, String> {
    let value = json_string_member(body, "action").map_err(str::to_owned)?;
    PowerAction::parse(&value).map_err(|error| error.to_string())
}

fn is_json_content_type(value: &str) -> bool {
    value
        .split(';')
        .next()
        .is_some_and(|media_type| media_type.trim().eq_ignore_ascii_case("application/json"))
}

fn origin_allowed(origin: &str, port: u16) -> bool {
    if port == 80 {
        // Browsers omit the default port from the Origin header.
        matches!(
            origin,
            "http://127.0.0.1" | "http://localhost" | "http://127.0.0.1:80" | "http://localhost:80"
        )
    } else {
        origin == format!("http://127.0.0.1:{port}") || origin == format!("http://localhost:{port}")
    }
}

fn host_allowed(host: Option<&str>, port: u16) -> bool {
    host.is_some_and(|host| authority_matches(host.trim(), port))
}

fn authority_matches(authority: &str, port: u16) -> bool {
    if port == 80 {
        authority.eq_ignore_ascii_case("127.0.0.1") || authority.eq_ignore_ascii_case("localhost")
    } else {
        authority.eq_ignore_ascii_case(&format!("127.0.0.1:{port}"))
            || authority.eq_ignore_ascii_case(&format!("localhost:{port}"))
    }
}

fn send_action(envelope: &str, udp_port: u16) -> io::Result<()> {
    let socket = UdpSocket::bind((Ipv4Addr::LOCALHOST, 0))?;
    socket.set_write_timeout(Some(Duration::from_secs(2)))?;
    socket.send_to(envelope.as_bytes(), (Ipv4Addr::LOCALHOST, udp_port))?;
    Ok(())
}

/// Prefer the system service's config when it owns the daemon, so the dashboard signs with
/// the same secret and sends to the same UDP port as the running service.
fn dashboard_config_path() -> PathBuf {
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let system = PathBuf::from("/var/lib/pc_onoff/config.ini");
        if system_service::state().is_installed() && system.exists() {
            return system;
        }
    }
    paths::config_path()
}

fn dashboard_log_path(config_path: &Path) -> PathBuf {
    #[cfg(all(unix, not(target_os = "macos")))]
    if config_path.starts_with("/var/lib/pc_onoff") {
        return config_path.with_file_name("pc_onoff.log");
    }
    paths::log_path()
}

fn status_json() -> String {
    let config_path = dashboard_config_path();
    let config = AppConfig::load(&config_path);
    let log_path = dashboard_log_path(&config_path);
    let service = system_service::state();
    let user_running = instance::is_running();
    // A root system service owns UDP 9877 without keeping a per-user lock file, so it must
    // also count as online; otherwise the dashboard would disable every action it can serve.
    let running = user_running || service == system_service::ServiceState::Active;
    let pid = if user_running {
        instance::running_pid()
    } else {
        None
    };
    let local_ips = local_ipv4_addresses();
    let log_lines = read_log_tail(&log_path, STATUS_LOG_LINES).unwrap_or_default();

    let pid_json = pid.map_or_else(|| "null".to_owned(), |pid| pid.to_string());

    format!(
        concat!(
            "{{\"version\":{},\"running\":{},\"pid\":{},\"udp_port\":{},",
            "\"local_ips\":{},\"autostart\":{},\"system_service\":{},",
            "\"system_service_installed\":{},\"config_path\":{},\"log_path\":{},",
            "\"log_lines\":{}}}"
        ),
        json_string(env!("CARGO_PKG_VERSION")),
        running,
        pid_json,
        config.udp_port,
        json_array(local_ips.iter().map(String::as_str)),
        autostart::is_enabled(),
        json_string(service.label()),
        service.is_installed(),
        json_string(&config_path.display().to_string()),
        json_string(&log_path.display().to_string()),
        json_array(log_lines.iter().map(String::as_str)),
    )
}

fn log_json(lines: usize) -> String {
    let log_path = dashboard_log_path(&dashboard_config_path());
    let tail = read_log_tail(&log_path, lines).unwrap_or_default();
    format!(
        "{{\"lines\":{},\"count\":{}}}",
        json_array(tail.iter().map(String::as_str)),
        tail.len()
    )
}

/// Read the last `limit` lines without loading the whole (up to 5 MB) log every poll.
fn read_log_tail(path: &Path, limit: usize) -> io::Result<Vec<String>> {
    use std::io::{Seek, SeekFrom};

    let mut file = std::fs::File::open(path)?;
    let length = file.metadata()?.len();
    let start = length.saturating_sub(LOG_TAIL_WINDOW_BYTES);
    if start > 0 {
        file.seek(SeekFrom::Start(start))?;
    }

    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes)?;
    let content = String::from_utf8_lossy(&bytes);
    // When the window starts mid-line, drop that partial line.
    let content = if start > 0 {
        content
            .find('\n')
            .map_or("", |newline| &content[newline + 1..])
    } else {
        content.as_ref()
    };
    Ok(tail_lines(content, limit))
}

fn tail_lines(content: &str, limit: usize) -> Vec<String> {
    let lines: Vec<&str> = content.lines().collect();
    let start = lines.len().saturating_sub(limit);
    lines[start..]
        .iter()
        .map(|line| (*line).to_owned())
        .collect()
}

fn json_string(value: &str) -> String {
    let mut output = String::with_capacity(value.len() + 2);
    output.push('"');
    for character in value.chars() {
        match character {
            '"' => output.push_str("\\\""),
            '\\' => output.push_str("\\\\"),
            '\n' => output.push_str("\\n"),
            '\r' => output.push_str("\\r"),
            '\t' => output.push_str("\\t"),
            '\u{0008}' => output.push_str("\\b"),
            '\u{000c}' => output.push_str("\\f"),
            character if character < '\u{0020}' => {
                output.push_str(&format!("\\u{:04x}", character as u32));
            }
            character => output.push(character),
        }
    }
    output.push('"');
    output
}

fn json_array<'a>(items: impl IntoIterator<Item = &'a str>) -> String {
    let mut output = String::from("[");
    for (index, item) in items.into_iter().enumerate() {
        if index > 0 {
            output.push(',');
        }
        output.push_str(&json_string(item));
    }
    output.push(']');
    output
}

/// Extract a string member from a flat-or-nested JSON object.
///
/// The dashboard only sends `{"action":"lock"}`; this reader still accepts extra
/// members so callers can add metadata later. It rejects malformed JSON and
/// non-string `action` values rather than guessing.
fn json_string_member(body: &[u8], key: &str) -> Result<String, &'static str> {
    let text = std::str::from_utf8(body).map_err(|_| "request body is not valid UTF-8")?;
    let mut parser = JsonParser::new(text);

    parser.skip_whitespace();
    parser
        .expect(b'{')
        .map_err(|()| "request body must be a JSON object")?;

    let mut found = None;
    parser.skip_whitespace();
    if parser.peek() == Some(b'}') {
        parser.position += 1;
    } else {
        loop {
            parser.skip_whitespace();
            let name = parser
                .parse_string()
                .map_err(|()| "request body contains invalid JSON")?;
            parser.skip_whitespace();
            parser
                .expect(b':')
                .map_err(|()| "request body contains invalid JSON")?;
            parser.skip_whitespace();

            if name == key {
                let value = parser
                    .parse_string()
                    .map_err(|()| "action must be a string")?;
                found = Some(value);
            } else {
                parser
                    .skip_value(0)
                    .map_err(|()| "request body contains invalid JSON")?;
            }

            parser.skip_whitespace();
            match parser.next() {
                Some(b',') => continue,
                Some(b'}') => break,
                _ => return Err("request body contains invalid JSON"),
            }
        }
    }

    parser.skip_whitespace();
    if parser.position != parser.bytes.len() {
        return Err("request body contains invalid JSON");
    }

    found.ok_or("request body is missing the action field")
}

struct JsonParser<'a> {
    bytes: &'a [u8],
    position: usize,
}

impl<'a> JsonParser<'a> {
    fn new(text: &'a str) -> Self {
        Self {
            bytes: text.as_bytes(),
            position: 0,
        }
    }

    fn peek(&self) -> Option<u8> {
        self.bytes.get(self.position).copied()
    }

    fn next(&mut self) -> Option<u8> {
        let byte = self.peek()?;
        self.position += 1;
        Some(byte)
    }

    fn skip_whitespace(&mut self) {
        while matches!(self.peek(), Some(b' ' | b'\t' | b'\r' | b'\n')) {
            self.position += 1;
        }
    }

    fn expect(&mut self, expected: u8) -> Result<(), ()> {
        (self.next() == Some(expected)).then_some(()).ok_or(())
    }

    fn parse_string(&mut self) -> Result<String, ()> {
        self.expect(b'"')?;
        let mut output = String::new();

        loop {
            match self.next().ok_or(())? {
                b'"' => return Ok(output),
                b'\\' => match self.next().ok_or(())? {
                    b'"' => output.push('"'),
                    b'\\' => output.push('\\'),
                    b'/' => output.push('/'),
                    b'b' => output.push('\u{0008}'),
                    b'f' => output.push('\u{000c}'),
                    b'n' => output.push('\n'),
                    b'r' => output.push('\r'),
                    b't' => output.push('\t'),
                    b'u' => {
                        let value = self.parse_hex4()?;
                        output.push(char::from_u32(value).ok_or(())?);
                    }
                    _ => return Err(()),
                },
                byte if byte < 0x20 => return Err(()),
                byte if byte < 0x80 => output.push(byte as char),
                byte => {
                    let start = self.position - 1;
                    let width = utf8_width(byte).ok_or(())?;
                    let end = start + width;
                    let sequence = self.bytes.get(start..end).ok_or(())?;
                    output.push_str(std::str::from_utf8(sequence).map_err(|_| ())?);
                    self.position = end;
                }
            }
        }
    }

    fn parse_hex4(&mut self) -> Result<u32, ()> {
        let end = self.position + 4;
        let digits = self.bytes.get(self.position..end).ok_or(())?;
        let mut value = 0u32;
        for &digit in digits {
            let nibble = match digit {
                b'0'..=b'9' => u32::from(digit - b'0'),
                b'a'..=b'f' => u32::from(digit - b'a') + 10,
                b'A'..=b'F' => u32::from(digit - b'A') + 10,
                _ => return Err(()),
            };
            value = value * 16 + nibble;
        }
        self.position = end;
        Ok(value)
    }

    fn skip_value(&mut self, depth: usize) -> Result<(), ()> {
        if depth > MAX_JSON_DEPTH {
            return Err(());
        }
        self.skip_whitespace();
        match self.peek().ok_or(())? {
            b'"' => {
                self.parse_string()?;
                Ok(())
            }
            b'{' => {
                self.position += 1;
                self.skip_whitespace();
                if self.peek() == Some(b'}') {
                    self.position += 1;
                    return Ok(());
                }
                loop {
                    self.skip_whitespace();
                    self.parse_string()?;
                    self.skip_whitespace();
                    self.expect(b':')?;
                    self.skip_value(depth + 1)?;
                    self.skip_whitespace();
                    match self.next() {
                        Some(b',') => continue,
                        Some(b'}') => return Ok(()),
                        _ => return Err(()),
                    }
                }
            }
            b'[' => {
                self.position += 1;
                self.skip_whitespace();
                if self.peek() == Some(b']') {
                    self.position += 1;
                    return Ok(());
                }
                loop {
                    self.skip_value(depth + 1)?;
                    self.skip_whitespace();
                    match self.next() {
                        Some(b',') => continue,
                        Some(b']') => return Ok(()),
                        _ => return Err(()),
                    }
                }
            }
            b't' => self.expect_literal("true"),
            b'f' => self.expect_literal("false"),
            b'n' => self.expect_literal("null"),
            b'-' | b'0'..=b'9' => self.skip_number(),
            _ => Err(()),
        }
    }

    fn expect_literal(&mut self, literal: &str) -> Result<(), ()> {
        let end = self.position + literal.len();
        if self.bytes.get(self.position..end) == Some(literal.as_bytes()) {
            self.position = end;
            Ok(())
        } else {
            Err(())
        }
    }

    fn skip_number(&mut self) -> Result<(), ()> {
        if self.peek() == Some(b'-') {
            self.position += 1;
        }

        let digits_start = self.position;
        while matches!(self.peek(), Some(b'0'..=b'9')) {
            self.position += 1;
        }
        if self.position == digits_start {
            return Err(());
        }

        if self.peek() == Some(b'.') {
            self.position += 1;
            let fraction_start = self.position;
            while matches!(self.peek(), Some(b'0'..=b'9')) {
                self.position += 1;
            }
            if self.position == fraction_start {
                return Err(());
            }
        }

        if matches!(self.peek(), Some(b'e' | b'E')) {
            self.position += 1;
            if matches!(self.peek(), Some(b'+' | b'-')) {
                self.position += 1;
            }
            let exponent_start = self.position;
            while matches!(self.peek(), Some(b'0'..=b'9')) {
                self.position += 1;
            }
            if self.position == exponent_start {
                return Err(());
            }
        }

        Ok(())
    }
}

fn utf8_width(byte: u8) -> Option<usize> {
    match byte {
        0x00..=0x7f => Some(1),
        0xc0..=0xdf => Some(2),
        0xe0..=0xef => Some(3),
        0xf0..=0xf7 => Some(4),
        _ => None,
    }
}

fn local_ipv4_addresses() -> Vec<String> {
    let mut addresses = vec![server::local_ipv4()];

    #[cfg(target_os = "linux")]
    if let Ok(output) = crate::platform::command("hostname").arg("-I").output()
        && output.status.success()
    {
        merge_ipv4_tokens(&mut addresses, &String::from_utf8_lossy(&output.stdout));
    }

    #[cfg(target_os = "macos")]
    if let Ok(output) = crate::platform::command("ifconfig").output()
        && output.status.success()
    {
        let text = String::from_utf8_lossy(&output.stdout);
        for line in text.lines() {
            let line = line.trim_start();
            if let Some(rest) = line.strip_prefix("inet ") {
                let address = rest.split_whitespace().next().unwrap_or_default();
                merge_ipv4_tokens(&mut addresses, address);
            }
        }
    }

    #[cfg(windows)]
    if let Ok(output) = crate::platform::command("ipconfig").output()
        && output.status.success()
    {
        let text = String::from_utf8_lossy(&output.stdout);
        for line in text.lines() {
            if line.to_ascii_lowercase().contains("ipv4") {
                let value = line.rsplit_once(':').map_or(line, |(_, value)| value);
                merge_ipv4_tokens(&mut addresses, value);
            }
        }
    }

    // Keep loopback only as the fallback when no interface address was discovered.
    if addresses.len() > 1 {
        addresses.retain(|address| address != "127.0.0.1");
    }

    addresses
}

/// Add unique, non-loopback IPv4 tokens found in platform command output.
fn merge_ipv4_tokens(addresses: &mut Vec<String>, text: &str) {
    for token in text.split_whitespace() {
        let candidate =
            token.trim_matches(|character: char| !character.is_ascii_digit() && character != '.');
        let Ok(address) = candidate.parse::<Ipv4Addr>() else {
            continue;
        };
        if address.is_loopback() || address.is_unspecified() {
            continue;
        }
        if !addresses.iter().any(|existing| existing == candidate) {
            addresses.push(candidate.to_owned());
        }
    }
}

struct Response {
    status: u16,
    content_type: &'static str,
    body: Vec<u8>,
    csp: bool,
}

impl Response {
    fn json(status: u16, body: String) -> Self {
        Self {
            status,
            content_type: "application/json; charset=utf-8",
            body: body.into_bytes(),
            csp: false,
        }
    }

    fn html(body: &'static str) -> Self {
        Self {
            status: 200,
            content_type: "text/html; charset=utf-8",
            body: body.as_bytes().to_vec(),
            csp: true,
        }
    }

    fn error(status: u16, message: &str) -> Self {
        Self::json(
            status,
            format!("{{\"ok\":false,\"error\":{}}}", json_string(message)),
        )
    }
}

fn write_response(stream: &mut TcpStream, response: &Response) -> io::Result<()> {
    let mut head = format!(
        "HTTP/1.1 {} {}\r\n\
         Content-Type: {}\r\n\
         Content-Length: {}\r\n\
         Cache-Control: no-store\r\n\
         X-Content-Type-Options: nosniff\r\n\
         Connection: close\r\n",
        response.status,
        reason_phrase(response.status),
        response.content_type,
        response.body.len(),
    );

    if response.csp {
        head.push_str(
            "Content-Security-Policy: default-src 'none'; \
             style-src 'unsafe-inline'; script-src 'unsafe-inline'; \
             connect-src 'self'; img-src data:; base-uri 'none'; \
             form-action 'none'; frame-ancestors 'none'\r\n",
        );
    }
    head.push_str("\r\n");

    stream.write_all(head.as_bytes())?;
    stream.write_all(&response.body)?;
    stream.flush()
}

fn reason_phrase(status: u16) -> &'static str {
    match status {
        200 => "OK",
        400 => "Bad Request",
        403 => "Forbidden",
        404 => "Not Found",
        408 => "Request Timeout",
        413 => "Content Too Large",
        415 => "Unsupported Media Type",
        502 => "Bad Gateway",
        503 => "Service Unavailable",
        _ => "Internal Server Error",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_request_line_and_headers() {
        let head = parse_request_head(
            "GET /api/status?probe=1 HTTP/1.1\r\n\
             Host: 127.0.0.1:9878\r\n\
             Content-Type: application/json\r\n\
             \r\n",
        )
        .expect("valid request head");

        assert_eq!(head.method, "GET");
        assert_eq!(head.target, "/api/status?probe=1");
        assert_eq!(head.header("host"), Some("127.0.0.1:9878"));
        assert_eq!(head.content_length(), Ok(0));
        assert_eq!(head.headers.len(), 2);
    }

    #[test]
    fn accepts_lf_only_request_heads() {
        let head = parse_request_head("POST /api/action HTTP/1.1\nContent-Length: 2\n\n")
            .expect("LF-only request head");
        assert_eq!(head.content_length(), Ok(2));
    }

    #[test]
    fn rejects_malformed_request_heads() {
        for text in [
            "",
            "GET",
            "GET /",
            "GET / HTTP/2.0",
            "GET / HTTP/1.1 extra",
            "GET / HTTP/1.1\r\nBroken header\r\n\r\n",
        ] {
            assert!(parse_request_head(text).is_err(), "accepted {text:?}");
        }

        let bad_length = parse_request_head("GET / HTTP/1.1\r\nContent-Length: nope\r\n\r\n")
            .expect("request head is syntactically valid");
        assert_eq!(bad_length.content_length(), Err(ReadError::BadRequest));
    }

    #[test]
    fn routes_known_endpoints_and_rejects_everything_else() {
        assert_eq!(route("GET", "/"), Route::Index);
        assert_eq!(route("GET", "/api/status"), Route::Status);
        assert_eq!(route("GET", "/api/log?lines=40"), Route::Log(40));
        assert_eq!(route("POST", "/api/action"), Route::Action);

        assert_eq!(route("POST", "/api/status"), Route::NotFound);
        assert_eq!(route("GET", "/api/action"), Route::NotFound);
        assert_eq!(route("GET", "/favicon.ico"), Route::NotFound);
        assert_eq!(route("GET", "/api/status/"), Route::NotFound);
    }

    #[test]
    fn log_line_query_defaults_and_caps_at_200() {
        assert_eq!(log_lines_from_query(""), DEFAULT_LOG_LINES);
        assert_eq!(log_lines_from_query("lines=12"), 12);
        assert_eq!(log_lines_from_query("probe=1&lines=7"), 7);
        assert_eq!(log_lines_from_query("lines=0"), 1);
        assert_eq!(log_lines_from_query("lines=999"), MAX_LOG_LINES);
        assert_eq!(log_lines_from_query("lines=nope"), DEFAULT_LOG_LINES);
        assert_eq!(log_lines_from_query("lines="), DEFAULT_LOG_LINES);
    }

    #[test]
    fn json_string_escapes_quotes_backslashes_and_controls() {
        assert_eq!(
            json_string("a\"b\\c\n\r\t\u{0008}\u{000c}\u{0001}"),
            "\"a\\\"b\\\\c\\n\\r\\t\\b\\f\\u0001\""
        );
        assert_eq!(json_string("plain / ünïcode"), "\"plain / ünïcode\"");
    }

    #[test]
    fn json_array_escapes_each_member() {
        assert_eq!(json_array(["a", "b\"c"]), "[\"a\",\"b\\\"c\"]");
        assert_eq!(json_array(std::iter::empty()), "[]");
    }

    #[test]
    fn action_json_accepts_extra_members_and_escapes() {
        assert_eq!(
            json_string_member(br#"{"action":"lock"}"#, "action"),
            Ok("lock".to_owned())
        );
        assert_eq!(
            json_string_member(
                br#" { "action" : "shutdown", "meta": {"nested": [1, true, null]} } "#,
                "action"
            ),
            Ok("shutdown".to_owned())
        );
        assert_eq!(
            json_string_member(br#"{"action":"\u006cock"}"#, "action"),
            Ok("lock".to_owned())
        );
    }

    #[test]
    fn action_json_rejects_malformed_or_non_string_values() {
        for body in [
            br#"{"action":42}"#.as_slice(),
            br#"{"action":"lock",}"#.as_slice(),
            br#"{"action":"lock"} trailing"#.as_slice(),
            br#"{"nope":"lock"}"#.as_slice(),
            br#"[]"#.as_slice(),
            br#"{"action":"lock""#.as_slice(),
        ] {
            assert!(
                json_string_member(body, "action").is_err(),
                "accepted {body:?}"
            );
        }
    }

    #[test]
    fn action_body_validates_against_the_power_action_whitelist() {
        assert_eq!(
            action_from_body(br#"{"action":"lock"}"#),
            Ok(PowerAction::Lock)
        );
        assert_eq!(
            action_from_body(br#"{"action":"reboot:192.168.1.50"}"#),
            Ok(PowerAction::Reboot)
        );
        assert!(
            action_from_body(br#"{"action":"explode"}"#)
                .expect_err("unknown action must be rejected")
                .contains("unknown command")
        );
        assert!(action_from_body(br#"{"action":42}"#).is_err());
    }

    #[test]
    fn origin_check_only_accepts_the_same_loopback_authority() {
        assert!(origin_allowed("http://127.0.0.1:9878", 9878));
        assert!(origin_allowed("http://localhost:9878", 9878));

        assert!(!origin_allowed("http://127.0.0.1:9879", 9878));
        assert!(!origin_allowed("http://localhost:9878/", 9878));
        assert!(!origin_allowed("https://127.0.0.1:9878", 9878));
        assert!(!origin_allowed("null", 9878));
        assert!(!origin_allowed("http://evil.example:9878", 9878));
    }

    #[test]
    fn origin_check_accepts_default_port_forms_on_port_80() {
        for origin in [
            "http://127.0.0.1",
            "http://localhost",
            "http://127.0.0.1:80",
            "http://localhost:80",
        ] {
            assert!(origin_allowed(origin, 80), "{origin} should be allowed");
        }
        assert!(!origin_allowed("http://127.0.0.1:8080", 80));
        assert!(!origin_allowed("https://127.0.0.1", 80));
        assert!(!origin_allowed("http://evil.example", 80));
    }

    #[test]
    fn host_check_rejects_foreign_authorities() {
        assert!(
            !host_allowed(None, 9878),
            "a request without a Host header must be rejected"
        );
        assert!(host_allowed(Some("127.0.0.1:9878"), 9878));
        assert!(host_allowed(Some("LOCALHOST:9878"), 9878));
        assert!(!host_allowed(Some("evil.example:9878"), 9878));
        assert!(!host_allowed(Some("127.0.0.1:9999"), 9878));
        assert!(host_allowed(Some("127.0.0.1"), 80));
        assert!(host_allowed(Some("localhost"), 80));
    }

    #[test]
    fn header_boundary_uses_the_earliest_terminator() {
        assert_eq!(
            find_header_boundary(b"GET / HTTP/1.1\r\n\r\n"),
            Some((14, 4))
        );
        assert_eq!(find_header_boundary(b"GET / HTTP/1.1\n\n"), Some((14, 2)));
        // A stray LF LF before the real CRLF CRLF must win, not be skipped over.
        assert_eq!(
            find_header_boundary(b"A: b\n\nHost: x\r\n\r\n"),
            Some((4, 2))
        );
        assert_eq!(find_header_boundary(b"no terminator"), None);
    }

    #[test]
    fn rejects_transfer_encoding_and_duplicate_security_headers() {
        for text in [
            "POST /api/action HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n",
            "POST /api/action HTTP/1.1\r\nHost: 127.0.0.1:9878\r\nHost: 127.0.0.1:9878\r\n\r\n",
            "POST /api/action HTTP/1.1\r\nOrigin: http://127.0.0.1:9878\r\nOrigin: http://127.0.0.1:9878\r\n\r\n",
            "POST /api/action HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n",
            "POST /api/action HTTP/1.1\r\nContent-Type: application/json\r\nContent-Type: application/json\r\n\r\n",
        ] {
            assert!(parse_request_head(text).is_err(), "accepted {text:?}");
        }
    }

    #[test]
    fn deeply_nested_json_is_rejected() {
        let depth = MAX_JSON_DEPTH * 4;
        let body = format!("\"x\":{}{}", "[".repeat(depth), "]".repeat(depth));
        let wrapped = format!("{{{body}}}");
        assert!(
            json_string_member(wrapped.as_bytes(), "action").is_err(),
            "deeply nested JSON must not be walked recursively forever"
        );

        assert_eq!(
            json_string_member(br#"{"meta":[[[{"a":[1,2,3]}]]],"action":"lock"}"#, "action"),
            Ok("lock".to_owned())
        );
    }

    #[test]
    fn read_log_tail_reads_only_the_window_and_drops_partial_lines() {
        use std::io::Write as _;

        let dir = crate::test_support::TempDir::new("web-log-tail");
        let path = dir.path().join("pc_onoff.log");
        let mut file = std::fs::File::create(&path).expect("create log");
        // 400 lines of ~200 bytes => ~80 KB, comfortably above the 64 KB window.
        for index in 0..400 {
            writeln!(
                file,
                "[2026-11-19 12:00:00] [test] line-{index:03}-{}",
                "x".repeat(170)
            )
            .expect("write log");
        }
        drop(file);

        let tail = read_log_tail(&path, 5).expect("read tail");
        assert_eq!(tail.len(), 5);
        assert!(
            tail.last().expect("last").contains("line-399"),
            "tail was: {tail:?}"
        );
        assert!(
            tail.first().expect("first").starts_with("[2026-11-19"),
            "first line must be complete, not a partial fragment: {tail:?}"
        );

        let all = read_log_tail(&path, 1000).expect("read all");
        assert!(
            (100..400).contains(&all.len()),
            "windowed read should return only recent lines, got {}",
            all.len()
        );
    }

    #[test]
    fn content_type_must_be_json() {
        assert!(is_json_content_type("application/json"));
        assert!(is_json_content_type("Application/JSON; charset=utf-8"));
        assert!(!is_json_content_type("text/plain"));
        assert!(!is_json_content_type("application/jsonp"));
        assert!(!is_json_content_type(""));
    }

    #[test]
    fn tail_lines_returns_the_requested_suffix() {
        let content = "one\ntwo\nthree\nfour\n";
        assert_eq!(tail_lines(content, 2), vec!["three", "four"]);
        assert_eq!(tail_lines(content, 99), vec!["one", "two", "three", "four"]);
        assert!(tail_lines(content, 0).is_empty());
        assert!(tail_lines("", 10).is_empty());
    }

    #[test]
    fn ipv4_merging_deduplicates_and_drops_loopback() {
        let mut addresses = vec!["10.0.0.9".to_owned()];
        merge_ipv4_tokens(
            &mut addresses,
            "inet 192.168.1.5, 10.0.0.2 192.168.1.5 127.0.0.1 0.0.0.0 nope",
        );
        assert_eq!(addresses, vec!["10.0.0.9", "192.168.1.5", "10.0.0.2"]);
    }

    #[test]
    fn dashboard_port_env_parsing_is_lenient() {
        assert_eq!(resolve_port(None), DEFAULT_WEB_PORT);
        assert_eq!(resolve_port(Some("9999")), 9999);
        assert_eq!(resolve_port(Some(" 9878 ")), 9878);
        assert_eq!(resolve_port(Some("0")), DEFAULT_WEB_PORT);
        assert_eq!(resolve_port(Some("nope")), DEFAULT_WEB_PORT);
    }

    #[test]
    fn embedded_dashboard_contains_the_frost_shell() {
        assert!(DASHBOARD_HTML.contains("<title>PC Power</title>"));
        assert!(DASHBOARD_HTML.contains("backdrop-filter"));
        assert!(DASHBOARD_HTML.contains("prefers-color-scheme: dark"));
        assert!(DASHBOARD_HTML.contains("prefers-reduced-motion"));
    }
}
