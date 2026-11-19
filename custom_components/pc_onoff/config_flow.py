"""Home Assistant config and options flows for the ``pc_onoff`` integration.

Layer 2 adapter: this module only renders forms, delegates every rule to the
pure :mod:`.validation` module, and turns the clean result into a config
entry.  Keeping the logic in Layer 1 means the same rules are unit tested
without a Home Assistant runtime and cannot drift between the initial setup
and later option changes.

``name`` and ``mac`` are immutable after setup.  They determine unique IDs,
entity IDs, the device registry entry and therefore the Google Home voice
name (for example *"Hey Google, turn on Gaming PC"*).
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import voluptuous as vol

from homeassistant.config_entries import ConfigEntry, ConfigFlow, OptionsFlow
from homeassistant.core import callback
from homeassistant.data_entry_flow import FlowResult
from homeassistant.helpers import selector

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
    OFF_ACTIONS,
    PROBE_MODES,
)
from .validation import validate_options_input, validate_user_input

_MIN_PORT = 1
_MAX_PORT = 65535

_TEXT_SELECTOR = selector.TextSelector()
_SECRET_SELECTOR = selector.TextSelector(
    selector.TextSelectorConfig(type=selector.TextSelectorType.PASSWORD)
)
_PORT_SELECTOR = selector.NumberSelector(
    selector.NumberSelectorConfig(
        min=_MIN_PORT,
        max=_MAX_PORT,
        step=1,
        mode=selector.NumberSelectorMode.BOX,
    )
)
_SCAN_INTERVAL_SELECTOR = selector.NumberSelector(
    selector.NumberSelectorConfig(
        min=MIN_SCAN_INTERVAL,
        max=MAX_SCAN_INTERVAL,
        step=1,
        mode=selector.NumberSelectorMode.BOX,
    )
)
_OFF_ACTION_SELECTOR = selector.SelectSelector(
    selector.SelectSelectorConfig(
        options=list(OFF_ACTIONS),
        mode=selector.SelectSelectorMode.DROPDOWN,
        translation_key=CONF_OFF_ACTION,
    )
)
_PROBE_MODE_SELECTOR = selector.SelectSelector(
    selector.SelectSelectorConfig(
        options=list(PROBE_MODES),
        mode=selector.SelectSelectorMode.DROPDOWN,
        translation_key=CONF_PROBE_MODE,
    )
)


def _text_default(
    defaults: Mapping[str, Any],
    key: str,
    fallback: str = "",
) -> str:
    """Return a form-safe string default for *key*."""

    value = defaults.get(key, fallback)
    if value is None:
        return fallback
    text = str(value)
    return text if text else fallback


def _int_default(
    defaults: Mapping[str, Any],
    key: str,
    fallback: int,
    minimum: int,
    maximum: int,
) -> int:
    """Return an in-range integer default for *key*."""

    value = defaults.get(key, fallback)
    if isinstance(value, bool):
        return fallback
    try:
        number = int(value)
    except (TypeError, ValueError):
        return fallback
    return min(max(number, minimum), maximum)


def _identity_fields(defaults: Mapping[str, Any]) -> dict[Any, Any]:
    """Return the immutable name/MAC fields shown only during add."""

    return {
        vol.Required(
            CONF_NAME, default=_text_default(defaults, CONF_NAME)
        ): _TEXT_SELECTOR,
        vol.Required(
            CONF_MAC, default=_text_default(defaults, CONF_MAC)
        ): _TEXT_SELECTOR,
    }


def _mutable_fields(defaults: Mapping[str, Any]) -> dict[Any, Any]:
    """Return the fields shared by the add form and the options form."""

    return {
        vol.Required(
            CONF_HOST, default=_text_default(defaults, CONF_HOST)
        ): _TEXT_SELECTOR,
        vol.Required(
            CONF_UDP_PORT,
            default=_int_default(
                defaults,
                CONF_UDP_PORT,
                DEFAULT_UDP_PORT,
                _MIN_PORT,
                _MAX_PORT,
            ),
        ): _PORT_SELECTOR,
        vol.Required(
            CONF_WOL_PORT,
            default=_int_default(
                defaults,
                CONF_WOL_PORT,
                DEFAULT_WOL_PORT,
                _MIN_PORT,
                _MAX_PORT,
            ),
        ): _PORT_SELECTOR,
        vol.Required(
            CONF_BROADCAST_IP,
            default=_text_default(
                defaults, CONF_BROADCAST_IP, DEFAULT_BROADCAST_IP
            ),
        ): _TEXT_SELECTOR,
        vol.Optional(
            CONF_PC_SECRET, default=_text_default(defaults, CONF_PC_SECRET)
        ): _SECRET_SELECTOR,
        vol.Required(
            CONF_OFF_ACTION,
            default=_text_default(
                defaults, CONF_OFF_ACTION, DEFAULT_OFF_ACTION
            ),
        ): _OFF_ACTION_SELECTOR,
        vol.Required(
            CONF_PROBE_MODE,
            default=_text_default(
                defaults, CONF_PROBE_MODE, DEFAULT_PROBE_MODE
            ),
        ): _PROBE_MODE_SELECTOR,
        vol.Required(
            CONF_PROBE_PORT,
            default=_int_default(
                defaults,
                CONF_PROBE_PORT,
                DEFAULT_PROBE_PORT,
                _MIN_PORT,
                _MAX_PORT,
            ),
        ): _PORT_SELECTOR,
        vol.Required(
            CONF_SCAN_INTERVAL,
            default=_int_default(
                defaults,
                CONF_SCAN_INTERVAL,
                DEFAULT_SCAN_INTERVAL,
                MIN_SCAN_INTERVAL,
                MAX_SCAN_INTERVAL,
            ),
        ): _SCAN_INTERVAL_SELECTOR,
    }


def _user_schema(defaults: Mapping[str, Any]) -> vol.Schema:
    """Build the initial add-PC form schema."""

    return vol.Schema({**_identity_fields(defaults), **_mutable_fields(defaults)})


def _options_schema(defaults: Mapping[str, Any]) -> vol.Schema:
    """Build the options form schema (name/MAC intentionally absent)."""

    return vol.Schema(_mutable_fields(defaults))


class PcOnOffConfigFlow(ConfigFlow, domain=DOMAIN):
    """Handle the one-time UI setup of a target computer."""

    VERSION = 1

    async def async_step_user(
        self,
        user_input: dict[str, Any] | None = None,
    ) -> FlowResult:
        """Validate the add-PC form and create the config entry."""

        errors: dict[str, str] = {}

        if user_input is not None:
            cleaned, errors = validate_user_input(user_input)
            if not errors:
                # A MAC address identifies the physical PC, so it is also the
                # unique ID that prevents duplicate entries.
                await self.async_set_unique_id(cleaned[CONF_MAC])
                self._abort_if_unique_id_configured()
                return self.async_create_entry(
                    title=cleaned[CONF_NAME],
                    data=cleaned,
                )

        defaults: Mapping[str, Any] = user_input or {}
        return self.async_show_form(
            step_id="user",
            data_schema=_user_schema(defaults),
            errors=errors,
        )

    @staticmethod
    @callback
    def async_get_options_flow(
        config_entry: ConfigEntry,
    ) -> PcOnOffOptionsFlow:
        """Return the options flow for an existing PC entry."""

        return PcOnOffOptionsFlow(config_entry)


class PcOnOffOptionsFlow(OptionsFlow):
    """Update mutable network and power settings without breaking identity."""

    def __init__(self, config_entry: ConfigEntry) -> None:
        # Keep our own reference instead of assigning ``self.config_entry``:
        # newer Home Assistant versions expose ``config_entry`` as a read-only
        # property, while 2024.1's plain ``OptionsFlow`` has no such property.
        # The override below works with both shapes.
        self._config_entry = config_entry

    @property
    def config_entry(self) -> ConfigEntry:
        """Return the config entry this options flow belongs to."""

        return self._config_entry

    async def async_step_init(
        self,
        user_input: dict[str, Any] | None = None,
    ) -> FlowResult:
        """Validate the options form and save the mutable subset."""

        errors: dict[str, str] = {}

        if user_input is not None:
            cleaned, errors = validate_options_input(
                user_input, self.config_entry.data
            )
            if not errors:
                return self.async_create_entry(title="", data=cleaned)

        defaults: dict[str, Any] = {
            **self.config_entry.data,
            **self.config_entry.options,
        }
        if user_input is not None:
            defaults.update(user_input)

        return self.async_show_form(
            step_id="init",
            data_schema=_options_schema(defaults),
            errors=errors,
        )
