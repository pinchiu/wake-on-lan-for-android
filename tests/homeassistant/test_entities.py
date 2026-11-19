"""Unit tests for the Home Assistant entity adapters (Wave 3, HA-6).

The real ``homeassistant`` package is intentionally **not** installed in CI (it
is large and slow), so every test installs a minimal set of
``homeassistant.*`` ``sys.modules`` stubs, imports the three platform modules
against those stubs, and removes them again in ``addCleanup``.  Nothing is
installed at module import time, therefore other test modules never see the
stubs.

Fake coordinators are used on purpose: the entity adapters must only forward
calls (``async_wake`` / ``async_turn_off`` / ``async_power``) and expose
``coordinator.data`` / ``coordinator.assumed_state``; all protocol and state
logic is covered by the pure-module tests.

Run with::

    python3 -m unittest discover -s tests/homeassistant -p "test_entities.py" -v
"""

from __future__ import annotations

import importlib
import sys
import types
import unittest
from dataclasses import dataclass
from types import ModuleType, SimpleNamespace
from typing import Any, Generic, TypeVar
from unittest.mock import AsyncMock, Mock

from custom_components.pc_onoff.protocol import PowerAction

_MAC = "AA:BB:CC:DD:EE:FF"
_PACKAGE = "custom_components.pc_onoff"
_PLATFORM_MODULES = (
    f"{_PACKAGE}.switch",
    f"{_PACKAGE}.button",
    f"{_PACKAGE}.binary_sensor",
)
_COORDINATOR_MODULE = f"{_PACKAGE}.coordinator"
_HA_STUB_MODULES = (
    "homeassistant",
    "homeassistant.core",
    "homeassistant.components",
    "homeassistant.components.switch",
    "homeassistant.components.button",
    "homeassistant.components.binary_sensor",
    "homeassistant.helpers",
    "homeassistant.helpers.device_registry",
    "homeassistant.helpers.event",
    "homeassistant.helpers.update_coordinator",
)
_TRACKED_MODULES = _HA_STUB_MODULES + (_COORDINATOR_MODULE,)

_MISSING = object()

_T = TypeVar("_T")


# ---------------------------------------------------------------------------
# Minimal Home Assistant stand-ins
# ---------------------------------------------------------------------------


class _StubEntity:
    """Stand-in for ``homeassistant.helpers.entity.Entity``."""

    _attr_has_entity_name = False
    _attr_name: str | None = None

    def __init__(self) -> None:
        pass


class _StubSwitchEntity(_StubEntity):
    pass


class _StubButtonEntity(_StubEntity):
    pass


class _StubBinarySensorEntity(_StubEntity):
    pass


class _StubCoordinatorEntity(_StubEntity, Generic[_T]):
    """Stand-in for ``homeassistant.helpers.update_coordinator``."""

    def __init__(self, coordinator: _T) -> None:
        super().__init__()
        self.coordinator = coordinator


class _StubSwitchDeviceClass:
    SWITCH = "switch"


class _StubBinarySensorDeviceClass:
    CONNECTIVITY = "connectivity"


@dataclass(frozen=True, kw_only=True)
class _StubButtonEntityDescription:
    """Minimal ``ButtonEntityDescription`` with the fields we set."""

    key: str = ""
    translation_key: str | None = None
    icon: str | None = None


class _StubDataUpdateCoordinator(Generic[_T]):
    """Enough of ``DataUpdateCoordinator`` for ``coordinator.py`` to import."""

    def __init__(
        self,
        hass: Any,
        logger: Any,
        *,
        name: str,
        update_interval: Any = None,
    ) -> None:
        self.hass = hass
        self.logger = logger
        self.name = name
        self.update_interval = update_interval
        self.data: _T | None = None


class _StubUpdateFailed(Exception):
    pass


class _StubDeviceInfo(dict):
    """``DeviceInfo`` is a TypedDict in Home Assistant, so a dict is faithful."""

    def __init__(self, **kwargs: Any) -> None:
        super().__init__(**kwargs)


def _make_module(name: str, *, package: bool = False) -> ModuleType:
    module = types.ModuleType(name)
    if package:
        module.__path__ = []  # type: ignore[attr-defined]
    sys.modules[name] = module
    return module


