"""Pure-Python wire protocol for the ``pc_onoff`` Home Assistant integration.

This is a Layer 1 domain module: it imports **only** the Python standard
library and never imports ``homeassistant``.  The wire formats match the Rust
daemon in ``computer/`` and the two Android apps bit-for-bit:

* Wake-on-LAN magic packet: ``6 x 0xFF`` followed by the 6-byte MAC address
  repeated 16 times (102 bytes) on UDP (default port 9).
* Power command on UDP 9877: the bare lowercase keyword
  (``shutdown``/``reboot``/``sleep``/``hibernate``/``lock``) when the target is
  in a trusted LAN/Tailscale range, otherwise the HMAC-SHA256 envelope::

      <command>|ts=<unix>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>

  The signature covers ``<command>|ts=<ts>|nonce=<nonce>`` exactly as sent,
  keyed by the shared command secret.

Secrets and signatures are never logged or included in exception messages.

The fixed cross-language test vector shared with ``computer/src/auth.rs`` is::

    sign_command(
        "SHUTDOWN:192.168.1.10",
        "0123456789abcdef0123456789abcdef",
        timestamp=1700000000,
        nonce="0011223344556677",
    ) == (
        "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677"
        "|sig=6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc"
    )
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import ipaddress
import re
import secrets
import socket
import time
from enum import Enum
from typing import Final

__all__ = [
    "MAX_ENVELOPE_BYTES",
    "MAC_COLON_RE",
    "MAC_DASH_RE",
    "PowerAction",
    "ProtocolError",
    "InvalidMacError",
    "InvalidTargetError",
    "AuthenticationRequiredError",
    "normalize_mac",
    "parse_mac_bytes",
    "build_magic_packet",
    "is_valid_unicast_ip",
    "is_trusted_address",
    "sign_command",
    "prepare_power_payload",
    "send_magic_packet_sync",
    "send_power_command_sync",
    "async_send_magic_packet",
    "async_send_power_command",
]

#: Maximum accepted size of a signed command envelope, in UTF-8 bytes.
MAX_ENVELOPE_BYTES: Final[int] = 512

#: Canonical colon-separated MAC address (exactly two hex digits per octet).
MAC_COLON_RE: Final[re.Pattern[str]] = re.compile(
    r"^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"
)

#: Tolerated dash-separated MAC address (exactly two hex digits per octet).
MAC_DASH_RE: Final[re.Pattern[str]] = re.compile(
    r"^([0-9A-Fa-f]{2}-){5}[0-9A-Fa-f]{2}$"
)

_NONCE_RE: Final[re.Pattern[str]] = re.compile(r"[0-9a-f]{16}")

# Trusted IPv4 ranges are listed explicitly.  ``IPv4Address.is_private`` must
# not be used: its behaviour changed across Python versions and it also covers
# non-RFC1918 ranges such as 192.0.2.0/24 and 198.18.0.0/15.
_TRUSTED_IPV4_NETWORKS: Final[tuple[ipaddress.IPv4Network, ...]] = (
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    ipaddress.ip_network("169.254.0.0/16"),
    # Tailscale / shared CGNAT space.
    ipaddress.ip_network("100.64.0.0/10"),
)

_IPV6_LOOPBACK: Final[ipaddress.IPv6Address] = ipaddress.IPv6Address("::1")
_IPV6_LINK_LOCAL: Final[ipaddress.IPv6Network] = ipaddress.ip_network("fe80::/10")
_IPV6_UNIQUE_LOCAL: Final[ipaddress.IPv6Network] = ipaddress.ip_network("fc00::/7")

_IPV4_BROADCAST: Final[str] = "255.255.255.255"


class PowerAction(str, Enum):
    """Power actions understood by the ``pc_onoff`` daemon (UDP 9877)."""

    SHUTDOWN = "shutdown"
    REBOOT = "reboot"
    SLEEP = "sleep"
    HIBERNATE = "hibernate"
    LOCK = "lock"


class ProtocolError(Exception):
    """Base error for invalid commands, secrets, targets or envelopes."""


class InvalidMacError(ProtocolError):
    """The supplied MAC address is not a valid 48-bit hardware address."""


class InvalidTargetError(ProtocolError):
    """The supplied IP address, broadcast address or port is not usable."""


class AuthenticationRequiredError(ProtocolError):
    """A public target requires a non-empty ``pc_secret``."""


def _contains_control_characters(value: str) -> bool:
    """Return ``True`` when *value* contains C0 or DEL control characters."""

    return any(ord(ch) < 32 or ord(ch) == 127 for ch in value)


def _validate_port(port: object) -> int:
    """Validate a UDP port number and return it unchanged."""

    if isinstance(port, bool) or not isinstance(port, int):
        raise InvalidTargetError(f"Invalid UDP port: {port!r}")
    if not 1 <= port <= 65535:
        raise InvalidTargetError(f"UDP port out of range: {port}")
    return port


def _require_action(action: object) -> PowerAction:
    """Validate that *action* is a :class:`PowerAction` member."""

    if not isinstance(action, PowerAction):
        raise ProtocolError(f"Unsupported power action: {action!r}")
    return action


def normalize_mac(mac: str) -> str:
    """Return the canonical uppercase colon-separated form of *mac*.

    Accepts colon- or dash-separated addresses with exactly two hex digits per
    octet, surrounded by optional whitespace.  Mixed separators, shortened or
    zero-padded octets and non-hex characters are rejected.

    Raises:
        InvalidMacError: when *mac* cannot be parsed as a 48-bit address.
    """

    if not isinstance(mac, str):
        raise InvalidMacError(f"Invalid MAC address: {mac!r}")

    candidate = mac.strip()
    if MAC_COLON_RE.fullmatch(candidate) is None and (
        MAC_DASH_RE.fullmatch(candidate) is None
    ):
        raise InvalidMacError(f"Invalid MAC address: {mac!r}")

    return candidate.replace("-", ":").upper()


def parse_mac_bytes(mac: str) -> bytes:
    """Return the six raw bytes encoded by *mac*.

    Raises:
        InvalidMacError: when *mac* is not a valid 48-bit address.
    """

    normalized = normalize_mac(mac)
    raw = bytes.fromhex(normalized.replace(":", ""))
    if len(raw) != 6:  # Defensive: unreachable after normalize_mac().
        raise InvalidMacError(f"Invalid MAC address: {mac!r}")
    return raw


def build_magic_packet(mac: str) -> bytes:
    """Build the 102-byte Wake-on-LAN magic packet for *mac*."""

    return b"\xff" * 6 + parse_mac_bytes(mac) * 16


def is_valid_unicast_ip(ip_str: str) -> bool:
    """Return ``True`` when *ip_str* is a usable unicast IP address.

    The unspecified addresses (``0.0.0.0``, ``::``), multicast ranges and the
    IPv4 limited broadcast address ``255.255.255.255`` are rejected.
    """

    if not isinstance(ip_str, str):
        return False
    try:
        addr = ipaddress.ip_address(ip_str.strip())
    except ValueError:
        return False

    if addr.is_unspecified or addr.is_multicast:
        return False
    if isinstance(addr, ipaddress.IPv4Address) and str(addr) == _IPV4_BROADCAST:
        return False
    return True


def is_trusted_address(ip_str: str) -> bool:
    """Return ``True`` for loopback, RFC1918, link-local, ULA and Tailscale.

    Addresses outside those ranges require the HMAC envelope.  IPv4-mapped
    IPv6 addresses (``::ffff:a.b.c.d``) are unwrapped before classification.
    The deprecated site-local range ``fec0::/10`` is **not** trusted.
    """

    if not isinstance(ip_str, str):
        return False
    try:
        addr = ipaddress.ip_address(ip_str.strip())
    except ValueError:
        return False

    if isinstance(addr, ipaddress.IPv6Address):
        mapped = addr.ipv4_mapped
        if mapped is not None:
            addr = mapped

    if isinstance(addr, ipaddress.IPv4Address):
        return any(addr in network for network in _TRUSTED_IPV4_NETWORKS)

    return (
        addr == _IPV6_LOOPBACK
        or addr in _IPV6_LINK_LOCAL
        or addr in _IPV6_UNIQUE_LOCAL
    )


def sign_command(
    command: str,
    secret: str,
    timestamp: int | None = None,
    nonce: str | None = None,
) -> str:
    """Sign *command* into a complete HMAC-SHA256 wire envelope.

    Args:
        command: Bare protocol keyword, for example ``"shutdown"``.
        secret: Shared command secret; surrounding whitespace is trimmed.
        timestamp: Unix seconds; defaults to the current time.
        nonce: 16 lowercase hex characters; defaults to a random 64-bit nonce.

    Raises:
        AuthenticationRequiredError: when *secret* is empty.
        ProtocolError: for control characters, malformed commands or nonces,
            negative timestamps, or an envelope larger than
            :data:`MAX_ENVELOPE_BYTES`.
    """

    if not isinstance(secret, str):
        raise ProtocolError("Secret must be a string")

    trimmed_secret = secret.strip()
    if not trimmed_secret:
        raise AuthenticationRequiredError("Command secret is empty")
    if _contains_control_characters(trimmed_secret):
        raise ProtocolError("Secret contains control characters")

    if (
        not isinstance(command, str)
        or not command
        or command.strip() != command
        or "|" in command
        or _contains_control_characters(command)
    ):
        raise ProtocolError("Invalid command")

    if timestamp is None:
        ts = int(time.time())
    else:
        try:
            ts = int(timestamp)
        except (TypeError, ValueError, OverflowError) as err:
            raise ProtocolError("Invalid timestamp") from err
    if ts < 0:
        raise ProtocolError("Timestamp must be non-negative")

    if nonce is None:
        nonce_val = secrets.token_hex(8)
    elif isinstance(nonce, str):
        nonce_val = nonce
    else:
        raise ProtocolError("Invalid nonce")
    if _NONCE_RE.fullmatch(nonce_val) is None:
        raise ProtocolError("Invalid nonce")

    sign_input = f"{command}|ts={ts}|nonce={nonce_val}"
    signature = hmac.new(
        trimmed_secret.encode("utf-8"),
        sign_input.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    envelope = f"{sign_input}|sig={signature}"

    if len(envelope.encode("utf-8")) > MAX_ENVELOPE_BYTES:
        raise ProtocolError("Envelope exceeds 512 bytes")

    return envelope


def prepare_power_payload(
    action: PowerAction,
    target_ip: str,
    secret: str = "",
    timestamp: int | None = None,
    nonce: str | None = None,
) -> bytes:
    """Return the UDP payload for *action* addressed to *target_ip*.

    Trusted LAN/Tailscale targets accept a bare keyword.  Any non-empty
    *secret* switches to the signed envelope; a public target without a secret
    fails closed.

    Raises:
        InvalidTargetError: when *target_ip* is not a usable unicast address.
        AuthenticationRequiredError: when a public target has no secret.
        ProtocolError: when signing fails.
    """

    if not isinstance(target_ip, str) or not is_valid_unicast_ip(target_ip):
        raise InvalidTargetError(f"Invalid unicast target address: {target_ip!r}")

    if not isinstance(secret, str):
        raise ProtocolError("Secret must be a string")
    trimmed_secret = secret.strip()

    if not trimmed_secret:
        if not is_trusted_address(target_ip):
            raise AuthenticationRequiredError(
                "PC secret is required for a public IP"
            )
        return _require_action(action).value.encode("utf-8")

    return sign_command(
        _require_action(action).value,
        trimmed_secret,
        timestamp=timestamp,
        nonce=nonce,
    ).encode("utf-8")


def send_magic_packet_sync(
    mac: str,
    broadcast_ip: str = "255.255.255.255",
    port: int = 9,
) -> None:
    """Broadcast a Wake-on-LAN magic packet (blocking).

    Raises:
        InvalidMacError: when *mac* is not a valid 48-bit address.
        InvalidTargetError: when *broadcast_ip* is not an IPv4 literal or
            *port* is outside ``1..65535``.
    """

    _validate_port(port)
    if not isinstance(broadcast_ip, str):
        raise InvalidTargetError(f"Invalid broadcast address: {broadcast_ip!r}")
    try:
        broadcast = str(ipaddress.IPv4Address(broadcast_ip.strip()))
    except ValueError as err:
        raise InvalidTargetError(
            f"Invalid broadcast IPv4 address: {broadcast_ip!r}"
        ) from err

    packet = build_magic_packet(mac)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.sendto(packet, (broadcast, port))


def send_power_command_sync(
    target_ip: str,
    action: PowerAction,
    port: int = 9877,
    secret: str = "",
) -> None:
    """Send one power command over UDP (blocking).

    Raises:
        InvalidTargetError: for a bad target address or port.
        AuthenticationRequiredError: for a public target without a secret.
        ProtocolError: when signing fails.
    """

    _validate_port(port)
    payload = prepare_power_payload(action, target_ip, secret=secret)
    addr = ipaddress.ip_address(target_ip.strip())
    family = socket.AF_INET6 if addr.version == 6 else socket.AF_INET
    with socket.socket(family, socket.SOCK_DGRAM) as sock:
        sock.sendto(payload, (str(addr), port))


async def async_send_magic_packet(
    mac: str,
    broadcast_ip: str = "255.255.255.255",
    port: int = 9,
) -> None:
    """Broadcast a Wake-on-LAN magic packet without blocking the event loop."""

    await asyncio.to_thread(
        send_magic_packet_sync,
        mac,
        broadcast_ip,
        port,
    )


async def async_send_power_command(
    target_ip: str,
    action: PowerAction,
    port: int = 9877,
    secret: str = "",
) -> None:
    """Send one power command over UDP without blocking the event loop."""

    await asyncio.to_thread(
        send_power_command_sync,
        target_ip,
        action,
        port,
        secret,
    )
