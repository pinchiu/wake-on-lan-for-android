"""Connectivity sensor for the ``pc_onoff`` integration.

The sensor mirrors the coordinator's reconciled reachability state.  It is
independent from the primary switch: when probing is enabled and the PC is
offline the switch still exists, while this diagnostic entity reports
``off`` so dashboards and automations can distinguish "PC is off" from
"integration has no data".
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from homeassistant.components.binary_sensor import (
    BinarySensorDeviceClass,
    BinarySensorEntity,
)
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .coordinator import PcOnOffCoordinator

if TYPE_CHECKING:  # pragma: no cover - typing only
    from homeassistant.config_entries import ConfigEntry
    from homeassistant.core import HomeAssistant
    from homeassistant.helpers.entity_platform import AddEntitiesCallback

__all__ = ["PcConnectivitySensor", "async_setup_entry"]


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Add the connectivity sensor for one target PC."""

    coordinator: PcOnOffCoordinator = entry.runtime_data
    async_add_entities([PcConnectivitySensor(coordinator)])


class PcConnectivitySensor(
    CoordinatorEntity[PcOnOffCoordinator],
    BinarySensorEntity,
):
    """Reachability of the target PC as seen by the status probe.

    ``is_on`` is ``True`` when the probe confirmed the host is reachable,
    ``False`` when it did not, and ``None`` when probing is disabled or
    inconclusive (for example ``ping`` is missing in a container).
    """

    _attr_has_entity_name = True
    _attr_translation_key = "connectivity"
    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY

    def __init__(self, coordinator: PcOnOffCoordinator) -> None:
        super().__init__(coordinator)
        self._attr_unique_id = f"{coordinator.config.mac}_connectivity"
        self._attr_device_info = coordinator.device_info

    @property
    def is_on(self) -> bool | None:
        """Return the coordinator's reconciled reachability state."""

        return self.coordinator.data
