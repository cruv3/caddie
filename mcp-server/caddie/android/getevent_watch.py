"""PC-side human-takeover detector via ``adb shell getevent``.

The agent injects taps with ``adb input`` (framework level, InputManager),
which do NOT appear at the kernel input node ``/dev/input``. A real finger DOES.
So any getevent activity on the touchscreen node is a human takeover -> pause.

Verified on a Pixel (event4 "fts_ts", 2026-06-18): an injected ``input tap``
produced 0 getevent lines; a real finger produced a full BTN_TOUCH/ABS_MT_*
stream (see vault decisions 2026-06-18).

This lives PC-side because only the adb ``shell`` user can read ``/dev/input``
(a normal app can't without root). It is paired with the ADB backend and
reuses the same adb channel that drives the taps.
"""
from __future__ import annotations

import os
import subprocess
import threading
import time
from typing import Callable

ENV_ADB = "ANDROID_ADB"


def _adb_path() -> str:
    return os.environ.get(ENV_ADB) or "adb"


def detect_touch_device(adb_path: str) -> str | None:
    """Return the /dev/input node of the touchscreen (the device that reports
    ABS_MT_POSITION_X), or None if it can't be found."""
    try:
        out = subprocess.run(
            [adb_path, "shell", "getevent", "-lp"],
            capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=15,
        ).stdout or ""
    except Exception:
        return None
    current: str | None = None
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("add device"):
            # "add device N: /dev/input/eventX"
            current = s.split(":", 1)[1].strip() if ":" in s else None
        elif "ABS_MT_POSITION_X" in s and current:
            return current
    return None


class GeteventWatcher:
    """Streams getevent on the touchscreen node. Calls ``on_touch`` on the first
    contact of a touch sequence and ``on_quiet`` after ``quiet_s`` seconds
    without any further touch. ``on_touch`` returns True if it actually paused
    (so a touch while no run is active is a cheap no-op)."""

    def __init__(
        self,
        on_touch: Callable[[], bool],
        on_quiet: Callable[[], None],
        quiet_s: float = 1.5,
        log: Callable[[str], None] | None = None,
    ) -> None:
        self._on_touch = on_touch
        self._on_quiet = on_quiet
        self._quiet_s = quiet_s
        self._log = log or (lambda _m: None)
        self._proc: subprocess.Popen | None = None
        self._running = False
        self._paused_by_touch = False
        self._last_touch = 0.0
        self._lock = threading.Lock()
        self._device: str | None = None

    def start(self) -> bool:
        adb = _adb_path()
        self._device = detect_touch_device(adb)
        if not self._device:
            self._log("getevent: no touchscreen node found -> human-touch detection OFF")
            return False
        try:
            self._proc = subprocess.Popen(
                [adb, "shell", "getevent", self._device],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                text=True, encoding="utf-8", errors="replace", bufsize=1,
            )
        except Exception as exc:  # pragma: no cover - defensive
            self._log(f"getevent: failed to start ({exc}) -> detection OFF")
            return False
        self._running = True
        threading.Thread(target=self._read_loop, daemon=True).start()
        threading.Thread(target=self._monitor_loop, daemon=True).start()
        self._log(f"getevent: watching {self._device} for human touches")
        return True

    def _read_loop(self) -> None:
        proc = self._proc
        if proc is None or proc.stdout is None:
            return
        for line in proc.stdout:
            if not self._running:
                break
            if not line.strip():
                continue
            now = time.monotonic()
            with self._lock:
                self._last_touch = now
                first = not self._paused_by_touch
            if not first:
                continue
            try:
                paused = bool(self._on_touch())
            except Exception as exc:  # pragma: no cover - defensive
                self._log(f"getevent on_touch error: {exc}")
                paused = False
            if paused:
                with self._lock:
                    self._paused_by_touch = True
                self._log(f"getevent: human touch -> paused (t={now:.3f})")

    def _monitor_loop(self) -> None:
        while self._running:
            time.sleep(0.15)
            with self._lock:
                paused = self._paused_by_touch
                quiet = time.monotonic() - self._last_touch
            if paused and quiet >= self._quiet_s:
                with self._lock:
                    self._paused_by_touch = False
                try:
                    self._on_quiet()
                except Exception as exc:  # pragma: no cover - defensive
                    self._log(f"getevent on_quiet error: {exc}")
                self._log(f"getevent: touch quiet {quiet:.1f}s -> resumed")

    def stop(self) -> None:
        self._running = False
        if self._proc is not None:
            try:
                self._proc.terminate()
            except Exception:
                pass
            self._proc = None
