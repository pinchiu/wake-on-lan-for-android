"""Pure-Python validation for the ``pc_onoff`` config and options flows.

This is a Layer 1 domain module.  It imports **only** the Python standard
library and the sibling pure-Python modules :mod:`.const` and
:mod:`.protocol`; it must never import ``homeassistant`` or ``voluptuous`` so
that every rule below can be executed by ``python3 -m unittest`` without the
Home Assistant runtime.

The two public functions return ``(cleaned, errors)``:

* ``errors`` is empty when the input is valid.  When it is not, each key is
  the exact Home Assistant form field name and each value is one of the
  translation keys declared in ``strings.json`` (``invalid_mac``,
  ``invalid_host``, ``secret_required_for_public_ip``, ...).
* ``cleaned`` is a normalised copy that is safe to persist as config-entry
  data.  When ``errors`` is non-empty the caller must not store it; invalid
  numeric or enum fields fall back to their documented defaults so the
  returned mapping is always type-safe.

Security rules mirror ``protocol.prepare_power_payload`` exactly: a public IP
target requires a non-empty ``pc_secret`` and the flow fails closed.  Secrets
are validated for control characters but are never logged or echoed.
"""

from __future__ import annotations

import ipaddress
from collections.abc import Mapping
from typing import Any, Final

from .const import (
    CONF_BROADCAST_IP,
    CONF_HOST,
    CONF_MAC,
    CONF_NAME,
    CONF_OFF_ACTION,
    CONF_PC_SECRET,
    CONF_PROBE_MODE,
    CONF_PROBE_PORT,
    CONF_SCAN_INTERVAL,
    CONF_UDP_PORT,
    CONF_WOL_PORT,
    DEFAULT_BROADCAST_IP,
    DEFAULT_OFF_ACTION,
    DEFAULT_PROBE_MODE,
    DEFAULT_PROBE_PORT,
    DEFAULT_SCAN_INTERVAL,
    DEFAULT_UDP_PORT,
    DEFAULT_WOL_PORT,
    MAX_SCAN_INTERVAL,
    MIN_SCAN_INTERVAL,
    OFF_ACTIONS,
    PROBE_MODES,
)
from .protocol import (
    InvalidMacError,
    is_trusted_address,
    is_valid_unicast_ip,
    normalize_mac,
)

__all__ = ["validate_options_input", "validate_user_input"]

#: Fields that may change through the options flow.  ``name`` and ``mac`` are
#: deliberately immutable: they feed the entity IDs, the device registry and
#: therefore the Google Home voice names.
_OPTION_KEYS: Final[tuple[str, ...]] = (
    CONF_HOST,
    CONF_UDP_PORT,
    CONF_WOL_PORT,
    CONF_BROADCAST_IP,
    CONF_PC_SECRET,
    CONF_OFF_ACTION,
    CONF_PROBE_MODE,
    CONF_PROBE_PORT,
    CONF_SCAN_INTERVAL,
)

_DEFAULT_PORTS: Final[dict[str, int]] = {
    CONF_UDP_PORT: DEFAULT_UDP_PORT,
    CONF_WOL_PORT: DEFAULT_WOL_PORT,
    CONF_PROBE_PORT: DEFAULT_PROBE_PORT,
}

_MIN_PORT: Final[int] = 1
_MAX_PORT: Final[int] = 65535


def _as_text(value: Any, default: str = "") -> str:
    """Return *value* as stripped text, mapping ``None`` to *default*."""

    if value is None:
        return default
    return str(value).strip()


def _coerce_integer(value: Any) -> int | None:
    """Return *value* as an ``int`` when it is an exact whole number.

    The Home Assistant number selector may submit ``int``, integral ``float``
    or a numeric string depending on the frontend and selector mode, so all
    three are accepted.  Booleans are rejected (``bool`` is an ``int``
    subclass) and fractional numbers are refused rather than truncated.
    """

    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value) if value.is_integer() else None
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return int(text, 10)
        except ValueError:
            return None
    return None


def _has_control_characters(value: str) -> bool:
    """Return ``True`` when *value* contains C0 or DEL control characters."""

    return any(ord(char) < 32 or ord(char) == 127 for char in value)


def _validate_port(raw_value: Any, key: str, errors: dict[str, str]) -> int:
    """Validate one UDP/TCP port and return its numeric value.

    Invalid values are reported as ``invalid_port`` and replaced by the
    documented default for that field.
    """

    port = _coerce_integer(raw_value)
    if port is None or not _MIN_PORT <= port <= _MAX_PORT:
        errors[key] = "invalid_port"
        return _DEFAULT_PORTS[key]
    return port


