"""Study session lifecycle and RunControl ownership.

This module implements the single-active-session contract: when a study trial
is running, it owns the agent's run slot (``RunControl``) and intercepts
pause/stop/touch/confirm signals. On every terminal path (success, failure,
cancellation) it performs hard cleanup — releasing the run slot, cancelling
any pending verification, and logging the final state.

The session is designed to work alongside the existing agent loop without
modifying its core code; the executor passes a ``RunControl`` into the
session, and the session delegates pause/stop/confirm signals to it.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from caddie.study.executor import TrialExecutor
    from caddie.study.logger import StudyLogger
    from caddie.study.oversight import OversightDecision, OversightManager
    from caddie.study.verification import VerificationBackendProtocol
    from caddie.agent.run_control import RunControl, RunState

logger = logging.getLogger(__name__)

# Runtime import for return types (avoids circular import at module level)
from caddie.study.oversight import OversightDecision  # noqa: E402


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------


class SessionState(str, Enum):
    """Lifecycle states of a study session."""

    IDLE = "idle"
    RUNNING = "running"
    PAUSED = "paused"
    CANCELLING = "cancelling"
    COMPLETED = "completed"
    FAILED = "failed"


class SessionEvent(str, Enum):
    """State transitions emitted by the session manager."""

    STARTED = auto()
    PAUSED = auto()
    RESUMED = auto()
    CANCELLED = auto()
    COMPLETED = auto()
    FAILED = auto()


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class SessionMetrics:
    """Immutable snapshot of session metrics taken at a point in time."""

    elapsed_ms: int
    steps_executed: int
    pending_oversight_steps: int = 0
    current_step_index: int = 0
    is_paused: bool = False
    verification_pending: bool = False

    @classmethod
    def snapshot(cls, state: StudySession) -> "SessionMetrics":
        """Create an atomic snapshot from the current session state.

        Acquires the session lock once and reads all fields under it
        to avoid internally inconsistent snapshots.
        """
        with state._lock:
            return cls(
                elapsed_ms=state._compute_elapsed(),
                steps_executed=state._steps_executed,
                pending_oversight_steps=state._pending_oversight_steps,
                current_step_index=state._current_step_index,
                is_paused=state._state is SessionState.PAUSED,
                verification_pending=state._verification_pending,
            )


# ---------------------------------------------------------------------------
# Study session manager
# ---------------------------------------------------------------------------


class StudySession:
    """Manages the lifecycle of a single study session.

    The session owns a ``RunControl`` reference and tracks all state
    transitions (start, pause, resume, cancel, complete). It ensures
    hard cleanup on every terminal path.

    Args:
        logger: The study logger for event recording.
        run_control: The agent's ``RunControl`` to delegate to.
        oversight_manager: The oversight manager for C1/C2/C3 gates.
    """

    def __init__(
        self,
        logger: StudyLogger,
        run_control: RunControl,
        oversight_manager: OversightManager,
        screen_off_manager: Optional["ScreenOffManager"] = None,
    ) -> None:
        self._logger = logger
        self._run_control = run_control
        self._oversight_manager = oversight_manager
        self._screen_off_manager = screen_off_manager

        self._state = SessionState.IDLE
        self._start_time: float = 0.0
        self._paused_at: float = 0.0
        self._pause_duration_ms: int = 0
        self._steps_executed: int = 0
        self._pending_oversight_steps: int = 0
        self._current_step_index: int = -1
        self._verification_pending: bool = False
        self._cancelled: bool = False
        self._completed_trials: int = 0
        self._cleanup_called: bool = False

        # Thread safety for state mutations (RLock for re-entrant calls)
        self._lock = threading.RLock()

    # ------------------------------------------------------------------
    # Properties (thread-safe)
    # ------------------------------------------------------------------

    @property
    def state(self) -> SessionState:
        """Current session state (read-only snapshot)."""
        with self._lock:
            return self._state

    @property
    def is_running(self) -> bool:
        return self.state in (SessionState.RUNNING, SessionState.PAUSED)

    @property
    def is_terminal(self) -> bool:
        return self.state in (
            SessionState.COMPLETED,
            SessionState.FAILED,
        )

    @property
    def elapsed_ms(self) -> int:
        """Milliseconds since session start (or pause start if paused)."""
        with self._lock:
            return self._compute_elapsed()

    @property
    def steps_executed(self) -> int:
        """Thread-safe: read under lock."""
        with self._lock:
            return self._steps_executed

    @property
    def pending_oversight_steps(self) -> int:
        """Thread-safe: read under lock."""
        with self._lock:
            return self._pending_oversight_steps

    @property
    def current_step_index(self) -> int:
        """Thread-safe: read under lock."""
        with self._lock:
            return self._current_step_index

    @property
    def verification_pending(self) -> bool:
        """Thread-safe: read under lock."""
        with self._lock:
            return self._verification_pending

    # ------------------------------------------------------------------
    # Lifecycle transitions
    # ------------------------------------------------------------------

    def start(self) -> None:
        """Start the session. Transitions IDLE -> RUNNING.

        Raises:
            RuntimeError: if the session is already running or terminal.
        """
        with self._lock:
            if self._state is SessionState.IDLE:
                self._state = SessionState.RUNNING
                self._start_time = time.monotonic()
                self._logger.session_started()
                logger.info(
                    "StudySession started: %s/%s",
                    self._logger.participant_id,
                    self._logger.session_id,
                )
            else:
                raise RuntimeError(
                    f"Cannot start session in state {self._state.value}"
                )

    def pause(self) -> None:
        """Pause the session. Transitions RUNNING -> PAUSED.

        Delegates to the RunControl so the agent loop also pauses.
        """
        with self._lock:
            if self._state is SessionState.RUNNING:
                self._state = SessionState.PAUSED
                self._paused_at = time.monotonic()
                self._run_control.request_pause()
                self._logger.session_paused()
                logger.debug("StudySession paused")

    def resume(self) -> None:
        """Resume the session. Transitions PAUSED -> RUNNING.

        Delegates to the RunControl so the agent loop also resumes.
        Accumulates pause duration (does not overwrite).
        """
        with self._lock:
            if self._state is SessionState.PAUSED:
                pause_ms = int(
                    (time.monotonic() - self._paused_at) * 1000
                )
                self._pause_duration_ms += pause_ms
                self._state = SessionState.RUNNING
                self._run_control.request_resume()
                self._logger.session_resumed()
                logger.debug("StudySession resumed")

    def cancel(self) -> None:
        """Cancel the session. Transitions RUNNING|PAUSED -> CANCELLING.

        Delegates to the RunControl so the agent loop stops.
        Cancels the oversight manager.
        Hard cleanup (_cleanup) is triggered by the executor on the terminal path,
        not by cancel() itself — allowing the session to be cancelled while
        still RUNNING/PAUSED.

        Returns:
            True if cancellation was applied (state was RUNNING or PAUSED).
        """
        with self._lock:
            if self._state in (SessionState.RUNNING, SessionState.PAUSED):
                self._state = SessionState.CANCELLING
                self._cancelled = True
                applied = True
            else:
                applied = False

        if applied:
            # Cancel oversight manager outside lock
            try:
                self._oversight_manager.cancel()
            except Exception:
                logger.warning("OversightManager.cancel() failed", exc_info=True)

            # Request stop outside lock to avoid deadlocks
            try:
                self._run_control.request_stop()
            except Exception:
                logger.warning("RunControl.request_stop() failed", exc_info=True)

            self._logger.session_cancelled()
            logger.info("StudySession cancelled -> CANCELLING")

        return applied

    # ------------------------------------------------------------------
    # Progress tracking
    # ------------------------------------------------------------------

    def mark_step_executed(self, step_index: int) -> None:
        """Record that a step has been executed."""
        with self._lock:
            self._steps_executed += 1
            self._current_step_index = step_index

    def update_oversight_pending(self, count: int) -> None:
        """Update the number of pending oversight steps."""
        with self._lock:
            self._pending_oversight_steps = count

    def mark_verification_pending(self) -> None:
        """Mark that verification is pending (no-op if already set)."""
        with self._lock:
            self._verification_pending = True

    # ------------------------------------------------------------------
    # Terminal transitions + hard cleanup
    # ------------------------------------------------------------------

    def complete(self) -> None:
        """Complete the session (terminal state). Hard cleanup.

        Idempotent — safe to call multiple times.
        """
        self._cleanup("completed")

    def fail(self, reason: str = "unknown") -> None:
        """Fail the session (terminal state). Hard cleanup.

        Idempotent — safe to call multiple times.

        Args:
            reason: The reason for failure.
        """
        self._cleanup("failed")
        # Log outside the lock to avoid nested lock acquisition
        self._logger.session_failed(reason=reason)
        logger.error("StudySession failed: %s", reason)

    def mark_trial_complete(self, outcome: str) -> None:
        """Mark that a trial completed with the given outcome.

        Accepts either a ``TrialOutcome`` enum value or its string value.

        Args:
            outcome: Either 'success', 'verification_failed', 'aborted',
                or 'technical_failure'.
        """
        # Accept TrialOutcome enum or string value
        outcome_str = outcome.value if hasattr(outcome, 'value') else outcome
        with self._lock:
            if outcome_str in ("success", "verification_failed"):
                self._completed_trials += 1
            # Update pending oversight steps (cleared after trial)
            self._pending_oversight_steps = 0
            self._current_step_index = -1

    def _cleanup(self, outcome: str) -> None:
        """Hard cleanup: release resources, log final state.

        Idempotent — subsequent calls return immediately.
        All state mutations happen under the lock; external callbacks
        (session_ended) execute outside the lock to avoid deadlocks.

        Args:
            outcome: Either 'completed' or 'failed'.
        """
        with self._lock:
            if self._cleanup_called:
                return
            self._cleanup_called = True

            # Compute elapsed before releasing lock
            elapsed_ms = self._compute_elapsed()

            # Ensure the run slot is released (idempotent in RunControl)
            try:
                self._run_control.request_stop()
            except Exception:
                logger.warning("RunControl cleanup failed", exc_info=True)

            # Cancel oversight manager outside lock
            try:
                self._oversight_manager.cancel()
            except Exception:
                logger.warning("OversightManager.cancel() failed", exc_info=True)

            # Ensure verification is cleaned up (no pending state)
            self._verification_pending = False
            self._state = (
                SessionState.COMPLETED
                if outcome == "completed"
                else SessionState.FAILED
            )

        # Log outside the lock to avoid nested lock acquisition in session_ended
        self._logger.session_ended(
            outcome=outcome,
            elapsed_ms=elapsed_ms,
        )
        logger.info("StudySession cleaned up: %s", outcome)

    def _compute_elapsed(self) -> int:
        """Compute elapsed ms (must be called while holding _lock)."""
        if self._paused_at > 0 and self._state is SessionState.PAUSED:
            return int(
                (time.monotonic() - self._paused_at) * 1000
                + self._pause_duration_ms
            )
        return int((time.monotonic() - self._start_time) * 1000)

    # ------------------------------------------------------------------
    # Confirm/Touch handling
    # ------------------------------------------------------------------

    def resolve_confirmation(self, approved: bool) -> None:
        """Resolve a pending Swipe-to-Confirm via the RunControl.

        Args:
            approved: Whether the user approved or declined the action.
        """
        self._run_control.resolve_confirmation(approved)

    def wait_for_confirmation(self, timeout: float = 30.0) -> bool:
        """Block until a confirmation is resolved.

        Args:
            timeout: Maximum seconds to wait.

        Returns:
            True if approved, False otherwise.
        """
        return self._run_control.await_confirmation(timeout)

    def wait_while_paused(self) -> None:
        """Block the calling thread until the session resumes or is stopped.

        Delegates to the RunControl's ``wait_while_paused``.
        """
        self._run_control.wait_while_paused()

    def request_stop(self) -> None:
        """Request stop from the outside (e.g., executor abort).

        Transitions to CANCELLING if currently RUNNING or PAUSED.
        Idempotent — safe to call multiple times.
        """
        with self._lock:
            if self._state in (SessionState.RUNNING, SessionState.PAUSED):
                self._state = SessionState.CANCELLING
                self._cancelled = True
                self._run_control.request_stop()
                self._logger.session_cancelled()
            elif self._state in (SessionState.COMPLETED, SessionState.FAILED):
                pass  # already terminal, ignore
            elif self._state is SessionState.CANCELLING:
                pass  # already cancelling
            else:
                # IDLE state — just log cancellation
                self._logger.session_cancelled()

    def request_pause(self, *, intervention: bool = False) -> Optional[OversightDecision]:
        """Request pause from the outside (e.g., touch takeover).

        Non-blocking when the session is RUNNING — returns None immediately.
        Blocks only when an external caller has already paused the session
        (state is PAUSED), waiting for resume or cancellation.

        Args:
            intervention: Whether this pause was triggered by user intervention.

        Returns:
            An ``OversightDecision`` if the session was cancelled during pause,
            otherwise ``None``.
        """
        with self._lock:
            if self._state is SessionState.RUNNING:
                self.pause()

        # Only block if an external pause is pending (state is PAUSED)
        # Otherwise return immediately so the executor can continue.
        with self._lock:
            if self._state is SessionState.PAUSED:
                # External pause is pending — block until resume or cancel
                while True:
                    with self._lock:
                        if self._state in (SessionState.RUNNING, SessionState.PAUSED):
                            return None
                        if self._state is SessionState.CANCELLING:
                            return OversightDecision(cancelled=True)
                        return None
                    time.sleep(0.1)
            # RUNNING or terminal — return immediately
            return None

    # ------------------------------------------------------------------
    # Screen-off micro-trials
    # ------------------------------------------------------------------

    def run_screen_off_block(self, configs) -> list:
        """Run a block of screen-off micro-trials.

        Args:
            configs: List of ScreenOffConfig objects.

        Returns:
            List of ScreenOffResult objects.
        """
        if self._screen_off_manager is None:
            logger.warning("ScreenOffManager not configured — skipping block")
            return []
        from caddie.study.screen_off import run_screen_off_block as _block
        return _block(self._screen_off_manager, configs)

    # ------------------------------------------------------------------
    # Metrics
    # ------------------------------------------------------------------

    def get_metrics(self) -> SessionMetrics:
        """Return a frozen snapshot of current metrics."""
        return SessionMetrics.snapshot(self)

    # ------------------------------------------------------------------
    # Representation
    # ------------------------------------------------------------------

    def __repr__(self) -> str:
        return (
            f"StudySession(state={self._state.value!r}, "
            f"steps={self._steps_executed}, "
            f"paused={self._paused_at > 0})"
        )


# ---------------------------------------------------------------------------
# Session manager (singleton pattern for the study system)
# ---------------------------------------------------------------------------


_session_manager_instance: Optional["SessionManager"] = None


class SessionManager:
    """Manages the single active study session.

    This is a singleton that ensures only one study session exists
    at a time. It handles session creation, state transitions, and
    cleanup.

    Usage:
        mgr = SessionManager.instance()
        session = mgr.create(...)
        session.start()
        # ... run trial ...
        session.complete()
    """

    @classmethod
    def instance(cls) -> "SessionManager":
        """Return the singleton instance."""
        global _session_manager_instance
        if _session_manager_instance is None:
            _session_manager_instance = cls()
        return _session_manager_instance

    def __init__(self) -> None:
        self._session: Optional[StudySession] = None
        self._lock = threading.Lock()

    def create(
        self,
        logger: StudyLogger,
        run_control: RunControl,
        oversight_manager: OversightManager,
        screen_off_manager: Optional[Any] = None,
    ) -> StudySession:
        """Create a new study session.

        Args:
            logger: The study logger.
            run_control: The agent's RunControl.
            oversight_manager: The oversight manager.
            screen_off_manager: Optional ScreenOffManager for micro-trials.

        Returns:
            The newly created StudySession.

        Raises:
            RuntimeError: if a session is already active.
        """
        with self._lock:
            if self._session is not None:
                raise RuntimeError(
                    "A study session is already active"
                )
            self._session = StudySession(
                logger=logger,
                run_control=run_control,
                oversight_manager=oversight_manager,
                screen_off_manager=screen_off_manager,
            )
            return self._session

    @property
    def session(self) -> Optional[StudySession]:
        """Return the active session, or None."""
        return self._session

    def clear(self) -> None:
        """Clear the active session reference."""
        with self._lock:
            self._session = None
