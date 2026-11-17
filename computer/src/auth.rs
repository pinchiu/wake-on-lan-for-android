//! Command authentication for remote power commands.
//!
//! Wire envelope (UTF-8, maximum [`MAX_ENVELOPE_BYTES`] bytes):
//!
//! ```text
//! <command>|ts=<unix-seconds>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>
//! ```
//!
//! The signature covers `<command>|ts=<ts>|nonce=<nonce>`, exactly as sent, with
//! HMAC-SHA256 keyed by the shared command secret. The timestamp must be within
//! [`TIMESTAMP_WINDOW_SECONDS`] of local time, and each nonce may be used only once within
//! [`NONCE_TTL_SECONDS`] (bounded by [`MAX_REPLAY_ENTRIES`]).
//!
//! There is intentionally **no unsigned fallback**: a blank secret makes the verifier reject
//! every command with [`AuthError::NotConfigured`] and the signer refuse to produce one.
//! This module never logs; callers log [`AuthError::reason`], which contains no user input.

use std::collections::HashMap;
use std::fmt;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::crypto;

/// Maximum accepted envelope size in bytes.
pub const MAX_ENVELOPE_BYTES: usize = 512;

/// Accepted clock skew between signer and verifier.
pub const TIMESTAMP_WINDOW_SECONDS: u64 = 300;

/// How long a nonce is remembered to prevent replay.
pub const NONCE_TTL_SECONDS: u64 = 600;

/// Upper bound on remembered nonces; keeps memory use fixed.
pub const MAX_REPLAY_ENTRIES: usize = 1024;

const NONCE_HEX_CHARS: usize = 16;
const SIGNATURE_HEX_CHARS: usize = 64;

/// Why an envelope was rejected.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AuthError {
    /// No authentication envelope (or required field) was supplied.
    Missing,
    /// The HMAC does not match.
    BadSignature,
    /// Timestamp is outside the allowed clock-skew window (past or future).
    Expired,
    /// The nonce was already accepted recently.
    Replayed,
    /// No command secret is configured; unsigned fallback is not allowed.
    NotConfigured,
    /// Envelope structure is invalid (unknown/duplicate fields, bad hex, oversized, ...).
    Malformed,
}

impl AuthError {
    /// Stable, input-free token for `auth_rejected` log lines.
    pub const fn reason(self) -> &'static str {
        match self {
            Self::Missing => "missing",
            Self::BadSignature => "bad_signature",
            Self::Expired => "expired",
            Self::Replayed => "replayed",
            Self::NotConfigured => "not_configured",
            Self::Malformed => "malformed",
        }
    }
}

impl fmt::Display for AuthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Missing => "authentication envelope missing",
            Self::BadSignature => "authentication signature mismatch",
            Self::Expired => "authentication timestamp outside the allowed window",
            Self::Replayed => "authentication nonce already used",
            Self::NotConfigured => "command secret is not configured",
            Self::Malformed => "malformed authentication envelope",
        })
    }
}

impl std::error::Error for AuthError {}

/// Sign `command` into a complete envelope.
///
/// `nonce` must be 16 hex characters (see [`generate_nonce`]); `timestamp` is Unix seconds.
/// A blank secret is an error, never a request to send unsigned.
pub fn sign(command: &str, secret: &str, timestamp: u64, nonce: &str) -> Result<String, AuthError> {
    if secret.trim().is_empty() {
        return Err(AuthError::NotConfigured);
    }
    if command.is_empty()
        || command.trim() != command
        || command.contains('|')
        || command.chars().any(char::is_control)
    {
        return Err(AuthError::Malformed);
    }
    if !is_hex_of_length(nonce, NONCE_HEX_CHARS) {
        return Err(AuthError::Malformed);
    }

    let message = sign_input(command, timestamp, nonce);
    let signature = crypto::hmac_sha256(secret.as_bytes(), message.as_bytes());
    let envelope = format!(
        "{command}|ts={timestamp}|nonce={nonce}|sig={}",
        crypto::hex_encode(&signature)
    );
    if envelope.len() > MAX_ENVELOPE_BYTES {
        return Err(AuthError::Malformed);
    }
    Ok(envelope)
}

