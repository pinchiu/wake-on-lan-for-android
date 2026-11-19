"""Primary power switch for the ``pc_onoff`` integration.

This is a thin Layer 3 entity adapter: it owns no protocol or state logic.
Every command is forwarded to :class:`PcOnOffCoordinator`, which broadcasts
the Wake-on-LAN magic packet or sends the UDP 9877 power command and keeps the
optimistic state machine consistent.

The entity deliberately uses ``_attr_has_entity_name = True`` together with
``_attr_name = None``.  Its friendly name is therefore exactly the device name
(for example ``Gaming PC``) instead of ``Gaming PC Power``.  Google Home
exposes that name to voice control, so users can say *"Hey Google, turn on
Gaming PC"*.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from homeassistant.components.switch import SwitchDeviceClass, SwitchEntity
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .coordinator import PcOnOffCoordinator

if TYPE_CHECKING:  # pragma: no cover - typing only
    from homeassistant.config_entries import ConfigEntry
    from homeassistant.core import HomeAssistant
    from homeassistant.helpers.entity_platform import AddEntitiesCallback

__all__ = ["PcPowerSwitch", "async_setup_entry"]


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Add the single power switch for one target PC."""

    coordinator: PcOnOffCoordinator = entry.runtime_data
    async_add_entities([PcPowerSwitch(coordinator)])


class PcPowerSwitch(CoordinatorEntity[PcOnOffCoordinator], SwitchEntity):
    """On/off switch backed by Wake-on-LAN and the PC power daemon.

    ``is_on`` is the coordinator's reconciled state.  Turning the switch on
    sends a magic packet; turning it off applies the configured
    ``off_action`` (shutdown, sleep or hibernate) so a single switch is enough
    for Google Home voice control.
    """

    _attr_has_entity_name = True
    _attr_name = None
    _attr_device_class = SwitchDeviceClass.SWITCH

    def __init__(self, coordinator: PcOnOffCoordinator) -> None:
        super().__init__(coordinator)
        self._attr_unique_id = f"{coordinator.config.mac}_power"
        self._attr_device_info = coordinator.device_info

    @property
    def assumed_state(self) -> bool:
        """Return whether the real state cannot be probed."""

        return self.coordinator.assumed_state

    @property
    def is_on(self) -> bool | None:
        """Return the reconciled state (``None`` when unknown)."""

        return self.coordinator.data

    async def async_turn_on(self, **kwargs: Any) -> None:
        """Wake the PC by broadcasting a magic packet."""

        await self.coordinator.async_wake()

    async def async_turn_off(self, **kwargs: Any) -> None:
        """Apply the configured turn-off action."""

        await self.coordinator.async_turn_off()
