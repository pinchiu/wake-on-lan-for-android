"""Unit tests for the ``pc_onoff`` state machine, coordinator and entry point.

Standard library only.  The thin ``homeassistant`` surface imported by
``coordinator.py`` is replaced with in-memory stubs *before* the module under
test is imported; the stubs are removed from ``sys.modules`` in ``tearDown``
so they cannot leak into other test modules or a real Home Assistant install.

Run with::

    python3 -m unittest discover -s tests/homeassistant -p "test_coordinator.py" -v
"""

from __future__ import annotations

import sys
import types
import unittest
from dataclasses import FrozenInstanceError
from datetime import timedelta
from typing import Any, Generic, TypeVar
from unittest.mock import AsyncMock, MagicMock, patch

# --------------------------------------------------------------------------- #
# Minimal Home Assistant stubs (installed before importing the code under test)
# --------------------------------------------------------------------------- #

_scheduled_calls: list[dict[str, Any]] = []
_installed_stubs: dict[str, types.ModuleType] = {}


def _stub_module(name: str) -> types.ModuleType:
    """Create *name* as an empty module unless it already exists."""

    existing = sys.modules.get(name)
    if existing is not None:
        return existing
    module = types.ModuleType(name)
    if name in ("homeassistant", "homeassistant.helpers"):
        module.__path__ = []  # type: ignore[attr-defined]
    sys.modules[name] = module
    _installed_stubs[name] = module
    return module


def _install_homeassistant_stubs() -> None:
    """Install the handful of HA symbols used by ``coordinator.py``."""

    ha = _stub_module("homeassistant")
    core = _stub_module("homeassistant.core")
    exceptions = _stub_module("homeassistant.exceptions")
    helpers = _stub_module("homeassistant.helpers")
    device_registry = _stub_module("homeassistant.helpers.device_registry")
    event = _stub_module("homeassistant.helpers.event")
    update_coordinator = _stub_module(
        "homeassistant.helpers.update_coordinator"
    )

    ha.core = core
    ha.exceptions = exceptions
    ha.helpers = helpers
    helpers.device_registry = device_registry
    helpers.event = event
    helpers.update_coordinator = update_coordinator

    class HomeAssistant:
        """Placeholder; tests supply their own ``hass`` double."""

        def __init__(self) -> None:
            self.data: dict[str, Any] = {}

        def async_create_task(self, target: Any, *args: Any, **kwargs: Any) -> Any:
            raise NotImplementedError("tests provide their own hass double")

    def callback(func: Any) -> Any:
        return func

    core.HomeAssistant = HomeAssistant
    core.callback = callback

    class HomeAssistantError(Exception):
        """Base Home Assistant error stub."""

    class ConfigEntryNotReady(HomeAssistantError):
        """Setup-retry error stub."""

    exceptions.HomeAssistantError = HomeAssistantError
    exceptions.ConfigEntryNotReady = ConfigEntryNotReady

    class UpdateFailed(Exception):
        """Coordinator refresh error stub."""

    data_type = TypeVar("data_type")

    class DataUpdateCoordinator(Generic[data_type]):
        """Very small subset of HA's DataUpdateCoordinator."""

        def __init__(
            self,
            hass: Any,
            logger: Any,
            *,
            name: str,
            update_interval: timedelta | None = None,
            **kwargs: Any,
        ) -> None:
            self.hass = hass
            self.logger = logger
            self.name = name
            self.update_interval = update_interval
            self.data: Any = None
            self.last_update_success = True
            self._listeners: list[Any] = []
            self._unsub_refresh: Any = None

        async def _async_update_data(self) -> Any:
            raise NotImplementedError

        async def async_config_entry_first_refresh(self) -> None:
            try:
                data = await self._async_update_data()
            except UpdateFailed:
                self.last_update_success = False
                raise
            self.data = data
            self.last_update_success = True

        async def async_request_refresh(self) -> None:
            self.data = await self._async_update_data()
            self.last_update_success = True

        def async_set_updated_data(self, data: Any) -> None:
            self.data = data
            self.last_update_success = True
            for listener in list(self._listeners):
                listener()

        def async_add_listener(
            self, update_callback: Any, context: Any = None
        ) -> Any:
            self._listeners.append(update_callback)

            def _remove() -> None:
                if update_callback in self._listeners:
                    self._listeners.remove(update_callback)

            return _remove

    update_coordinator.DataUpdateCoordinator = DataUpdateCoordinator
    update_coordinator.UpdateFailed = UpdateFailed

    class DeviceInfo(dict):
        """Dict-compatible stand-in for HA's DeviceInfo TypedDict."""

    device_registry.DeviceInfo = DeviceInfo
    device_registry.CONNECTION_NETWORK_MAC = "mac"

    def async_call_later(
        hass: Any, delay: float, action: Any
    ) -> Any:
        record: dict[str, Any] = {
            "hass": hass,
            "delay": delay,
            "action": action,
            "cancelled": False,
        }

        def _cancel() -> None:
            record["cancelled"] = True

        record["cancel"] = _cancel
        _scheduled_calls.append(record)
        return _cancel

    event.async_call_later = async_call_later


