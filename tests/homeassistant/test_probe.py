"""Unit tests for :mod:`custom_components.pc_onoff.probe`.

Standard library only: no Home Assistant runtime and no third-party packages
are required.  Run with::

    python3 -m unittest discover -s tests/homeassistant -p "test_probe.py" -v
"""

from __future__ import annotations

import asyncio
import socket
import unittest
from unittest.mock import AsyncMock, patch

from custom_components.pc_onoff.probe import async_probe_host

_EXEC_TARGET = "custom_components.pc_onoff.probe.asyncio.create_subprocess_exec"


class _CompletedProcess:
    """Minimal stand-in for ``asyncio.subprocess.Process`` (already exited)."""

    def __init__(self, returncode: int) -> None:
        self.returncode = returncode
        self.killed = False
        self.kill_calls = 0

    async def wait(self) -> int:
        return self.returncode

    def kill(self) -> None:
        self.killed = True
        self.kill_calls += 1


class _HangingProcess:
    """Process whose first ``wait()`` hangs until ``kill()`` is called."""

    def __init__(self) -> None:
        self.returncode: int | None = None
        self.killed = False
        self.kill_calls = 0

    async def wait(self) -> int:
        if not self.killed:
            await asyncio.Event().wait()
        return self.returncode  # type: ignore[return-value]

    def kill(self) -> None:
        self.killed = True
        self.kill_calls += 1


class ProbeSkipModeTests(unittest.IsolatedAsyncioTestCase):
    """Modes that intentionally do not touch the network."""

    async def test_none_mode_returns_none(self) -> None:
        self.assertIsNone(await async_probe_host("192.168.1.10", "none"))

    async def test_none_mode_does_not_validate_host(self) -> None:
        # Disabled probing short-circuits before any host sanitisation.
        self.assertIsNone(await async_probe_host("", "none"))
        self.assertIsNone(await async_probe_host("-c", "none"))

    async def test_unknown_mode_returns_none(self) -> None:
        self.assertIsNone(await async_probe_host("192.168.1.10", "icmp"))


class ProbeHostSanitisationTests(unittest.IsolatedAsyncioTestCase):
    """Host and timeout sanitisation happens before any I/O."""

    async def test_empty_host_returns_false(self) -> None:
        with patch(_EXEC_TARGET, new=AsyncMock()) as exec_mock:
            self.assertFalse(await async_probe_host("   ", "ping"))
        exec_mock.assert_not_called()

    async def test_leading_dash_host_returns_false(self) -> None:
        with patch(_EXEC_TARGET, new=AsyncMock()) as exec_mock:
            self.assertFalse(await async_probe_host("-c", "ping"))
        exec_mock.assert_not_called()

    async def test_unparseable_timeout_returns_false(self) -> None:
        with patch(_EXEC_TARGET, new=AsyncMock()) as exec_mock:
            self.assertFalse(
                await async_probe_host(
                    "192.168.1.10", "ping", timeout="not-a-number"  # type: ignore[arg-type]
                )
            )
        exec_mock.assert_not_called()


class ProbeTcpModeTests(unittest.IsolatedAsyncioTestCase):
    """TCP mode against real loopback sockets."""

    async def asyncSetUp(self) -> None:
        self._connections: list[asyncio.StreamWriter] = []
        self.server = await asyncio.start_server(
            self._handle_client, "127.0.0.1", 0
        )
        self.port = self.server.sockets[0].getsockname()[1]

    async def asyncTearDown(self) -> None:
        self.server.close()
        await self.server.wait_closed()
        for writer in self._connections:
            writer.close()
            try:
                await writer.wait_closed()
            except OSError:
                pass

    async def _handle_client(
        self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter
    ) -> None:
        # Accept the connection, then immediately close our side.
        self._connections.append(writer)
        writer.close()
        try:
            await writer.wait_closed()
        except OSError:
            pass

    async def test_open_tcp_port_returns_true(self) -> None:
        self.assertTrue(
            await async_probe_host(
                "127.0.0.1", "tcp", tcp_port=self.port, timeout=2.0
            )
        )

    async def test_open_tcp_port_ignores_refused_policy(self) -> None:
        self.assertTrue(
            await async_probe_host(
                "127.0.0.1",
                "tcp",
                tcp_port=self.port,
                treat_refused_as_online=False,
            )
        )

    async def _closed_loopback_port(self) -> int:
        """Return a loopback TCP port with no listener (connection refused)."""
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.bind(("127.0.0.1", 0))
            # Bound but not listening: the kernel answers SYNs with RST, so
            # connect() fails with ECONNREFUSED deterministically.
            return sock.getsockname()[1]
        finally:
            sock.close()

    async def test_refused_tcp_port_treated_as_online(self) -> None:
        port = await self._closed_loopback_port()
        self.assertTrue(
            await async_probe_host(
                "127.0.0.1",
                "tcp",
                tcp_port=port,
                timeout=2.0,
                treat_refused_as_online=True,
            )
        )

    async def test_refused_tcp_port_treated_as_offline(self) -> None:
        port = await self._closed_loopback_port()
        self.assertFalse(
            await async_probe_host(
                "127.0.0.1",
                "tcp",
                tcp_port=port,
                timeout=2.0,
                treat_refused_as_online=False,
            )
        )

    async def test_invalid_tcp_ports_return_false(self) -> None:
        for port in (0, -1, 65536, 70000):
            with self.subTest(port=port):
                self.assertFalse(
                    await async_probe_host(
                        "127.0.0.1", "tcp", tcp_port=port
                    )
                )


