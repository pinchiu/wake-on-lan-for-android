"""Metadata and translation tests for the pc_onoff Home Assistant integration.

The tests read the component JSON files and ``const.py`` straight from disk, so
they run with plain ``unittest`` and never import the Home Assistant runtime.
"""

from __future__ import annotations

import importlib.util
import json
import types
import unittest
from functools import lru_cache
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
COMPONENT_DIR = REPO_ROOT / "custom_components" / "pc_onoff"
CONST_PATH = COMPONENT_DIR / "const.py"
MANIFEST_PATH = COMPONENT_DIR / "manifest.json"
LANGUAGE_FILES: dict[str, Path] = {
    "strings": COMPONENT_DIR / "strings.json",
    "en": COMPONENT_DIR / "translations" / "en.json",
    "zh-Hant": COMPONENT_DIR / "translations" / "zh-Hant.json",
}

ERROR_KEYS: tuple[str, ...] = (
    "invalid_mac",
    "invalid_host",
    "invalid_broadcast_ip",
    "invalid_port",
    "invalid_scan_interval",
    "invalid_off_action",
    "invalid_probe_mode",
    "secret_required_for_public_ip",
    "invalid_secret",
)

BUTTON_KEYS: tuple[str, ...] = (
    "wake",
    "shutdown",
    "reboot",
    "sleep",
    "hibernate",
    "lock",
)


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    """Build a JSON object, rejecting duplicate keys instead of hiding them."""
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


@lru_cache(maxsize=None)
def load_document(path: Path) -> dict[str, Any]:
    """Load a JSON document, failing on duplicate keys."""
    with path.open(encoding="utf-8") as handle:
        document = json.load(handle, object_pairs_hook=_reject_duplicate_keys)
    if not isinstance(document, dict):
        raise AssertionError(f"{path} does not contain a JSON object")
    return document