_install_homeassistant_stubs()

from homeassistant.helpers.update_coordinator import UpdateFailed  # noqa: E402

import custom_components.pc_onoff as integration  # noqa: E402
from custom_components.pc_onoff.const import (  # noqa: E402
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
    PLATFORMS,
    PROBE_MODE_NONE,
    PROBE_MODE_PING,
    PROBE_MODE_TCP,
)
from custom_components.pc_onoff.coordinator import (  # noqa: E402
    PcOnOffCoordinator,
    PcTargetConfig,
)
from custom_components.pc_onoff.protocol import (  # noqa: E402
    InvalidMacError,
    PowerAction,
)
from custom_components.pc_onoff.state import OptimisticStateTracker  # noqa: E402

_PROBE = "custom_components.pc_onoff.coordinator.async_probe_host"
_SEND_MAGIC = "custom_components.pc_onoff.coordinator.async_send_magic_packet"
_SEND_POWER = "custom_components.pc_onoff.coordinator.async_send_power_command"

MAC = "AA:BB:CC:DD:EE:FF"
HOST = "192.168.1.50"
BASE_DATA: dict[str, Any] = {
    CONF_NAME: "Gaming PC",
    CONF_MAC: MAC,
    CONF_HOST: HOST,
    CONF_UDP_PORT: DEFAULT_UDP_PORT,
    CONF_WOL_PORT: DEFAULT_WOL_PORT,
    CONF_BROADCAST_IP: DEFAULT_BROADCAST_IP,
    CONF_PC_SECRET: "",
    CONF_OFF_ACTION: DEFAULT_OFF_ACTION,
    CONF_PROBE_MODE: DEFAULT_PROBE_MODE,
    CONF_PROBE_PORT: DEFAULT_PROBE_PORT,
    CONF_SCAN_INTERVAL: DEFAULT_SCAN_INTERVAL,
}


def _cleanup_homeassistant_stubs() -> None:
    """Remove only the stubs this module installed, in reverse order."""

    for name in reversed(list(_installed_stubs)):
        module = _installed_stubs[name]
        if sys.modules.get(name) is module:
            del sys.modules[name]
    _installed_stubs.clear()


class _StubCleanupMixin:
    """Guarantee no ``homeassistant`` stubs survive a test."""

    def tearDown(self) -> None:
        super().tearDown()
        _cleanup_homeassistant_stubs()
        _scheduled_calls.clear()


class _FakeClock:
    """Deterministic monotonic clock for tracker tests."""

    def __init__(self) -> None:
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class _FakeConfigEntries:
    """Config-entry manager double."""

    def __init__(self) -> None:
        self.forwarded: list[tuple[Any, tuple[str, ...]]] = []
        self.unloaded: list[tuple[Any, tuple[str, ...]]] = []
        self.reloaded: list[str] = []
        self.unload_result = True
        self.fail_forward = False

    async def async_forward_entry_setups(
        self, entry: Any, platforms: Any
    ) -> None:
        if self.fail_forward:
            raise RuntimeError("forward failed")
        self.forwarded.append((entry, tuple(platforms)))

    async def async_unload_platforms(
        self, entry: Any, platforms: Any
    ) -> bool:
        self.unloaded.append((entry, tuple(platforms)))
        return self.unload_result

    async def async_reload(self, entry_id: str) -> None:
        self.reloaded.append(entry_id)


