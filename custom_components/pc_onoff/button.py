"""Action buttons for the ``pc_onoff`` integration.

Six declarative :class:`PcActionButton` entities expose the complete command
vocabulary of the PC daemon: wake (magic packet), shutdown, reboot, sleep,
hibernate and lock screen.  All of them delegate to
:class:`PcOnOffCoordinator`; the button layer never touches sockets or the
optimistic state machine itself.

The buttons are intentionally separate from the primary switch so Google Home
can keep a single clean on/off entity while automations or HA scripts still
reach every power action.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from homeassistant.components.button import ButtonEntity, ButtonEntityDescription
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .coordinator import PcOnOffCoordinator
from .protocol import PowerAction

if TYPE_CHECKING:  # pragma: no cover - typing only
    from homeassistant.config_entries import ConfigEntry
    from homeassistant.core import HomeAssistant
    from homeassistant.helpers.entity_platform import AddEntitiesCallback

__all__ = [
    "BUTTON_DESCRIPTIONS",
    "PcActionButton",
    "PcButtonDescription",
    "async_setup_entry",
]


@dataclass(frozen=True, kw_only=True)
class PcButtonDescription(ButtonEntityDescription):
    """Entity description with the power action to send when pressed.

    ``action is None`` is the Wake button, which broadcasts a magic packet
    instead of sending a UDP 9877 command.
    """

    action: PowerAction | None = None


BUTTON_DESCRIPTIONS: tuple[PcButtonDescription, ...] = (
    PcButtonDescription(
        key="wake",
        translation_key="wake",
        icon="mdi:power-on",
        action=None,
    ),
    PcButtonDescription(
        key="shutdown",
        translation_key="shutdown",
        icon="mdi:power",
        action=PowerAction.SHUTDOWN,
    ),
    PcButtonDescription(
        key="reboot",
        translation_key="reboot",
        icon="mdi:restart",
        action=PowerAction.REBOOT,
    ),
    PcButtonDescription(
        key="sleep",
        translation_key="sleep",
        icon="mdi:power-sleep",
        action=PowerAction.SLEEP,
    ),
    PcButtonDescription(
        key="hibernate",
        translation_key="hibernate",
        icon="mdi:snowflake",
        action=PowerAction.HIBERNATE,
    ),
    PcButtonDescription(
        key="lock",
        translation_key="lock",
        icon="mdi:lock",
        action=PowerAction.LOCK,
    ),
)


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Add all six action buttons for one target PC."""

    coordinator: PcOnOffCoordinator = entry.runtime_data
    async_add_entities(
        [
            PcActionButton(coordinator, description)
            for description in BUTTON_DESCRIPTIONS
        ]
    )


class PcActionButton(CoordinatorEntity[PcOnOffCoordinator], ButtonEntity):
    """One stateless action button backed by the shared coordinator."""

    _attr_has_entity_name = True

    def __init__(
        self,
        coordinator: PcOnOffCoordinator,
        description: PcButtonDescription,
    ) -> None:
        super().__init__(coordinator)
        self.entity_description = description
        self._attr_unique_id = f"{coordinator.config.mac}_{description.key}"
        self._attr_device_info = coordinator.device_info

    async def async_press(self) -> None:
        """Dispatch the description's action through the coordinator."""

        action = self.entity_description.action
        if action is None:
            await self.coordinator.async_wake()
            return

        await self.coordinator.async_power(action)
