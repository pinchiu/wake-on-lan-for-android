"""Home Assistant runtime glue for the ``pc_onoff`` integration.

This is a Layer 2 module: it may import ``homeassistant.*`` but delegates all
wire encoding to the pure :mod:`protocol` module, all reachability checks to
:mod:`probe`, and all optimistic-state decisions to :mod:`state`.

Responsibilities
----------------
* Turn a ConfigEntry/Options mapping into an immutable
  :class:`PcTargetConfig` (the only place option precedence is interpreted).
* Poll the target according to ``scan_interval`` and ``probe_mode`` and feed
  results through :class:`OptimisticStateTracker`, so a booting PC does not
  bounce the Google Home switch back to *off*.
* Send Wake-on-LAN magic packets (UDP 9) and signed/plain power commands
  (UDP 9877) with explicit optimistic holds for shutdown/sleep/hibernate.
* Schedule exactly one delayed reconciliation refresh (+26 s) per intent and
  cancel it cleanly on unload/reload.

Secrets are passed to :mod:`protocol` only; they are never logged here.
"""

from __future__ import annotations

import logging
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import timedelta
from typing import Any

from homeassistant.core import callback
from homeassistant.helpers.device_registry import CONNECTION_NETWORK_MAC, DeviceInfo
from homeassistant.helpers.event import async_call_later
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

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
    DOMAIN,
    MAX_SCAN_INTERVAL,
    MIN_SCAN_INTERVAL,
    OPTIMISTIC_HOLD_SECONDS,
    PROBE_MODE_NONE,
    PROBE_MODES,
)
from .probe import async_probe_host
from .protocol import (
    PowerAction,
    async_send_magic_packet,
    async_send_power_command,
    normalize_mac,
)
from .state import OptimisticStateTracker

_LOGGER = logging.getLogger(__name__)

#: Friendly name used when the ConfigEntry has no name (cannot happen through
#: the config flow, but keeps old/imported entries working).
_DEFAULT_NAME = "PC"

#: Actions that leave the machine off and therefore flip the switch to off.
_OFF_ACTIONS = (
    PowerAction.SHUTDOWN,
    PowerAction.SLEEP,
    PowerAction.HIBERNATE,
)

#: Extra delay on top of the optimistic hold before the reconciliation poll,
#: so a booting PC has the full hold window to answer before we look again.
_RECONCILE_MARGIN_SECONDS = 1.0

__all__ = ["PcTargetConfig", "PcOnOffCoordinator"]


def _clean_text(value: object) -> str:
    """Return a stripped string for *value*, treating ``None`` as ``""``."""

    if value is None:
        return ""
    return str(value).strip()


def _coerce_int(
    value: object,
    default: int,
    minimum: int,
    maximum: int,
) -> int:
    """Coerce *value* to an int inside ``[minimum, maximum]``.

    Malformed stored settings fall back to *default*; out-of-range numbers are
    clamped.  This keeps an entry that was written by an older version (or
    hand-edited ``.storage``) from crashing setup.
    """

    if value is None or isinstance(value, bool):
        return default
    try:
        parsed = int(str(value).strip()) if isinstance(value, str) else int(value)
    except (TypeError, ValueError):
        return default
    return max(minimum, min(maximum, parsed))


def _coerce_off_action(value: object) -> PowerAction:
    """Return the :class:`PowerAction` for *value*, failing closed.

    An unknown action must never silently turn a "sleep" into a "shutdown", so
    this raises instead of falling back to the default.  The config/options
    flow validates the value before it is stored; this guard only catches
    corrupted or hand-edited entries.
    """

    raw = _clean_text(value).lower() or DEFAULT_OFF_ACTION
    try:
        return PowerAction(raw)
    except ValueError as err:
        raise ValueError(f"Unsupported off_action: {raw!r}") from err


def _coerce_probe_mode(value: object) -> str:
    """Return a supported probe mode, defaulting to ping."""

    raw = _clean_text(value).lower()
    return raw if raw in PROBE_MODES else DEFAULT_PROBE_MODE


@dataclass(frozen=True)
class PcTargetConfig:
    """Immutable, validated view of one target PC's settings.

    Built from ``{**entry.data, **entry.options}`` so options always override
    the initial setup values, while ``name`` and ``mac`` remain stable for
    Google Home entity IDs.
    """

    name: str
    mac: str
    host: str
    udp_port: int
    wol_port: int
    broadcast_ip: str
    pc_secret: str
    off_action: PowerAction
    probe_mode: str
    probe_port: int
    scan_interval: int

    @classmethod
    def from_mapping(cls, data: Mapping[str, Any]) -> "PcTargetConfig":
        """Build a config from a ConfigEntry data/options mapping.

        Raises:
            InvalidMacError: when the MAC address is missing or malformed.
            ValueError: when ``off_action`` is not a supported action.
        """

        return cls(
            name=_clean_text(data.get(CONF_NAME)) or _DEFAULT_NAME,
            mac=normalize_mac(_clean_text(data.get(CONF_MAC))),
            host=_clean_text(data.get(CONF_HOST)),
            udp_port=_coerce_int(
                data.get(CONF_UDP_PORT),
                DEFAULT_UDP_PORT,
                1,
                65535,
            ),
            wol_port=_coerce_int(
                data.get(CONF_WOL_PORT),
                DEFAULT_WOL_PORT,
                1,
                65535,
            ),
            broadcast_ip=(
                _clean_text(data.get(CONF_BROADCAST_IP)) or DEFAULT_BROADCAST_IP
            ),
            pc_secret=_clean_text(data.get(CONF_PC_SECRET)),
            off_action=_coerce_off_action(data.get(CONF_OFF_ACTION)),
            probe_mode=_coerce_probe_mode(data.get(CONF_PROBE_MODE)),
            probe_port=_coerce_int(
                data.get(CONF_PROBE_PORT),
                DEFAULT_PROBE_PORT,
                1,
                65535,
            ),
            scan_interval=_coerce_int(
                data.get(CONF_SCAN_INTERVAL),
                DEFAULT_SCAN_INTERVAL,
                MIN_SCAN_INTERVAL,
                MAX_SCAN_INTERVAL,
            ),
        )