class _FakeHass:
    """Home Assistant double with just enough surface for the coordinator."""

    def __init__(self) -> None:
        self.config_entries = _FakeConfigEntries()
        self.created_tasks: list[Any] = []

    def async_create_task(
        self, target: Any, *args: Any, **kwargs: Any
    ) -> Any:
        self.created_tasks.append(target)
        close = getattr(target, "close", None)
        if close is not None:
            close()
        return MagicMock()


class _FakeEntry:
    """ConfigEntry double used by the entry-point tests."""

    def __init__(
        self,
        data: dict[str, Any],
        options: dict[str, Any] | None = None,
        entry_id: str = "entry-1",
    ) -> None:
        self.data = dict(data)
        self.options = dict(options or {})
        self.entry_id = entry_id
        self.runtime_data: Any = None
        self.update_listeners: list[Any] = []
        self.on_unload: list[Any] = []

    def add_update_listener(self, listener: Any) -> Any:
        self.update_listeners.append(listener)

        def _remove() -> None:
            if listener in self.update_listeners:
                self.update_listeners.remove(listener)

        return _remove

    def async_on_unload(self, func: Any) -> None:
        self.on_unload.append(func)


class OptimisticStateTrackerTest(_StubCleanupMixin, unittest.TestCase):
    """The core anti-bounce-back state machine."""

    def setUp(self) -> None:
        self.clock = _FakeClock()
        self.tracker = OptimisticStateTracker(
            hold_seconds=OPTIMISTIC_HOLD_SECONDS,
            clock=self.clock,
        )

    def test_initial_state_is_unknown(self) -> None:
        self.assertIsNone(self.tracker.current_state)

    def test_record_intent_returns_target_and_updates_state(self) -> None:
        self.assertIs(self.tracker.record_intent(True), True)
        self.assertIs(self.tracker.current_state, True)

        self.assertIs(self.tracker.record_intent(False), False)
        self.assertIs(self.tracker.current_state, False)

    def test_holds_optimistic_true_while_pc_boots(self) -> None:
        self.tracker.record_intent(True)

        self.clock.advance(10.0)
        self.assertIs(self.tracker.reconcile(False), True)
        self.assertIs(self.tracker.current_state, True)

        # Still inside the 25 s hold window: the probe stays ignored.
        self.clock.advance(14.9)
        self.assertIs(self.tracker.reconcile(False), True)

    def test_converges_early_when_probe_matches_target(self) -> None:
        self.tracker.record_intent(True)

        self.clock.advance(12.0)
        self.assertIs(self.tracker.reconcile(True), True)

        # The hold is released, so the next conflicting probe is authoritative.
        self.clock.advance(1.0)
        self.assertIs(self.tracker.reconcile(False), False)

    def test_expired_hold_accepts_reality(self) -> None:
        self.tracker.record_intent(True)

        self.clock.advance(26.0)
        self.assertIs(self.tracker.reconcile(False), False)
        self.assertIs(self.tracker.current_state, False)

        # The pending intent is gone: later probes keep working normally.
        self.clock.advance(10.0)
        self.assertIs(self.tracker.reconcile(False), False)

    def test_unknown_probe_preserves_optimistic_state(self) -> None:
        self.tracker.record_intent(True)

        self.clock.advance(10_000.0)
        self.assertIs(self.tracker.reconcile(None), True)
        self.assertIs(self.tracker.current_state, True)

    def test_unknown_probe_before_any_intent_stays_unknown(self) -> None:
        self.assertIsNone(self.tracker.reconcile(None))

    def test_zero_hold_disables_optimism(self) -> None:
        tracker = OptimisticStateTracker(hold_seconds=0, clock=self.clock)
        tracker.record_intent(True)

        self.assertIs(tracker.reconcile(False), False)

    def test_off_intent_holds_off_while_pc_shuts_down(self) -> None:
        self.tracker.record_intent(False)

        self.clock.advance(5.0)
        self.assertIs(self.tracker.reconcile(True), False)

        self.clock.advance(21.0)
        self.assertIs(self.tracker.reconcile(True), True)

    def test_negative_hold_is_clamped_to_zero(self) -> None:
        tracker = OptimisticStateTracker(hold_seconds=-5, clock=self.clock)
        tracker.record_intent(True)

        self.assertIs(tracker.reconcile(False), False)


