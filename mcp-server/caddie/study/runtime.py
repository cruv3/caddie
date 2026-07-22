"""Reusable preparation and execution for one claimed study trial."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable
import uuid

from caddie.study.coordinator import ArmedTrialConfig, ClaimedTrial
from caddie.study.executor import TrialExecutor
from caddie.study.logger import StudyLogger
from caddie.study.matrix import generate_matrix
from caddie.study.model import StudyCondition, TrialOutcome, TrialSpec
from caddie.study.oversight import OversightManager
from caddie.study.session import SessionConflictError, SessionManager
from caddie.study.spec_loader import STUDY_SPECS_DIR, SpecError, load_trial_spec
from caddie.study.verification import VerificationBackendProtocol


@dataclass(frozen=True, slots=True)
class PreparedTrial:
    config: ArmedTrialConfig
    spec: TrialSpec


@dataclass(frozen=True, slots=True)
class RuntimeResult:
    outcome: TrialOutcome
    session_id: str
    steps_executed: int
    duration_ms: float
    reason: str

    @property
    def trial_id(self) -> str:
        return self.session_id


class RuntimeExecutionError(RuntimeError):
    """A trial could not be constructed or executed safely."""

    def __init__(self, stage: str, message: str, *, session_id: str | None = None) -> None:
        self.stage = stage
        self.session_id = session_id
        super().__init__(f"{stage}: {message}")


class RuntimeConflictError(RuntimeExecutionError):
    """Another trial owns the single study session slot."""

    def __init__(self, message: str) -> None:
        super().__init__("session", message)


class StudyVerificationBackend(VerificationBackendProtocol):
    """Adapt the real study backend to verification checks."""

    def __init__(self, backend: Any) -> None:
        self._backend = backend

    def _elements(self) -> list[dict[str, Any]]:
        try:
            response = self._backend.list_elements()
            elements = response.get("elements", []) if isinstance(response, dict) else []
            return elements if isinstance(elements, list) else []
        except Exception:
            return []

    def check_text_present(self, text: str) -> bool:
        needle = text.lower()
        return any(needle in str(element.get("text", "")).lower() for element in self._elements())

    def check_text_absent(self, text: str) -> bool:
        return not self.check_text_present(text)

    def check_accessibility_element(self, label: str) -> bool:
        needle = label.lower()
        return any(needle in str(element.get("text", "")).lower() for element in self._elements())

    def check_field_count(self, container_label: str, expected: int) -> bool:
        needle = container_label.lower()
        count = sum(needle in str(element.get("text", "")).lower() for element in self._elements())
        return count >= expected

    def capture_screenshot(self) -> str | None:
        try:
            result = self._backend.capture_screenshot()
            return result if isinstance(result, str) else None
        except Exception:
            return None


def prepare_trial(
    participant: str,
    trial_index: int,
    condition: StudyCondition,
    specs_dir: Path | None,
    data_dir: Path | None,
) -> PreparedTrial:
    """Resolve one matrix assignment without causing runtime side effects."""
    if type(participant) is not str:
        raise TypeError("participant must be an exact string")
    if participant not in {f"P{index:02d}" for index in range(1, 19)}:
        raise ValueError("participant must be exactly P01 through P18")
    if not isinstance(trial_index, int) or isinstance(trial_index, bool):
        raise TypeError("trial_index must be a non-bool integer")
    if trial_index < 0:
        raise ValueError("trial_index must be nonnegative")
    if not isinstance(condition, StudyCondition):
        raise TypeError("condition must be a StudyCondition")
    if specs_dir is not None and not isinstance(specs_dir, Path):
        raise TypeError("specs_dir must be a pathlib.Path or None")
    if data_dir is not None and not isinstance(data_dir, Path):
        raise TypeError("data_dir must be a pathlib.Path or None")

    specs = _load_strict_specs(specs_dir or STUDY_SPECS_DIR)
    matrix = generate_matrix(specs)
    participant_config = matrix.get(participant)
    if participant_config is None:
        raise ValueError(f"participant {participant} is absent from the study matrix")
    if trial_index >= len(participant_config.task_order):
        raise ValueError(
            f"trial_index {trial_index} is out of range (max: {len(participant_config.task_order) - 1})"
        )
    if trial_index >= len(participant_config.condition_order):
        raise ValueError(f"trial_index {trial_index} has no assigned condition")

    assigned_condition = participant_config.condition_order[trial_index]
    if condition is not assigned_condition:
        raise ValueError(
            f"condition mismatch: trial {trial_index} is assigned {assigned_condition.value}, got {condition.value}"
        )
    task_id = participant_config.task_order[trial_index]
    spec = specs.get(task_id)
    if spec is None:
        raise ValueError(f"assigned task {task_id!r} has no loaded trial spec")

    config = ArmedTrialConfig(
        participant_id=participant,
        trial_index=trial_index,
        task_id=task_id,
        condition=condition,
        inject_error=task_id in participant_config.error_tasks,
        specs_dir=specs_dir,
        data_dir=data_dir,
    )
    return PreparedTrial(config=config, spec=spec)


def _load_strict_specs(specs_dir: Path) -> dict[str, TrialSpec]:
    if not specs_dir.is_dir():
        raise ValueError(f"specs directory does not exist: {specs_dir}")
    paths = sorted(specs_dir.glob("*.yaml")) + sorted(specs_dir.glob("*.yml"))
    if not paths:
        raise ValueError(f"no study specs found in {specs_dir}")
    specs: dict[str, TrialSpec] = {}
    for path in paths:
        spec = load_trial_spec(path)
        if spec.id in specs:
            raise SpecError(f"Duplicate spec ID {spec.id!r} in {path}")
        specs[spec.id] = spec
    return specs


def execute_claimed_trial(
    claim: ClaimedTrial,
    backend: Any,
    run_control: Any,
    *,
    on_session_started: Callable[[], None] | None = None,
) -> RuntimeResult:
    """Execute a claimed trial against the supplied real backend."""
    if not isinstance(claim, ClaimedTrial):
        raise TypeError("claim must be a ClaimedTrial")

    manager = SessionManager.instance()
    try:
        reservation = manager.reserve_for_create()
    except SessionConflictError as exc:
        raise RuntimeConflictError(str(exc)) from exc

    session_id = f"sess_{uuid.uuid4().hex}"
    session = None
    stage = "logger creation"
    try:
        logger_kwargs: dict[str, Any] = {
            "study_version": claim.spec.version,
            "participant_id": claim.config.participant_id,
            "session_id": session_id,
            "condition": claim.config.condition,
        }
        if claim.config.data_dir is not None:
            logger_kwargs["base_dir"] = claim.config.data_dir
        study_logger = StudyLogger(**logger_kwargs)

        stage = "oversight creation"

        def step_callback(step: Any, narration: str):
            return OversightManager._decision_from_bool(
                run_control.await_confirmation(timeout=30.0)
            )

        def batch_callback(steps: Any, narrations: Any = None):
            return OversightManager._decision_from_bool(
                run_control.await_confirmation(timeout=60.0)
            )

        oversight = OversightManager(
            logger=study_logger,
            condition=claim.config.condition,
            step_callback=step_callback,
            batch_callback=batch_callback,
        )

        stage = "session creation"
        session = manager.create(
            logger=study_logger,
            run_control=run_control,
            oversight_manager=oversight,
            reservation=reservation,
        )
        session.start()
        if on_session_started is not None:
            on_session_started()
        if getattr(run_control, "stop_requested", False) is True:
            session.cancel()
        study_logger.participant_utterance(claim.participant_utterance)

        stage = "executor construction"
        executor = TrialExecutor(
            backend=backend,
            logger=study_logger,
            oversight=oversight,
            spec=claim.spec,
            condition=claim.config.condition,
            error_tasks=(
                frozenset({claim.spec.id}) if claim.config.inject_error else frozenset()
            ),
            verification_backend=StudyVerificationBackend(backend),
            session=session,
        )
        stage = "executor run"
        result = executor.run()
        if result.outcome is TrialOutcome.SUCCESS:
            session.complete()
        else:
            session.fail(reason=result.reason or result.outcome.value)
        return RuntimeResult(
            outcome=result.outcome,
            session_id=session_id,
            steps_executed=result.steps_done,
            duration_ms=result.duration_ms,
            reason=result.reason,
        )
    except SessionConflictError as exc:
        manager.release_reservation(reservation)
        raise RuntimeConflictError(str(exc)) from exc
    except RuntimeExecutionError:
        manager.release_reservation(reservation)
        raise
    except Exception as exc:
        manager.release_reservation(reservation)
        if session is not None and not session.is_terminal:
            try:
                session.fail(reason=f"{stage}: {exc}")
            except Exception:
                pass
        raise RuntimeExecutionError(stage, str(exc), session_id=session_id) from exc