class PcOnOffCoordinator(DataUpdateCoordinator[bool | None]):
    """Poll one target PC and expose a bounce-free on/off state.

    ``data`` is ``True`` when the PC is reachable, ``False`` when it is not,
    and ``None`` when probing is disabled or inconclusive.
    """

    config: PcTargetConfig
    device_info: DeviceInfo

    def __init__(self, hass: Any, config: PcTargetConfig) -> None:
        self.config = config
        self.device_info = DeviceInfo(
            identifiers={(DOMAIN, config.mac)},
            name=config.name,
            manufacturer="pc_onoff",
            model="Target Computer",
            connections={(CONNECTION_NETWORK_MAC, config.mac)},
        )
        self._tracker = OptimisticStateTracker(
            hold_seconds=OPTIMISTIC_HOLD_SECONDS,
        )
        self._unsub_refresh: Callable[[], None] | None = None

        super().__init__(
            hass,
            _LOGGER,
            name=f"{DOMAIN}_{config.mac}",
            # With probing disabled the switch is optimistic-only, so polling
            # would only burn a timer for a guaranteed ``None`` result.
            update_interval=(
                None
                if config.probe_mode == PROBE_MODE_NONE
                else timedelta(seconds=config.scan_interval)
            ),
        )

    @property
    def assumed_state(self) -> bool:
        """Return ``True`` when the switch cannot know the real state.

        Google Home uses this to show the switch without claiming a verified
        state; it is only true when probing is disabled.
        """

        return self.config.probe_mode == PROBE_MODE_NONE

    async def _async_update_data(self) -> bool | None:
        """Probe the host and merge the result into the optimistic tracker."""

        try:
            probed = await async_probe_host(
                self.config.host,
                self.config.probe_mode,
                self.config.probe_port,
            )
        except Exception as err:  # noqa: BLE001 - surfaced as UpdateFailed
            # The probe module handles expected network errors itself; this
            # catches genuinely unexpected failures so the coordinator marks
            # the refresh as failed instead of killing the update task.
            raise UpdateFailed(
                f"Probing {self.config.host} failed: {err}"
            ) from err

        return self._tracker.reconcile(probed)

    async def async_wake(self) -> None:
        """Send a Wake-on-LAN magic packet and hold the switch optimistically on."""

        await async_send_magic_packet(
            self.config.mac,
            self.config.broadcast_ip,
            self.config.wol_port,
        )
        self.async_set_updated_data(self._tracker.record_intent(True))
        self._schedule_reconcile()

    async def async_turn_off(self) -> None:
        """Apply the configured turn-off action (shutdown/sleep/hibernate)."""

        await self.async_power(self.config.off_action)

    async def async_power(self, action: PowerAction) -> None:
        """Send one power command to the target PC over UDP 9877.

        Only shutdown/sleep/hibernate flip the switch off; ``LOCK`` keeps the
        PC on and ``REBOOT`` is transient, so neither changes the reported
        state or schedules a reconciliation poll.
        """

        await async_send_power_command(
            self.config.host,
            action,
            self.config.udp_port,
            self.config.pc_secret,
        )

        if action not in _OFF_ACTIONS:
            return

        self.async_set_updated_data(self._tracker.record_intent(False))
        self._schedule_reconcile()

    def _schedule_reconcile(self) -> None:
        """Schedule one delayed refresh to confirm the requested state.

        The refresh fires just after the optimistic hold window, replacing the
        normal polling timer for that period so intermediate boot/shutdown
        probes cannot bounce the entity.  With ``probe_mode == "none"`` there
        is nothing to confirm and no timer is scheduled.
        """

        if self.config.probe_mode == PROBE_MODE_NONE:
            return

        # Never stack reconciliation timers (rapid on/off taps).
        self.async_cancel_scheduled_refresh()

        @callback
        def _handle_reconcile(_now: Any) -> None:
            # The callback is one-shot: forget the handle before refreshing so
            # a failing refresh cannot leave a stale unsubscribe around.
            self._unsub_refresh = None
            self.hass.async_create_task(self.async_request_refresh())

        self._unsub_refresh = async_call_later(
            self.hass,
            OPTIMISTIC_HOLD_SECONDS + _RECONCILE_MARGIN_SECONDS,
            _handle_reconcile,
        )

    @callback
    def async_cancel_scheduled_refresh(self) -> None:
        """Cancel a pending reconciliation poll, if any.

        Safe to call repeatedly and from the unload path; a failing
        unsubscribe callback is logged at debug level but never blocks unload.
        """

        unsub = self._unsub_refresh
        self._unsub_refresh = None
        if unsub is None:
            return

        try:
            unsub()
        except Exception:  # noqa: BLE001 - defensive during shutdown
            _LOGGER.debug(
                "Could not cancel the scheduled refresh for %s",
                self.config.mac,
                exc_info=True,
            )