class PcTargetConfigTest(_StubCleanupMixin, unittest.TestCase):
    """Mapping -> immutable config translation."""

    def test_full_mapping_is_normalized(self) -> None:
        config = PcTargetConfig.from_mapping(
            {
                **BASE_DATA,
                CONF_MAC: "  aa-bb-cc-dd-ee-ff  ",
                CONF_NAME: "  Gaming PC  ",
                CONF_PC_SECRET: "  s3cret  ",
                CONF_OFF_ACTION: "  SLEEP ",
                CONF_PROBE_MODE: "TCP",
                CONF_SCAN_INTERVAL: "45",
            }
        )

        self.assertEqual(config.name, "Gaming PC")
        self.assertEqual(config.mac, MAC)
        self.assertEqual(config.host, HOST)
        self.assertEqual(config.udp_port, DEFAULT_UDP_PORT)
        self.assertEqual(config.wol_port, DEFAULT_WOL_PORT)
        self.assertEqual(config.broadcast_ip, DEFAULT_BROADCAST_IP)
        self.assertEqual(config.pc_secret, "s3cret")
        self.assertIs(config.off_action, PowerAction.SLEEP)
        self.assertEqual(config.probe_mode, PROBE_MODE_TCP)
        self.assertEqual(config.probe_port, DEFAULT_PROBE_PORT)
        self.assertEqual(config.scan_interval, 45)

    def test_minimal_mapping_uses_safe_defaults(self) -> None:
        config = PcTargetConfig.from_mapping(
            {CONF_MAC: MAC, CONF_HOST: HOST}
        )

        self.assertEqual(config.name, "PC")
        self.assertEqual(config.udp_port, DEFAULT_UDP_PORT)
        self.assertEqual(config.wol_port, DEFAULT_WOL_PORT)
        self.assertEqual(config.broadcast_ip, DEFAULT_BROADCAST_IP)
        self.assertEqual(config.pc_secret, "")
        self.assertIs(config.off_action, PowerAction.SHUTDOWN)
        self.assertEqual(config.probe_mode, PROBE_MODE_PING)
        self.assertEqual(config.probe_port, DEFAULT_PROBE_PORT)
        self.assertEqual(config.scan_interval, DEFAULT_SCAN_INTERVAL)

    def test_blank_name_falls_back_to_pc(self) -> None:
        config = PcTargetConfig.from_mapping(
            {CONF_MAC: MAC, CONF_HOST: HOST, CONF_NAME: "   "}
        )
        self.assertEqual(config.name, "PC")

    def test_options_override_entry_data(self) -> None:
        merged = {
            **BASE_DATA,
            **{
                CONF_OFF_ACTION: "hibernate",
                CONF_HOST: "10.0.0.9",
                CONF_SCAN_INTERVAL: 5,
            },
        }
        config = PcTargetConfig.from_mapping(merged)

        self.assertIs(config.off_action, PowerAction.HIBERNATE)
        self.assertEqual(config.host, "10.0.0.9")
        self.assertEqual(config.scan_interval, 5)

    def test_unknown_keys_are_ignored(self) -> None:
        config = PcTargetConfig.from_mapping(
            {**BASE_DATA, "future_option": "whatever"}
        )
        self.assertEqual(config.mac, MAC)

    def test_invalid_mac_raises(self) -> None:
        with self.assertRaises(InvalidMacError):
            PcTargetConfig.from_mapping(
                {CONF_MAC: "AA:BB:CC:DD:EE", CONF_HOST: HOST}
            )

    def test_invalid_off_action_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            PcTargetConfig.from_mapping(
                {**BASE_DATA, CONF_OFF_ACTION: "explode"}
            )

    def test_unknown_probe_mode_falls_back_to_ping(self) -> None:
        config = PcTargetConfig.from_mapping(
            {**BASE_DATA, CONF_PROBE_MODE: "carrier-pigeon"}
        )
        self.assertEqual(config.probe_mode, PROBE_MODE_PING)

    def test_out_of_range_values_are_clamped(self) -> None:
        config = PcTargetConfig.from_mapping(
            {
                **BASE_DATA,
                CONF_UDP_PORT: 70_000,
                CONF_WOL_PORT: 0,
                CONF_SCAN_INTERVAL: 1,
            }
        )

        self.assertEqual(config.udp_port, 65_535)
        self.assertEqual(config.wol_port, 1)
        self.assertEqual(config.scan_interval, MIN_SCAN_INTERVAL)

        config = PcTargetConfig.from_mapping(
            {**BASE_DATA, CONF_SCAN_INTERVAL: 99_999}
        )
        self.assertEqual(config.scan_interval, MAX_SCAN_INTERVAL)

    def test_config_is_frozen(self) -> None:
        config = PcTargetConfig.from_mapping(BASE_DATA)
        with self.assertRaises(FrozenInstanceError):
            config.name = "Other PC"  # type: ignore[misc]