def _install_homeassistant_stubs() -> None:
    """Install the smallest ``homeassistant`` surface the platforms import."""

    root = _make_module("homeassistant", package=True)

    core = _make_module("homeassistant.core")
    core.callback = lambda function: function
    root.core = core

    components = _make_module("homeassistant.components", package=True)
    root.components = components

    switch_module = _make_module("homeassistant.components.switch")
    switch_module.SwitchEntity = _StubSwitchEntity
    switch_module.SwitchDeviceClass = _StubSwitchDeviceClass
    components.switch = switch_module

    button_module = _make_module("homeassistant.components.button")
    button_module.ButtonEntity = _StubButtonEntity
    button_module.ButtonEntityDescription = _StubButtonEntityDescription
    components.button = button_module

    binary_sensor_module = _make_module(
        "homeassistant.components.binary_sensor"
    )
    binary_sensor_module.BinarySensorEntity = _StubBinarySensorEntity
    binary_sensor_module.BinarySensorDeviceClass = _StubBinarySensorDeviceClass
    components.binary_sensor = binary_sensor_module

    helpers = _make_module("homeassistant.helpers", package=True)
    root.helpers = helpers

    device_registry = _make_module("homeassistant.helpers.device_registry")
    device_registry.CONNECTION_NETWORK_MAC = "mac"
    device_registry.DeviceInfo = _StubDeviceInfo
    helpers.device_registry = device_registry

    event = _make_module("homeassistant.helpers.event")

    def _async_call_later(*args: Any, **kwargs: Any) -> Any:
        raise AssertionError("async_call_later must not run in entity tests")

    event.async_call_later = _async_call_later
    helpers.event = event

    update_coordinator = _make_module(
        "homeassistant.helpers.update_coordinator"
    )
    update_coordinator.CoordinatorEntity = _StubCoordinatorEntity
    update_coordinator.DataUpdateCoordinator = _StubDataUpdateCoordinator
    update_coordinator.UpdateFailed = _StubUpdateFailed
    helpers.update_coordinator = update_coordinator


def _forget_entity_modules() -> None:
    """Drop the platform/coordinator modules built against temporary stubs."""

    package = sys.modules.get(_PACKAGE)
    for name in (*_PLATFORM_MODULES, _COORDINATOR_MODULE):
        module = sys.modules.pop(name, None)
        if package is None or module is None:
            continue
        child = name.rsplit(".", 1)[1]
        if getattr(package, child, None) is module:
            delattr(package, child)


def _restore_tracked_modules(saved: dict[str, object]) -> None:
    """Restore the exact ``sys.modules`` entries seen before a test."""

    for name, previous in saved.items():
        if previous is _MISSING:
            sys.modules.pop(name, None)
            continue
        sys.modules[name] = previous
        if name.startswith(f"{_PACKAGE}."):
            package = sys.modules.get(_PACKAGE)
            if package is not None:
                setattr(package, name.rsplit(".", 1)[1], previous)


# ---------------------------------------------------------------------------
# Test doubles
# ---------------------------------------------------------------------------


class _FakeCoordinator:
    """The entity-visible slice of :class:`PcOnOffCoordinator`."""

    def __init__(
        self,
        *,
        data: bool | None = True,
        assumed_state: bool = False,
        mac: str = _MAC,
    ) -> None:
        self.config = SimpleNamespace(mac=mac)
        self.data = data
        self.assumed_state = assumed_state
        self.device_info: dict[str, Any] = {
            "identifiers": {("pc_onoff", mac)},
            "name": "Gaming PC",
        }
        self.async_wake = AsyncMock()
        self.async_turn_off = AsyncMock()
        self.async_power = AsyncMock()


class _EntityTestCase(unittest.IsolatedAsyncioTestCase):
    """Install stubs per test and import freshly built platform modules."""

    switch: ModuleType
    button: ModuleType
    binary_sensor: ModuleType

    def setUp(self) -> None:
        super().setUp()

        saved = {
            name: sys.modules.get(name, _MISSING) for name in _TRACKED_MODULES
        }
        # LIFO: the entity modules are removed first, then the sys.modules
        # entries (including any real ``homeassistant`` package) are restored.
        self.addCleanup(_restore_tracked_modules, saved)
        self.addCleanup(_forget_entity_modules)

        _forget_entity_modules()
        _install_homeassistant_stubs()

        self.switch = importlib.import_module(f"{_PACKAGE}.switch")
        self.button = importlib.import_module(f"{_PACKAGE}.button")
        self.binary_sensor = importlib.import_module(
            f"{_PACKAGE}.binary_sensor"
        )


