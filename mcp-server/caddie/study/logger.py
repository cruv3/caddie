"""Append-only JSONL event logging and trial summaries for the study system.

Every event carries a shared metadata envelope (ISO wall-clock time,
monotonic elapsed, study version, participant, session, block, task,
condition, trial identifiers).  Events are written as one JSON line per
call to an append-only file.  A final trial summary is generated from
the raw event stream and written as a separate JSON file.

Directory layout:
    mcp-server/study-data/<study_version>/<participant_id>/
        events.jsonl          ← append-only event log
        screenshots/          ← screenshot images captured during the trial
        summary.json          ← aggregated trial summary

The logger is thread-safe and may be shared across the executor,
oversight, and screen-off modules.
"""

from __future__ import annotations

import json
import os
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

# ---------------------------------------------------------------------------
# Public constants
# ---------------------------------------------------------------------------

STUDY_VERSION: str = "v1.0.0"
STUDY_DATA_ROOT: Path = Path(__file__).resolve().parent.parent.parent / "study-data"

# ---------------------------------------------------------------------------
# Event type enum
# ---------------------------------------------------------------------------


class EventType:
    """Immutable string-enum for study log event types.

    Using a plain class instead of ``enum.Enum`` keeps the value JSON-serialisable
    without extra encoding.  Every attribute is a ``str``.
    """

    # Session lifecycle
    SESSION_STARTED = "session_started"
    SESSION_PAUSED = "session_paused"
    SESSION_RESUMED = "session_resumed"
    SESSION_CANCELLED = "session_cancelled"
    SESSION_FAILED = "session_failed"
    SESSION_ENDED = "session_ended"
    TRIAL_START = "trial_start"
    STEP_START = "step_start"
    STEP_FINISH = "step_finish"
    TRIAL_COMPLETE = "trial_complete"

    # Confirmation
    CONFIRMATION_SHOWN = "confirmation_shown"
    CONFIRMATION_RESOLVED = "confirmation_resolved"

    # Interventions
    PAUSE = "pause"
    RESUME = "resume"
    STOP = "stop"
    CORRECTION = "correction"
    TOUCH_INTERVENTION = "touch_intervention"

    # Controlled error
    ERROR_INJECTED = "error_injected"

    # Verification
    VERIFICATION_START = "verification_start"
    VERIFICATION_COMPLETE = "verification_complete"
    VERIFICATION_RESULT = "verification_result"

    # Screen-off
    SCREEN_OFF_INITIATION = "screen_off_initiation"

    # Failures / meta
    TECHNICAL_FAILURE = "technical_failure"
    REPEAT = "repeat"
    EXCLUSION = "exclusion"

    # Artifacts
    SCREENSHOT_CAPTURED = "screenshot_captured"

    # Experimenter actions
    EXPERIMENTER_ACTION = "experimenter_action"

    # All event types for validation
    ALL = frozenset({
        SESSION_STARTED, SESSION_PAUSED, SESSION_RESUMED,
        SESSION_CANCELLED, SESSION_FAILED, SESSION_ENDED,
        TRIAL_START, STEP_START, STEP_FINISH, TRIAL_COMPLETE,
        CONFIRMATION_SHOWN, CONFIRMATION_RESOLVED,
        PAUSE, RESUME, STOP, CORRECTION, TOUCH_INTERVENTION,
        ERROR_INJECTED,
        VERIFICATION_START, VERIFICATION_COMPLETE, VERIFICATION_RESULT,
        SCREEN_OFF_INITIATION,
        TECHNICAL_FAILURE, REPEAT, EXCLUSION,
        SCREENSHOT_CAPTURED, EXPERIMENTER_ACTION,
    })


# ---------------------------------------------------------------------------
# StudyEvent dataclass
# ---------------------------------------------------------------------------


