from __future__ import annotations

import json
from pathlib import Path
import threading
from unittest.mock import MagicMock, patch

import pytest

from caddie.study.coordinator import ClaimToken, ClaimedTrial
from caddie.study.executor import TrialResult
from caddie.study.matrix import generate_from_specs_dir
from caddie.study.model import StudyCondition, TrialOutcome
from caddie.study.runtime import (
    RuntimeConflictError,
    RuntimeExecutionError,
    execute_claimed_trial,
    prepare_trial,
)
from caddie.study.session import SessionManager
from caddie.agent.run_control import RunControl


@pytest.fixture(autouse=True)
def clear_session_manager() -> None:
    SessionManager.instance().clear()
    yield
    SessionManager.instance().clear()


@pytest.fixture
def specs_dir(tmp_path: Path) -> Path:
    directory = tmp_path / "specs"
    directory.mkdir()
    tasks = (
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    for index, (task_id, criticality) in enumerate(tasks):
        error = index % 2 == 0
        error_variant = (
            "\n    error_variant:\n"
            f"      id: err_{index}\n"
            "      field: recipient\n"
            "      wrong_value: Wrong\n"
            "      correct_value: Right\n"
            "      description: Wrong recipient"
            if error else ""
        )
        (directory / f"{task_id}.yaml").write_text(
            f'''version: "1.0"
id: {task_id}
instruction_de: Aufgabe {index}
criticality: {criticality}
trigger:
  reference_phrases: [Aufgabe {index}]
  required_concepts:
    - [aufgabe]
steps:
  - id: step1
    action: tap "Send"
    narration: Senden
    step_type: normal{error_variant}
error_steps: {"[step1]" if error else "[]"}
reset_checklist: [Reset]
''', encoding="utf-8",
        )
    return directory


def _prepared_claim(specs_dir: Path, data_dir: Path, utterance: str = "Aufgabe") -> ClaimedTrial:
    matrix = generate_from_specs_dir(specs_dir)
    condition = matrix["P01"].condition_order[0]
    prepared = prepare_trial("P01", 0, condition, specs_dir, data_dir)
    return ClaimedTrial(prepared.config, prepared.spec, utterance, ClaimToken(1))


def test_prepare_selects_exact_assignment_without_side_effects(specs_dir: Path, tmp_path: Path) -> None:
    data_dir = tmp_path / "not-created"
    before = sorted(path.relative_to(specs_dir) for path in specs_dir.rglob("*"))
    assigned = generate_from_specs_dir(specs_dir)["P04"]

    prepared = prepare_trial("P04", 2, assigned.condition_order[2], specs_dir, data_dir)

    assert prepared.config.participant_id == "P04"
    assert prepared.config.trial_index == 2
    assert prepared.config.task_id == assigned.task_order[2]
    assert prepared.config.condition is assigned.condition_order[2]
    assert prepared.config.inject_error is (assigned.task_order[2] in assigned.error_tasks)
    assert prepared.spec.id == assigned.task_order[2]
    assert not data_dir.exists()
    assert sorted(path.relative_to(specs_dir) for path in specs_dir.rglob("*")) == before


@pytest.mark.parametrize(
    ("participant", "trial_index"),
    [("P00", 0), ("P19", 0), ("p01", 0), ("P01", -1), ("P01", True), ("P01", 6)],
)
def test_prepare_rejects_invalid_participant_or_index(
    specs_dir: Path, participant: str, trial_index: object,
) -> None:
    with pytest.raises((TypeError, ValueError)):
        prepare_trial(participant, trial_index, StudyCondition.STEPWISE, specs_dir, None)  # type: ignore[arg-type]


def test_prepare_rejects_mismatched_condition(specs_dir: Path) -> None:
    assigned = generate_from_specs_dir(specs_dir)["P01"]
    wrong = next(condition for condition in StudyCondition if condition is not assigned.condition_order[0])
    with pytest.raises(ValueError, match="condition"):
        prepare_trial("P01", 0, wrong, specs_dir, None)


def test_prepare_rejects_missing_assigned_spec(specs_dir: Path) -> None:
    assigned = generate_from_specs_dir(specs_dir)["P01"]
    missing_task = assigned.task_order[0]
    (specs_dir / f"{missing_task}.yaml").unlink()
    with patch("caddie.study.runtime.generate_matrix", return_value={"P01": assigned}):
        with pytest.raises(ValueError, match=missing_task):
            prepare_trial("P01", 0, assigned.condition_order[0], specs_dir, None)


def test_execute_uses_real_backend_and_single_assigned_error(specs_dir: Path, tmp_path: Path) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    backend = object()
    result = TrialResult(outcome=TrialOutcome.SUCCESS, steps_done=1, duration_ms=12.5, reason="done")
    with patch("caddie.study.runtime.TrialExecutor") as executor_type:
        executor_type.return_value.run.return_value = result
        runtime_result = execute_claimed_trial(claim, backend, MagicMock())
    kwargs = executor_type.call_args.kwargs
    assert kwargs["backend"] is backend
    assert kwargs["verification_backend"]._backend is backend
    assert kwargs["error_tasks"] == (frozenset({claim.spec.id}) if claim.config.inject_error else frozenset())
    assert runtime_result.outcome is TrialOutcome.SUCCESS
    assert runtime_result.steps_executed == 1


def test_execute_logs_utterance_before_executor_run(specs_dir: Path, tmp_path: Path) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data", "Bitte Aufgabe ausführen")

    def inspect_log() -> TrialResult:
        events_path = next((tmp_path / "data").rglob("events.jsonl"))
        event_types = [json.loads(line)["event_type"] for line in events_path.read_text(encoding="utf-8").splitlines()]
        assert event_types == ["session_started", "participant_utterance"]
        return TrialResult(outcome=TrialOutcome.SUCCESS, steps_done=0, duration_ms=1, reason="")

    with patch("caddie.study.runtime.TrialExecutor") as executor_type:
        executor_type.return_value.run.side_effect = inspect_log
        execute_claimed_trial(claim, object(), MagicMock())


def test_execute_cancels_before_executor_when_control_was_stopped_during_startup(
    specs_dir: Path, tmp_path: Path,
) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    control = RunControl()
    control.request_stop()

    def inspect_cancelled_runtime() -> TrialResult:
        oversight = executor_type.call_args.kwargs["oversight"]
        assert oversight.is_cancelled()
        return TrialResult(
            outcome=TrialOutcome.ABORTED,
            steps_done=0,
            duration_ms=1,
            reason="cancelled before first step",
        )

    with patch("caddie.study.runtime.TrialExecutor") as executor_type:
        executor_type.return_value.run.side_effect = inspect_cancelled_runtime
        result = execute_claimed_trial(claim, MagicMock(), control)

    assert result.outcome is TrialOutcome.ABORTED


def test_sequential_trials_clear_only_terminal_previous_session(specs_dir: Path, tmp_path: Path) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    success = TrialResult(outcome=TrialOutcome.SUCCESS, steps_done=0, duration_ms=1, reason="")
    manager = SessionManager.instance()
    with patch.object(manager, "clear", side_effect=AssertionError("unsafe clear")), patch("caddie.study.runtime.TrialExecutor") as executor_type:
        executor_type.return_value.run.return_value = success
        first = execute_claimed_trial(claim, object(), MagicMock())
        second = execute_claimed_trial(claim, object(), MagicMock())
    assert first.session_id != second.session_id
    assert executor_type.call_count == 2


@pytest.mark.parametrize("failure_point", ["construction", "run"])
def test_execute_failure_terminalizes_session_and_allows_next_trial(
    specs_dir: Path, tmp_path: Path, failure_point: str,
) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    success = TrialResult(outcome=TrialOutcome.SUCCESS, steps_done=0, duration_ms=1, reason="")
    with patch("caddie.study.runtime.TrialExecutor") as executor_type:
        failed = MagicMock(run=MagicMock(side_effect=RuntimeError("boom")))
        recovered = MagicMock(run=MagicMock(return_value=success))
        executor_type.side_effect = [RuntimeError("boom"), recovered] if failure_point == "construction" else [failed, recovered]
        with pytest.raises(RuntimeExecutionError, match="boom"):
            execute_claimed_trial(claim, object(), MagicMock())
        failed_session = SessionManager.instance().session
        assert failed_session is not None and failed_session.is_terminal
        assert failed_session.state.value == "failed"
        result = execute_claimed_trial(claim, object(), MagicMock())
    assert result.outcome is TrialOutcome.SUCCESS


def test_execute_refuses_to_clear_running_session(specs_dir: Path, tmp_path: Path) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    manager = SessionManager.instance()
    existing = manager.create(MagicMock(), MagicMock(), MagicMock())
    existing.start()
    with pytest.raises(RuntimeExecutionError, match="active"):
        execute_claimed_trial(claim, object(), MagicMock())
    assert manager.session is existing


def test_concurrent_starts_create_one_logger_and_one_typed_conflict(
    specs_dir: Path, tmp_path: Path,
) -> None:
    claim = _prepared_claim(specs_dir, tmp_path / "data")
    success = TrialResult(
        outcome=TrialOutcome.SUCCESS, steps_done=0, duration_ms=1, reason="",
    )
    first_entered = threading.Event()
    release_first = threading.Event()
    logger_calls: list[str] = []
    outcomes: list[object] = []
    errors: list[BaseException] = []

    from caddie.study.runtime import StudyLogger as RealStudyLogger

    def delayed_logger(**kwargs):
        logger_calls.append(kwargs["session_id"])
        first_entered.set()
        assert release_first.wait(timeout=5)
        return RealStudyLogger(**kwargs)

    def run_trial() -> None:
        try:
            outcomes.append(execute_claimed_trial(claim, object(), MagicMock()))
        except BaseException as exc:
            errors.append(exc)

    with (
        patch("caddie.study.runtime.StudyLogger", side_effect=delayed_logger),
        patch("caddie.study.runtime.TrialExecutor") as executor_type,
    ):
        executor_type.return_value.run.return_value = success
        owner = threading.Thread(target=run_trial)
        owner.start()
        assert first_entered.wait(timeout=5)
        run_trial()
        release_first.set()
        owner.join(timeout=5)

    assert not owner.is_alive()
    assert len(outcomes) == 1
    assert len(errors) == 1
    assert isinstance(errors[0], RuntimeConflictError)
    assert len(logger_calls) == 1
    assert len(list((tmp_path / "data").rglob("events.jsonl"))) == 1
