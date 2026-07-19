"""Core immutable types for the deterministic study runtime.

All enums and dataclasses defined here are frozen and intended to be imported
by every other module in caddie.study.  No business logic lives here — only
the shapes of the data.
"""

from __future__ import annotations

import types
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any, Mapping


# ── Enums ────────────────────────────────────────────────────────────────────


class StudyCondition(StrEnum):
    """Oversight condition for a trial."""

    STEPWISE = "c1_stepwise"
    """Every consequential step requires a confirmation before execution."""

    FINAL_CHECKPOINT = "c2_final_checkpoint"
    """Navigation and preparation run free; one summary confirmed before first consequential step."""

    VOLUNTARY_INTERVENTION = "c3_voluntary_intervention"
    """No mandatory confirmation; pause / stop / correction / decline / touch takeover available."""


class StepType(StrEnum):
    """Classification of a deterministic study step."""

    NORMAL = "normal"
    """A preparatory or navigation step (no commit)."""

    CONSEQUENTIAL = "consequential"
    """A step that changes persistent state — needs oversight attention."""

    COMMIT = "commit"
    """The final irreversible action in a sequence (e.g. send, save, transfer)."""


class CriticalityClass(StrEnum):
    """Task criticality for counterbalancing and error injection."""

    LOW = "low"
    HIGH = "high"


class ScreenOffMode(StrEnum):
    """Initiation policy for the screen-off block."""

    NOTIFY_ONLY = "notify_only"
    """After one minute, post a notification. Do not wake the display."""

    WAKE_ASK = "wake_ask"
    """After one minute, wake the display and request confirmation."""

    WAKE_EXECUTE = "wake_execute"
    """After one minute, wake the display and execute without new confirmation."""


class InitiationResult(StrEnum):
    """Outcome of a screen-off initiation attempt."""

    SUCCESS = "success"
    WAKE_FAILED = "wake_failed"
    NO_RESPONSE = "no_response"


class TrialOutcome(StrEnum):
    """Terminal state of a trial or micro-trial."""

    SUCCESS = "success"
    ERROR_INJECTED = "error_injected"
    VERIFICATION_FAILED = "verification_failed"
    CONFIRMATION_TIMEOUT = "confirmation_timeout"
    ABORTED = "aborted"
    TECHNICAL_FAILURE = "technical_failure"
    PARTICIPANT_STOP = "participant_stop"
    REPEATED = "repeated"


# ── Data classes ─────────────────────────────────────────────────────────────


@dataclass(frozen=True, slots=True)
class StudyStep:
    """A single deterministic step in a trial spec.

    Each step describes one action the agent should perform, with annotations
    for oversight and error injection.
    """

    id: str
    """Stable, unique step identifier (e.g. 'msg_open', 'msg_send')."""

    action: str
    """The Android automation action to execute (package, target view, etc.)."""

    narration: str
    """Visible current-action text shown to the participant in German."""

    step_type: StepType
    """StepType classification for this step."""

    consequential: bool = False
    """True when this step requires oversight attention."""

    commit: bool = False
    """True when this is the final irreversible action."""

    error_variant: ErrorVariant | None = None
    """Controlled error parameters injected into this step, if scheduled."""

    min_narration_ms: int = 800
    """Minimum display time for the narration before the action fires (ms)."""

    def __post_init__(self) -> None:
        """Validate immutable invariants."""
        if not self.id.strip():
            raise ValueError("StudyStep.id must be a non-empty string")
        if not self.action.strip():
            raise ValueError("StudyStep.action must be a non-empty string")
        if not self.narration.strip():
            raise ValueError("StudyStep.narration must be a non-empty string")
        if self.step_type == StepType.COMMIT and not self.commit:
            object.__setattr__(self, "commit", True)
        if self.min_narration_ms < 0:
            raise ValueError("min_narration_ms must be >= 0")


@dataclass(frozen=True, slots=True)
class ErrorVariant:
    """A controlled parameter deviation for error injection.

    The same error object is referenced by the C1 confirmation, the C2
    summary, and the C3 narration so that the participant sees a consistent
    wrong value across all conditions.
    """

    id: str
    """Unique error identifier (e.g. 'err_wrong_recipient')."""

    field: str
    """The parameter name that differs from the correct value (e.g. 'recipient')."""

    wrong_value: str
    """The incorrect value injected into the action."""

    correct_value: str
    """The value the participant was asked for."""

    description: str
    """Human-readable German description of the error for logging."""


