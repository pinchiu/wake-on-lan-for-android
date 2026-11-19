"""Optimistic state machine for the ``pc_onoff`` integration.

This is a Layer 1 domain module: it imports **only** the Python standard
library (plus the integration's own :mod:`const` module) and never imports
``homeassistant``.  The logic can therefore be unit-tested with plain
``python3 -m unittest`` without a Home Assistant runtime.

Why this exists
---------------
A PC that was just woken by a Wake-on-LAN magic packet needs 15–35 seconds
before the operating system answers a ping or accepts a TCP connection.  If a
background poll runs five seconds after the user said *"Hey Google, turn on my
PC"* it sees an offline host and reports ``off``.  Google Home then flips the
switch back to *off* in the app, which looks like the voice command failed
even though the PC is booting.

:class:`OptimisticStateTracker` solves that by holding the requested state for
``OPTIMISTIC_HOLD_SECONDS`` (25 s) until either:

* the probe confirms the target state (the hold is cleared early), or
* the hold window expires (the real probe result wins from then on).

The tracker is intentionally free of asyncio and Home Assistant types: the
coordinator owns scheduling and calling :meth:`reconcile` after each probe.
It is not thread-safe; Home Assistant calls it from the event loop only.
"""

from __future__ import annotations

import time
from collections.abc import Callable

from .const import OPTIMISTIC_HOLD_SECONDS

__all__ = ["OptimisticStateTracker"]


class OptimisticStateTracker:
    """Hold an optimistic on/off intent until reality catches up.

    Args:
        hold_seconds: How long a requested state is trusted regardless of the
            probe result.  Values are clamped to ``>= 0``; ``0`` disables the
            optimistic window (every non-``None`` probe wins immediately).
        clock: Monotonic time source, injectable for deterministic tests.

    The externally visible state is exposed through :attr:`current_state`.
    """

    __slots__ = (
        "_clock",
        "_hold_seconds",
        "_hold_until",
        "_pending_target",
        "_state",
    )

    def __init__(
        self,
        hold_seconds: float = OPTIMISTIC_HOLD_SECONDS,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._hold_seconds = max(0.0, float(hold_seconds))
        self._clock = clock
        self._state: bool | None = None
        self._pending_target: bool | None = None
        self._hold_until: float = 0.0

    @property
    def current_state(self) -> bool | None:
        """Return the state that should be shown to the user.

        ``None`` means "unknown" (before the first probe or intent).
        """

        return self._state

    def record_intent(self, target_on: bool) -> bool:
        """Record a user-requested state and start the optimistic hold.

        Must be called *before* (or immediately after) the UDP command is
        sent, so a poll racing with the command cannot bounce the entity.

        Returns:
            The requested state, so callers can pass it straight to
            ``async_set_updated_data()``.
        """

        self._state = target_on
        self._pending_target = target_on
        self._hold_until = self._clock() + self._hold_seconds
        return target_on

    def reconcile(self, probed: bool | None) -> bool | None:
        """Merge a probe result into the optimistic state machine.

        Args:
            probed: ``True`` when the host is reachable, ``False`` when it is
                not, or ``None`` when probing is disabled/unavailable.

        Returns:
            The state the entity should display now.
        """

        # Probing disabled or unavailable (e.g. ping missing in a container):
        # keep whatever we know, including an optimistic state, forever.
        if probed is None:
            return self._state

        if self._pending_target is not None:
            if probed == self._pending_target:
                # The PC caught up with the requested state: converge early
                # and release the hold so future probes are authoritative.
                self._pending_target = None
                self._hold_until = 0.0
                self._state = probed
                return self._state

            if self._clock() < self._hold_until:
                # The PC is still booting (or shutting down): show the user's
                # intent instead of the stale probe result.
                return self._pending_target

            # The hold window expired without reaching the target: accept
            # reality and drop the pending intent.
            self._pending_target = None
            self._hold_until = 0.0

        self._state = probed
        return self._state