/// Verify `envelope` and return the bare command it authenticates.
///
/// On success the nonce is remembered in `replay`; on any failure nothing is remembered.
pub fn verify(
    envelope: &str,
    secret: &str,
    now: u64,
    replay: &mut ReplayGuard,
) -> Result<String, AuthError> {
    if secret.trim().is_empty() {
        return Err(AuthError::NotConfigured);
    }

    let envelope = envelope.trim();
    if envelope.is_empty() {
        return Err(AuthError::Missing);
    }
    if envelope.len() > MAX_ENVELOPE_BYTES {
        return Err(AuthError::Malformed);
    }

    let parsed = ParsedEnvelope::parse(envelope)?;
    if now.abs_diff(parsed.timestamp) > TIMESTAMP_WINDOW_SECONDS {
        return Err(AuthError::Expired);
    }

    let message = sign_input(parsed.command, parsed.timestamp, parsed.nonce);
    let expected = crypto::hmac_sha256(secret.as_bytes(), message.as_bytes());
    let provided = crypto::hex_decode(parsed.signature).ok_or(AuthError::Malformed)?;
    if !crypto::constant_time_eq(&expected, &provided) {
        return Err(AuthError::BadSignature);
    }

    if !replay.accept(parsed.nonce, now) {
        return Err(AuthError::Replayed);
    }

    Ok(parsed.command.to_owned())
}

/// Current Unix time in seconds, saturating at 0 for clocks before the epoch.
pub fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or(0)
}

/// Generate a random 16-character lowercase hex nonce (64 bits).
pub fn generate_nonce() -> String {
    use std::sync::atomic::{AtomicU64, Ordering};

    static FALLBACK_COUNTER: AtomicU64 = AtomicU64::new(0);

    let mut bytes = [0u8; 8];
    if crypto::fill_random(&mut bytes) {
        return crypto::hex_encode(&bytes);
    }

    // Fallback for platforms without an OS random source: uniqueness matters most for a
    // nonce; the HMAC still ensures only the secret holder can mint an accepted envelope.
    let mut material = Vec::with_capacity(24);
    material.extend_from_slice(&unix_now().to_le_bytes());
    material.extend_from_slice(&std::process::id().to_le_bytes());
    material.extend_from_slice(
        &FALLBACK_COUNTER
            .fetch_add(1, Ordering::Relaxed)
            .to_le_bytes(),
    );
    let digest = crypto::sha256(&material);
    crypto::hex_encode(&digest[..8])
}

fn sign_input(command: &str, timestamp: u64, nonce: &str) -> String {
    format!("{command}|ts={timestamp}|nonce={nonce}")
}

/// Timestamp of a structurally valid envelope. Used by transports that need to reject
/// envelopes minted before the current process (e.g. a captured reboot replayed after boot).
pub fn timestamp(envelope: &str) -> Option<u64> {
    ParsedEnvelope::parse(envelope)
        .ok()
        .map(|parsed| parsed.timestamp)
}

fn is_hex_of_length(value: &str, length: usize) -> bool {
    value.len() == length && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

struct ParsedEnvelope<'a> {
    command: &'a str,
    timestamp: u64,
    nonce: &'a str,
    signature: &'a str,
}

impl<'a> ParsedEnvelope<'a> {
    fn parse(envelope: &'a str) -> Result<Self, AuthError> {
        let mut fields = envelope.split('|');
        let command = fields.next().unwrap_or_default();
        if command.is_empty() {
            return Err(AuthError::Missing);
        }

        let mut timestamp = None;
        let mut nonce = None;
        let mut signature = None;

        for field in fields {
            let Some((name, value)) = field.split_once('=') else {
                return Err(AuthError::Malformed);
            };
            match name {
                "ts" => {
                    if timestamp.is_some() {
                        return Err(AuthError::Malformed);
                    }
                    timestamp = Some(value.parse::<u64>().map_err(|_| AuthError::Malformed)?);
                }
                "nonce" => {
                    if nonce.is_some() {
                        return Err(AuthError::Malformed);
                    }
                    if !is_hex_of_length(value, NONCE_HEX_CHARS) {
                        return Err(AuthError::Malformed);
                    }
                    nonce = Some(value);
                }
                "sig" => {
                    if signature.is_some() {
                        return Err(AuthError::Malformed);
                    }
                    if !is_hex_of_length(value, SIGNATURE_HEX_CHARS) {
                        return Err(AuthError::Malformed);
                    }
                    signature = Some(value);
                }
                _ => return Err(AuthError::Malformed),
            }
        }

        Ok(Self {
            command,
            timestamp: timestamp.ok_or(AuthError::Missing)?,
            nonce: nonce.ok_or(AuthError::Missing)?,
            signature: signature.ok_or(AuthError::Missing)?,
        })
    }
}

