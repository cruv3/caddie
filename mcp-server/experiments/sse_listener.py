"""SSE-Subscriber für den EVENT_BUS-Stream auf :8787/events.

Hält im Hintergrund eine Streaming-Verbindung offen, parst SSE-Frames
und sammelt sie als Liste. Pro Trial werden Events per Zeitfenster geslict.
"""

from __future__ import annotations

import json
import threading
import time
import urllib.error
import urllib.request
from typing import Any


class SseListener:
    def __init__(self, sse_url: str) -> None:
        self.url = sse_url
        self._events: list[dict[str, Any]] = []
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._recording_start: float | None = None

    # ---- Lifecycle ----

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)

    # ---- Per-Trial-API ----

    def start_recording(self, trial_id: str) -> None:
        # Marker für „ab hier sind die Events relevant"
        with self._lock:
            self._recording_start = time.monotonic()

    def stop_recording(self, trial_id: str) -> list[dict[str, Any]]:
        with self._lock:
            cutoff = self._recording_start or 0.0
            events = [e for e in self._events if e.get("_received_at", 0) >= cutoff]
            self._recording_start = None
        return events

    def count_tool_calls(self) -> int:
        cutoff = self._recording_start or 0.0
        with self._lock:
            return sum(
                1 for e in self._events
                if e.get("_received_at", 0) >= cutoff
                and e.get("type") == "tool_call_started"
            )

    def wait_for_task_finished(
        self, trial_id: str, timeout: float
    ) -> dict[str, Any] | None:
        deadline = time.monotonic() + timeout
        cutoff = self._recording_start or 0.0
        while time.monotonic() < deadline:
            with self._lock:
                for e in self._events:
                    if (
                        e.get("_received_at", 0) >= cutoff
                        and e.get("type") == "task_finished"
                    ):
                        return e
            time.sleep(0.2)
        return None

    # ---- Internal ----

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                req = urllib.request.Request(
                    self.url, headers={"Accept": "text/event-stream"}
                )
                with urllib.request.urlopen(req, timeout=10) as resp:
                    buffer = ""
                    while not self._stop.is_set():
                        chunk = resp.readline()
                        if not chunk:
                            break
                        line = chunk.decode("utf-8", errors="replace").rstrip("\n")
                        if line == "":
                            # Frame-Ende
                            if buffer:
                                self._ingest(buffer)
                                buffer = ""
                        elif line.startswith("data: "):
                            buffer = line[len("data: ") :]
            except (urllib.error.URLError, ConnectionError, TimeoutError):
                # Reconnect-Loop
                time.sleep(1)
            except Exception as exc:  # pragma: no cover
                print(f"[sse_listener] error: {exc}")
                time.sleep(2)

    def _ingest(self, data_line: str) -> None:
        try:
            payload = json.loads(data_line)
        except json.JSONDecodeError:
            return
        if isinstance(payload, dict) and payload.get("type") == "ready":
            return  # initial handshake-Marker
        payload["_received_at"] = time.monotonic()
        with self._lock:
            self._events.append(payload)
