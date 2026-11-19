"""Unit tests for the pure ``pc_onoff`` validation rules and config flow.

Runs with the Python standard library only::

    python3 -m unittest discover -s tests/homeassistant -p "test_config_flow.py" -v

``validation.py`` is loaded as a submodule of a synthetic package so its
relative imports (``.const`` and ``.protocol``) work without executing the
real ``custom_components.pc_onoff`` package, which requires Home Assistant.
``config_flow.py`` cannot be imported here because ``homeassistant`` and
``voluptuous`` are not installed; it is checked with ``compile()`` and an AST
contract test instead, while the user's verification command also runs
``python3 -m py_compile`` on it.
"""

from __future__ import annotations

import ast
import importlib.util
import sys
import types
import unittest
from pathlib import Path
from typing import Any

_REPO_ROOT = Path(__file__).resolve().parents[2]
_COMPONENT_DIR = _REPO_ROOT / "custom_components" / "pc_onoff"
_VALIDATION_PATH = _COMPONENT_DIR / "validation.py"
_CONFIG_FLOW_PATH = _COMPONENT_DIR / "config_flow.py"

# Private package name: it never collides with the real integration package
# and never triggers ``custom_components/pc_onoff/__init__.py``.
_PACKAGE_NAME = "_pc_onoff_validation_under_test"


def _load_component_modules() -> tuple[Any, Any, Any]:
    """Load const, protocol and validation as one synthetic package."""

    if _PACKAGE_NAME not in sys.modules:
        package = types.ModuleType(_PACKAGE_NAME)
        package.__path__ = [str(_COMPONENT_DIR)]  # type: ignore[attr-defined]
        package.__package__ = _PACKAGE_NAME
        sys.modules[_PACKAGE_NAME] = package

        for name in ("const", "protocol", "validation"):
            module_name = f"{_PACKAGE_NAME}.{name}"
            module_path = _COMPONENT_DIR / f"{name}.py"
            spec = importlib.util.spec_from_file_location(
                module_name, module_path
            )
            if spec is None or spec.loader is None:  # pragma: no cover
                raise RuntimeError(f"Cannot load {module_path}")
            module = importlib.util.module_from_spec(spec)
            sys.modules[module_name] = module
            spec.loader.exec_module(module)

    return (
        sys.modules[f"{_PACKAGE_NAME}.const"],
        sys.modules[f"{_PACKAGE_NAME}.protocol"],
        sys.modules[f"{_PACKAGE_NAME}.validation"],
    )


const, protocol, validation = _load_component_modules()

MAC = "AA:BB:CC:DD:EE:FF"
PUBLIC_IP = "8.8.8.8"
SECRET = "0123456789abcdef0123456789abcdef"

CONTRACT_KEYS = (
    const.CONF_NAME,
    const.CONF_MAC,
    const.CONF_HOST,
    const.CONF_UDP_PORT,
    const.CONF_WOL_PORT,
    const.CONF_BROADCAST_IP,
    const.CONF_PC_SECRET,
    const.CONF_OFF_ACTION,
    const.CONF_PROBE_MODE,
    const.CONF_PROBE_PORT,
    const.CONF_SCAN_INTERVAL,
)

MUTABLE_KEYS = (
    const.CONF_HOST,
    const.CONF_UDP_PORT,
    const.CONF_WOL_PORT,
    const.CONF_BROADCAST_IP,
    const.CONF_PC_SECRET,
    const.CONF_OFF_ACTION,
    const.CONF_PROBE_MODE,
    const.CONF_PROBE_PORT,
    const.CONF_SCAN_INTERVAL,
)


def _user_input(**overrides: Any) -> dict[str, Any]:
    """Return a valid LAN submission, with optional field overrides."""

    payload: dict[str, Any] = {
        const.CONF_NAME: "Gaming PC",
        const.CONF_MAC: MAC,
        const.CONF_HOST: "192.168.1.50",
        const.CONF_UDP_PORT: 9877,
        const.CONF_WOL_PORT: 9,
        const.CONF_BROADCAST_IP: "192.168.1.255",
        const.CONF_PC_SECRET: "",
        const.CONF_OFF_ACTION: "shutdown",
        const.CONF_PROBE_MODE: "ping",
        const.CONF_PROBE_PORT: 445,
        const.CONF_SCAN_INTERVAL: 30,
    }
    payload.update(overrides)
    return payload