/// Bounded nonce cache that rejects replays for [`NONCE_TTL_SECONDS`].
///
/// Entries are pruned on every check. If the cache is full of unexpired entries, the entry
/// closest to expiry is evicted so new authenticated commands keep working; the cache can
/// never grow beyond its configured capacity.
#[derive(Debug)]
pub struct ReplayGuard {
    entries: HashMap<String, u64>,
    capacity: usize,
    ttl_seconds: u64,
}

impl ReplayGuard {
    /// Guard with the protocol defaults (1024 entries, 600 seconds).
    pub fn new() -> Self {
        Self::with_limits(MAX_REPLAY_ENTRIES, NONCE_TTL_SECONDS)
    }

    /// Guard with explicit limits (used by tests and future transports).
    pub fn with_limits(capacity: usize, ttl_seconds: u64) -> Self {
        Self {
            entries: HashMap::new(),
            capacity,
            ttl_seconds,
        }
    }

    /// Remember `nonce` until `now + ttl`. Returns `false` when it was already present.
    pub fn accept(&mut self, nonce: &str, now: u64) -> bool {
        self.entries.retain(|_, expires_at| *expires_at > now);

        if self.entries.contains_key(nonce) {
            return false;
        }
        if self.capacity == 0 {
            return false;
        }
        if self.entries.len() >= self.capacity
            && let Some(expiring_soonest) = self
                .entries
                .iter()
                .min_by_key(|(_, expires_at)| **expires_at)
                .map(|(nonce, _)| nonce.clone())
        {
            self.entries.remove(&expiring_soonest);
        }

        self.entries
            .insert(nonce.to_owned(), now.saturating_add(self.ttl_seconds));
        true
    }
}

impl Default for ReplayGuard {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SECRET: &str = "0123456789abcdef0123456789abcdef";
    const OTHER_SECRET: &str = "fedcba9876543210fedcba9876543210";
    const NOW: u64 = 1_700_000_000;
    const NONCE: &str = "0011223344556677";

    fn signed(command: &str, timestamp: u64, nonce: &str) -> String {
        sign(command, SECRET, timestamp, nonce).expect("sign envelope")
    }

    fn verify_at(envelope: &str, secret: &str, now: u64) -> Result<String, AuthError> {
        verify(envelope, secret, now, &mut ReplayGuard::new())
    }

    fn dummy_signature() -> String {
        "0".repeat(SIGNATURE_HEX_CHARS)
    }

    #[test]
    fn round_trip_returns_the_bare_command() {
        let envelope = signed("SHUTDOWN:192.168.1.10", NOW, NONCE);
        assert_eq!(
            verify_at(&envelope, SECRET, NOW),
            Ok("SHUTDOWN:192.168.1.10".to_owned())
        );
    }

    // Fixed vector shared with the Kotlin test suites; the expected signature was produced
    // by an independent HMAC-SHA256 implementation.
    #[test]
    fn signs_the_fixed_cross_language_vector() {
        let envelope = signed("SHUTDOWN:192.168.1.10", 1_700_000_000, NONCE);
        assert_eq!(
            envelope,
            "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677|sig=6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc"
        );
        assert_eq!(
            verify_at(&envelope, SECRET, 1_700_000_000),
            Ok("SHUTDOWN:192.168.1.10".to_owned())
        );
    }

    #[test]
    fn tampering_each_field_is_rejected() {
        let envelope = signed("shutdown", NOW, NONCE);

        let tampered_command = envelope.replacen("shutdown", "reboot", 1);
        assert_eq!(
            verify_at(&tampered_command, SECRET, NOW),
            Err(AuthError::BadSignature)
        );

        let tampered_timestamp = envelope.replacen("ts=1700000000", "ts=1700000001", 1);
        assert_eq!(
            verify_at(&tampered_timestamp, SECRET, NOW),
            Err(AuthError::BadSignature)
        );

        let tampered_nonce = envelope.replacen(NONCE, "0011223344556678", 1);
        assert_eq!(
            verify_at(&tampered_nonce, SECRET, NOW),
            Err(AuthError::BadSignature)
        );

        let mut tampered_signature = envelope.clone();
        let last = tampered_signature.pop().expect("signature hex digit");
        tampered_signature.push(if last == '0' { '1' } else { '0' });
        assert_eq!(
            verify_at(&tampered_signature, SECRET, NOW),
            Err(AuthError::BadSignature)
        );
    }