# ---------------------------------------------------------------------------
# switch.py
# ---------------------------------------------------------------------------


class PcPowerSwitchTests(_EntityTestCase):
    """The primary Google Home on/off switch."""

    async def test_setup_adds_exactly_one_switch(self) -> None:
        coordinator = _FakeCoordinator()
        entry = SimpleNamespace(runtime_data=coordinator)
        add_entities = Mock()

        await self.switch.async_setup_entry(
            SimpleNamespace(), entry, add_entities
        )

        add_entities.assert_called_once()
        entities = add_entities.call_args.args[0]
        self.assertEqual(len(entities), 1)
        self.assertIsInstance(entities[0], self.switch.PcPowerSwitch)
        self.assertIs(entities[0].coordinator, coordinator)

    def test_entity_metadata(self) -> None:
        coordinator = _FakeCoordinator()
        entity = self.switch.PcPowerSwitch(coordinator)

        # Name must be None so Google Home sees the device name ("Gaming PC")
        # instead of "Gaming PC Power".
        self.assertIsNone(entity._attr_name)
        self.assertTrue(entity._attr_has_entity_name)
        self.assertEqual(
            entity._attr_device_class,
            self.switch.SwitchDeviceClass.SWITCH,
        )
        self.assertEqual(entity._attr_unique_id, f"{_MAC}_power")
        self.assertIs(entity._attr_device_info, coordinator.device_info)
        self.assertIs(entity.coordinator, coordinator)

    def test_is_on_tracks_coordinator_data(self) -> None:
        for value in (True, False, None):
            with self.subTest(value=value):
                entity = self.switch.PcPowerSwitch(
                    _FakeCoordinator(data=value)
                )
                self.assertIs(entity.is_on, value)

    def test_assumed_state_delegates_to_coordinator(self) -> None:
        coordinator = _FakeCoordinator(assumed_state=True)
        entity = self.switch.PcPowerSwitch(coordinator)
        self.assertTrue(entity.assumed_state)

        coordinator.assumed_state = False
        self.assertFalse(entity.assumed_state)

    async def test_turn_on_wakes_the_pc(self) -> None:
        coordinator = _FakeCoordinator()
        entity = self.switch.PcPowerSwitch(coordinator)

        await entity.async_turn_on()

        coordinator.async_wake.assert_awaited_once_with()
        coordinator.async_turn_off.assert_not_awaited()

    async def test_turn_off_uses_configured_off_action(self) -> None:
        coordinator = _FakeCoordinator()
        entity = self.switch.PcPowerSwitch(coordinator)

        await entity.async_turn_off()

        coordinator.async_turn_off.assert_awaited_once_with()
        coordinator.async_wake.assert_not_awaited()

    async def test_turn_on_accepts_service_kwargs(self) -> None:
        coordinator = _FakeCoordinator()
        entity = self.switch.PcPowerSwitch(coordinator)

        await entity.async_turn_on(transition=1)

        coordinator.async_wake.assert_awaited_once_with()


# ---------------------------------------------------------------------------
# button.py
# ---------------------------------------------------------------------------

_EXPECTED_BUTTONS = (
    ("wake", None, "mdi:power-on"),
    ("shutdown", PowerAction.SHUTDOWN, "mdi:power"),
    ("reboot", PowerAction.REBOOT, "mdi:restart"),
    ("sleep", PowerAction.SLEEP, "mdi:power-sleep"),
    ("hibernate", PowerAction.HIBERNATE, "mdi:snowflake"),
    ("lock", PowerAction.LOCK, "mdi:lock"),
)