def validate_user_input(
    user_input: Mapping[str, Any],
) -> tuple[dict[str, Any], dict[str, str]]:
    """Validate one initial config-flow submission.

    Every field is optional at the mapping level: missing keys fall back to
    the defaults in :mod:`.const` (``name`` falls back to ``"PC"``).  A blank
    ``pc_secret`` is accepted only for loopback, RFC1918, link-local, IPv6 ULA
    and Tailscale/CGNAT targets.  Public targets fail closed with
    ``secret_required_for_public_ip``.

    Returns:
        A ``(cleaned, errors)`` tuple.  ``errors`` maps form field names to
        translation keys; persist ``cleaned`` only when ``errors`` is empty.
    """

    errors: dict[str, str] = {}

    name = _as_text(user_input.get(CONF_NAME), "") or "PC"
    cleaned: dict[str, Any] = {CONF_NAME: name}

    raw_mac = _as_text(user_input.get(CONF_MAC), "")
    try:
        cleaned[CONF_MAC] = normalize_mac(raw_mac)
    except InvalidMacError:
        cleaned[CONF_MAC] = raw_mac
        errors[CONF_MAC] = "invalid_mac"

    host = _as_text(user_input.get(CONF_HOST), "")
    cleaned[CONF_HOST] = host
    if not is_valid_unicast_ip(host):
        errors[CONF_HOST] = "invalid_host"

    raw_broadcast = _as_text(
        user_input.get(CONF_BROADCAST_IP, DEFAULT_BROADCAST_IP)
    )
    cleaned[CONF_BROADCAST_IP] = raw_broadcast
    try:
        # The magic packet is IPv4-only, so the broadcast address must be a
        # real IPv4 literal (255.255.255.255 itself is allowed here).
        cleaned[CONF_BROADCAST_IP] = str(ipaddress.IPv4Address(raw_broadcast))
    except ValueError:
        errors[CONF_BROADCAST_IP] = "invalid_broadcast_ip"

    for key in (CONF_UDP_PORT, CONF_WOL_PORT, CONF_PROBE_PORT):
        cleaned[key] = _validate_port(
            user_input.get(key, _DEFAULT_PORTS[key]),
            key,
            errors,
        )

    scan_interval = _coerce_integer(
        user_input.get(CONF_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL)
    )
    if scan_interval is None or not (
        MIN_SCAN_INTERVAL <= scan_interval <= MAX_SCAN_INTERVAL
    ):
        errors[CONF_SCAN_INTERVAL] = "invalid_scan_interval"
        cleaned[CONF_SCAN_INTERVAL] = DEFAULT_SCAN_INTERVAL
    else:
        cleaned[CONF_SCAN_INTERVAL] = scan_interval

    off_action = _as_text(
        user_input.get(CONF_OFF_ACTION, DEFAULT_OFF_ACTION)
    )
    if off_action not in OFF_ACTIONS:
        errors[CONF_OFF_ACTION] = "invalid_off_action"
        cleaned[CONF_OFF_ACTION] = DEFAULT_OFF_ACTION
    else:
        cleaned[CONF_OFF_ACTION] = off_action

    probe_mode = _as_text(
        user_input.get(CONF_PROBE_MODE, DEFAULT_PROBE_MODE)
    )
    if probe_mode not in PROBE_MODES:
        errors[CONF_PROBE_MODE] = "invalid_probe_mode"
        cleaned[CONF_PROBE_MODE] = DEFAULT_PROBE_MODE
    else:
        cleaned[CONF_PROBE_MODE] = probe_mode

    pc_secret = _as_text(user_input.get(CONF_PC_SECRET), "")
    cleaned[CONF_PC_SECRET] = pc_secret
    if _has_control_characters(pc_secret):
        errors[CONF_PC_SECRET] = "invalid_secret"

    # Match protocol.prepare_power_payload(): a usable public host without a
    # secret would fail at runtime, so reject it during setup/options instead.
    if (
        CONF_HOST not in errors
        and not is_trusted_address(host)
        and not pc_secret
    ):
        errors[CONF_PC_SECRET] = "secret_required_for_public_ip"

    return cleaned, errors


def validate_options_input(
    options_input: Mapping[str, Any],
    base_data: Mapping[str, Any],
) -> tuple[dict[str, Any], dict[str, str]]:
    """Validate one options-flow submission for an existing config entry.

    ``name`` and ``mac`` are taken from *base_data* and can never be changed
    through options: keeping them stable preserves entity IDs, unique IDs and
    Google Home voice commands.  Missing mutable fields inherit their current
    value from *base_data*; every present field is validated with exactly the
    same rules as :func:`validate_user_input`, including the public-IP secret
    guard.

    Returns:
        A ``(cleaned_options, errors)`` tuple.  ``cleaned_options`` contains
        only the nine mutable option keys.
    """

    merged: dict[str, Any] = dict(base_data)
    merged.update(options_input)

    # Options must never override identity, even if a crafted payload sends
    # ``name`` or ``mac`` keys.  Base data always wins.
    merged[CONF_NAME] = base_data.get(CONF_NAME, "")
    merged[CONF_MAC] = base_data.get(CONF_MAC, "")

    cleaned, errors = validate_user_input(merged)

    # The options form only exposes mutable fields, so identity errors (which
    # would indicate corrupt entry data) cannot be fixed by the user and are
    # not surfaced here.
    option_errors = {
        key: value for key, value in errors.items() if key in _OPTION_KEYS
    }
    cleaned_options = {key: cleaned[key] for key in _OPTION_KEYS}
    return cleaned_options, option_errors