    #[test]
    fn expired_timestamps_are_rejected() {
        assert_eq!(
            verify_at(&signed("shutdown", NOW - 301, NONCE), SECRET, NOW),
            Err(AuthError::Expired)
        );

        let past_edge = signed("shutdown", NOW - TIMESTAMP_WINDOW_SECONDS, NONCE);
        assert_eq!(
            verify_at(&past_edge, SECRET, NOW),
            Ok("shutdown".to_owned())
        );
    }

    #[test]
    fn future_timestamps_are_rejected() {
        assert_eq!(
            verify_at(&signed("shutdown", NOW + 301, NONCE), SECRET, NOW),
            Err(AuthError::Expired)
        );

        let future_edge = signed("shutdown", NOW + TIMESTAMP_WINDOW_SECONDS, NONCE);
        assert_eq!(
            verify_at(&future_edge, SECRET, NOW),
            Ok("shutdown".to_owned())
        );
    }

    #[test]
    fn replayed_nonces_are_rejected() {
        let envelope = signed("shutdown", NOW, NONCE);
        let mut replay = ReplayGuard::new();

        assert_eq!(
            verify(&envelope, SECRET, NOW, &mut replay),
            Ok("shutdown".to_owned())
        );
        assert_eq!(
            verify(&envelope, SECRET, NOW, &mut replay),
            Err(AuthError::Replayed)
        );
    }

    #[test]
    fn nonces_are_forgotten_after_the_ttl() {
        let mut replay = ReplayGuard::new();
        let first = signed("shutdown", NOW, NONCE);
        assert!(verify(&first, SECRET, NOW, &mut replay).is_ok());

        let later = NOW + NONCE_TTL_SECONDS + 1;
        let reused = signed("shutdown", later, NONCE);
        assert_eq!(
            verify(&reused, SECRET, later, &mut replay),
            Ok("shutdown".to_owned())
        );
    }

    #[test]
    fn wrong_secret_is_rejected() {
        let envelope = signed("shutdown", NOW, NONCE);
        assert_eq!(
            verify_at(&envelope, OTHER_SECRET, NOW),
            Err(AuthError::BadSignature)
        );
    }

    #[test]
    fn blank_secret_never_falls_back_to_unsigned() {
        let envelope = signed("shutdown", NOW, NONCE);
        assert_eq!(verify_at(&envelope, "", NOW), Err(AuthError::NotConfigured));
        assert_eq!(
            verify_at(&envelope, "   ", NOW),
            Err(AuthError::NotConfigured)
        );
        assert_eq!(
            sign("shutdown", "", NOW, NONCE),
            Err(AuthError::NotConfigured)
        );
        assert_eq!(
            sign("shutdown", "   ", NOW, NONCE),
            Err(AuthError::NotConfigured)
        );
    }

    #[test]
    fn missing_and_malformed_envelopes_are_rejected() {
        let signature = dummy_signature();
        let valid = format!("WAKE|ts={NOW}|nonce={NONCE}|sig={signature}");

        let cases = [
            ("", AuthError::Missing),
            ("   ", AuthError::Missing),
            ("WAKE", AuthError::Missing),
            ("WAKE|ts=1700000000", AuthError::Missing),
            (
                "WAKE|ts=1700000000|nonce=0011223344556677",
                AuthError::Missing,
            ),
            (
                "|ts=1700000000|nonce=0011223344556677|sig=",
                AuthError::Missing,
            ),
            (
                "WAKE|ts=abc|nonce=0011223344556677|sig=",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=001122334455667|sig=",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=0011223344556677|sig=short",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=00112233445566zz|sig=",
                AuthError::Malformed,
            ),
            ("WAKE|ts=1700000000|nonce", AuthError::Malformed),
            (
                "WAKE|ts=1700000000|ts=1700000000|nonce=0011223344556677|sig=",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=0011223344556677|nonce=0011223344556677|sig=",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=0011223344556677|sig=|sig=",
                AuthError::Malformed,
            ),
            (
                "WAKE|ts=1700000000|nonce=0011223344556677|sig=|extra=1",
                AuthError::Malformed,
            ),
        ];

        for (envelope, expected) in cases {
            assert_eq!(
                verify_at(envelope, SECRET, NOW),
                Err(expected),
                "envelope: {envelope}"
            );
        }

        // Structurally valid (wrong HMAC is checked separately from malformed).
        assert_eq!(verify_at(&valid, SECRET, NOW), Err(AuthError::BadSignature));
    }