def _expected_cleaned(**overrides: Any) -> dict[str, Any]:
    """Return the normalised form of :func:`_user_input`."""

    expected: dict[str, Any] = {
        const.CONF_NAME: "Gaming PC",
        const.CONF_MAC: MAC,
        const.CONF_HOST: "192.168.1.50",
        const.CONF_UDP_PORT: 9877,
        const.CONF_WOL_PORT: 9,
        const.CONF_BROADCAST_IP: "192.168.1.255",
        const.CONF_PC_SECRET: "",
        const.CONF_OFF_ACTION: "shutdown",
        const.CONF_PROBE_MODE: "ping",
        const.CONF_PROBE_PORT: 445,
        const.CONF_SCAN_INTERVAL: 30,
    }
    expected.update(overrides)
    return expected


def _base_config() -> dict[str, Any]:
    """Return a valid stored config-entry data mapping."""

    return {
        const.CONF_NAME: "Gaming PC",
        const.CONF_MAC: MAC,
        const.CONF_HOST: "192.168.1.50",
        const.CONF_UDP_PORT: 9877,
        const.CONF_WOL_PORT: 9,
        const.CONF_BROADCAST_IP: "255.255.255.255",
        const.CONF_PC_SECRET: "",
        const.CONF_OFF_ACTION: "shutdown",
        const.CONF_PROBE_MODE: "ping",
        const.CONF_PROBE_PORT: 445,
        const.CONF_SCAN_INTERVAL: 30,
    }


def _options_from(base: dict[str, Any]) -> dict[str, Any]:
    """Return only the mutable slice of *base*."""

    return {key: base[key] for key in MUTABLE_KEYS}


