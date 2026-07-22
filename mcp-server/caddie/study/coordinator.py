"""Thread-safe coordination for one user-initiated armed study trial."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from threading import RLock
import time

from caddie.study.model import StudyCondition, TrialSpec
from caddie.study.routing import MatchResult, RouteDecision, StudyTaskRouter


class ArmedState(StrEnum):
    ARMED = "armed"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    ABORTED = "aborted"


@dataclass(frozen=True, slots=True)
class ArmedTrialConfig:
    participant_id: str
    trial_index: int
    task_id: str
    condition: StudyCondition
    inject_error: bool
    specs_dir: Path | None
    data_dir: Path | None

    def __post_init__(self) -> None:
        self._validate()

    def _validate(self) -> None:
        if not isinstance(self.participant_id, str):
            raise TypeError("participant_id must be a string")
        if not self.participant_id.strip():
            raise ValueError("participant_id must be non-empty")
        if not isinstance(self.trial_index, int) or isinstance(self.trial_index, bool):
            raise TypeError("trial_index must be an integer")
        if self.trial_index < 0:
            raise ValueError("trial_index must be nonnegative")
        if not isinstance(self.task_id, str):
            raise TypeError("task_id must be a string")
        if not self.task_id.strip():
            raise ValueError("task_id must be non-empty")
        if not isinstance(self.condition, StudyCondition):
            raise TypeError("condition must be a StudyCondition")
        if type(self.inject_error) is not bool:
            raise TypeError("inject_error must be a bool")
        if self.specs_dir is not None and not isinstance(self.specs_dir, Path):
            raise TypeError("specs_dir must be a pathlib.Path or None")
        if self.data_dir is not None and not isinstance(self.data_dir, Path):
            raise TypeError("data_dir must be a pathlib.Path or None")


@dataclass(frozen=True, slots=True)
class ClaimedTrial:
    config: ArmedTrialConfig
    spec: TrialSpec
    participant_utterance: str


@dataclass(frozen=True, slots=True)
class RoutingAttempt:
    wall_time: float
    monotonic_time: float
    utterance: str
    match: MatchResult | None
    decision: RouteDecision
    reason: str


@dataclass(frozen=True, slots=True)
class CoordinatorRouteResult:
    decision: RouteDecision
    match: MatchResult | None
    claim: ClaimedTrial | None
    reason: str


@dataclass(frozen=True, slots=True)
class CoordinatorStatus:
    state: ArmedState | None
    participant_id: str | None
    trial_index: int | None
    task_id: str | None
    condition: StudyCondition | None
    inject_error: bool | None
    reason: str | None
    attempt_count: int


class CoordinatorError(RuntimeError):
    """Base error for coordinator state conflicts."""


class CoordinatorConflictError(CoordinatorError):
    """Raised when arming would overwrite an active trial."""


class InvalidTransitionError(CoordinatorError):
    """Raised when an operation is invalid for the current state."""


class ArmedTrialCoordinator:
    """Own the in-memory lifecycle and exactly-once claim for one trial."""

    def __init__(self) -> None:
        self._lock = RLock()
        self._router = StudyTaskRouter()
        self._state: ArmedState | None = None
        self._config: ArmedTrialConfig | None = None
        self._spec: TrialSpec | None = None
        self._claim: ClaimedTrial | None = None
        self._reason: str | None = None
        self._attempts: list[RoutingAttempt] = []

    def arm(self, config: ArmedTrialConfig, spec: TrialSpec) -> None:
        config._validate()
        if config.task_id != spec.id:
            raise ValueError("config task_id must equal spec.id")
        if spec.trigger is None:
            raise ValueError("spec must have a trigger contract")
        with self._lock:
            if self._state in (ArmedState.ARMED, ArmedState.RUNNING):
                raise CoordinatorConflictError("a trial is already active")
            self._state = ArmedState.ARMED
            self._config = config
            self._spec = spec
            self._claim = None
            self._reason = None
            self._attempts = []

    def route_and_claim(self, text: str) -> CoordinatorRouteResult:
        with self._lock:
            if self._state is None:
                return CoordinatorRouteResult(
                    RouteDecision.PASS_THROUGH, None, None, "idle"
                )
            if self._state in (
                ArmedState.COMPLETED,
                ArmedState.FAILED,
                ArmedState.ABORTED,
            ):
                return CoordinatorRouteResult(
                    RouteDecision.PASS_THROUGH,
                    None,
                    None,
                    f"trial_{self._state.value}",
                )
            if self._state is ArmedState.RUNNING:
                result = CoordinatorRouteResult(
                    RouteDecision.RETRY, None, None, "trial_running"
                )
                self._record_attempt(text, result)
                return result
            routed = self._router.route(text, self._spec)
            claim = None
            if routed.decision is RouteDecision.CLAIMED:
                assert self._config is not None and self._spec is not None
                claim = ClaimedTrial(self._config, self._spec, text)
                self._claim = claim
                self._state = ArmedState.RUNNING
            reason = (
                "claimed"
                if routed.decision is RouteDecision.CLAIMED
                else routed.match.reason
                if routed.match is not None
                else routed.decision.value
            )
            result = CoordinatorRouteResult(routed.decision, routed.match, claim, reason)
            self._record_attempt(text, result)
            return result

    def finish_success(self) -> None:
        with self._lock:
            if self._state is not ArmedState.RUNNING:
                raise InvalidTransitionError("finish_success requires a running trial")
            self._state = ArmedState.COMPLETED
            self._reason = None

    def finish_failure(self, reason: str) -> None:
        with self._lock:
            if self._state is not ArmedState.RUNNING:
                raise InvalidTransitionError("finish_failure requires a running trial")
            self._state = ArmedState.FAILED
            self._reason = reason

    def abort(self, reason: str) -> None:
        with self._lock:
            if self._state not in (ArmedState.ARMED, ArmedState.RUNNING):
                raise InvalidTransitionError("abort requires an armed or running trial")
            self._state = ArmedState.ABORTED
            self._reason = reason

    def clear(self) -> None:
        with self._lock:
            if self._state in (ArmedState.ARMED, ArmedState.RUNNING):
                raise InvalidTransitionError("cannot clear an active trial")
            self._state = None
            self._config = None
            self._spec = None
            self._claim = None
            self._reason = None
            self._attempts = []

    def _record_attempt(
        self, text: str, result: CoordinatorRouteResult
    ) -> None:
        self._attempts.append(
            RoutingAttempt(
                wall_time=time.time(),
                monotonic_time=time.monotonic(),
                utterance=text,
                match=result.match,
                decision=result.decision,
                reason=result.reason,
            )
        )

    def status(self) -> CoordinatorStatus:
        with self._lock:
            config = self._config
            return CoordinatorStatus(
                state=self._state,
                participant_id=config.participant_id if config else None,
                trial_index=config.trial_index if config else None,
                task_id=config.task_id if config else None,
                condition=config.condition if config else None,
                inject_error=config.inject_error if config else None,
                reason=self._reason,
                attempt_count=len(self._attempts),
            )

    def attempts(self) -> tuple[RoutingAttempt, ...]:
        with self._lock:
            return tuple(self._attempts)
