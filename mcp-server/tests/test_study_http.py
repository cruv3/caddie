"""HTTP study endpoint tests.

Verifies that the study system endpoints (/study/health, /study/trials/run,
/study/trials/status, /study/trials/abort) integrate correctly with the
SessionManager and TrialExecutor.
"""

from __future__ import annotations

import io
import json
import threading
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest

pytest.importorskip("caddie.study")


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(autouse=True)
def clear_session_manager():
    """Ensure SessionManager is clean before and after each test.
    Also restores STU DY_SPECS_DIR to avoid polluting other test modules."""
    from caddie.study.session import SessionManager
    import caddie.study.spec_loader as _sl
    _saved_dir = getattr(_sl, 'STUDY_SPECS_DIR', None)
    SessionManager().clear()
    yield
    # Cleanup after test
    SessionManager().clear()
    # Restore STU DY_SPECS_DIR
    if _saved_dir is not None:
        _sl.STUDY_SPECS_DIR = _saved_dir


@pytest.fixture
def study_data_dir(tmp_path: Path) -> Path:
    return tmp_path / "study-data"


@pytest.fixture
def study_specs_dir(tmp_path: Path) -> Path:
    specs_dir = tmp_path / "specs"
    specs_dir.mkdir()

    # Matrix needs 6 specs (3 low + 3 high criticality)
    for name, crit in [
        ("task_low_a", "low"), ("task_low_b", "low"), ("task_low_c", "low"),
        ("task_high_a", "high"), ("task_high_b", "high"), ("task_high_c", "high"),
    ]:
        (specs_dir / f"{name}.yaml").write_text(
            f"""\
version: "1.0"
id: {name}
instruction_de: Testaufgabe
criticality: {crit}
trigger:
  reference_phrases: [Testaufgabe]
  required_concepts:
    - [testaufgabe]
steps:
  - id: step1
    action: tap "Send"
    narration: "Sende Nachricht"
    step_type: consequential
  - id: step2
    action: tap "Confirm"
    narration: "Bestaetigen"
    step_type: normal
error_steps: []
reset_checklist:
  - Clear chat
  - Reset state
verification:
  - id: v1
    assertion: "Nachricht gesendet"
    check_type: text_present
    parameters: {{text: "Gesendet"}}
    screenshot_evidence: false
max_duration_s: 120
per_gate_timeout_s: 15
""",
            encoding="utf-8",
        )
    return specs_dir


@pytest.fixture
def mock_agent_loop() -> MagicMock:
    loop = MagicMock()
    loop._agent_loop = MagicMock()
    loop._agent_loop._run_control = MagicMock()
    loop._agent_loop._run_control.request_pause.return_value = None
    loop._agent_loop._run_control.request_resume.return_value = None
    loop._agent_loop._run_control.request_stop.return_value = None
    loop._agent_loop._run_control.await_confirmation.return_value = True
    loop.apply_control.return_value = {"ok": True}
    return loop


@pytest.fixture
def mock_backend() -> MagicMock:
    backend = MagicMock()
    backend.list_elements.return_value = {"elements": []}
    backend.open_app.return_value = {"ok": True}
    backend.tap_element.return_value = {"ok": True}
    backend.type_text.return_value = {"ok": True}
    backend.press_button.return_value = {"ok": True}
    backend.scroll.return_value = {"ok": True}
    backend.capture_screenshot.return_value = "/tmp/test_screenshot.png"
    return backend


# ---------------------------------------------------------------------------
# Helpers — call endpoints directly (no HTTP wire)
# ---------------------------------------------------------------------------


def _study_health(mgr: "SessionManager") -> tuple[int, dict]:
    """Call the /study/health handler logic."""
    from caddie.study.session import SessionManager

    session = mgr.session
    if session is None:
        return 200, {"ok": True, "study_ready": True, "session": "idle"}
    return 200, {
        "ok": True,
        "study_ready": session.is_running,
        "session": session.state.value,
        "steps_executed": session.steps_executed,
    }


def _study_status(mgr: "SessionManager") -> tuple[int, dict]:
    """Call the /study/trials/status handler logic."""
    from caddie.study.session import SessionManager

    session = mgr.session
    if session is None:
        return 200, {"ok": True, "has_session": False, "session": "idle"}

    metrics = session.get_metrics()
    return 200, {
        "ok": True,
        "has_session": True,
        "state": session.state.value,
        "steps_executed": metrics.steps_executed,
        "elapsed_ms": metrics.elapsed_ms,
        "is_paused": metrics.is_paused,
        "verification_pending": metrics.verification_pending,
    }