    #[test]
    fn maximum_envelope_length_is_enforced() {
        let oversized = "A".repeat(MAX_ENVELOPE_BYTES + 1);
        assert_eq!(
            verify_at(&oversized, SECRET, NOW),
            Err(AuthError::Malformed)
        );

        let oversized_command = "A".repeat(MAX_ENVELOPE_BYTES);
        assert_eq!(
            sign(&oversized_command, SECRET, NOW, NONCE),
            Err(AuthError::Malformed)
        );

        // "|ts=" + 10 digits + "|nonce=" + 16 + "|sig=" + 64 = 106 bytes of overhead.
        let command = "A".repeat(MAX_ENVELOPE_BYTES - 106);
        let envelope = sign(&command, SECRET, NOW, NONCE).expect("boundary envelope");
        assert_eq!(envelope.len(), MAX_ENVELOPE_BYTES);
        assert_eq!(verify_at(&envelope, SECRET, NOW), Ok(command));
    }

    #[test]
    fn signer_rejects_invalid_input() {
        assert_eq!(
            sign("shutdown", SECRET, NOW, "not-a-nonce"),
            Err(AuthError::Malformed)
        );
        assert_eq!(
            sign("shutdown", SECRET, NOW, "001122334455667"),
            Err(AuthError::Malformed)
        );
        assert_eq!(
            sign("shut|down", SECRET, NOW, NONCE),
            Err(AuthError::Malformed)
        );
        assert_eq!(
            sign(" shutdown", SECRET, NOW, NONCE),
            Err(AuthError::Malformed)
        );
        assert_eq!(
            sign("shut\ndown", SECRET, NOW, NONCE),
            Err(AuthError::Malformed)
        );
        assert_eq!(sign("", SECRET, NOW, NONCE), Err(AuthError::Malformed));
    }

    #[test]
    fn generated_nonces_are_16_lowercase_hex() {
        let nonce = generate_nonce();
        assert_eq!(nonce.len(), NONCE_HEX_CHARS);
        assert!(nonce.bytes().all(|byte| byte.is_ascii_hexdigit()));
        assert!(!nonce.bytes().any(|byte| byte.is_ascii_uppercase()));
        assert_ne!(nonce, generate_nonce());
        assert_eq!(
            sign("shutdown", SECRET, NOW, &nonce),
            sign("shutdown", SECRET, NOW, &nonce)
        );
    }

    #[test]
    fn replay_guard_respects_its_capacity() {
        let mut guard = ReplayGuard::with_limits(2, NONCE_TTL_SECONDS);
        assert!(guard.accept("a", NOW));
        assert!(guard.accept("b", NOW));
        assert!(!guard.accept("a", NOW));
        assert!(guard.accept("c", NOW));
        assert_eq!(guard.entries.len(), 2);

        let mut disabled = ReplayGuard::with_limits(0, NONCE_TTL_SECONDS);
        assert!(!disabled.accept("a", NOW));
    }

    #[test]
    fn old_nonces_are_pruned_even_below_capacity() {
        let mut guard = ReplayGuard::with_limits(MAX_REPLAY_ENTRIES, NONCE_TTL_SECONDS);
        assert!(guard.accept("a", NOW));
        assert!(guard.accept("a", NOW + NONCE_TTL_SECONDS + 1));
        assert_eq!(guard.entries.len(), 1);
    }

    #[test]
    fn reasons_are_stable_log_tokens() {
        assert_eq!(AuthError::Missing.reason(), "missing");
        assert_eq!(AuthError::BadSignature.reason(), "bad_signature");
        assert_eq!(AuthError::Expired.reason(), "expired");
        assert_eq!(AuthError::Replayed.reason(), "replayed");
        assert_eq!(AuthError::NotConfigured.reason(), "not_configured");
        assert_eq!(AuthError::Malformed.reason(), "malformed");
    }
}