@lru_cache(maxsize=None)
def load_const() -> types.ModuleType:
    """Load ``const.py`` directly, without executing the package ``__init__``."""
    spec = importlib.util.spec_from_file_location("pc_onoff_const_under_test", CONST_PATH)
    if spec is None or spec.loader is None:
        raise AssertionError(f"cannot load {CONST_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def flatten(document: Any) -> dict[tuple[str, ...], Any]:
    """Return every leaf of a JSON document keyed by its full path."""
    flattened: dict[tuple[str, ...], Any] = {}

    def walk(node: Any, prefix: tuple[str, ...]) -> None:
        if isinstance(node, dict):
            for key, value in node.items():
                walk(value, (*prefix, key))
        else:
            flattened[prefix] = node

    walk(document, ())
    return flattened


def format_path(path: tuple[str, ...]) -> str:
    """Render a key path as a dotted string for readable failures."""
    return ".".join(path) or "<root>"


def format_paths(paths: list[tuple[str, ...]]) -> str:
    """Render several key paths for readable assertion messages."""
    return ", ".join(format_path(path) for path in paths)


def config_field_keys(const: types.ModuleType) -> tuple[str, ...]:
    """Return the 11 config flow fields shared with ``const.py``."""
    return (
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


def options_field_keys(const: types.ModuleType) -> tuple[str, ...]:
    """Return the mutable options flow fields (name and MAC stay fixed)."""
    return (
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


class TranslationMetadataTest(unittest.TestCase):
    """Verify metadata, translation structure, and bilingual key parity."""

    def setUp(self) -> None:
        self.documents = {
            language: load_document(path) for language, path in LANGUAGE_FILES.items()
        }

    def test_json_documents_are_non_empty_objects(self) -> None:
        for language, document in self.documents.items():
            with self.subTest(language=language):
                self.assertIsInstance(document, dict)
                self.assertTrue(document, f"{language} translations are empty")
        manifest = load_document(MANIFEST_PATH)
        self.assertIsInstance(manifest, dict)
        self.assertTrue(manifest, "manifest.json is empty")

    def test_translation_key_parity(self) -> None:
        reference = flatten(self.documents["strings"])
        for language in ("en", "zh-Hant"):
            actual = flatten(self.documents[language])
            missing = sorted(set(reference) - set(actual))
            extra = sorted(set(actual) - set(reference))
            with self.subTest(language=language):
                self.assertEqual(
                    missing,
                    [],
                    f"{language}: keys missing from {language}.json: {format_paths(missing)}",
                )
                self.assertEqual(
                    extra,
                    [],
                    f"{language}: keys missing from strings.json: {format_paths(extra)}",
                )

    def test_translation_values_are_non_empty_strings(self) -> None:
        for language, document in self.documents.items():
            for path, value in flatten(document).items():
                with self.subTest(language=language, key=format_path(path)):
                    self.assertIsInstance(value, str, f"{language}: {format_path(path)} is not a string")
                    self.assertTrue(
                        value.strip(),
                        f"{language}: {format_path(path)} is empty",
                    )

    def test_english_translations_match_strings(self) -> None:
        self.assertEqual(
            self.documents["en"],
            self.documents["strings"],
            "translations/en.json must mirror strings.json",
        )

    def test_required_translation_structure(self) -> None:
        const = load_const()
        config_keys = config_field_keys(const)
        options_keys = options_field_keys(const)

        required: set[tuple[str, ...]] = {
            ("config", "step", "user", "title"),
            ("config", "step", "user", "description"),
            ("config", "abort", "already_configured"),
            ("options", "step", "init", "title"),
            ("options", "step", "init", "description"),
            ("entity", "binary_sensor", "connectivity", "name"),
        }
        required.update(("config", "step", "user", "data", key) for key in config_keys)
        required.update(
            ("config", "step", "user", "data_description", key) for key in config_keys
        )
        required.update(("config", "error", key) for key in ERROR_KEYS)
        required.update(("options", "step", "init", "data", key) for key in options_keys)
        required.update(
            ("options", "step", "init", "data_description", key) for key in options_keys
        )
        required.update(
            ("selector", "off_action", "options", option) for option in const.OFF_ACTIONS
        )
        required.update(
            ("selector", "probe_mode", "options", option) for option in const.PROBE_MODES
        )
        required.update(("entity", "button", key, "name") for key in BUTTON_KEYS)

        for language, document in self.documents.items():
            available = set(flatten(document))
            missing = sorted(required - available)
            with self.subTest(language=language):
                self.assertEqual(
                    missing,
                    [],
                    f"{language}: missing required translation keys: {format_paths(missing)}",
                )

    def test_field_sets_match_constants(self) -> None:
        const = load_const()
        strings = self.documents["strings"]
        config_step = strings["config"]["step"]["user"]
        options_step = strings["options"]["step"]["init"]

        self.assertEqual(set(config_step["data"]), set(config_field_keys(const)))
        self.assertEqual(set(config_step["data_description"]), set(config_field_keys(const)))
        self.assertEqual(set(options_step["data"]), set(options_field_keys(const)))
        self.assertEqual(set(options_step["data_description"]), set(options_field_keys(const)))

    def test_selector_options_match_constants(self) -> None:
        const = load_const()
        selector = self.documents["strings"]["selector"]

        self.assertEqual(set(selector["off_action"]["options"]), set(const.OFF_ACTIONS))
        self.assertEqual(set(selector["probe_mode"]["options"]), set(const.PROBE_MODES))

    def test_chinese_translations_are_localized(self) -> None:
        chinese = flatten(self.documents["zh-Hant"])
        english = flatten(self.documents["en"])
        localized_paths = (
            ("config", "step", "user", "title"),
            ("config", "step", "user", "description"),
            ("config", "step", "user", "data", "name"),
            ("config", "error", "invalid_mac"),
            ("config", "abort", "already_configured"),
            ("options", "step", "init", "title"),
            ("selector", "off_action", "options", "shutdown"),
            ("selector", "probe_mode", "options", "ping"),
            ("entity", "binary_sensor", "connectivity", "name"),
            ("entity", "button", "wake", "name"),
        )
        for path in localized_paths:
            with self.subTest(key=format_path(path)):
                self.assertNotEqual(
                    chinese[path],
                    english[path],
                    f"{format_path(path)} is not localized in zh-Hant.json",
                )
                self.assertRegex(chinese[path], r"[\u4e00-\u9fff]")

    def test_manifest_required_fields(self) -> None:
        const = load_const()
        manifest = load_document(MANIFEST_PATH)

        self.assertEqual(manifest.get("domain"), const.DOMAIN)
        self.assertEqual(manifest.get("name"), "Wake-on-LAN & PC Power (pc_onoff)")
        self.assertEqual(manifest.get("version"), "1.8.0")
        self.assertIs(manifest.get("config_flow"), True)
        self.assertEqual(manifest.get("iot_class"), "local_polling")
        self.assertEqual(manifest.get("integration_type"), "device")
        self.assertEqual(manifest.get("requirements"), [])
        self.assertEqual(manifest.get("codeowners"), ["@pinchiu"])
        self.assertTrue(
            manifest.get("documentation", "").startswith("https://"),
            "manifest.json needs an https documentation URL",
        )
        self.assertTrue(
            manifest.get("issue_tracker", "").startswith("https://"),
            "manifest.json needs an https issue tracker URL",
        )


if __name__ == "__main__":
    unittest.main()