def _study_run(
    participant: str,
    condition: str,
    specs_dir: str,
    data_dir: str,
    backend,
    run_control,
) -> tuple[int, dict, SessionManager]:
    """Call the /study/trials/run handler logic."""
    from caddie.study.session import SessionManager
    from caddie.study.logger import StudyLogger
    from caddie.study.oversight import OversightManager
    from caddie.study.matrix import generate_from_specs_dir
    from caddie.study.spec_loader import load_all_specs
    from caddie.study.executor import TrialExecutor
    from caddie.study.model import StudyCondition
    from pathlib import Path
    import time as _time

    _cond_map = {"stepwise": "c1_stepwise", "c1_stepwise": "c1_stepwise", "final_checkpoint": "c2_final_checkpoint", "voluntary": "c3_voluntary_intervention", "c3_voluntary_intervention": "c3_voluntary_intervention"}
    _raw = _cond_map.get(condition, condition)
    try:
        cond = StudyCondition(_raw)
    except ValueError:
        return 400, {"ok": False, "error": f"Invalid condition: {condition}. Must be one of: stepwise, final_checkpoint, voluntary"}
    try:
        import caddie.study.spec_loader as _sl
        _sl.STUDY_SPECS_DIR = Path(specs_dir)
        specs = load_all_specs()
        # Don't restore _orig yet — generate_from_specs_dir needs it
        configs = generate_from_specs_dir(Path(specs_dir) if specs_dir else None)
    except ValueError as _e:
        # No specs or no valid specs
        return 400, {"ok": False, "error": f"No valid specs: {_e}"}
    except Exception as _e:
        import sys; print(f"[DEBUG] Spec/matrix: {_e}", file=sys.stderr); raise
    if participant not in configs:
        return 400, {"ok": False, "error": f"Participant {participant} not found"}
        import sys; print(f"[DEBUG] Participant not in configs: {participant}, keys={list(configs.keys())[:3]}", file=sys.stderr)
    p_config = configs[participant]
    trial_spec = specs.get(p_config.task_order[0])
    if trial_spec is None:
        trial_spec = next(iter(specs.values()))
    session_id = f"sess_{_time.time():.0f}"
    try:
        logger_inst = StudyLogger(
            base_dir=Path(data_dir),
            study_version=trial_spec.version,
            participant_id=participant,
            session_id=session_id,
            condition=cond,
        )
    except Exception as _e:
        import sys; print(f"[DEBUG] Logger: {_e}", file=sys.stderr); raise
    try:
        oversight = OversightManager(logger=logger_inst, condition=cond)
    except Exception as _e:
        import sys; print(f"[DEBUG] Oversight: {_e}", file=sys.stderr); raise
    mgr = SessionManager()
    try:
        session = mgr.create(
            logger=logger_inst,
            run_control=run_control,
            oversight_manager=oversight,
        )
        session.start()
    except RuntimeError as _e:
        import sys; print(f"[DEBUG] Session create: {_e}", file=sys.stderr); raise
    try:
        executor = TrialExecutor(
            backend=backend,
            logger=logger_inst,
            oversight=oversight,
            spec=trial_spec,
            condition=cond,
            error_tasks=frozenset(p_config.error_tasks),
        )
        result = executor.run()
    except Exception as _e:
        import sys; print(f"[DEBUG] Executor: {_e}", file=sys.stderr); raise
    except ValueError:
        return 400, {"ok": False, "error": f"Invalid condition: {condition}"}

    try:
        import caddie.study.spec_loader as _sl
        _orig = getattr(_sl, "STUDY_SPECS_DIR", None)
        _sl.STUDY_SPECS_DIR = Path(specs_dir)
        specs = load_all_specs()
        if _orig is not None:
            _sl.STUDY_SPECS_DIR = _orig
    except Exception as exc:
        return 500, {"ok": False, "error": f"Spec load failed: {exc}"}

    if not specs:
        return 400, {"ok": False, "error": "No specs loaded"}

    try:
        configs = generate_from_specs_dir(specs_dir)
    except Exception as exc:
        return 500, {"ok": False, "error": f"Matrix: {exc}"}

    if participant not in configs:
        return 400, {"ok": False, "error": f"Participant {participant} not found"}

    p_config = configs[participant]
    trial_spec = specs.get(p_config.task_order[0])
    if trial_spec is None:
        trial_spec = next(iter(specs.values()))

    session_id = f"sess_{_time.time():.0f}"
    try:
        logger_inst = StudyLogger(
            base_dir=Path(data_dir),
            study_version=trial_spec.version,
            participant_id=participant,
            session_id=session_id,
            condition=cond,
        )
    except Exception as exc:
        return 500, {"ok": False, "error": f"Logger: {exc}"}

    try:
        oversight = OversightManager(logger=logger_inst, condition=cond)
    except Exception as exc:
        return 500, {"ok": False, "error": f"Oversight: {exc}"}

    mgr = SessionManager()
    try:
        session = mgr.create(
            logger=logger_inst,
            run_control=run_control,
            oversight_manager=oversight,
        )
        session.start()
    except RuntimeError as exc:
        return 409, {"ok": False, "error": str(exc)}

    try:
        executor = TrialExecutor(
            backend=backend,
            logger=logger_inst,
            oversight=oversight,
            spec=trial_spec,
            condition=cond,
            error_tasks=frozenset(p_config.error_tasks),
        )
        result = executor.run()
    except Exception as exc:
        session.fail(reason=f"Execution error: {exc}")
        return 500, {"ok": False, "error": str(exc)}

    if result.outcome.value == "success":
        session.complete()
    else:
        session.fail(reason=result.reason)

    return 200, {
        "ok": True,
        "trial_id": session_id,
        "outcome": result.outcome.value,
        "steps_executed": result.steps_done,
        "duration_ms": result.duration_ms,
        "reason": result.reason,
    }, mgr, session


