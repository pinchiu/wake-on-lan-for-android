"""Asynchronous host reachability probing for the ``pc_onoff`` integration.

Layer 1 pure-Python domain core: this module imports ONLY the standard library
and must never depend on ``homeassistant`` so its behaviour can be unit tested
directly with ``python3 -m unittest``.

``async_probe_host`` answers one question: "is the target PC reachable right
now?"  It supports three configurable modes:

* ``ping``  - spawn one ICMP echo request (requires a real IP literal).
* ``tcp``   - attempt one TCP connection to a user supplied port (for example
  445/SMB, 22/SSH or 3389/RDP).  A refused connection still proves that the
  host is up unless the caller explicitly opts out.
* ``none``  - probing disabled; the caller keeps optimistic state.

The function returns ``True`` (online), ``False`` (offline) or ``None``
(unknown / probing unavailable, e.g. a container without ``ping`` or
``CAP_NET_RAW``).  ``None`` deliberately degrades to optimistic mode rather
than falsely forcing the switch entity offline.
"""

from __future__ import annotations

import asyncio
import ipaddress

_MODE_NONE = "none"
_MODE_TCP = "tcp"
_MODE_PING = "ping"

_MIN_TIMEOUT = 0.1
_PING_GRACE_SECONDS = 1.0

# ``ping -W`` takes whole seconds; anything below one second is pointless.
_MIN_PING_WAIT_SECONDS = 1


async def _close_writer(writer: asyncio.StreamWriter) -> None:
    """Close an ``asyncio`` stream writer, suppressing teardown errors."""
    try:
        writer.close()
        await writer.wait_closed()
    except Exception:  # noqa: BLE001 - best effort cleanup only
        pass


async def _kill_process(process: asyncio.subprocess.Process) -> None:
    """Kill a subprocess and reap it without raising on teardown races."""
    try:
        process.kill()
    except (OSError, ProcessLookupError):
        return
    try:
        await process.wait()
    except (OSError, TimeoutError):
        pass


def _coerce_timeout(timeout: float) -> float | None:
    """Return a sane positive timeout, or ``None`` for unusable input."""
    try:
        value = float(timeout)
    except (TypeError, ValueError):
        return None
    return max(_MIN_TIMEOUT, value)


def _coerce_port(port: int) -> int | None:
    """Return the TCP port as an ``int`` when it is in range, else ``None``."""
    try:
        value = int(port)
    except (TypeError, ValueError):
        return None
    if not 1 <= value <= 65535:
        return None
    return value


async def async_probe_host(
    host: str,
    mode: str,
    tcp_port: int = 445,
    timeout: float = 2.0,
    treat_refused_as_online: bool = True,
) -> bool | None:
    """Probe ``host`` for reachability.

    Args:
        host: Target IPv4/IPv6 literal or hostname (``tcp`` mode only).
        mode: One of ``"ping"``, ``"tcp"`` or ``"none"``.
        tcp_port: TCP port used by ``tcp`` mode.
        timeout: Overall timeout in seconds (clamped to at least 0.1).
        treat_refused_as_online: In ``tcp`` mode, a refused connection still
            means the machine is powered on and reachable.

    Returns:
        ``True`` if the host is considered online, ``False`` if it is
        considered offline, or ``None`` when probing is disabled/unavailable.
    """
    if mode == _MODE_NONE:
        return None

    clean_host = str(host).strip()
    if not clean_host or clean_host.startswith("-"):
        # Empty input and option-like strings can never be a valid target and
        # must not reach a subprocess (argument-injection defence in depth).
        return False

    parsed_timeout = _coerce_timeout(timeout)
    if parsed_timeout is None:
        return False
    timeout = parsed_timeout

    if mode == _MODE_TCP:
        port = _coerce_port(tcp_port)
        if port is None:
            return False

        try:
            _reader, writer = await asyncio.wait_for(
                asyncio.open_connection(clean_host, port),
                timeout=timeout,
            )
        except ConnectionRefusedError:
            # The kernel answered with RST: the machine is up, the service is
            # not.  Most users still consider that "online".
            return bool(treat_refused_as_online)
        except (TimeoutError, OSError):
            return False

        await _close_writer(writer)
        return True

    if mode == _MODE_PING:
        # Only accept real IP literals: avoids DNS hangs, rejects flag-like
        # strings, and keeps the subprocess arguments unambiguous.
        try:
            ipaddress.ip_address(clean_host)
        except ValueError:
            return False

        ping_wait = str(max(_MIN_PING_WAIT_SECONDS, int(timeout)))
        try:
            process = await asyncio.create_subprocess_exec(
                "ping",
                "-c",
                "1",
                "-W",
                ping_wait,
                "--",
                clean_host,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
        except (FileNotFoundError, PermissionError):
            # No ``ping`` binary or no permission for raw sockets (common in
            # containers).  Report "unknown" instead of a false "offline".
            return None
        except OSError:
            return False

        try:
            await asyncio.wait_for(
                process.wait(),
                timeout=timeout + _PING_GRACE_SECONDS,
            )
        except TimeoutError:
            await _kill_process(process)
            return False
        except (FileNotFoundError, PermissionError):
            return None
        except OSError:
            return False

        return process.returncode == 0

    # Unknown probe mode: leave the state undecided.
    return None