class CoordinatorTest(_StubCleanupMixin, unittest.IsolatedAsyncioTestCase):
    """Coordinator behaviour with mocked UDP senders and probes."""

    def setUp(self) -> None:
        self.hass = _FakeHass()
        _scheduled_calls.clear()

    def _make_coordinator(self, **overrides: Any) -> PcOnOffCoordinator:
        config = PcTargetConfig.from_mapping({**BASE_DATA, **overrides})
        return PcOnOffCoordinator(self.hass, config)  # type: ignore[arg-type]

    def test_device_info_identifies_the_mac(self) -> None:
        coordinator = self._make_coordinator()

        self.assertEqual(coordinator.config.mac, MAC)
        self.assertEqual(coordinator.device_info["name"], "Gaming PC")
        self.assertEqual(
            coordinator.device_info["identifiers"], {(DOMAIN, MAC)}
        )
        self.assertEqual(
            coordinator.device_info["connections"], {("mac", MAC)}
        )
        self.assertEqual(
            coordinator.device_info["manufacturer"], "pc_onoff"
        )
        self.assertEqual(coordinator.device_info["model"], "Target Computer")

    def test_update_interval_uses_scan_interval(self) -> None:
        coordinator = self._make_coordinator()
        self.assertEqual(coordinator.update_interval, timedelta(seconds=30))
        self.assertFalse(coordinator.assumed_state)

    def test_probe_disabled_means_no_polling_and_assumed_state(self) -> None:
        coordinator = self._make_coordinator(
            **{CONF_PROBE_MODE: PROBE_MODE_NONE}
        )

        self.assertIsNone(coordinator.update_interval)
        self.assertTrue(coordinator.assumed_state)

    async def test_async_wake_sends_packet_and_holds_on(self) -> None:
        coordinator = self._make_coordinator()

        with patch(_SEND_MAGIC, new_callable=AsyncMock) as send:
            await coordinator.async_wake()

        send.assert_awaited_once_with(MAC, DEFAULT_BROADCAST_IP, DEFAULT_WOL_PORT)
        self.assertIs(coordinator.data, True)
        self.assertEqual(len(_scheduled_calls), 1)
        self.assertEqual(
            _scheduled_calls[0]["delay"],
            OPTIMISTIC_HOLD_SECONDS + 1.0,
        )
        self.assertIsNotNone(coordinator._unsub_refresh)

    async def test_async_wake_uses_configured_broadcast_and_port(self) -> None:
        coordinator = self._make_coordinator(
            **{
                CONF_BROADCAST_IP: "192.168.1.255",
                CONF_WOL_PORT: 7,
            }
        )

        with patch(_SEND_MAGIC, new_callable=AsyncMock) as send:
            await coordinator.async_wake()

        send.assert_awaited_once_with(MAC, "192.168.1.255", 7)

    async def test_async_turn_off_uses_configured_off_action(self) -> None:
        coordinator = self._make_coordinator(
            **{CONF_OFF_ACTION: "sleep", CONF_PC_SECRET: "s3cret"}
        )

        with patch(_SEND_POWER, new_callable=AsyncMock) as send:
            await coordinator.async_turn_off()

        send.assert_awaited_once_with(HOST, PowerAction.SLEEP, 9877, "s3cret")
        self.assertIs(coordinator.data, False)
        self.assertEqual(len(_scheduled_calls), 1)

    async def test_off_actions_flip_switch_and_schedule_reconcile(self) -> None:
        for action in (
            PowerAction.SHUTDOWN,
            PowerAction.SLEEP,
            PowerAction.HIBERNATE,
        ):
            with self.subTest(action=action):
                _scheduled_calls.clear()
                coordinator = self._make_coordinator()

                with patch(_SEND_POWER, new_callable=AsyncMock) as send:
                    await coordinator.async_power(action)

                send.assert_awaited_once_with(HOST, action, 9877, "")
                self.assertIs(coordinator.data, False)
                self.assertEqual(len(_scheduled_calls), 1)

    async def test_lock_and_reboot_do_not_change_the_switch(self) -> None:
        for action in (PowerAction.LOCK, PowerAction.REBOOT):
            with self.subTest(action=action):
                _scheduled_calls.clear()
                coordinator = self._make_coordinator()

                with patch(_SEND_POWER, new_callable=AsyncMock) as send:
                    await coordinator.async_power(action)

                send.assert_awaited_once_with(HOST, action, 9877, "")
                self.assertIsNone(coordinator.data)
                self.assertEqual(_scheduled_calls, [])

    async def test_scheduled_callback_requests_one_refresh(self) -> None:
        coordinator = self._make_coordinator()

        with patch(_SEND_MAGIC, new_callable=AsyncMock):
            await coordinator.async_wake()

        record = _scheduled_calls[-1]
        with patch.object(
            coordinator, "async_request_refresh", new_callable=AsyncMock
        ) as refresh:
            record["action"](None)

        refresh.assert_called_once_with()
        self.assertEqual(len(self.hass.created_tasks), 1)
        self.assertIsNone(coordinator._unsub_refresh)

    async def test_rescheduling_cancels_the_previous_timer(self) -> None:
        coordinator = self._make_coordinator()

        with patch(_SEND_MAGIC, new_callable=AsyncMock):
            await coordinator.async_wake()
            await coordinator.async_wake()

        first, second = _scheduled_calls
        self.assertTrue(first["cancelled"])
        self.assertFalse(second["cancelled"])
        self.assertIs(coordinator._unsub_refresh, second["cancel"])

    async def test_cancel_scheduled_refresh_is_idempotent(self) -> None:
        coordinator = self._make_coordinator()

        with patch(_SEND_MAGIC, new_callable=AsyncMock):
            await coordinator.async_wake()

        record = _scheduled_calls[-1]
        coordinator.async_cancel_scheduled_refresh()
        self.assertTrue(record["cancelled"])
        self.assertIsNone(coordinator._unsub_refresh)

        # A second call must be a harmless no-op (unload path).
        coordinator.async_cancel_scheduled_refresh()
        self.assertIsNone(coordinator._unsub_refresh)

    def test_cancel_without_a_schedule_is_safe(self) -> None:
        coordinator = self._make_coordinator()
        coordinator.async_cancel_scheduled_refresh()
        self.assertIsNone(coordinator._unsub_refresh)

    def test_cancel_swallows_unsubscribe_errors(self) -> None:
        coordinator = self._make_coordinator()
        coordinator._unsub_refresh = MagicMock(
            side_effect=RuntimeError("timer already gone")
        )

        coordinator.async_cancel_scheduled_refresh()
        self.assertIsNone(coordinator._unsub_refresh)

    async def test_probe_disabled_skips_reconcile_timers(self) -> None:
        coordinator = self._make_coordinator(
            **{CONF_PROBE_MODE: PROBE_MODE_NONE}
        )

        with patch(_SEND_MAGIC, new_callable=AsyncMock):
            await coordinator.async_wake()

        self.assertIs(coordinator.data, True)
        self.assertEqual(_scheduled_calls, [])
        self.assertIsNone(coordinator._unsub_refresh)

        with patch(_SEND_POWER, new_callable=AsyncMock):
            await coordinator.async_power(PowerAction.SHUTDOWN)

        self.assertIs(coordinator.data, False)
        self.assertEqual(_scheduled_calls, [])

    async def test_update_data_probes_configured_target(self) -> None:
        coordinator = self._make_coordinator()

        with patch(
            _PROBE, new_callable=AsyncMock, return_value=True
        ) as probe:
            self.assertIs(await coordinator._async_update_data(), True)

        probe.assert_awaited_once_with(HOST, PROBE_MODE_PING, DEFAULT_PROBE_PORT)

    async def test_update_data_holds_intent_while_probe_disagrees(self) -> None:
        coordinator = self._make_coordinator()
        coordinator._tracker.record_intent(True)

        with patch(_PROBE, new_callable=AsyncMock, return_value=False):
            self.assertIs(await coordinator._async_update_data(), True)

        self.assertIs(coordinator._tracker.current_state, True)

    async def test_update_data_accepts_reality_after_hold_expires(self) -> None:
        coordinator = self._make_coordinator()
        clock = _FakeClock()
        coordinator._tracker = OptimisticStateTracker(
            hold_seconds=5.0, clock=clock
        )
        coordinator._tracker.record_intent(True)
        clock.advance(6.0)

        with patch(_PROBE, new_callable=AsyncMock, return_value=False):
            self.assertIs(await coordinator._async_update_data(), False)

    async def test_update_data_without_probe_keeps_optimistic_state(self) -> None:
        coordinator = self._make_coordinator(
            **{CONF_PROBE_MODE: PROBE_MODE_NONE}
        )
        coordinator._tracker.record_intent(True)

        with patch(_PROBE, new_callable=AsyncMock, return_value=None):
            self.assertIs(await coordinator._async_update_data(), True)

    async def test_unexpected_probe_error_becomes_update_failed(self) -> None:
        coordinator = self._make_coordinator()

        with patch(
            _PROBE,
            new_callable=AsyncMock,
            side_effect=RuntimeError("boom"),
        ):
            with self.assertRaises(UpdateFailed):
                await coordinator._async_update_data()

    async def test_first_refresh_populates_data(self) -> None:
        coordinator = self._make_coordinator()

        with patch(_PROBE, new_callable=AsyncMock, return_value=True):
            await coordinator.async_config_entry_first_refresh()

        self.assertIs(coordinator.data, True)
        self.assertTrue(coordinator.last_update_success)