class StudyEvent:
    """Immutable log event with shared metadata envelope.

    Every event carries:
    - ``timestamp`` — ISO 8601 wall-clock time (UTC)
    - ``elapsed_ms`` — monotonic elapsed milliseconds since session start
    - ``study_version``, ``participant_id``, ``session_id`` — always present
    - ``block``, ``task_id``, ``condition``, ``trial_id``, ``variant`` — optional
      context identifiers

    Additional fields are optional and only present on relevant event types.
    """

    __slots__ = (
        "event_type", "timestamp", "elapsed_ms",
        "study_version", "participant_id", "session_id",
        "block", "task_id", "condition", "trial_id", "variant",
        "step_id", "narration", "action", "details",
    )

    def __init__(
        self,
        event_type: str,
        timestamp: str,
        elapsed_ms: float,
        study_version: str,
        participant_id: str,
        session_id: str,
        *,
        block: Optional[str] = None,
        task_id: Optional[str] = None,
        condition: Optional[str] = None,
        trial_id: Optional[str] = None,
        variant: Optional[str] = None,
        step_id: Optional[str] = None,
        narration: Optional[str] = None,
        action: Optional[str] = None,
        details: Optional[dict[str, Any]] = None,
    ):
        if event_type not in EventType.ALL:
            raise ValueError(f"Unknown event type: {event_type}")
        self.event_type = event_type
        self.timestamp = timestamp
        self.elapsed_ms = elapsed_ms
        self.study_version = study_version
        self.participant_id = participant_id
        self.session_id = session_id
        self.block = block
        self.task_id = task_id
        self.condition = condition
        self.trial_id = trial_id
        self.variant = variant
        self.step_id = step_id
        self.narration = narration
        self.action = action
        self.details = details if details is not None else {}

    def to_dict(self) -> dict[str, Any]:
        return {
            "event_type": self.event_type,
            "timestamp": self.timestamp,
            "elapsed_ms": self.elapsed_ms,
            "study_version": self.study_version,
            "participant_id": self.participant_id,
            "session_id": self.session_id,
            "block": self.block,
            "task_id": self.task_id,
            "condition": self.condition,
            "trial_id": self.trial_id,
            "variant": self.variant,
            "step_id": self.step_id,
            "narration": self.narration,
            "action": self.action,
            "details": self.details,
        }

    def to_json_line(self) -> str:
        return json.dumps(self.to_dict(), ensure_ascii=False)


# ---------------------------------------------------------------------------
# Helper: monotonic time source
# ---------------------------------------------------------------------------


class MonotonicClock:
    """Thread-safe monotonic time provider for elapsed_ms tracking.

    The study system needs a monotonic clock that is stable across threads.
    This wrapper uses ``time.monotonic_ns`` and converts to milliseconds.
    """

    def __init__(self) -> None:
        self._start_ns: int = time.monotonic_ns()
        self._lock = threading.RLock()

    def elapsed_ms(self) -> float:
        with self._lock:
            return (time.monotonic_ns() - self._start_ns) / 1_000_000

    def reset(self) -> None:
        with self._lock:
            self._start_ns = time.monotonic_ns()


# ---------------------------------------------------------------------------
# StudyLogger
# ---------------------------------------------------------------------------