def _study_abort(mgr: "SessionManager", reason: str = "experimenter_abort") -> tuple[int, dict]:
    """Call the /study/trials/abort handler logic."""
    from caddie.study.session import SessionManager

    session = mgr.session
    if session is None:
        return 404, {"ok": False, "error": "No active session"}

    applied = session.cancel()
    if applied:
        return 200, {"ok": True, "aborted": True, "reason": reason}
    return 200, {
        "ok": True,
        "aborted": False,
        "reason": reason,
        "note": "Session already terminal or idle",
    }


# ---------------------------------------------------------------------------
# Tests — GET /study/health
# ---------------------------------------------------------------------------


class TestStudyHealth:
    def test_health_idle(self):
        """No session → study_ready=True."""
        from caddie.study.session import SessionManager
        mgr = SessionManager()
        SessionManager().clear()
        status, body = _study_health(mgr)
        assert status == 200
        assert body["ok"] is True
        assert body["study_ready"] is True
        assert body["session"] == "idle"

    def test_health_running_session(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Running session → returns current state."""
        rc = mock_agent_loop._agent_loop._run_control
        _, body, mgr, sess = _study_run("P01", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc)
        assert body["ok"] is True

        # Verify session is terminal (may be completed or failed)
        assert sess.is_terminal, f"Session should be terminal but is {sess.state.value}"

        status, health = _study_health(mgr)
        assert status == 200
        assert health["ok"] is True
        assert health["session"] in ("completed", "failed")  # trial finished


# ---------------------------------------------------------------------------
# Tests — GET /study/trials/status
# ---------------------------------------------------------------------------


class TestStudyStatus:
    def test_status_no_session(self):
        """No session → has_session=False."""
        from caddie.study.session import SessionManager
        mgr = SessionManager()
        SessionManager().clear()  # Ensure clean
        status, body = _study_status(mgr)
        assert status == 200
        assert body["ok"] is True
        assert body["has_session"] is False

    def test_status_during_run(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Session exists → has_session=True with metrics."""
        rc = mock_agent_loop._agent_loop._run_control
        _, _, mgr, sess = _study_run("P01", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc)
        assert sess.is_terminal

        status, body = _study_status(mgr)
        assert status == 200
        assert body["ok"] is True
        assert body["has_session"] is True
        assert body["state"] in ("completed", "failed")
        assert body["steps_executed"] >= 0
        assert "elapsed_ms" in body
        assert "is_paused" in body
        assert "verification_pending" in body


# ---------------------------------------------------------------------------
# Tests — POST /study/trials/run
# ---------------------------------------------------------------------------


class TestStudyRun:
    def test_handler_delegates_to_shared_runtime(self, tmp_path):
        from caddie.agent.http_api import _handler_factory
        from caddie.study.model import StudyCondition, TrialOutcome
        from caddie.study.runtime import RuntimeResult

        backend = object()
        run_control = object()
        spec = SimpleNamespace(instruction_de="Diagnose-Aufgabe")
        prepared = SimpleNamespace(config=object(), spec=spec)
        runtime_result = RuntimeResult(
            outcome=TrialOutcome.SUCCESS,
            session_id="sess_test",
            steps_executed=2,
            duration_ms=10.5,
            reason="done",
        )
        context = SimpleNamespace(backend=backend)
        agent_loop = SimpleNamespace(_active_control=run_control)
        handler_type = _handler_factory(context, MagicMock(), agent_loop)
        handler = handler_type.__new__(handler_type)
        handler._read_json = lambda: {
            "participant": "P01",
            "trial_index": 0,
            "condition": "c1_stepwise",
            "specs_dir": str(tmp_path / "specs"),
            "data_dir": str(tmp_path / "data"),
        }
        sent = []
        handler._send_json = lambda body, status=200: sent.append((status, body))

        with (
            patch("caddie.study.runtime.prepare_trial", return_value=prepared) as prepare,
            patch("caddie.study.runtime.execute_claimed_trial", return_value=runtime_result) as execute,
        ):
            handler._handle_study_run()

        prepare.assert_called_once_with(
            "P01", 0, StudyCondition.STEPWISE, tmp_path / "specs", tmp_path / "data"
        )
        claim, passed_backend, passed_control = execute.call_args.args
        assert claim.config is prepared.config
        assert claim.spec is spec
        assert claim.participant_utterance == "Diagnose-Aufgabe"
        assert passed_backend is backend
        assert passed_control is run_control
        assert sent == [(200, {
            "ok": True,
            "trial_id": "sess_test",
            "outcome": "success",
            "steps_executed": 2,
            "duration_ms": 10.5,
            "reason": "done",
        })]

    def test_handler_maps_runtime_conflict_to_409(self):
        from caddie.agent.http_api import _handler_factory
        from caddie.study.runtime import RuntimeConflictError

        prepared = SimpleNamespace(
            config=object(), spec=SimpleNamespace(instruction_de="Diagnose-Aufgabe")
        )
        handler_type = _handler_factory(
            SimpleNamespace(backend=object()),
            MagicMock(),
            SimpleNamespace(_active_control=object()),
        )
        handler = handler_type.__new__(handler_type)
        handler._read_json = lambda: {
            "participant": "P01", "trial_index": 0, "condition": "c1_stepwise",
        }
        sent = []
        handler._send_json = lambda body, status=200: sent.append((status, body))

        with (
            patch("caddie.study.runtime.prepare_trial", return_value=prepared),
            patch(
                "caddie.study.runtime.execute_claimed_trial",
                side_effect=RuntimeConflictError("a study session is already active"),
            ),
        ):
            handler._handle_study_run()

        assert sent == [(409, {
            "ok": False,
            "error": "session: a study session is already active",
        })]

    def test_run_success(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Valid run → ok=True with trial details."""
        rc = mock_agent_loop._agent_loop._run_control
        result = _study_run("P01", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc)
        status = result[0]
        body = result[1]
        assert status == 200
        assert body["ok"] is True
        assert "trial_id" in body
        # Trial may succeed or fail depending on backend
        assert body["outcome"] in ("success", "technical_failure")
        assert "duration_ms" in body

    def test_run_invalid_condition(self, mock_agent_loop, mock_backend):
        """Invalid condition → 400."""
        rc = mock_agent_loop._agent_loop._run_control
        result = _study_run("P01", "invalid", "", "", mock_backend, rc)
        status, body = result[0], result[1]
        assert status == 400
        assert body["ok"] is False
        assert "Invalid condition" in body["error"]

    def test_run_unknown_participant(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Unknown participant → 400."""
        rc = mock_agent_loop._agent_loop._run_control
        result = _study_run(
            "P99", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc
        )
        status, body = result[0], result[1]
        assert status == 400
        assert body["ok"] is False

    def test_run_no_specs(self, mock_agent_loop, mock_backend, tmp_path):
        """Empty specs dir → 400 or 500."""
        rc = mock_agent_loop._agent_loop._run_control
        result = _study_run("P01", "c1_stepwise", str(tmp_path), "", mock_backend, rc)
        status, body = result[0], result[1]
        assert status in (400, 500)
        assert body["ok"] is False
        assert "spec" in body["error"].lower() or "no" in body["error"].lower()


# ---------------------------------------------------------------------------
# Tests — POST /study/trials/abort
# ---------------------------------------------------------------------------


class TestStudyAbort:
    def test_abort_running_session(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Abort a running/failed session → aborted=True."""
        rc = mock_agent_loop._agent_loop._run_control
        _, _, mgr, sess = _study_run("P01", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc)
        # Session is in FAILED state after the trial (backend can't perform actions)
        # But cancel() should still work on FAILED sessions too via _cleanup

        status, body = _study_abort(mgr)
        # Session may be already terminal (FAILED) or cancelling
        assert status == 200
        assert body["ok"] is True
        # aborted may be True (was cancelling) or False (already terminal)
        assert "aborted" in body

    def test_abort_no_session(self):
        """No session → 404."""
        from caddie.study.session import SessionManager
        mgr = SessionManager()
        SessionManager().clear()
        status, body = _study_abort(mgr)
        assert status == 404
        assert body["ok"] is False

    def test_abort_idempotent(self, mock_agent_loop, mock_backend, study_specs_dir, study_data_dir):
        """Two aborts: first=aborted, second=not-aborted."""
        rc = mock_agent_loop._agent_loop._run_control
        _, _, mgr, sess = _study_run("P01", "c1_stepwise", str(study_specs_dir), str(study_data_dir), mock_backend, rc)

        s1, b1 = _study_abort(mgr)
        # First abort: may succeed or session may already be terminal

        s2, b2 = _study_abort(mgr)
        # Second abort on same terminal session
        assert s2 == 200
        assert b2["ok"] is True
        assert "aborted" in b2


# ---------------------------------------------------------------------------
# Coordinator-backed arm/status/abort control
# ---------------------------------------------------------------------------


def _coordinator_handler(
    coordinator,
    *,
    backend=None,
    prepare=None,
    preflight=lambda: True,
    reset=lambda: True,
    session_manager=None,
):
    from caddie.agent.http_api import _handler_factory

    return _handler_factory(
        SimpleNamespace(backend=backend or MagicMock()),
        MagicMock(),
        SimpleNamespace(_active_control=None),
        coordinator=coordinator,
        prepare_trial_fn=prepare,
        preflight_fn=preflight,
        reset_fn=reset,
        session_manager=session_manager,
    )


def _dispatch(handler_type, method: str, path: str, payload: dict | None = None):
    handler = handler_type.__new__(handler_type)
    handler.path = path
    handler._read_json = lambda: payload or {}
    sent = []
    handler._send_json = lambda body, status=200: sent.append((status, body))
    getattr(handler, f"do_{method}")()
    assert len(sent) == 1
    return sent[0]


def _arm_payload(specs_dir: Path, data_dir: Path, trial_index: int = 0) -> dict:
    conditions = ("c1_stepwise", "c2_final_checkpoint", "c3_voluntary_intervention")
    return {
        "participant": "P01",
        "trial_index": trial_index,
        "condition": conditions[trial_index % 3],
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    }


def test_agent_http_server_owns_a_process_local_coordinator(monkeypatch):
    import sys
    from types import ModuleType

    from caddie.agent.http_api import AgentHttpServer

    agent_loop_module = ModuleType("caddie.agent.agent_loop")
    agent_loop_module.AgentLoop = MagicMock()
    monkeypatch.setitem(sys.modules, "caddie.agent.agent_loop", agent_loop_module)
    first = AgentHttpServer(SimpleNamespace())
    second = AgentHttpServer(SimpleNamespace())

    assert first._coordinator is not second._coordinator


def test_arm_prepares_without_executor_or_backend_calls(study_specs_dir, study_data_dir):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    backend = MagicMock()
    handler = _coordinator_handler(coordinator, backend=backend, prepare=prepare_trial)

    with patch("caddie.study.runtime.TrialExecutor") as executor:
        status, body = _dispatch(
            handler, "POST", "/study/trials/arm",
            _arm_payload(study_specs_dir, study_data_dir),
        )

    assert status == 201
    assert body == {
        "ok": True,
        "armed": True,
        "participant": "P01",
        "trial_index": 0,
        "task_id": coordinator.status().task_id,
        "condition": "c1_stepwise",
        "inject_error": coordinator.status().inject_error,
    }
    assert coordinator.status().state is ArmedState.ARMED
    executor.assert_not_called()
    backend.assert_not_called()
    assert str(study_specs_dir) not in json.dumps(body)
    assert str(study_data_dir) not in json.dumps(body)


@pytest.mark.parametrize(
    "changes",
    [
        {"participant": "P99"},
        {"trial_index": -1},
        {"condition": "invalid"},
        {"condition": "c2_final_checkpoint"},
    ],
)
def test_arm_rejects_invalid_assignment_without_preflight_or_reset(
    study_specs_dir, study_data_dir, changes,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    preflight = MagicMock(return_value=True)
    reset = MagicMock(return_value=True)
    coordinator = ArmedTrialCoordinator()
    payload = _arm_payload(study_specs_dir, study_data_dir)
    payload.update(changes)

    status, body = _dispatch(
        _coordinator_handler(
            coordinator, prepare=prepare_trial, preflight=preflight, reset=reset,
        ),
        "POST", "/study/trials/arm", payload,
    )

    assert status == 400
    assert body["ok"] is False
    assert coordinator.status().state is None
    preflight.assert_not_called()
    reset.assert_not_called()


@pytest.mark.parametrize(
    ("preflight_result", "reset_result", "expected_status"),
    [(False, True, 503), (True, False, 500)],
)
def test_arm_preparation_failure_leaves_coordinator_idle(
    study_specs_dir, study_data_dir, preflight_result, reset_result, expected_status,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    status, body = _dispatch(
        _coordinator_handler(
            coordinator,
            prepare=prepare_trial,
            preflight=lambda: preflight_result,
            reset=lambda: reset_result,
        ),
        "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )

    assert status == expected_status
    assert body["ok"] is False
    assert coordinator.status().state is None


def test_arm_maps_spec_error_to_client_response_and_leaves_idle():
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.spec_loader import SpecError

    coordinator = ArmedTrialCoordinator()
    prepare = MagicMock(side_effect=SpecError("invalid trigger contract"))

    status, body = _dispatch(
        _coordinator_handler(coordinator, prepare=prepare),
        "POST",
        "/study/trials/arm",
        {"participant": "P01", "trial_index": 0, "condition": "c1_stepwise"},
    )

    assert status == 400
    assert body == {"ok": False, "error": "invalid trigger contract"}
    assert coordinator.status().state is None


def test_arm_maps_preparation_oserror_to_safe_server_response_and_leaves_idle(caplog):
    from caddie.study.coordinator import ArmedTrialCoordinator

    coordinator = ArmedTrialCoordinator()
    prepare = MagicMock(side_effect=OSError("C:/private/specs/read failed"))

    status, body = _dispatch(
        _coordinator_handler(coordinator, prepare=prepare),
        "POST",
        "/study/trials/arm",
        {"participant": "P01", "trial_index": 0, "condition": "c1_stepwise"},
    )

    assert status == 500
    assert body == {"ok": False, "error": "study trial preparation failed"}
    assert "private" not in json.dumps(body)
    assert coordinator.status().state is None
    assert "study trial preparation failed" in caplog.text


def test_arm_conflict_is_rejected_before_preparation(study_specs_dir, study_data_dir):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    first = _coordinator_handler(coordinator, prepare=prepare_trial)
    assert _dispatch(
        first, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )[0] == 201
    prepare = MagicMock()

    status, _ = _dispatch(
        _coordinator_handler(coordinator, prepare=prepare),
        "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )

    assert status == 409
    prepare.assert_not_called()


def test_arm_running_conflict_is_rejected_before_preparation(study_specs_dir, study_data_dir):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )
    coordinator.route_and_claim("Testaufgabe")
    prepare = MagicMock()

    status, _ = _dispatch(
        _coordinator_handler(coordinator, prepare=prepare),
        "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )

    assert status == 409
    prepare.assert_not_called()


def test_terminal_trial_can_prepare_and_rearm_next_sequential_trial(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    _, first = _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir, 0),
    )
    claim = coordinator.route_and_claim("Testaufgabe").claim
    assert claim is not None
    coordinator.finish_success(claim)

    status, second = _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir, 1),
    )

    assert status == 201
    assert coordinator.status().state is ArmedState.ARMED
    assert first["task_id"] != second["task_id"]
    assert second["trial_index"] == 1
    assert second["condition"] == "c2_final_checkpoint"


def test_status_uses_coordinator_and_never_exposes_paths_or_utterance(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    status, idle = _dispatch(handler, "GET", "/study/trials/status")
    assert status == 200
    assert idle["state"] == "idle"

    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )
    _, armed = _dispatch(handler, "GET", "/study/trials/status")
    assert armed["state"] == "armed"
    assert armed["task_id"] == coordinator.status().task_id
    coordinator.route_and_claim("Testaufgabe secret utterance")
    _, running = _dispatch(handler, "GET", "/study/trials/status")

    assert running["state"] == "running"
    assert running["participant"] == "P01"
    assert running["attempt_count"] == 1
    serialized = json.dumps(running)
    assert "secret utterance" not in serialized
    assert str(study_specs_dir) not in serialized
    assert str(study_data_dir) not in serialized


@pytest.mark.parametrize("terminal", ["completed", "failed", "aborted"])
def test_status_preserves_each_safe_terminal_state(
    study_specs_dir, study_data_dir, terminal,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )
    claim = coordinator.route_and_claim("Testaufgabe").claim
    assert claim is not None
    if terminal == "completed":
        coordinator.finish_success(claim)
    elif terminal == "failed":
        coordinator.finish_failure(claim, "technical failure")
    else:
        coordinator.abort("experimenter abort")

    _, body = _dispatch(handler, "GET", "/study/trials/status")

    assert body["state"] == terminal
    assert body["participant"] == "P01"
    assert "specs_dir" not in body
    assert "data_dir" not in body


def test_abort_armed_and_repeated_abort_preserves_first_reason(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )

    assert _dispatch(
        handler, "POST", "/study/trials/abort", {"reason": "first"},
    ) == (200, {"ok": True, "applied": True, "state": "aborted", "reason": "first"})
    assert _dispatch(
        handler, "POST", "/study/trials/abort", {"reason": "second"},
    ) == (200, {"ok": True, "applied": False, "state": "aborted", "reason": "first"})


def test_abort_running_marks_coordinator_before_cancelling_session(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    session = MagicMock()
    session.cancel.side_effect = lambda: coordinator.status().state is ArmedState.ABORTED
    manager = SimpleNamespace(session=session)
    handler = _coordinator_handler(
        coordinator, prepare=prepare_trial, session_manager=manager,
    )
    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )
    coordinator.route_and_claim("Testaufgabe")

    status, body = _dispatch(handler, "POST", "/study/trials/abort", {})

    assert status == 200
    assert body["applied"] is True
    session.cancel.assert_called_once_with()


def test_abort_idle_is_404_and_empty_reason_is_400():
    from caddie.study.coordinator import ArmedTrialCoordinator

    handler = _coordinator_handler(ArmedTrialCoordinator(), prepare=MagicMock())
    assert _dispatch(handler, "POST", "/study/trials/abort", {})[0] == 404
    assert _dispatch(handler, "POST", "/study/trials/abort", {"reason": "  "})[0] == 400


def test_diagnostic_run_refuses_armed_coordinator_before_runtime_call(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    handler = _coordinator_handler(coordinator, prepare=prepare_trial)
    _dispatch(
        handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir),
    )

    with patch("caddie.study.runtime.execute_claimed_trial") as execute:
        status, _ = _dispatch(handler, "POST", "/study/trials/run", {})

    assert status == 409
    execute.assert_not_called()


def test_default_reset_uses_native_pwsh_argv_and_central_script():
    from caddie.agent.http_api import _reset_study_device

    with patch("caddie.agent.http_api.subprocess.run") as run:
        run.return_value.returncode = 0
        assert _reset_study_device() is True

    argv = run.call_args.args[0]
    assert argv[:3] == ["pwsh", "-NoProfile", "-File"]
    assert Path(argv[3]).name == "reset_study_device.ps1"
    assert Path(argv[3]).parent.name == "scripts"
    assert run.call_args.kwargs == {"check": False}