class TestValidateUserInput(unittest.TestCase):
    """Rules for the initial add-PC config flow."""

    def test_valid_lan_input_without_secret(self) -> None:
        cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_NAME: "  Gaming PC  ",
                    const.CONF_MAC: "aa-bb-cc-dd-ee-ff",
                    const.CONF_HOST: " 192.168.1.50 ",
                }
            )
        )

        self.assertEqual(errors, {})
        self.assertEqual(cleaned, _expected_cleaned())

    def test_missing_optional_fields_use_documented_defaults(self) -> None:
        cleaned, errors = validation.validate_user_input(
            {
                const.CONF_NAME: "Office PC",
                const.CONF_MAC: MAC,
                const.CONF_HOST: "192.168.1.60",
            }
        )

        self.assertEqual(errors, {})
        self.assertEqual(
            cleaned,
            _expected_cleaned(
                **{
                    const.CONF_NAME: "Office PC",
                    const.CONF_HOST: "192.168.1.60",
                    const.CONF_BROADCAST_IP: "255.255.255.255",
                }
            ),
        )

    def test_blank_name_falls_back_to_pc(self) -> None:
        cleaned, errors = validation.validate_user_input(
            _user_input(**{const.CONF_NAME: "   "})
        )

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_NAME], "PC")

    def test_empty_input_reports_only_mac_and_host(self) -> None:
        cleaned, errors = validation.validate_user_input({})

        self.assertEqual(
            errors,
            {
                const.CONF_MAC: "invalid_mac",
                const.CONF_HOST: "invalid_host",
            },
        )
        self.assertEqual(cleaned[const.CONF_NAME], "PC")
        self.assertEqual(
            cleaned[const.CONF_BROADCAST_IP], const.DEFAULT_BROADCAST_IP
        )
        self.assertEqual(cleaned[const.CONF_UDP_PORT], const.DEFAULT_UDP_PORT)
        self.assertEqual(cleaned[const.CONF_WOL_PORT], const.DEFAULT_WOL_PORT)
        self.assertEqual(
            cleaned[const.CONF_PROBE_PORT], const.DEFAULT_PROBE_PORT
        )
        self.assertEqual(
            cleaned[const.CONF_SCAN_INTERVAL], const.DEFAULT_SCAN_INTERVAL
        )
        self.assertEqual(cleaned[const.CONF_OFF_ACTION], "shutdown")
        self.assertEqual(cleaned[const.CONF_PROBE_MODE], "ping")
        self.assertEqual(cleaned[const.CONF_PC_SECRET], "")

    def test_cleaned_always_contains_every_contract_key(self) -> None:
        cleaned, errors = validation.validate_user_input(_user_input())

        self.assertEqual(errors, {})
        self.assertEqual(set(cleaned), set(CONTRACT_KEYS))

    def test_invalid_mac_variants(self) -> None:
        invalid_macs = (
            "",
            "AA:BB:CC:DD:EE",
            "AA:BB:CC:DD:EE:FF:00",
            "AA-BB:CC:DD:EE:FF",
            "AA:BB-CC:DD:EE:FF",
            "GG:BB:CC:DD:EE:FF",
            "A:B:C:D:E:F",
            "AABBCCDDEEFF",
            "AA:BB:CC:DD:EE:F",
        )
        for raw_mac in invalid_macs:
            with self.subTest(mac=raw_mac):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_MAC: raw_mac})
                )
                self.assertEqual(
                    errors, {const.CONF_MAC: "invalid_mac"}
                )

    def test_valid_mac_formats_are_canonicalised(self) -> None:
        for raw_mac in (
            "aa:bb:cc:dd:ee:ff",
            "AA-BB-CC-DD-EE-FF",
            "  aa-bb-cc-dd-ee-ff  ",
            "AA:BB:CC:DD:EE:FF  ",
        ):
            with self.subTest(mac=raw_mac):
                cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_MAC: raw_mac})
                )
                self.assertEqual(errors, {})
                self.assertEqual(cleaned[const.CONF_MAC], MAC)

    def test_invalid_host_variants(self) -> None:
        invalid_hosts = (
            "",
            "not-an-ip",
            "300.1.1.1",
            "0.0.0.0",
            "::",
            "255.255.255.255",
            "224.0.0.1",
            "ff02::1",
        )
        for raw_host in invalid_hosts:
            with self.subTest(host=raw_host):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_HOST: raw_host})
                )
                self.assertEqual(
                    errors, {const.CONF_HOST: "invalid_host"}
                )

    def test_host_is_trimmed(self) -> None:
        cleaned, errors = validation.validate_user_input(
            _user_input(**{const.CONF_HOST: "  192.168.1.50  "})
        )

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_HOST], "192.168.1.50")

    def test_public_ip_without_secret_fails_closed(self) -> None:
        _cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: PUBLIC_IP,
                    const.CONF_PC_SECRET: "",
                }
            )
        )

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "secret_required_for_public_ip"},
        )

    def test_whitespace_only_secret_is_not_a_secret(self) -> None:
        _cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: PUBLIC_IP,
                    const.CONF_PC_SECRET: "   ",
                }
            )
        )

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "secret_required_for_public_ip"},
        )

    def test_public_ip_with_secret_is_valid(self) -> None:
        cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: PUBLIC_IP,
                    const.CONF_PC_SECRET: f"  {SECRET}  ",
                }
            )
        )

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_PC_SECRET], SECRET)
        self.assertEqual(cleaned[const.CONF_HOST], PUBLIC_IP)

    def test_public_ipv6_without_secret_fails_closed(self) -> None:
        _cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: "2001:4860:4860::8888",
                    const.CONF_PC_SECRET: "",
                }
            )
        )

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "secret_required_for_public_ip"},
        )

    def test_trusted_ranges_do_not_require_secret(self) -> None:
        trusted_hosts = (
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.10",
            "169.254.1.1",
            "100.64.0.1",
            "100.127.255.254",
            "::1",
            "fe80::1",
            "fc00::1",
            "fd7a:115c:a1e0::1",
        )
        for host in trusted_hosts:
            with self.subTest(host=host):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(
                        **{
                            const.CONF_HOST: host,
                            const.CONF_PC_SECRET: "",
                        }
                    )
                )
                self.assertEqual(errors, {})

    def test_deprecated_site_local_ipv6_is_public(self) -> None:
        _cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: "fec0::1",
                    const.CONF_PC_SECRET: "",
                }
            )
        )

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "secret_required_for_public_ip"},
        )

    def test_invalid_broadcast_variants(self) -> None:
        for raw_broadcast in (
            "",
            "::1",
            "192.168.1.256",
            "255.255.255.255/24",
            "broadcast",
            None,
        ):
            with self.subTest(broadcast=raw_broadcast):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_BROADCAST_IP: raw_broadcast})
                )
                self.assertEqual(
                    errors,
                    {
                        const.CONF_BROADCAST_IP: "invalid_broadcast_ip"
                    },
                )

    def test_broadcast_is_canonicalised_and_trimmed(self) -> None:
        for raw_broadcast, expected in (
            (" 255.255.255.255 ", "255.255.255.255"),
            ("192.168.1.255", "192.168.1.255"),
        ):
            with self.subTest(broadcast=raw_broadcast):
                cleaned, errors = validation.validate_user_input(
                    _user_input(
                        **{const.CONF_BROADCAST_IP: raw_broadcast}
                    )
                )
                self.assertEqual(errors, {})
                self.assertEqual(
                    cleaned[const.CONF_BROADCAST_IP], expected
                )

    def test_invalid_ports(self) -> None:
        invalid_ports = (0, -1, 65536, 70000, "abc", None, 9877.5, True)
        for key in (
            const.CONF_UDP_PORT,
            const.CONF_WOL_PORT,
            const.CONF_PROBE_PORT,
        ):
            for bad_port in invalid_ports:
                with self.subTest(key=key, port=bad_port):
                    _cleaned, errors = validation.validate_user_input(
                        _user_input(**{key: bad_port})
                    )
                    self.assertEqual(errors, {key: "invalid_port"})

    def test_numeric_ports_are_normalised_to_int(self) -> None:
        for key in (
            const.CONF_UDP_PORT,
            const.CONF_WOL_PORT,
            const.CONF_PROBE_PORT,
        ):
            for raw_port, expected in (
                ("9877", 9877),
                ("  9  ", 9),
                (445.0, 445),
                (1, 1),
                (65535, 65535),
            ):
                with self.subTest(key=key, port=raw_port):
                    cleaned, errors = validation.validate_user_input(
                        _user_input(**{key: raw_port})
                    )
                    self.assertEqual(errors, {})
                    self.assertEqual(cleaned[key], expected)
                    self.assertIsInstance(cleaned[key], int)

    def test_invalid_scan_interval(self) -> None:
        for bad_interval in (
            const.MIN_SCAN_INTERVAL - 1,
            const.MAX_SCAN_INTERVAL + 1,
            0,
            9999,
            "abc",
            None,
            30.5,
            True,
        ):
            with self.subTest(interval=bad_interval):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_SCAN_INTERVAL: bad_interval})
                )
                self.assertEqual(
                    errors,
                    {const.CONF_SCAN_INTERVAL: "invalid_scan_interval"},
                )

    def test_scan_interval_boundaries_are_valid(self) -> None:
        for interval in (
            const.MIN_SCAN_INTERVAL,
            const.MAX_SCAN_INTERVAL,
            "30",
            30.0,
        ):
            with self.subTest(interval=interval):
                cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_SCAN_INTERVAL: interval})
                )
                self.assertEqual(errors, {})
                self.assertEqual(
                    cleaned[const.CONF_SCAN_INTERVAL], int(interval)
                )

    def test_invalid_off_action_variants(self) -> None:
        for bad_action in ("lock", "reboot", "Shutdown", "", None, "sleepy"):
            with self.subTest(action=bad_action):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_OFF_ACTION: bad_action})
                )
                self.assertEqual(
                    errors,
                    {const.CONF_OFF_ACTION: "invalid_off_action"},
                )

    def test_allowed_off_actions_are_valid(self) -> None:
        for action in const.OFF_ACTIONS:
            with self.subTest(action=action):
                cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_OFF_ACTION: action})
                )
                self.assertEqual(errors, {})
                self.assertEqual(cleaned[const.CONF_OFF_ACTION], action)

    def test_invalid_probe_mode_variants(self) -> None:
        for bad_mode in ("icmp", "Ping", "", None, "auto"):
            with self.subTest(mode=bad_mode):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_PROBE_MODE: bad_mode})
                )
                self.assertEqual(
                    errors,
                    {const.CONF_PROBE_MODE: "invalid_probe_mode"},
                )

    def test_allowed_probe_modes_are_valid(self) -> None:
        for mode in const.PROBE_MODES:
            with self.subTest(mode=mode):
                cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_PROBE_MODE: mode})
                )
                self.assertEqual(errors, {})
                self.assertEqual(cleaned[const.CONF_PROBE_MODE], mode)

    def test_control_characters_in_secret_are_rejected(self) -> None:
        for bad_secret in (
            "abc\x00def",
            "abc\ndef",
            "abc\rdef",
            "abc\x1fdef",
            "abc\x7fdef",
            "\x00",
        ):
            with self.subTest(secret=repr(bad_secret)):
                _cleaned, errors = validation.validate_user_input(
                    _user_input(**{const.CONF_PC_SECRET: bad_secret})
                )
                self.assertEqual(
                    errors,
                    {const.CONF_PC_SECRET: "invalid_secret"},
                )

    def test_invalid_secret_wins_over_public_ip_guard(self) -> None:
        # A non-empty secret is present, so the public-IP guard must not
        # overwrite the more specific control-character error.
        _cleaned, errors = validation.validate_user_input(
            _user_input(
                **{
                    const.CONF_HOST: PUBLIC_IP,
                    const.CONF_PC_SECRET: "abc\x00def",
                }
            )
        )

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "invalid_secret"},
        )

    def test_secret_is_trimmed(self) -> None:
        cleaned, errors = validation.validate_user_input(
            _user_input(**{const.CONF_PC_SECRET: "  abc def  "})
        )

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_PC_SECRET], "abc def")

    def test_every_invalid_field_is_reported_together(self) -> None:
        _cleaned, errors = validation.validate_user_input(
            {
                const.CONF_NAME: "Broken PC",
                const.CONF_MAC: "not-a-mac",
                const.CONF_HOST: "0.0.0.0",
                const.CONF_UDP_PORT: 0,
                const.CONF_WOL_PORT: 70000,
                const.CONF_BROADCAST_IP: "::1",
                const.CONF_PC_SECRET: "bad\nsecret",
                const.CONF_OFF_ACTION: "reboot",
                const.CONF_PROBE_MODE: "icmp",
                const.CONF_PROBE_PORT: -1,
                const.CONF_SCAN_INTERVAL: 1,
            }
        )

        self.assertEqual(
            errors,
            {
                const.CONF_MAC: "invalid_mac",
                const.CONF_HOST: "invalid_host",
                const.CONF_UDP_PORT: "invalid_port",
                const.CONF_WOL_PORT: "invalid_port",
                const.CONF_BROADCAST_IP: "invalid_broadcast_ip",
                const.CONF_PC_SECRET: "invalid_secret",
                const.CONF_OFF_ACTION: "invalid_off_action",
                const.CONF_PROBE_MODE: "invalid_probe_mode",
                const.CONF_PROBE_PORT: "invalid_port",
                const.CONF_SCAN_INTERVAL: "invalid_scan_interval",
            },
        )


