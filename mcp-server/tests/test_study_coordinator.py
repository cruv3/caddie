from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import FrozenInstanceError
from pathlib import Path
from threading import Barrier

import pytest

from caddie.study.coordinator import (
    ArmedState,
    ArmedTrialConfig,
    ArmedTrialCoordinator,
    CoordinatorConflictError,
    CoordinatorRouteResult,
    InvalidTransitionError,
)
from caddie.study.model import (
    CriticalityClass,
    StudyCondition,
    StudyStep,
    StepType,
    TrialSpec,
    TriggerContract,
)
from caddie.study.routing import RouteDecision


def _spec(task_id: str = "task_calendar") -> TrialSpec:
    return TrialSpec(
        version="v1",
        id=task_id,
        instruction_de="Finde die Prüfung im Kalender.",
        criticality=CriticalityClass.LOW,
        steps=(
            StudyStep(
                id="open",
                action="open_calendar",
                narration="Kalender öffnen",
                step_type=StepType.NORMAL,
            ),
        ),
        trigger=TriggerContract(
            reference_phrases=("Prüfung im Kalender",),
            required_concepts=(("prüfung",), ("kalender",)),
        ),
    )


def _config(task_id: str = "task_calendar") -> ArmedTrialConfig:
    return ArmedTrialConfig(
        participant_id="P01",
        trial_index=2,
        task_id=task_id,
        condition=StudyCondition.STEPWISE,
        inject_error=True,
        specs_dir=Path("private/specs"),
        data_dir=Path("private/data"),
    )


def test_new_coordinator_is_idle_and_passes_through_without_an_audit_attempt():
    coordinator = ArmedTrialCoordinator()

    result = coordinator.route_and_claim("Prüfung im Kalender")

    assert result.decision is RouteDecision.PASS_THROUGH
    assert result.claim is None
    assert result.match is None
    assert result.reason == "idle"
    assert coordinator.status().state is None
    assert coordinator.status().attempt_count == 0
    assert coordinator.attempts() == ()


def test_arm_exposes_safe_status_and_matching_claim_transitions_to_running():
    coordinator = ArmedTrialCoordinator()
    config = _config()
    spec = _spec()

    coordinator.arm(config, spec)
    armed = coordinator.status()
    result = coordinator.route_and_claim("Jarvis, Prüfung im Kalender")

    assert armed.state is ArmedState.ARMED
    assert armed.participant_id == "P01"
    assert armed.trial_index == 2
    assert armed.task_id == "task_calendar"
    assert armed.condition is StudyCondition.STEPWISE
    assert armed.inject_error is True
    assert not hasattr(armed, "specs_dir")
    assert not hasattr(armed, "data_dir")
    assert result.decision is RouteDecision.CLAIMED
    assert result.reason == "claimed"
    assert result.match is not None and result.match.matched
    assert result.claim is not None
    assert result.claim.config is config
    assert result.claim.spec is spec
    assert result.claim.participant_utterance == "Jarvis, Prüfung im Kalender"
    with pytest.raises(FrozenInstanceError):
        result.claim.participant_utterance = "changed"  # type: ignore[misc]
    assert coordinator.status().state is ArmedState.RUNNING
    assert coordinator.status().attempt_count == 1


@pytest.mark.parametrize(
    ("field", "value", "error_type"),
    [
        ("participant_id", "", ValueError),
        ("participant_id", "   ", ValueError),
        ("participant_id", None, TypeError),
        ("trial_index", -1, ValueError),
        ("trial_index", "0", TypeError),
        ("trial_index", True, TypeError),
        ("task_id", "", ValueError),
        ("task_id", "   ", ValueError),
        ("task_id", None, TypeError),
        ("condition", StudyCondition.STEPWISE.value, TypeError),
        ("condition", object(), TypeError),
        ("inject_error", 1, TypeError),
        ("inject_error", "false", TypeError),
        ("specs_dir", "private/specs", TypeError),
        ("specs_dir", 42, TypeError),
        ("data_dir", "private/data", TypeError),
        ("data_dir", 42, TypeError),
    ],
)
def test_invalid_config_fields_are_rejected_without_changing_idle_state(
    field, value, error_type
):
    coordinator = ArmedTrialCoordinator()
    values = {
        "participant_id": "P01",
        "trial_index": 0,
        "task_id": "task_calendar",
        "condition": StudyCondition.STEPWISE,
        "inject_error": False,
        "specs_dir": Path("private/specs"),
        "data_dir": Path("private/data"),
    }
    values[field] = value

    with pytest.raises(error_type):
        config = ArmedTrialConfig(**values)
        coordinator.arm(config, _spec())

    assert coordinator.status().state is None