class IntegrationEntryTest(_StubCleanupMixin, unittest.IsolatedAsyncioTestCase):
    """``__init__.py`` setup/unload orchestration."""

    def setUp(self) -> None:
        self.hass = _FakeHass()
        _scheduled_calls.clear()

    def _make_entry(
        self,
        *,
        data: dict[str, Any] | None = None,
        options: dict[str, Any] | None = None,
    ) -> _FakeEntry:
        return _FakeEntry({**BASE_DATA, **(data or {})}, options)

    async def _setup(
        self, entry: _FakeEntry, probe_result: bool = True
    ) -> _FakeEntry:
        with patch(_PROBE, new_callable=AsyncMock, return_value=probe_result):
            result = await integration.async_setup_entry(  # type: ignore[arg-type]
                self.hass, entry
            )
        self.assertTrue(result)
        return entry

    async def test_setup_entry_wires_runtime_data_and_platforms(self) -> None:
        entry = await self._setup(self._make_entry())

        coordinator = entry.runtime_data
        self.assertIsInstance(coordinator, PcOnOffCoordinator)
        self.assertIs(coordinator.data, True)
        self.assertEqual(
            self.hass.config_entries.forwarded, [(entry, PLATFORMS)]
        )
        self.assertEqual(len(entry.update_listeners), 1)
        self.assertIs(
            entry.update_listeners[0], integration._async_reload_entry
        )
        self.assertEqual(len(entry.on_unload), 1)

    async def test_options_override_entry_data_on_setup(self) -> None:
        entry = await self._setup(
            self._make_entry(
                options={
                    CONF_OFF_ACTION: "sleep",
                    CONF_SCAN_INTERVAL: 60,
                }
            )
        )

        coordinator = entry.runtime_data
        self.assertIs(coordinator.config.off_action, PowerAction.SLEEP)
        self.assertEqual(
            coordinator.update_interval, timedelta(seconds=60)
        )
        # Configuration identity is preserved from the entry data.
        self.assertEqual(coordinator.config.name, "Gaming PC")
        self.assertEqual(coordinator.config.mac, MAC)

    async def test_setup_failure_propagates_and_leaves_no_runtime_data(
        self,
    ) -> None:
        entry = self._make_entry()

        with patch(
            _PROBE,
            new_callable=AsyncMock,
            side_effect=RuntimeError("boom"),
        ):
            with self.assertRaises(UpdateFailed):
                await integration.async_setup_entry(  # type: ignore[arg-type]
                    self.hass, entry
                )

        self.assertIsNone(entry.runtime_data)
        self.assertEqual(self.hass.config_entries.forwarded, [])

    async def test_forwarding_failure_cancels_timer_and_resets_runtime_data(
        self,
    ) -> None:
        entry = self._make_entry()
        self.hass.config_entries.fail_forward = True

        with patch(_PROBE, new_callable=AsyncMock, return_value=True):
            with patch.object(
                PcOnOffCoordinator, "async_cancel_scheduled_refresh"
            ) as cancel:
                with self.assertRaises(RuntimeError):
                    await integration.async_setup_entry(  # type: ignore[arg-type]
                        self.hass, entry
                    )

        cancel.assert_called_once_with()
        self.assertIsNone(entry.runtime_data)

    async def test_unload_cancels_scheduled_refresh_and_platforms(self) -> None:
        entry = await self._setup(self._make_entry())
        coordinator = entry.runtime_data

        with patch.object(
            coordinator, "async_cancel_scheduled_refresh"
        ) as cancel:
            result = await integration.async_unload_entry(  # type: ignore[arg-type]
                self.hass, entry
            )

        self.assertTrue(result)
        cancel.assert_called_once_with()
        self.assertEqual(
            self.hass.config_entries.unloaded, [(entry, PLATFORMS)]
        )

    async def test_unload_returns_false_when_platform_unload_fails(self) -> None:
        self.hass.config_entries.unload_result = False
        entry = await self._setup(self._make_entry())

        result = await integration.async_unload_entry(  # type: ignore[arg-type]
            self.hass, entry
        )

        self.assertFalse(result)
        self.assertEqual(
            self.hass.config_entries.unloaded, [(entry, PLATFORMS)]
        )

    async def test_unload_without_runtime_data_is_safe(self) -> None:
        entry = self._make_entry()

        result = await integration.async_unload_entry(  # type: ignore[arg-type]
            self.hass, entry
        )

        self.assertTrue(result)
        self.assertEqual(
            self.hass.config_entries.unloaded, [(entry, PLATFORMS)]
        )

    async def test_reload_listener_requests_entry_reload(self) -> None:
        entry = self._make_entry()

        await integration._async_reload_entry(  # type: ignore[arg-type]
            self.hass, entry
        )

        self.assertEqual(self.hass.config_entries.reloaded, [entry.entry_id])


if __name__ == "__main__":  # pragma: no cover - convenience entry point
    unittest.main()