class StudyLogger:
    """Append-only JSONL logger for study trial events.

    Thread-safe.  All writes are synchronized to prevent interleaving.
    Screenshots are stored in a subdirectory alongside the JSONL file.

    Parameters
    ----------
    base_dir : Path
        Root directory for study data (default: ``STUDY_DATA_ROOT``).
    study_version : str
        Version identifier for this study run.
    participant_id : str
        Pseudonymous participant identifier (e.g. ``"P01"``).
    session_id : str
        Unique session/trial identifier.
    condition : str
        The study condition for this session (e.g. ``"c1_stepwise"``).
    """

    def __init__(
        self,
        base_dir: Path = STUDY_DATA_ROOT,
        study_version: str = STUDY_VERSION,
        participant_id: str = "",
        session_id: str = "",
        condition: str = "",
    ):
        self._base_dir = base_dir
        self._study_version = study_version
        self._participant_id = participant_id
        self._session_id = session_id
        self._condition = condition
        self._clock = MonotonicClock()
        self._lock = threading.RLock()
        self._counter_lock = threading.Lock()
        self._trial_id_counter = 0

        # Create session-scoped directory structure
        self._participant_dir = base_dir / study_version / participant_id
        self._participant_dir.mkdir(parents=True, exist_ok=True)
        self._session_dir = self._participant_dir / session_id
        self._session_dir.mkdir(parents=True, exist_ok=True)
        self._screenshots_dir = self._session_dir / "screenshots"
        self._screenshots_dir.mkdir(parents=True, exist_ok=True)

        self._events_path = self._session_dir / "events.jsonl"
        # Write empty file if it doesn't exist
        if not self._events_path.exists():
            self._events_path.write_text("", encoding="utf-8")

    # ------------------------------------------------------------------
    # Context helpers
    # ------------------------------------------------------------------

    def _event_context(self) -> dict[str, Any]:
        """Return shared context dict for all events."""
        return {
            "study_version": self._study_version,
            "participant_id": self._participant_id,
            "session_id": self._session_id,
            "condition": self._condition,
        }

    def _now_iso(self) -> str:
        return datetime.now(timezone.utc).isoformat()

    # ------------------------------------------------------------------
    # Public API — logging individual events
    # ------------------------------------------------------------------

    def log(
        self,
        event_type: str,
        *,
        block: Optional[str] = None,
        task_id: Optional[str] = None,
        trial_id: Optional[str] = None,
        variant: Optional[str] = None,
        step_id: Optional[str] = None,
        narration: Optional[str] = None,
        action: Optional[str] = None,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        """Log a single study event to the append-only JSONL file.

        Parameters
        ----------
        event_type : str
            One of the ``EventType`` constants.
        block, task_id, trial_id, variant, step_id : str, optional
            Context identifiers.
        narration : str, optional
            Current-action narration text.
        action : str, optional
            Human-readable action description.
        details : dict, optional
            Additional structured data for the event.
        """
        event = StudyEvent(
            event_type=event_type,
            timestamp=self._now_iso(),
            elapsed_ms=self._clock.elapsed_ms(),
            study_version=self._study_version,
            participant_id=self._participant_id,
            session_id=self._session_id,
            block=block,
            task_id=task_id,
            condition=self._condition,
            trial_id=trial_id,
            variant=variant,
            step_id=step_id,
            narration=narration,
            action=action,
            details=details or {},
        )
        self._write_event(event)

    def _write_event(self, event: StudyEvent) -> None:
        """Thread-safe append to the JSONL file."""
        with self._lock:
            with open(self._events_path, "a", encoding="utf-8") as f:
                f.write(event.to_json_line() + "\n")

    # ------------------------------------------------------------------
    # Trial lifecycle
    # ------------------------------------------------------------------

    def trial_start(
        self,
        task_id: str,
        *,
        block: str = "main",
        variant: str = "normal",
        trial_id: Optional[str] = None,
    ) -> str:
        """Start a new trial, return the trial ID."""
        if trial_id is None:
            with self._counter_lock:
                self._trial_id_counter += 1
                trial_id = f"trial_{self._trial_id_counter:03d}"
        self.log(
            EventType.TRIAL_START,
            block=block,
            task_id=task_id,
            trial_id=trial_id,
            variant=variant,
        )
        return trial_id

    def trial_complete(
        self,
        trial_id: str,
        outcome: str,
        *,
        task_id: str = "",
        total_steps: int = 0,
        errors_injected: int = 0,
        duration_ms: float = 0.0,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        """Mark a trial as complete with its outcome.

        Parameters
        ----------
        task_id : str
            The task that was completed. Defaults to empty (unknown).
        """
        self.log(
            EventType.TRIAL_COMPLETE,
            trial_id=trial_id,
            task_id=task_id,
            details={
                "outcome": outcome,
                "total_steps": total_steps,
                "errors_injected": errors_injected,
                "duration_ms": duration_ms,
            }
            | (details or {}),
        )

    # ------------------------------------------------------------------
    # Step events
    # ------------------------------------------------------------------

    def step_start(
        self,
        step_id: str,
        narration: str,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
    ) -> None:
        self.log(
            EventType.STEP_START,
            step_id=step_id,
            narration=narration,
            trial_id=trial_id,
            task_id=task_id,
        )

    def step_finish(
        self,
        step_id: str,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
        action: Optional[str] = None,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.STEP_FINISH,
            step_id=step_id,
            trial_id=trial_id,
            task_id=task_id,
            action=action,
            details=details or {},
        )

    # ------------------------------------------------------------------
    # Confirmation events
    # ------------------------------------------------------------------

    def confirmation_shown(
        self,
        step_id: str,
        narration: str,
        *,
        trial_id: Optional[str] = None,
    ) -> None:
        self.log(
            EventType.CONFIRMATION_SHOWN,
            step_id=step_id,
            narration=narration,
            trial_id=trial_id,
        )

    def confirmation_resolved(
        self,
        step_id: str,
        *,
        accepted: bool,
        response_latency_ms: float = 0.0,
        trial_id: Optional[str] = None,
        task_id: str = "",
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.CONFIRMATION_RESOLVED,
            step_id=step_id,
            trial_id=trial_id,
            task_id=task_id,
            details={
                "accepted": accepted,
                "response_latency_ms": response_latency_ms,
            }
            | (details or {}),
        )


    # ------------------------------------------------------------------
    # Intervention events
    # ------------------------------------------------------------------

    def intervention(
        self,
        kind: str,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
        latency_ms: float = 0.0,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        """Log an intervention event.

        Parameters
        ----------
        kind : str
            One of: ``pause``, ``resume``, ``stop``, ``correction``, ``touch_intervention``.
        """
        self.log(
            kind,
            details={
                "intervention_latency_ms": latency_ms,
            }
            | (details or {}),
        )

    # ------------------------------------------------------------------
    # Error injection
    # ------------------------------------------------------------------

    def error_injected(
        self,
        step_id: str,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
        error_variant_id: str,
        field: str,
        wrong_value: str,
        correct_value: str,
    ) -> None:
        self.log(
            EventType.ERROR_INJECTED,
            step_id=step_id,
            trial_id=trial_id,
            task_id=task_id,
            details={
                "error_variant_id": error_variant_id,
                "field": field,
                "wrong_value": wrong_value,
                "correct_value": correct_value,
            },
        )

    # ------------------------------------------------------------------
    # Verification
    # ------------------------------------------------------------------

    def verification_start(
        self,
        rule_count: int,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
    ) -> None:
        self.log(
            EventType.VERIFICATION_START,
            step_id="<verification>",
            narration=f"Starting {rule_count} verification checks",
            trial_id=trial_id,
            task_id=task_id,
        )

    def verification_complete(
        self,
        *,
        passed: int = 0,
        failed: int = 0,
        timed_out: int = 0,
        skipped: int = 0,
        trial_id: Optional[str] = None,
        task_id: str = "",
    ) -> None:
        self.log(
            EventType.VERIFICATION_COMPLETE,
            step_id="<verification>",
            narration=(
                f"Verification complete: "
                f"{passed} passed, {failed} failed, "
                f"{timed_out} timed out, {skipped} skipped"
            ),
            trial_id=trial_id,
            task_id=task_id,
            details={
                "total_rules": passed + failed + timed_out + skipped,
                "passed": passed,
                "failed": failed,
                "timed_out": timed_out,
                "skipped": skipped,
            },
        )

    def verification_result(
        self,
        rule_id: str,
        passed: bool,
        *,
        trial_id: Optional[str] = None,
        task_id: str = "",
        screenshot_path: Optional[str] = None,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.VERIFICATION_RESULT,
            trial_id=trial_id,
            task_id=task_id,
            details={
                "rule_id": rule_id,
                "passed": passed,
                "screenshot_path": screenshot_path,
            }
            | (details or {}),
        )

    # ------------------------------------------------------------------
    # Screen-off
    # ------------------------------------------------------------------

    def screen_off_initiation(
        self,
        task_id: str,
        mode: str,
        *,
        display_was_off: bool,
        wake_succeeded: bool,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.SCREEN_OFF_INITIATION,
            task_id=task_id,
            details={
                "mode": mode,
                "display_was_off": display_was_off,
                "wake_succeeded": wake_succeeded,
            }
            | (details or {}),
        )

    # ------------------------------------------------------------------
    # Failure / meta events
    # ------------------------------------------------------------------

    def technical_failure(
        self,
        *,
        error: str,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.TECHNICAL_FAILURE,
            details={"error": error} | (details or {}),
        )

    def repeat(
        self,
        trial_id: str,
        *,
        reason: str,
    ) -> None:
        self.log(
            EventType.REPEAT,
            trial_id=trial_id,
            details={"repeat_reason": reason},
        )

    def exclusion(
        self,
        *,
        reason: str,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.EXCLUSION,
            details={"exclusion_reason": reason} | (details or {}),
        )

    def experimenter_action(
        self,
        action: str,
        *,
        details: Optional[dict[str, Any]] = None,
    ) -> None:
        self.log(
            EventType.EXPERIMENTER_ACTION,
            action=action,
            details=details or {},
        )

    # ------------------------------------------------------------------
    # Session lifecycle
    # ------------------------------------------------------------------

    def session_started(self) -> None:
        """Log that a session has started."""
        self.log(EventType.SESSION_STARTED)

    def session_paused(self) -> None:
        """Log that a session has been paused."""
        self.log(EventType.SESSION_PAUSED)

    def session_resumed(self) -> None:
        """Log that a session has resumed."""
        self.log(EventType.SESSION_RESUMED)

    def session_cancelled(self) -> None:
        """Log that a session has been cancelled."""
        self.log(EventType.SESSION_CANCELLED)

    def session_failed(self, reason: str) -> None:
        """Log that a session has failed."""
        self.log(EventType.SESSION_FAILED, details={"reason": reason})

    def session_ended(self, outcome: str, elapsed_ms: int) -> None:
        """Log that a session has ended."""
        self.log(
            EventType.SESSION_ENDED,
            details={"outcome": outcome, "elapsed_ms": elapsed_ms},
        )

    # ------------------------------------------------------------------
    # Screenshot tracking
    # ------------------------------------------------------------------

    def screenshot_captured(
        self,
        label: str,
        *,
        trial_id: Optional[str] = None,
    ) -> str:
        """Record a screenshot capture and return its relative path.

        Returns a relative path like screenshots/P01_<trial>_<label>_<uuid>.png.
        The actual image must be written to self.screenshots_dir.

        Uses trial_id + UUID in the filename to guarantee uniqueness even
        under concurrent or repeated calls with the same label.
        """
        safe_pid = self._participant_id.replace(" ", "_")
        safe_tid = (trial_id or "none").replace(" ", "_")
        uuid_hex = uuid.uuid4().hex[:8]
        filename = f"{safe_pid}_{safe_tid}_{label}_{uuid_hex}.png"
        rel_path = f"screenshots/{filename}"
        self.log(
            EventType.SCREENSHOT_CAPTURED,
            trial_id=trial_id,
            action=rel_path,
            details={"screenshot_label": label, "screenshot_path": rel_path},
        )
        return rel_path

    # ------------------------------------------------------------------
    # Summary generation
    # ------------------------------------------------------------------

    def write_summary(
        self,
        *,
        outcome: str,
        total_steps: int = 0,
        errors_injected: int = 0,
        duration_ms: float = 0.0,
        trial_ids: Optional[list[str]] = None,
        verification_results: Optional[dict[str, bool]] = None,
        screenshots: Optional[list[str]] = None,
        notes: Optional[str] = None,
        metadata: Optional[dict[str, Any]] = None,
    ) -> Path:
        """Generate and write the trial summary JSON.

        The summary is derived from the raw events but serves as the
        canonical human-readable report.  It is never the sole record.

        Returns the path to the written summary file.
        """
        # Read back raw events for enrichment
        raw_events = self.get_raw_events()
        step_events = [e for e in raw_events if e.event_type in (
            EventType.STEP_START, EventType.STEP_FINISH,
        )]
        confirmation_events = [e for e in raw_events if e.event_type in (
            EventType.CONFIRMATION_SHOWN, EventType.CONFIRMATION_RESOLVED,
        )]
        error_events = [e for e in raw_events if e.event_type == EventType.ERROR_INJECTED]
        verification_events = [e for e in raw_events if e.event_type == EventType.VERIFICATION_RESULT]
        intervention_events = [e for e in raw_events if e.event_type in (
            EventType.PAUSE, EventType.RESUME, EventType.STOP,
            EventType.CORRECTION, EventType.TOUCH_INTERVENTION,
        )]

        summary = {
            "study_version": self._study_version,
            "participant_id": self._participant_id,
            "session_id": self._session_id,
            "condition": self._condition,
            "outcome": outcome,
            "total_steps": total_steps,
            "errors_injected": errors_injected,
            "duration_ms": duration_ms,
            "trial_ids": trial_ids or [],
            "step_event_count": len(step_events),
            "confirmation_event_count": len(confirmation_events),
            "error_event_count": len(error_events),
            "verification_results": verification_results or {},
            "intervention_count": len(intervention_events),
            "screenshots": screenshots or [],
            "notes": notes,
            "raw_events_path": str(
                self._events_path.relative_to(
                    self._base_dir / self._study_version
                ).as_posix()
            ),
            "summary_path": "summary.json",
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "metadata": metadata or {},
        }

        # Write atomically: temp file + os.replace prevents partial reads
        summary_path = self._session_dir / "summary.json"
        tmp_path = self._session_dir / "summary.json.tmp"
        tmp_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        os.replace(str(tmp_path), str(summary_path))
        return summary_path

    # ------------------------------------------------------------------
    # Event access
    # ------------------------------------------------------------------

    def get_raw_events(self) -> list[StudyEvent]:
        """Read all events from the JSONL file.

        Thread-safe: acquires the logger lock to prevent reading while
        another thread is appending.

        Returns a list of StudyEvent objects.
        """
        events: list[StudyEvent] = []
        with self._lock:
            events: list[StudyEvent] = []
            if not self._events_path.exists():
                return events
            text = self._events_path.read_text(encoding="utf-8")
            for line in text.strip().splitlines():
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                events.append(StudyEvent(
                    event_type=d["event_type"],
                    timestamp=d["timestamp"],
                    elapsed_ms=d["elapsed_ms"],
                    study_version=d["study_version"],
                    participant_id=d["participant_id"],
                    session_id=d["session_id"],
                    block=d.get("block"),
                    task_id=d.get("task_id"),
                    condition=d.get("condition"),
                    trial_id=d.get("trial_id"),
                    variant=d.get("variant"),
                    step_id=d.get("step_id"),
                    narration=d.get("narration"),
                    action=d.get("action"),
                    details=d.get("details", {}),
                ))
            return events

    # ------------------------------------------------------------------
    # Properties
    # ------------------------------------------------------------------

    @property
    def events_path(self) -> Path:
        return self._events_path

    @property
    def screenshots_dir(self) -> Path:
        return self._screenshots_dir

    @property
    def participant_dir(self) -> Path:
        return self._participant_dir

    @property
    def session_dir(self) -> Path:
        return self._session_dir

    @property
    def participant_id(self) -> str:
        return self._participant_id

    @property
    def session_id(self) -> str:
        return self._session_id

    @property
    def clock(self) -> MonotonicClock:
        return self._clock


# ---------------------------------------------------------------------------
# Convenience: create a logger and auto-log trial start
# ---------------------------------------------------------------------------


def create_study_logger(
    base_dir: Path = STUDY_DATA_ROOT,
    study_version: str = STUDY_VERSION,
    participant_id: str = "",
    session_id: Optional[str] = None,
    condition: str = "",
) -> StudyLogger:
    """Factory that creates a StudyLogger and returns it.

    The session_id is auto-generated as a UUID if not provided.
    """
    if not session_id:
        session_id = uuid.uuid4().hex[:12]
    return StudyLogger(
        base_dir=base_dir,
        study_version=study_version,
        participant_id=participant_id,
        session_id=session_id,
        condition=condition,
    )