@pytest.mark.parametrize(
    ("specs_dir", "data_dir"),
    [
        (Path("private/specs"), Path("private/data")),
        (Path("private/specs"), None),
        (None, Path("private/data")),
        (None, None),
    ],
)
def test_path_or_none_directory_fields_arm_successfully(specs_dir, data_dir):
    coordinator = ArmedTrialCoordinator()
    config = ArmedTrialConfig(
        participant_id="P01",
        trial_index=0,
        task_id="task_calendar",
        condition=StudyCondition.STEPWISE,
        inject_error=False,
        specs_dir=specs_dir,
        data_dir=data_dir,
    )

    coordinator.arm(config, _spec())

    assert coordinator.status().state is ArmedState.ARMED
    assert not hasattr(coordinator.status(), "specs_dir")
    assert not hasattr(coordinator.status(), "data_dir")


def test_arm_rejects_task_mismatch_and_missing_trigger():
    coordinator = ArmedTrialCoordinator()

    with pytest.raises(ValueError, match="task_id"):
        coordinator.arm(_config("different"), _spec())
    with pytest.raises(ValueError, match="trigger"):
        coordinator.arm(_config(), _spec().__class__(
            version="v1",
            id="task_calendar",
            instruction_de="Instruction",
            criticality=CriticalityClass.LOW,
            steps=_spec().steps,
            trigger=None,
        ))

    assert coordinator.status().state is None


def test_retry_preserves_armed_state_and_records_router_match():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())

    result = coordinator.route_and_claim("spiele Musik")

    assert result.decision is RouteDecision.RETRY
    assert result.reason == "missing_required_concepts"
    assert result.claim is None
    assert result.match is not None
    assert coordinator.status().state is ArmedState.ARMED
    assert coordinator.attempts()[0].match is result.match


def test_rearming_conflicts_while_armed_or_running_without_changing_record():
    armed = ArmedTrialCoordinator()
    armed.arm(_config(), _spec())

    with pytest.raises(CoordinatorConflictError):
        armed.arm(_config("replacement"), _spec("replacement"))

    assert armed.status().task_id == "task_calendar"

    running = ArmedTrialCoordinator()
    running.arm(_config(), _spec())
    running.route_and_claim("Prüfung im Kalender")

    with pytest.raises(CoordinatorConflictError):
        running.arm(_config("replacement"), _spec("replacement"))

    assert running.status().state is ArmedState.RUNNING
    assert running.status().task_id == "task_calendar"


def test_running_input_does_not_retrigger_and_is_audited_for_intervention_routing():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    first = coordinator.route_and_claim("Prüfung im Kalender")

    second = coordinator.route_and_claim("Prüfung im Kalender")

    assert first.decision is RouteDecision.CLAIMED
    assert second.decision is RouteDecision.RETRY
    assert second.claim is None
    assert second.match is None
    assert second.reason == "trial_running"
    assert coordinator.status().state is ArmedState.RUNNING
    assert [attempt.reason for attempt in coordinator.attempts()] == [
        "claimed",
        "trial_running",
    ]


def test_concurrent_matching_calls_produce_exactly_one_claim():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    callers_ready = Barrier(3)

    def route() -> CoordinatorRouteResult:
        callers_ready.wait()
        return coordinator.route_and_claim("Prüfung im Kalender")

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(route) for _ in range(2)]
        callers_ready.wait()
        results = [future.result(timeout=2) for future in futures]

    assert sorted(result.decision.value for result in results) == [
        RouteDecision.CLAIMED.value,
        RouteDecision.RETRY.value,
    ]
    assert sum(result.claim is not None for result in results) == 1
    assert {result.reason for result in results} == {"claimed", "trial_running"}
    assert coordinator.status().state is ArmedState.RUNNING
    assert coordinator.status().attempt_count == 2


def test_finish_success_only_transitions_running_to_completed():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())

    with pytest.raises(InvalidTransitionError):
        coordinator.finish_success()
    assert coordinator.status().state is ArmedState.ARMED

    coordinator.route_and_claim("Prüfung im Kalender")
    coordinator.finish_success()

    assert coordinator.status().state is ArmedState.COMPLETED
    assert coordinator.status().reason is None
    with pytest.raises(InvalidTransitionError):
        coordinator.finish_success()
    assert coordinator.status().state is ArmedState.COMPLETED


