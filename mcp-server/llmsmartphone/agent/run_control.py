"""Thread-sicherer Laufzeit-Zustand fuer Pause / Stop / Resume eines Runs.

Der Agent-Loop laeuft in einem Daemon-Thread; Pause/Stop/Resume-Signale kommen
von aussen (HTTP ``/control``, spaeter ausgeloest durch menschlichen Touch am
Geraet). ``RunControl`` ist die Bruecke: der Loop fragt es an seinem
``_pause_point`` ab, die Signal-Seite ruft ``request_*``.
"""

from __future__ import annotations

import threading
import time
from enum import Enum


class RunState(str, Enum):
    RUNNING = "running"
    PAUSED = "paused"
    STOPPED = "stopped"


class RunControl:
    """Pause/Stop/Resume-Zustand eines einzelnen Agent-Runs."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._state = RunState.RUNNING
        # Gesetzt = darf laufen. Geleert = pausiert (Loop blockiert darauf).
        self._resume_event = threading.Event()
        self._resume_event.set()
        self._paused_at: float | None = None
        # Einmal-Flag: beim naechsten Resume soll der Loop neu wahrnehmen,
        # weil der Nutzer waehrend der Pause am Geraet eingegriffen hat.
        self._intervened = False
        # Optionaler Nutzer-Text ("nimm das andere Restaurant"), den der Loop
        # beim naechsten _pause_point in die Conversation einspeist.
        self._pending_correction: str | None = None

    # ---- Signal-Seite (HTTP /control, Touch-Erkennung) ----

    def request_pause(self, *, intervention: bool = False) -> None:
        """Pausiert den Run. ``intervention=True`` markiert einen menschlichen
        Eingriff -> der Loop nimmt beim Resume den Screen neu wahr."""
        with self._lock:
            if self._state is RunState.STOPPED:
                return
            self._state = RunState.PAUSED
            self._paused_at = time.monotonic()
            if intervention:
                self._intervened = True
            self._resume_event.clear()

    def set_correction(self, text: str) -> None:
        """Hinterlegt eine gesprochene Nutzer-Korrektur. Markiert zugleich den
        Eingriff, damit der Loop beim naechsten _pause_point neu wahrnimmt und
        die Korrektur sieht."""
        with self._lock:
            if self._state is RunState.STOPPED:
                return
            self._pending_correction = text.strip() or None
            self._intervened = True

    def request_resume(self) -> None:
        with self._lock:
            if self._state is RunState.STOPPED:
                return
            self._state = RunState.RUNNING
            self._paused_at = None
            self._resume_event.set()

    def request_stop(self) -> None:
        """Bricht den Run terminal ab. Weckt einen pausierten Loop, damit er
        den STOPPED-Zustand sieht."""
        with self._lock:
            self._state = RunState.STOPPED
            self._resume_event.set()

    # ---- Loop-Seite ----

    @property
    def state(self) -> RunState:
        with self._lock:
            return self._state

    @property
    def stop_requested(self) -> bool:
        return self.state is RunState.STOPPED

    @property
    def is_paused(self) -> bool:
        return self.state is RunState.PAUSED

    def consume_intervention(self) -> bool:
        """True (genau einmal), wenn seit der letzten Abfrage ein menschlicher
        Eingriff gemeldet wurde — Signal fuer die Neu-Wahrnehmung."""
        with self._lock:
            was = self._intervened
            self._intervened = False
            return was

    def take_correction(self) -> str | None:
        """Gibt eine hinterlegte Nutzer-Korrektur zurueck und leert sie."""
        with self._lock:
            text = self._pending_correction
            self._pending_correction = None
            return text

    def wait_while_paused(self) -> RunState:
        """Blockiert den Loop-Thread, solange pausiert. Gibt den Zustand
        zurueck, mit dem es weitergeht (RUNNING oder STOPPED)."""
        self._resume_event.wait()
        return self.state