class PcActionButtonTests(_EntityTestCase):
    """The six declarative power buttons."""

    async def test_setup_adds_six_buttons(self) -> None:
        coordinator = _FakeCoordinator()
        entry = SimpleNamespace(runtime_data=coordinator)
        add_entities = Mock()

        await self.button.async_setup_entry(
            SimpleNamespace(), entry, add_entities
        )

        add_entities.assert_called_once()
        entities = add_entities.call_args.args[0]
        self.assertEqual(len(entities), 6)
        self.assertEqual(
            [entity.entity_description.key for entity in entities],
            [key for key, _, _ in _EXPECTED_BUTTONS],
        )
        for entity in entities:
            self.assertIsInstance(entity, self.button.PcActionButton)
            self.assertIs(entity.coordinator, coordinator)

    def test_description_table_is_exact(self) -> None:
        descriptions = self.button.BUTTON_DESCRIPTIONS
        self.assertEqual(len(descriptions), 6)
        self.assertEqual(
            [description.key for description in descriptions],
            [key for key, _, _ in _EXPECTED_BUTTONS],
        )
        for description, (key, action, icon) in zip(
            descriptions, _EXPECTED_BUTTONS
        ):
            with self.subTest(key=key):
                self.assertEqual(description.translation_key, key)
                self.assertEqual(description.icon, icon)
                self.assertEqual(description.action, action)

    def test_entity_metadata(self) -> None:
        coordinator = _FakeCoordinator()

        for description in self.button.BUTTON_DESCRIPTIONS:
            with self.subTest(key=description.key):
                entity = self.button.PcActionButton(
                    coordinator, description
                )
                self.assertTrue(entity._attr_has_entity_name)
                self.assertEqual(
                    entity._attr_unique_id,
                    f"{_MAC}_{description.key}",
                )
                self.assertIs(
                    entity._attr_device_info, coordinator.device_info
                )
                self.assertIs(entity.entity_description, description)
                self.assertIs(entity.coordinator, coordinator)

    async def test_each_button_dispatches_the_right_command(self) -> None:
        for description in self.button.BUTTON_DESCRIPTIONS:
            with self.subTest(key=description.key):
                coordinator = _FakeCoordinator()
                entity = self.button.PcActionButton(
                    coordinator, description
                )

                await entity.async_press()

                if description.action is None:
                    coordinator.async_wake.assert_awaited_once_with()
                    coordinator.async_power.assert_not_awaited()
                else:
                    coordinator.async_power.assert_awaited_once_with(
                        description.action
                    )
                    coordinator.async_wake.assert_not_awaited()

    async def test_wake_button_is_the_only_magic_packet_action(self) -> None:
        wake = next(
            description
            for description in self.button.BUTTON_DESCRIPTIONS
            if description.key == "wake"
        )
        coordinator = _FakeCoordinator()
        entity = self.button.PcActionButton(coordinator, wake)

        await entity.async_press()

        coordinator.async_wake.assert_awaited_once_with()
        coordinator.async_power.assert_not_awaited()

    async def test_power_buttons_never_wake(self) -> None:
        for description in self.button.BUTTON_DESCRIPTIONS:
            if description.action is None:
                continue
            with self.subTest(key=description.key):
                coordinator = _FakeCoordinator()
                entity = self.button.PcActionButton(
                    coordinator, description
                )

                await entity.async_press()

                coordinator.async_power.assert_awaited_once_with(
                    description.action
                )
                coordinator.async_wake.assert_not_awaited()


# ---------------------------------------------------------------------------
# binary_sensor.py
# ---------------------------------------------------------------------------


class PcConnectivitySensorTests(_EntityTestCase):
    """The connectivity diagnostic sensor."""

    async def test_setup_adds_one_sensor(self) -> None:
        coordinator = _FakeCoordinator()
        entry = SimpleNamespace(runtime_data=coordinator)
        add_entities = Mock()

        await self.binary_sensor.async_setup_entry(
            SimpleNamespace(), entry, add_entities
        )

        add_entities.assert_called_once()
        entities = add_entities.call_args.args[0]
        self.assertEqual(len(entities), 1)
        self.assertIsInstance(
            entities[0], self.binary_sensor.PcConnectivitySensor
        )
        self.assertIs(entities[0].coordinator, coordinator)

    def test_entity_metadata(self) -> None:
        coordinator = _FakeCoordinator()
        entity = self.binary_sensor.PcConnectivitySensor(coordinator)

        self.assertTrue(entity._attr_has_entity_name)
        self.assertEqual(entity._attr_translation_key, "connectivity")
        self.assertEqual(
            entity._attr_device_class,
            self.binary_sensor.BinarySensorDeviceClass.CONNECTIVITY,
        )
        self.assertEqual(entity._attr_unique_id, f"{_MAC}_connectivity")
        self.assertIs(entity._attr_device_info, coordinator.device_info)
        self.assertIs(entity.coordinator, coordinator)

    def test_is_on_tracks_coordinator_data(self) -> None:
        for value in (True, False, None):
            with self.subTest(value=value):
                entity = self.binary_sensor.PcConnectivitySensor(
                    _FakeCoordinator(data=value)
                )
                self.assertIs(entity.is_on, value)


if __name__ == "__main__":
    unittest.main()