def test_finish_failure_only_transitions_running_and_retains_reason():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    coordinator.route_and_claim("Prüfung im Kalender")

    coordinator.finish_failure("executor_failed")

    status = coordinator.status()
    assert status.state is ArmedState.FAILED
    assert status.reason == "executor_failed"
    with pytest.raises(InvalidTransitionError):
        coordinator.finish_failure("again")
    assert coordinator.status() == status


@pytest.mark.parametrize("running", [False, True])
def test_abort_armed_or_running_retains_reason(running):
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    if running:
        coordinator.route_and_claim("Prüfung im Kalender")

    coordinator.abort("participant_stop")

    status = coordinator.status()
    assert status.state is ArmedState.ABORTED
    assert status.reason == "participant_stop"
    with pytest.raises(InvalidTransitionError):
        coordinator.abort("again")
    assert coordinator.status() == status


@pytest.mark.parametrize("terminal", ["success", "failure", "abort"])
def test_terminal_states_pass_through_without_adding_attempts(terminal):
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    coordinator.route_and_claim("Prüfung im Kalender")
    if terminal == "success":
        coordinator.finish_success()
    elif terminal == "failure":
        coordinator.finish_failure("failed")
    else:
        coordinator.abort("stopped")
    previous_attempts = coordinator.attempts()

    result = coordinator.route_and_claim("Prüfung im Kalender")

    assert result.decision is RouteDecision.PASS_THROUGH
    assert result.claim is None
    assert result.match is None
    assert result.reason == {
        "success": "trial_completed",
        "failure": "trial_failed",
        "abort": "trial_aborted",
    }[terminal]
    assert coordinator.attempts() == previous_attempts


def test_clear_rejects_active_records_but_clears_terminal_record_completely():
    armed = ArmedTrialCoordinator()
    armed.arm(_config(), _spec())
    with pytest.raises(InvalidTransitionError):
        armed.clear()
    assert armed.status().state is ArmedState.ARMED

    running = ArmedTrialCoordinator()
    running.arm(_config(), _spec())
    running.route_and_claim("Prüfung im Kalender")
    with pytest.raises(InvalidTransitionError):
        running.clear()
    assert running.status().state is ArmedState.RUNNING

    running.finish_success()
    running.clear()

    status = running.status()
    assert status.state is None
    assert status.participant_id is None
    assert status.trial_index is None
    assert status.task_id is None
    assert status.condition is None
    assert status.inject_error is None
    assert status.reason is None
    assert status.attempt_count == 0
    assert running.attempts() == ()


@pytest.mark.parametrize("terminal", list(ArmedState)[2:])
def test_rearming_after_terminal_replaces_old_state_attempts_and_claim(terminal):
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    coordinator.route_and_claim("Prüfung im Kalender")
    if terminal is ArmedState.COMPLETED:
        coordinator.finish_success()
    elif terminal is ArmedState.FAILED:
        coordinator.finish_failure("failed")
    else:
        coordinator.abort("stopped")

    replacement_config = _config("replacement")
    replacement_spec = _spec("replacement")
    coordinator.arm(replacement_config, replacement_spec)

    status = coordinator.status()
    assert status.state is ArmedState.ARMED
    assert status.task_id == "replacement"
    assert status.reason is None
    assert status.attempt_count == 0
    assert coordinator.attempts() == ()


def test_snapshots_and_attempts_are_immutable_and_timestamps_are_ordered():
    coordinator = ArmedTrialCoordinator()
    coordinator.arm(_config(), _spec())
    coordinator.route_and_claim("kein Treffer eins")
    coordinator.route_and_claim("kein Treffer zwei")

    status = coordinator.status()
    attempts = coordinator.attempts()

    with pytest.raises(FrozenInstanceError):
        status.state = None  # type: ignore[misc]
    with pytest.raises(FrozenInstanceError):
        attempts[0].reason = "changed"  # type: ignore[misc]
    with pytest.raises(AttributeError):
        attempts.append(attempts[0])  # type: ignore[attr-defined]
    assert attempts[0].wall_time > 0
    assert attempts[0].monotonic_time > 0
    assert attempts[0].monotonic_time <= attempts[1].monotonic_time
    assert attempts[0].utterance == "kein Treffer eins"
    assert attempts[1].utterance == "kein Treffer zwei"


def test_new_instance_does_not_recover_another_instances_armed_record():
    first = ArmedTrialCoordinator()
    first.arm(_config(), _spec())

    restarted = ArmedTrialCoordinator()

    assert first.status().state is ArmedState.ARMED
    assert restarted.status().state is None
    assert restarted.route_and_claim("Prüfung im Kalender").reason == "idle"