class TestValidateOptionsInput(unittest.TestCase):
    """Rules for the mutable options flow on an existing entry."""

    def test_change_host_and_off_action(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_HOST] = "192.168.1.60"
        options[const.CONF_OFF_ACTION] = "sleep"

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(errors, {})
        self.assertEqual(set(cleaned), set(MUTABLE_KEYS))
        self.assertEqual(cleaned[const.CONF_HOST], "192.168.1.60")
        self.assertEqual(cleaned[const.CONF_OFF_ACTION], "sleep")
        self.assertNotIn(const.CONF_NAME, cleaned)
        self.assertNotIn(const.CONF_MAC, cleaned)

    def test_identity_cannot_be_overridden_through_options(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_NAME] = "Hijacked PC"
        options[const.CONF_MAC] = "not-a-mac"

        cleaned, errors = validation.validate_options_input(options, base)

        # The crafted MAC is ignored, so it is not reported as invalid.
        self.assertEqual(errors, {})
        self.assertNotIn(const.CONF_NAME, cleaned)
        self.assertNotIn(const.CONF_MAC, cleaned)

    def test_missing_fields_inherit_base_values(self) -> None:
        base = _base_config()

        cleaned, errors = validation.validate_options_input({}, base)

        self.assertEqual(errors, {})
        self.assertEqual(cleaned, _options_from(base))

    def test_public_host_requires_secret(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_HOST] = PUBLIC_IP
        options[const.CONF_PC_SECRET] = ""

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(
            errors,
            {const.CONF_PC_SECRET: "secret_required_for_public_ip"},
        )
        self.assertEqual(set(cleaned), set(MUTABLE_KEYS))

    def test_public_host_with_secret_is_valid(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_HOST] = PUBLIC_IP
        options[const.CONF_PC_SECRET] = SECRET

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_PC_SECRET], SECRET)

    def test_switching_public_host_back_to_lan_allows_blank_secret(self) -> None:
        base = _base_config()
        base[const.CONF_HOST] = PUBLIC_IP
        base[const.CONF_PC_SECRET] = "topsecret"

        options = _options_from(base)
        options[const.CONF_HOST] = "192.168.1.60"
        options[const.CONF_PC_SECRET] = ""

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_HOST], "192.168.1.60")
        self.assertEqual(cleaned[const.CONF_PC_SECRET], "")

    def test_all_mutable_fields_invalid_are_reported_together(self) -> None:
        base = _base_config()
        options = {
            const.CONF_HOST: "0.0.0.0",
            const.CONF_UDP_PORT: 0,
            const.CONF_WOL_PORT: 70000,
            const.CONF_BROADCAST_IP: "::1",
            const.CONF_PC_SECRET: "bad\nsecret",
            const.CONF_OFF_ACTION: "reboot",
            const.CONF_PROBE_MODE: "icmp",
            const.CONF_PROBE_PORT: 0,
            const.CONF_SCAN_INTERVAL: 1,
        }

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(
            errors,
            {
                const.CONF_HOST: "invalid_host",
                const.CONF_UDP_PORT: "invalid_port",
                const.CONF_WOL_PORT: "invalid_port",
                const.CONF_BROADCAST_IP: "invalid_broadcast_ip",
                const.CONF_PC_SECRET: "invalid_secret",
                const.CONF_OFF_ACTION: "invalid_off_action",
                const.CONF_PROBE_MODE: "invalid_probe_mode",
                const.CONF_PROBE_PORT: "invalid_port",
                const.CONF_SCAN_INTERVAL: "invalid_scan_interval",
            },
        )
        self.assertEqual(set(cleaned), set(MUTABLE_KEYS))

    def test_option_values_are_normalised(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_HOST] = "  192.168.1.60 "
        options[const.CONF_UDP_PORT] = "9877"
        options[const.CONF_WOL_PORT] = "9"
        options[const.CONF_PROBE_PORT] = 445.0
        options[const.CONF_SCAN_INTERVAL] = "30"
        options[const.CONF_BROADCAST_IP] = " 192.168.1.255 "
        options[const.CONF_PC_SECRET] = "  topsecret  "
        options[const.CONF_OFF_ACTION] = " sleep "
        options[const.CONF_PROBE_MODE] = " tcp "

        cleaned, errors = validation.validate_options_input(options, base)

        self.assertEqual(errors, {})
        self.assertEqual(cleaned[const.CONF_HOST], "192.168.1.60")
        self.assertEqual(cleaned[const.CONF_UDP_PORT], 9877)
        self.assertEqual(cleaned[const.CONF_WOL_PORT], 9)
        self.assertEqual(cleaned[const.CONF_PROBE_PORT], 445)
        self.assertEqual(cleaned[const.CONF_SCAN_INTERVAL], 30)
        self.assertEqual(cleaned[const.CONF_BROADCAST_IP], "192.168.1.255")
        self.assertEqual(cleaned[const.CONF_PC_SECRET], "topsecret")
        self.assertEqual(cleaned[const.CONF_OFF_ACTION], "sleep")
        self.assertEqual(cleaned[const.CONF_PROBE_MODE], "tcp")

    def test_inputs_are_not_mutated(self) -> None:
        base = _base_config()
        options = _options_from(base)
        options[const.CONF_HOST] = "192.168.1.60"
        base_before = dict(base)
        options_before = dict(options)

        validation.validate_options_input(options, base)

        self.assertEqual(base, base_before)
        self.assertEqual(options, options_before)


