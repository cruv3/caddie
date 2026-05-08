"""Physical UI-settle gate.

Action tools (tap, swipe, key, text, open_app, set_orientation) capture a
UI-tree hash before performing the action and then poll the backend until
the hash has changed AND stayed stable for ``stable_ms``. The action tool
returns only after that wait, so a model that fires the next tool call
immediately is throttled by HTTP latency rather than by a behavioural
protocol it can ignore.

Disable via ``LLM_SMARTPHONE_SETTLE=0`` for tests and performance
comparisons.
"""

from __future__ import annotations

import os
import time
from typing import Protocol


class _SupportsUiHash(Protocol):
    def ui_hash(self) -> str: ...


SETTLE_TIMEOUTS_MS: dict[str, int] = {
    "tap": 2500,
    "swipe": 2500,
    "key": 2500,
    "text": 3500,
    "open_app": 8000,
    "set_orientation": 4000,
}

_DEFAULT_STABLE_MS = 350
_DEFAULT_POLL_MS = 120


def settle_enabled() -> bool:
    raw = os.environ.get("LLM_SMARTPHONE_SETTLE", "1").strip().lower()
    return raw not in ("0", "false", "no", "off")


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        return default
    return max(0, value)
    

def baseline_hash(backend: _SupportsUiHash) -> str | None:
    """Capture the pre-action hash. Returns None if settling is disabled
    or hash capture fails (so the action proceeds without a settle wait)."""
    if not settle_enabled():
        return None
    try:
        return backend.ui_hash()
    except Exception:
        return None


def settle_after(
    backend: _SupportsUiHash,
    baseline: str | None,
    timeout_key: str,
) -> None:
    """No-op when ``baseline`` is None. Otherwise wait for the UI to settle
    using the per-tool timeout map."""
    if baseline is None:
        return
    timeout_ms = SETTLE_TIMEOUTS_MS.get(timeout_key, 2500)
    wait_for_ui_idle(backend, baseline=baseline, timeout_ms=timeout_ms)


def wait_for_ui_idle(
    backend: _SupportsUiHash,
    *,
    baseline: str,
    timeout_ms: int = 2500,
    stable_ms: int | None = None,
    poll_ms: int | None = None,
) -> str:
    """Poll ``backend.ui_hash()`` until it differs from ``baseline`` AND
    stays constant for ``stable_ms``. Returns the final hash. Returns the
    last observed hash on timeout (never raises)."""
    timeout_ms = _env_int("LLM_SMARTPHONE_SETTLE_TIMEOUT_MS", timeout_ms)
    effective_stable = _env_int(
        "LLM_SMARTPHONE_SETTLE_STABLE_MS",
        _DEFAULT_STABLE_MS if stable_ms is None else stable_ms,
    )
    effective_poll = _DEFAULT_POLL_MS if poll_ms is None else poll_ms

    deadline = time.monotonic() + timeout_ms / 1000.0
    last_hash = baseline
    changed_at: float | None = None

    while True:
        try:
            current = backend.ui_hash()
        except Exception:
            return last_hash
        now = time.monotonic()
        if current != baseline:
            if current != last_hash:
                # Hash moved (first time off baseline, or shifted again
                # before stabilising) — restart the stable-window timer.
                changed_at = now
            elif changed_at is not None:
                elapsed_ms = (now - changed_at) * 1000.0
                if elapsed_ms >= effective_stable:
                    return current
        last_hash = current
        if now >= deadline:
            return last_hash
        time.sleep(effective_poll / 1000.0)
