"""Home Assistant entry point for the ``pc_onoff`` integration.

The integration is deliberately thin here: build an immutable
:class:`PcTargetConfig` from the ConfigEntry (options override data), create
one :class:`PcOnOffCoordinator`, run its first refresh, publish it as
``entry.runtime_data`` and forward the platforms.

The Home Assistant imports are performed inside the functions (annotations
are strings thanks to ``from __future__ import annotations``).  This keeps
``import custom_components.pc_onoff.<pure module>`` working in the
standard-library-only test suite, where the 1 GB ``homeassistant`` package is
not installed; Home Assistant itself imports this module normally.
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from .const import PLATFORMS

if TYPE_CHECKING:  # pragma: no cover - typing only
    from homeassistant.config_entries import ConfigEntry
    from homeassistant.core import HomeAssistant

    from .coordinator import PcOnOffCoordinator

_LOGGER = logging.getLogger(__name__)

__all__ = [
    "async_setup_entry",
    "async_unload_entry",
]


async def async_setup_entry(hass: "HomeAssistant", entry: "ConfigEntry") -> bool:
    """Set up one target PC from a config entry."""

    # Imported lazily: see the module docstring.
    from .coordinator import PcOnOffCoordinator, PcTargetConfig

    merged = {**entry.data, **entry.options}
    config = PcTargetConfig.from_mapping(merged)
    coordinator = PcOnOffCoordinator(hass, config)

    # Fail setup early (with a repair-friendly ConfigEntryNotReady) instead of
    # exposing entities that have never been reconciled.
    await coordinator.async_config_entry_first_refresh()

    entry.runtime_data = coordinator

    try:
        await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    except Exception:
        # The entry never reached a running state: make sure the reconciliation
        # timer cannot outlive it and that no half-configured coordinator is
        # left behind for the unload path.
        coordinator.async_cancel_scheduled_refresh()
        entry.runtime_data = None
        raise

    entry.async_on_unload(entry.add_update_listener(_async_reload_entry))
    _LOGGER.debug(
        "Set up %s for %s (%s)",
        entry.entry_id,
        config.name,
        config.host,
    )
    return True


async def async_unload_entry(hass: "HomeAssistant", entry: "ConfigEntry") -> bool:
    """Unload one config entry and stop its scheduled reconciliation poll."""

    coordinator: "PcOnOffCoordinator | None" = getattr(entry, "runtime_data", None)
    if coordinator is not None:
        coordinator.async_cancel_scheduled_refresh()

    return bool(
        await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    )


async def _async_reload_entry(hass: "HomeAssistant", entry: "ConfigEntry") -> None:
    """Reload the entry after its options change."""

    await hass.config_entries.async_reload(entry.entry_id)