class TestModuleContract(unittest.TestCase):
    """Static guardrails for the pure module and the thin HA adapter."""

    def test_validation_imports_no_home_assistant_or_voluptuous(self) -> None:
        source = _VALIDATION_PATH.read_text(encoding="utf-8")
        tree = ast.parse(source, filename=str(_VALIDATION_PATH))

        forbidden = {"homeassistant", "voluptuous"}
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    self.assertNotIn(
                        alias.name.split(".", 1)[0],
                        forbidden,
                        f"validation.py must stay pure: import {alias.name}",
                    )
            elif isinstance(node, ast.ImportFrom):
                root = (node.module or "").split(".", 1)[0]
                self.assertNotIn(
                    root,
                    forbidden,
                    f"validation.py must stay pure: from {node.module}",
                )

        self.assertNotRegex(
            source,
            r"(?m)^\s*(?:from|import)\s+(?:homeassistant|voluptuous)\b",
        )

    def test_validation_exports_the_contract_functions(self) -> None:
        self.assertTrue(callable(validation.validate_user_input))
        self.assertTrue(callable(validation.validate_options_input))

    def test_config_flow_compiles(self) -> None:
        source = _CONFIG_FLOW_PATH.read_text(encoding="utf-8")
        compile(source, str(_CONFIG_FLOW_PATH), "exec")

    def test_config_flow_classes_match_the_contract(self) -> None:
        tree = ast.parse(
            _CONFIG_FLOW_PATH.read_text(encoding="utf-8"),
            filename=str(_CONFIG_FLOW_PATH),
        )
        classes = {
            node.name: node
            for node in tree.body
            if isinstance(node, ast.ClassDef)
        }
        self.assertIn("PcOnOffConfigFlow", classes)
        self.assertIn("PcOnOffOptionsFlow", classes)

        config_flow = classes["PcOnOffConfigFlow"]
        self.assertTrue(
            any(
                isinstance(base, ast.Name) and base.id == "ConfigFlow"
                for base in config_flow.bases
            )
        )
        self.assertTrue(
            any(
                keyword.arg == "domain"
                and isinstance(keyword.value, ast.Name)
                and keyword.value.id == "DOMAIN"
                for keyword in config_flow.keywords
            )
        )

        version_values = [
            ast.literal_eval(node.value)
            for node in config_flow.body
            if isinstance(node, ast.Assign)
            and any(
                isinstance(target, ast.Name) and target.id == "VERSION"
                for target in node.targets
            )
        ]
        self.assertEqual(version_values, [1])

        options_flow = classes["PcOnOffOptionsFlow"]
        self.assertTrue(
            any(
                isinstance(base, ast.Name) and base.id == "OptionsFlow"
                for base in options_flow.bases
            )
        )

    def test_config_flow_defines_both_async_steps(self) -> None:
        tree = ast.parse(_CONFIG_FLOW_PATH.read_text(encoding="utf-8"))
        classes = {
            node.name: node
            for node in tree.body
            if isinstance(node, ast.ClassDef)
        }

        config_methods = {
            node.name
            for node in classes["PcOnOffConfigFlow"].body
            if isinstance(node, ast.AsyncFunctionDef)
        }
        options_methods = {
            node.name
            for node in classes["PcOnOffOptionsFlow"].body
            if isinstance(node, ast.AsyncFunctionDef)
        }
        self.assertIn("async_step_user", config_methods)
        self.assertIn("async_step_init", options_methods)

    def test_options_flow_getter_is_static_and_callback_decorated(self) -> None:
        tree = ast.parse(_CONFIG_FLOW_PATH.read_text(encoding="utf-8"))
        config_flow = next(
            node
            for node in tree.body
            if isinstance(node, ast.ClassDef)
            and node.name == "PcOnOffConfigFlow"
        )
        getter = next(
            node
            for node in config_flow.body
            if isinstance(node, ast.FunctionDef)
            and node.name == "async_get_options_flow"
        )

        decorators = {
            decorator.id
            for decorator in getter.decorator_list
            if isinstance(decorator, ast.Name)
        }
        self.assertIn("staticmethod", decorators)
        self.assertIn("callback", decorators)
        self.assertTrue(
            any(
                isinstance(node, ast.Return)
                and isinstance(node.value, ast.Call)
                and isinstance(node.value.func, ast.Name)
                and node.value.func.id == "PcOnOffOptionsFlow"
                for node in ast.walk(getter)
            )
        )

    def test_options_flow_exposes_config_entry_property(self) -> None:
        tree = ast.parse(_CONFIG_FLOW_PATH.read_text(encoding="utf-8"))
        options_flow = next(
            node
            for node in tree.body
            if isinstance(node, ast.ClassDef)
            and node.name == "PcOnOffOptionsFlow"
        )

        initializer = next(
            node
            for node in options_flow.body
            if isinstance(node, ast.FunctionDef) and node.name == "__init__"
        )
        assigned_attributes = {
            target.attr
            for node in ast.walk(initializer)
            if isinstance(node, ast.Assign)
            for target in node.targets
            if isinstance(target, ast.Attribute)
        }
        # Stored privately because newer HA versions expose ``config_entry``
        # as a read-only property on OptionsFlow.
        self.assertIn("_config_entry", assigned_attributes)
        self.assertNotIn("config_entry", assigned_attributes)

        property_getter = next(
            node
            for node in options_flow.body
            if isinstance(node, ast.FunctionDef)
            and node.name == "config_entry"
        )
        decorators = {
            decorator.id
            for decorator in property_getter.decorator_list
            if isinstance(decorator, ast.Name)
        }
        self.assertIn("property", decorators)

    def test_config_flow_delegates_to_pure_validation(self) -> None:
        tree = ast.parse(_CONFIG_FLOW_PATH.read_text(encoding="utf-8"))

        imported: set[str] = set()
        for node in tree.body:
            if (
                isinstance(node, ast.ImportFrom)
                and node.level == 1
                and node.module == "validation"
            ):
                imported.update(alias.name for alias in node.names)
        self.assertEqual(
            imported, {"validate_options_input", "validate_user_input"}
        )

        called = {
            node.func.id
            for node in ast.walk(tree)
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name)
        }
        self.assertIn("validate_user_input", called)
        self.assertIn("validate_options_input", called)

    def test_config_flow_uses_unique_id_guard_and_entry_creation(self) -> None:
        source = _CONFIG_FLOW_PATH.read_text(encoding="utf-8")

        self.assertIn("async_set_unique_id", source)
        self.assertIn("_abort_if_unique_id_configured", source)
        self.assertIn("self.async_create_entry", source)
        self.assertIn("PcOnOffOptionsFlow(config_entry)", source)


if __name__ == "__main__":
    unittest.main()
