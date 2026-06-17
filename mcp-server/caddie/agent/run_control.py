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
        # Swipe-to-Confirm: der Loop blockiert vor einer kritischen Aktion auf
        # diesem Event, bis confirm/decline kommt. Gesetzt = aufgeloest.
        self._confirm_event = threading.Event()
        self._confirm_approved: bool = False
        # smartphone_ask_user: the loop blocks here until the user answers.
        self._answer_event = threading.Event()
        self._pending_answer: str | None = None

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
            # An intervention during a pending Swipe-to-Confirm cancels it
            # (treated as decline) so a risky action never auto-runs while the
            # user is taking over. Also wake a pending question wait.
            self._confirm_event.set()
            self._answer_event.set()

    def set_correction(self, text: str) -> None:
        """Hinterlegt eine gesprochene Nutzer-Korrektur. Markiert zugleich den
        Eingriff, damit der Loop beim naechsten _pause_point neu wahrnimmt und
        die Korrektur sieht."""
        with self._lock:
            if self._state is RunState.STOPPED:
                return
            self._pending_correction = text.strip() or None
            self._intervened = True
            # Wake a pending confirm wait so a voice correction is not blocked
            # behind await_confirmation (treated as decline). Same for a
            # pending question wait (await_answer).
            self._confirm_event.set()
            self._answer_event.set()

    def request_resume(self) -> None:
        with self._lock:
            if self._state is RunState.STOPPED:
                return
            self._state = RunState.RUNNING
            self._paused_at = None
            self._resume_event.set()

    def request_stop(self) -> None:
        """Bricht den Run terminal ab. Weckt einen pausierten Loop und einen
        auf eine Bestaetigung wartenden Loop, damit beide STOPPED sehen."""
        with self._lock:
            self._state = RunState.STOPPED
            self._resume_event.set()
            self._confirm_event.set()
            self._answer_event.set()

    def resolve_confirmation(self, approved: bool) -> None:
        """Antwort auf eine Swipe-to-Confirm-Abfrage (confirm/decline)."""
        with self._lock:
            self._confirm_approved = approved
            self._confirm_event.set()

    # ---- Loop-Seite ----

    def await_confirmation(self, timeout: float) -> bool:
        """Blockiert den Loop, bis confirm/decline kommt. Timeout oder Stop
        gelten als Ablehnung (sichere Default)."""
        self._confirm_event.clear()
        with self._lock:
            self._confirm_approved = False
        got = self._confirm_event.wait(timeout)
        if not got or self.stop_requested:
            return False
        with self._lock:
            return self._confirm_approved

    def provide_answer(self, text: str) -> None:
        """User's answer to a smartphone_ask_user question (signal side)."""
        with self._lock:
            self._pending_answer = text
        self._answer_event.set()

    def await_answer(self, timeout: float) -> str | None:
        """Block the loop until the user answers. None on timeout / stop."""
        self._answer_event.clear()
        with self._lock:
            self._pending_answer = None
        got = self._answer_event.wait(timeout)
        if not got or self.stop_requested:
            return None
        with self._lock:
            return self._pending_answer

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

    @property
    def has_intervention(self) -> bool:
        """True solange ein Eingriff/Korrektur ansteht (nicht-konsumierend)."""
        with self._lock:
            return self._intervened

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
