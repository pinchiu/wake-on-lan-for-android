"""Unit tests for the pure-Python ``pc_onoff`` protocol deep module.

Runs with the Python standard library only::

    python3 -m unittest discover -s tests/homeassistant -p "test_protocol.py" -v

The module under test is loaded directly from its file path so these tests do
not execute ``custom_components.pc_onoff.__init__`` (which needs Home
Assistant) and do not require the ``homeassistant`` package at all.
"""

from __future__ import annotations

import hashlib
import hmac
import importlib.util
import ipaddress
import re
import socket
import sys
import time
import unittest
from pathlib import Path
from unittest import mock

_MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "custom_components"
    / "pc_onoff"
    / "protocol.py"
)


def _load_protocol_module():
    """Load ``protocol.py`` under a private module name."""

    spec = importlib.util.spec_from_file_location(
        "pc_onoff_protocol_under_test", _MODULE_PATH
    )
    if spec is None or spec.loader is None:  # pragma: no cover - broken checkout
        raise RuntimeError(f"Cannot load protocol module from {_MODULE_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


protocol = _load_protocol_module()

MAC = "AA:BB:CC:DD:EE:FF"
MAC_BYTES = bytes.fromhex("AABBCCDDEEFF")
SECRET = "0123456789abcdef0123456789abcdef"
FIXED_TS = 1700000000
FIXED_NONCE = "0011223344556677"
FIXED_ENVELOPE = (
    "SHUTDOWN:192.168.1.10|ts=1700000000|nonce=0011223344556677"
    "|sig=6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc"
)


class TestMacAddresses(unittest.TestCase):
    """MAC normalization, byte parsing and magic packet construction."""

    def test_normalize_colon_mac(self) -> None:
        self.assertEqual(protocol.normalize_mac("AA:BB:CC:DD:EE:FF"), MAC)

    def test_normalize_dash_mac(self) -> None:
        self.assertEqual(protocol.normalize_mac("aa-bb-cc-dd-ee-ff"), MAC)

    def test_normalize_strips_surrounding_whitespace(self) -> None:
        self.assertEqual(protocol.normalize_mac(" \t aa:bb:cc:dd:ee:ff \n"), MAC)

    def test_normalize_rejects_mixed_separators(self) -> None:
        for value in (
            "AA:BB-CC:DD:EE:FF",
            "AA-BB:CC-DD:EE-FF",
            "AA:BB:CC:DD:EE-FF",
        ):
            with self.subTest(value=value):
                with self.assertRaises(protocol.InvalidMacError):
                    protocol.normalize_mac(value)

    def test_normalize_rejects_wrong_octet_width(self) -> None:
        for value in (
            "A:B:C:D:E:F",
            "AAA:BBB:CCC:DDD:EEE:FFF",
            "AA:B:CC:DD:EE:FF",
            "0A:0B:0C:0D:0E:00F",
        ):
            with self.subTest(value=value):
                with self.assertRaises(protocol.InvalidMacError):
                    protocol.normalize_mac(value)

    def test_normalize_rejects_non_hex_characters(self) -> None:
        for value in (
            "GG:BB:CC:DD:EE:FF",
            "AA:BB:CC:DD:EE:FF:00",
            "AA.BB.CC.DD.EE.FF",
            "AABBCCDDEEFF",
            "",
            "   ",
        ):
            with self.subTest(value=value):
                with self.assertRaises(protocol.InvalidMacError):
                    protocol.normalize_mac(value)

    def test_normalize_rejects_non_string(self) -> None:
        for value in (None, 0, b"AA:BB:CC:DD:EE:FF", ["AA:BB:CC:DD:EE:FF"]):
            with self.subTest(value=value):
                with self.assertRaises(protocol.InvalidMacError):
                    protocol.normalize_mac(value)  # type: ignore[arg-type]

    def test_error_hierarchy(self) -> None:
        self.assertTrue(issubclass(protocol.InvalidMacError, protocol.ProtocolError))
        self.assertTrue(
            issubclass(protocol.AuthenticationRequiredError, protocol.ProtocolError)
        )
        self.assertTrue(issubclass(protocol.ProtocolError, Exception))

    def test_parse_mac_bytes_returns_six_bytes(self) -> None:
        raw = protocol.parse_mac_bytes("aa-bb-cc-dd-ee-ff")
        self.assertIsInstance(raw, bytes)
        self.assertEqual(len(raw), 6)
        self.assertEqual(raw, MAC_BYTES)

    def test_parse_mac_bytes_rejects_invalid_mac(self) -> None:
        with self.assertRaises(protocol.InvalidMacError):
            protocol.parse_mac_bytes("not-a-mac")

    def test_build_magic_packet_layout(self) -> None:
        packet = protocol.build_magic_packet(MAC)
        self.assertEqual(len(packet), 102)
        self.assertEqual(packet[:6], b"\xff" * 6)
        self.assertEqual(packet[6:], MAC_BYTES * 16)
        self.assertEqual(packet[6:102].__len__(), 96)

    def test_build_magic_packet_rejects_invalid_mac(self) -> None:
        with self.assertRaises(protocol.InvalidMacError):
            protocol.build_magic_packet("AA:BB:CC:DD:EE:GG")

    def test_max_envelope_bytes_constant(self) -> None:
        self.assertEqual(protocol.MAX_ENVELOPE_BYTES, 512)

    def test_mac_regex_constants(self) -> None:
        self.assertIsInstance(protocol.MAC_COLON_RE, re.Pattern)
        self.assertIsInstance(protocol.MAC_DASH_RE, re.Pattern)
        self.assertIsNotNone(protocol.MAC_COLON_RE.fullmatch(MAC))
        self.assertIsNotNone(protocol.MAC_DASH_RE.fullmatch("aa-bb-cc-dd-ee-ff"))


class TestIpClassification(unittest.TestCase):
    """Unicast validation and trusted-network classification."""

    def test_valid_unicast_addresses(self) -> None:
        for value in (
            "192.168.1.10",
            " 10.0.0.5 ",
            "8.8.8.8",
            "::1",
            "2001:4860:4860::8888",
            "fe80::1",
        ):
            with self.subTest(value=value):
                self.assertTrue(protocol.is_valid_unicast_ip(value))

    def test_rejects_non_unicast_addresses(self) -> None:
        for value in (
            "0.0.0.0",
            "::",
            "224.0.0.1",
            "239.255.255.250",
            "ff02::1",
            "255.255.255.255",
        ):
            with self.subTest(value=value):
                self.assertFalse(protocol.is_valid_unicast_ip(value))

    def test_rejects_malformed_addresses(self) -> None:
        for value in ("", "   ", "not-an-ip", "192.168.1.999", "1.2.3"):
            with self.subTest(value=value):
                self.assertFalse(protocol.is_valid_unicast_ip(value))

    def test_rejects_non_string(self) -> None:
        for value in (None, 123, ["192.168.1.1"]):
            with self.subTest(value=value):
                self.assertFalse(protocol.is_valid_unicast_ip(value))  # type: ignore[arg-type]

    def test_trusted_addresses(self) -> None:
        for value in (
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
            "::ffff:192.168.1.1",
            " ::ffff:127.0.0.1 ",
        ):
            with self.subTest(value=value):
                self.assertTrue(protocol.is_trusted_address(value))

    def test_untrusted_addresses(self) -> None:
        for value in (
            "8.8.8.8",
            "100.128.0.1",
            "100.63.255.255",
            "192.0.2.1",
            "198.18.0.1",
            "2001:4860:4860::8888",
            "fec0::1",
            "::ffff:8.8.8.8",
        ):
            with self.subTest(value=value):
                self.assertFalse(protocol.is_trusted_address(value))

    def test_trusted_rejects_malformed_and_non_string(self) -> None:
        for value in ("", "example.com", None, 42):
            with self.subTest(value=value):
                self.assertFalse(protocol.is_trusted_address(value))  # type: ignore[arg-type]


class TestSignCommand(unittest.TestCase):
    """HMAC-SHA256 envelope formatting and validation."""

    def test_fixed_cross_language_vector(self) -> None:
        envelope = protocol.sign_command(
            "SHUTDOWN:192.168.1.10",
            SECRET,
            timestamp=FIXED_TS,
            nonce=FIXED_NONCE,
        )
        self.assertEqual(envelope, FIXED_ENVELOPE)

    def test_default_timestamp_and_nonce(self) -> None:
        before = int(time.time())
        envelope = protocol.sign_command("shutdown", SECRET)
        after = int(time.time())

        command, ts_field, nonce_field, sig_field = envelope.split("|")
        self.assertEqual(command, "shutdown")
        self.assertTrue(ts_field.startswith("ts="))
        self.assertTrue(before <= int(ts_field[3:]) <= after)
        self.assertRegex(nonce_field, r"^nonce=[0-9a-f]{16}$")
        self.assertRegex(sig_field, r"^sig=[0-9a-f]{64}$")

        expected = hmac.new(
            SECRET.encode("utf-8"),
            f"{command}|{ts_field}|{nonce_field}".encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual(sig_field, f"sig={expected}")

    def test_secret_is_trimmed_before_signing(self) -> None:
        padded = protocol.sign_command(
            "lock", f"  {SECRET}\t", timestamp=FIXED_TS, nonce=FIXED_NONCE
        )
        plain = protocol.sign_command(
            "lock", SECRET, timestamp=FIXED_TS, nonce=FIXED_NONCE
        )
        self.assertEqual(padded, plain)

    def test_empty_secret_is_rejected(self) -> None:
        for secret in ("", "   ", "\t\n"):
            with self.subTest(secret=secret):
                with self.assertRaises(protocol.AuthenticationRequiredError):
                    protocol.sign_command("shutdown", secret)

    def test_secret_control_characters_are_rejected(self) -> None:
        for secret in ("bad\nsecret", "bad\x00secret", "bad\x7fsecret"):
            with self.subTest(secret=secret):
                with self.assertRaises(protocol.ProtocolError) as ctx:
                    protocol.sign_command("shutdown", secret)
                self.assertNotIsInstance(
                    ctx.exception, protocol.AuthenticationRequiredError
                )

    def test_invalid_commands_are_rejected(self) -> None:
        for command in (
            "",
            " shutdown",
            "shutdown ",
            "shut|down",
            "shut\ndown",
            "shut\x00down",
            "shut\x7fdown",
            None,
            123,
        ):
            with self.subTest(command=command):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.sign_command(command, SECRET, FIXED_TS, FIXED_NONCE)  # type: ignore[arg-type]

    def test_negative_timestamp_is_rejected(self) -> None:
        with self.assertRaises(protocol.ProtocolError):
            protocol.sign_command("shutdown", SECRET, timestamp=-1)

    def test_invalid_nonces_are_rejected(self) -> None:
        for nonce in (
            "",
            "001122334455667",  # 15 chars
            "00112233445566778",  # 17 chars
            "001122334455667g",  # non-hex
            "00112233445566 7",  # space
            "AABBCCDDEEFF0011",  # uppercase is not accepted
            123,
        ):
            with self.subTest(nonce=nonce):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.sign_command(
                        "shutdown", SECRET, FIXED_TS, nonce  # type: ignore[arg-type]
                    )

    def test_envelope_size_limit(self) -> None:
        # Fixed envelope overhead with a 10-digit timestamp and 16-char nonce
        # is 106 bytes, so 400 chars fit (506) and 500 chars do not (606).
        fits = protocol.sign_command(
            "X" * 400, SECRET, timestamp=FIXED_TS, nonce=FIXED_NONCE
        )
        self.assertEqual(len(fits.encode("utf-8")), 506)

        with self.assertRaises(protocol.ProtocolError) as ctx:
            protocol.sign_command(
                "X" * 500, SECRET, timestamp=FIXED_TS, nonce=FIXED_NONCE
            )
        self.assertIn("512", str(ctx.exception))


class TestPreparePowerPayload(unittest.TestCase):
    """Bare vs. signed payload selection and fail-closed public targets."""

    def test_bare_command_for_trusted_targets(self) -> None:
        trusted = (
            "192.168.1.50",
            "10.1.2.3",
            "172.16.4.5",
            "169.254.10.20",
            "100.101.102.103",
            "127.0.0.1",
            "::1",
            "fe80::1",
            "fd7a:115c:a1e0::1",
            "::ffff:192.168.1.50",
        )
        for action in protocol.PowerAction:
            for target in trusted:
                with self.subTest(action=action, target=target):
                    payload = protocol.prepare_power_payload(action, target)
                    self.assertEqual(payload, action.value.encode("utf-8"))

    def test_trusted_target_strips_whitespace(self) -> None:
        payload = protocol.prepare_power_payload(
            protocol.PowerAction.REBOOT, "  192.168.1.50  "
        )
        self.assertEqual(payload, b"reboot")

    def test_public_target_without_secret_fails_closed(self) -> None:
        for secret in ("", "   "):
            with self.subTest(secret=secret):
                with self.assertRaises(protocol.AuthenticationRequiredError):
                    protocol.prepare_power_payload(
                        protocol.PowerAction.SHUTDOWN, "8.8.8.8", secret=secret
                    )

    def test_public_target_with_secret_is_signed(self) -> None:
        payload = protocol.prepare_power_payload(
            protocol.PowerAction.HIBERNATE,
            "8.8.8.8",
            secret=SECRET,
            timestamp=FIXED_TS,
            nonce=FIXED_NONCE,
        )
        self.assertEqual(
            payload,
            protocol.sign_command(
                "hibernate", SECRET, FIXED_TS, FIXED_NONCE
            ).encode("utf-8"),
        )
        self.assertTrue(payload.startswith(b"hibernate|ts="))
        self.assertIn(b"|sig=", payload)

    def test_lan_target_with_secret_is_signed(self) -> None:
        payload = protocol.prepare_power_payload(
            protocol.PowerAction.LOCK,
            "192.168.1.50",
            secret=SECRET,
            timestamp=FIXED_TS,
            nonce=FIXED_NONCE,
        )
        self.assertEqual(
            payload,
            protocol.sign_command(
                "lock", SECRET, FIXED_TS, FIXED_NONCE
            ).encode("utf-8"),
        )

    def test_invalid_targets_are_rejected(self) -> None:
        for target in (
            "0.0.0.0",
            "::",
            "255.255.255.255",
            "224.0.0.1",
            "ff02::1",
            "example.com",
            "",
            "not-an-ip",
        ):
            with self.subTest(target=target):
                with self.assertRaises(protocol.InvalidTargetError):
                    protocol.prepare_power_payload(
                        protocol.PowerAction.SHUTDOWN, target
                    )

    def test_non_string_secret_is_rejected(self) -> None:
        with self.assertRaises(protocol.ProtocolError):
            protocol.prepare_power_payload(
                protocol.PowerAction.SHUTDOWN,
                "192.168.1.50",
                secret=None,  # type: ignore[arg-type]
            )


class TestSyncSenders(unittest.TestCase):
    """Socket wiring for the blocking send helpers (socket mocked)."""

    def test_send_magic_packet_wiring(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            sock = mock.MagicMock()
            socket_cls.return_value.__enter__.return_value = sock
            protocol.send_magic_packet_sync(MAC, "192.168.1.255", 9)

        socket_cls.assert_called_once_with(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt.assert_called_once_with(
            socket.SOL_SOCKET, socket.SO_BROADCAST, 1
        )
        packet, destination = sock.sendto.call_args.args
        self.assertEqual(destination, ("192.168.1.255", 9))
        self.assertEqual(packet, protocol.build_magic_packet(MAC))
        self.assertEqual(len(packet), 102)

    def test_send_magic_packet_defaults(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            sock = mock.MagicMock()
            socket_cls.return_value.__enter__.return_value = sock
            protocol.send_magic_packet_sync(MAC)

        self.assertEqual(sock.sendto.call_args.args[1], ("255.255.255.255", 9))

    def test_send_magic_packet_rejects_bad_port(self) -> None:
        for port in (0, -1, 65536, 100000):
            with self.subTest(port=port):
                with self.assertRaises(protocol.InvalidTargetError):
                    protocol.send_magic_packet_sync(MAC, "255.255.255.255", port)

    def test_send_magic_packet_rejects_bad_broadcast_ip(self) -> None:
        for broadcast in ("999.1.1.1", "not-an-ip", "::1", ""):
            with self.subTest(broadcast=broadcast):
                with self.assertRaises(protocol.InvalidTargetError):
                    protocol.send_magic_packet_sync(MAC, broadcast, 9)

    def test_send_magic_packet_rejects_bad_mac(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            with self.assertRaises(protocol.InvalidMacError):
                protocol.send_magic_packet_sync("bad-mac", "255.255.255.255", 9)
        socket_cls.assert_not_called()

    def test_send_power_command_ipv4_wiring(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            sock = mock.MagicMock()
            socket_cls.return_value.__enter__.return_value = sock
            protocol.send_power_command_sync(
                "192.168.1.10", protocol.PowerAction.REBOOT, port=9999
            )

        socket_cls.assert_called_once_with(socket.AF_INET, socket.SOCK_DGRAM)
        sock.sendto.assert_called_once_with(b"reboot", ("192.168.1.10", 9999))

    def test_send_power_command_ipv6_wiring(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            sock = mock.MagicMock()
            socket_cls.return_value.__enter__.return_value = sock
            protocol.send_power_command_sync(
                "2001:4860:4860::8888",
                protocol.PowerAction.LOCK,
                port=9877,
                secret=SECRET,
            )

        socket_cls.assert_called_once_with(socket.AF_INET6, socket.SOCK_DGRAM)
        payload, destination = sock.sendto.call_args.args
        self.assertEqual(
            destination,
            (str(ipaddress.ip_address("2001:4860:4860::8888")), 9877),
        )
        self.assertTrue(payload.startswith(b"lock|ts="))

    def test_send_power_command_rejects_bad_port(self) -> None:
        for port in (0, 65536):
            with self.subTest(port=port):
                with mock.patch.object(protocol.socket, "socket") as socket_cls:
                    with self.assertRaises(protocol.InvalidTargetError):
                        protocol.send_power_command_sync(
                            "192.168.1.10", protocol.PowerAction.LOCK, port=port
                        )
                socket_cls.assert_not_called()

    def test_send_power_command_rejects_bad_target(self) -> None:
        with mock.patch.object(protocol.socket, "socket") as socket_cls:
            with self.assertRaises(protocol.InvalidTargetError):
                protocol.send_power_command_sync(
                    "255.255.255.255", protocol.PowerAction.SHUTDOWN
                )
        socket_cls.assert_not_called()


class TestAsyncSenders(unittest.IsolatedAsyncioTestCase):
    """Loopback UDP round-trips for the asyncio wrappers."""

    async def test_async_send_power_command_bare_roundtrip(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.addCleanup(sock.close)
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]

        await protocol.async_send_power_command(
            "127.0.0.1", protocol.PowerAction.SLEEP, port=port
        )

        sock.settimeout(2.0)
        payload, _source = sock.recvfrom(2048)
        self.assertEqual(payload, b"sleep")

    async def test_async_send_power_command_signed_roundtrip(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.addCleanup(sock.close)
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]

        await protocol.async_send_power_command(
            "127.0.0.1",
            protocol.PowerAction.HIBERNATE,
            port=port,
            secret=SECRET,
        )

        sock.settimeout(2.0)
        payload, _source = sock.recvfrom(2048)
        command, ts_field, nonce_field, sig_field = payload.decode("utf-8").split(
            "|"
        )
        self.assertEqual(command, "hibernate")
        self.assertRegex(ts_field, r"^ts=\d+$")
        self.assertRegex(nonce_field, r"^nonce=[0-9a-f]{16}$")
        self.assertRegex(sig_field, r"^sig=[0-9a-f]{64}$")

        expected = hmac.new(
            SECRET.encode("utf-8"),
            f"{command}|{ts_field}|{nonce_field}".encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual(sig_field, f"sig={expected}")
        self.assertLessEqual(len(payload), protocol.MAX_ENVELOPE_BYTES)

    async def test_async_send_magic_packet_roundtrip(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.addCleanup(sock.close)
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]

        await protocol.async_send_magic_packet(MAC, "127.0.0.1", port)

        sock.settimeout(2.0)
        packet, _source = sock.recvfrom(2048)
        self.assertEqual(len(packet), 102)
        self.assertEqual(packet, protocol.build_magic_packet(MAC))

    async def test_async_send_power_command_propagates_auth_error(self) -> None:
        with self.assertRaises(protocol.AuthenticationRequiredError):
            await protocol.async_send_power_command(
                "8.8.8.8", protocol.PowerAction.SHUTDOWN, port=9877
            )

    async def test_async_send_magic_packet_propagates_mac_error(self) -> None:
        with self.assertRaises(protocol.InvalidMacError):
            await protocol.async_send_magic_packet("not-a-mac")


if __name__ == "__main__":
    unittest.main()