class ProbePingModeTests(unittest.IsolatedAsyncioTestCase):
    """Ping mode with a mocked subprocess seam."""

    async def test_ping_exit_zero_returns_true(self) -> None:
        process = _CompletedProcess(0)
        with patch(_EXEC_TARGET, new=AsyncMock(return_value=process)) as exec_mock:
            result = await async_probe_host(
                "192.168.1.10", "ping", timeout=2.0
            )

        self.assertTrue(result)
        exec_mock.assert_awaited_once()
        args = exec_mock.await_args.args
        self.assertEqual(args[:5], ("ping", "-c", "1", "-W", "2"))
        self.assertEqual(args[5], "--")
        self.assertEqual(args[6], "192.168.1.10")
        self.assertIsNotNone(exec_mock.await_args.kwargs["stdout"])
        self.assertIsNotNone(exec_mock.await_args.kwargs["stderr"])

    async def test_ping_exit_nonzero_returns_false(self) -> None:
        process = _CompletedProcess(1)
        with patch(_EXEC_TARGET, new=AsyncMock(return_value=process)):
            self.assertFalse(
                await async_probe_host("192.168.1.10", "ping", timeout=2.0)
            )

    async def test_ping_command_minimum_wait_is_one_second(self) -> None:
        process = _CompletedProcess(0)
        with patch(_EXEC_TARGET, new=AsyncMock(return_value=process)) as exec_mock:
            await async_probe_host("192.168.1.10", "ping", timeout=0.1)
        self.assertEqual(exec_mock.await_args.args[4], "1")

    async def test_ping_accepts_ipv6_literal(self) -> None:
        process = _CompletedProcess(0)
        with patch(_EXEC_TARGET, new=AsyncMock(return_value=process)) as exec_mock:
            self.assertTrue(
                await async_probe_host("fe80::1", "ping", timeout=2.0)
            )
        self.assertEqual(exec_mock.await_args.args[6], "fe80::1")

    async def test_ping_rejects_flag_injection_without_subprocess(self) -> None:
        with patch(_EXEC_TARGET, new=AsyncMock()) as exec_mock:
            self.assertFalse(await async_probe_host("-c", "ping"))
        exec_mock.assert_not_called()

    async def test_ping_rejects_invalid_host_without_subprocess(self) -> None:
        for host in ("example.com", "8.8.8.8; reboot", "192.168.1.999", "-"):
            with self.subTest(host=host):
                with patch(_EXEC_TARGET, new=AsyncMock()) as exec_mock:
                    self.assertFalse(await async_probe_host(host, "ping"))
                exec_mock.assert_not_called()

    async def test_missing_ping_binary_returns_none(self) -> None:
        with patch(
            _EXEC_TARGET, new=AsyncMock(side_effect=FileNotFoundError)
        ):
            self.assertIsNone(
                await async_probe_host("192.168.1.10", "ping", timeout=2.0)
            )

    async def test_missing_raw_socket_permission_returns_none(self) -> None:
        with patch(
            _EXEC_TARGET, new=AsyncMock(side_effect=PermissionError)
        ):
            self.assertIsNone(
                await async_probe_host("192.168.1.10", "ping", timeout=2.0)
            )

    async def test_ping_timeout_kills_child_and_returns_false(self) -> None:
        process = _HangingProcess()
        with patch(
            _EXEC_TARGET, new=AsyncMock(return_value=process)
        ):
            result = await async_probe_host(
                "192.168.1.10", "ping", timeout=0.1
            )

        self.assertFalse(result)
        self.assertTrue(process.killed)
        self.assertEqual(process.kill_calls, 1)


if __name__ == "__main__":
    unittest.main()