@dataclass(frozen=True, slots=True)
class VerificationRule:
    """Deterministic postcondition check after a trial.

    Specifies what the verifier should assert and what evidence to capture.
    """

    id: str
    """Unique verification identifier (e.g. 'v_music_playlist_songs')."""

    assertion: str
    """Human-readable description of what should be true post-trial."""

    check_type: str
    """One of: 'accessibility_check', 'screenshot_match', 'text_present', 'text_absent', 'field_count'."""

    parameters: Mapping[str, Any] = field(
        default_factory=lambda: types.MappingProxyType({})
    )
    """Backend-specific parameters (package name, view label, expected text, etc.).

    Frozen at construction (MappingProxyType) but annotated as Mapping[str, Any]
    for flexibility with loader input types."""

    screenshot_evidence: bool = True
    """Whether to capture a screenshot at this checkpoint."""

    def __post_init__(self) -> None:
        """Ensure parameters is a frozen MappingProxyType."""
        if not isinstance(self.parameters, types.MappingProxyType):
            object.__setattr__(
                self, "parameters", types.MappingProxyType(dict(self.parameters))
            )


@dataclass(frozen=True, slots=True)
class TrialSpec:
    """A complete task specification loaded from a YAML file.

    This is the output of spec_loader — immutable and validated.
    """

    version: str
    """Spec version string (e.g. 'v1')."""

    id: str
    """Unique trial/task identifier (e.g. 'task_music_playlist')."""

    instruction_de: str
    """Participant-facing German instruction text."""

    criticality: CriticalityClass
    """Low or high criticality for counterbalancing."""

    required_packages: tuple[str, ...] = ()
    """Android package names that must be installed and accessible."""

    seeded_artifacts: tuple[str, ...] = ()
    """Deterministic artifact names (contacts, messages, photos) that must exist."""

    reset_checklist: tuple[str, ...] = ()
    """Manual or automated reset assertions the experimenter must confirm."""

    steps: tuple[StudyStep, ...] = ()
    """Ordered sequence of deterministic steps."""

    error_steps: tuple[str, ...] = ()
    """Step IDs where a controlled error is injected."""

    c2_summary_lines: tuple[str, ...] = ()
    """Summary text shown before first consequential step in C2."""

    verification: tuple[VerificationRule, ...] = ()
    """Postcondition verification rules."""

    max_duration_s: int = 300
    """Maximum allowed trial duration in seconds."""

    per_gate_timeout_s: int = 30
    """Per-confirmation-gate timeout in seconds."""

    def __post_init__(self) -> None:
        """Validate immutable invariants."""
        if not self.id.strip():
            raise ValueError("TrialSpec.id must be a non-empty string")
        if not self.version.strip():
            raise ValueError("TrialSpec.version must be a non-empty string")
        if not self.instruction_de.strip():
            raise ValueError("TrialSpec.instruction_de must be a non-empty string")
        if len(self.steps) == 0:
            raise ValueError("TrialSpec.steps must contain at least one step")
        if self.max_duration_s <= 0:
            raise ValueError("TrialSpec.max_duration_s must be > 0")
        if self.per_gate_timeout_s <= 0:
            raise ValueError("TrialSpec.per_gate_timeout_s must be > 0")


@dataclass(frozen=True, slots=True)
class TaskPair:
    """Two tasks grouped as a criticality pair for counterbalancing.

    Each participant receives exactly one low and one high task per pair.
    """

    id: str
    """Pair identifier (e.g. 'pair_chat_music')."""

    low_task: str
    """Trial spec ID for the low-criticality task."""

    high_task: str
    """Trial spec ID for the high-criticality task."""


@dataclass(frozen=True, slots=True)
class ParticipantConfig:
    """Full deterministic assignment for one participant (P01–P18).

    Generated by matrix.py and validated before touching the phone.
    """

    participant_id: str
    """Pseudonymous participant ID (e.g. 'P01')."""

    condition_order: tuple[StudyCondition, ...]
    """Ordered list of StudyCondition for the six main tasks."""

    task_order: tuple[str, ...]
    """Ordered trial spec IDs for the six main tasks."""

    error_tasks: tuple[str, ...]
    """Trial spec IDs where a controlled error is injected (exactly 3)."""

    screen_off_order: tuple[ScreenOffMode, ...]
    """Ordered ScreenOffMode for the three screen-off micro-trials."""

    screen_off_tasks: tuple[str, ...]
    """Trial spec IDs for the three screen-off tasks."""

    pair_assignments: tuple[tuple[str, CriticalityClass], ...] = ()
    """((task_id, criticality), ...) for transparency/logging."""

    def __post_init__(self) -> None:
        """Validate immutable invariants."""
        if not self.participant_id.strip():
            raise ValueError("ParticipantConfig.participant_id must be non-empty")
        if len(self.task_order) != len(self.condition_order):
            raise ValueError(
                f"ParticipantConfig: task_order length ({len(self.task_order)}) "
                f"must equal condition_order length ({len(self.condition_order)})"
            )
        if len(self.error_tasks) != len(self.screen_off_order):
            raise ValueError(
                "error_tasks length must equal screen_off_order length"
            )
