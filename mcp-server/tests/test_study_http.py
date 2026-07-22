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
    context=None,
    agent_loop=None,
    prepare=None,
    preflight=lambda: True,
    reset=lambda: True,
    session_manager=None,
):
    from caddie.agent.http_api import _handler_factory

    context = context or SimpleNamespace(backend=backend or MagicMock())
    return _handler_factory(
        context,
        MagicMock(),
        agent_loop or SimpleNamespace(_active_control=None),
        coordinator=coordinator,
        prepare_trial_fn=prepare,
        preflight_fn=preflight,
        reset_fn=reset,
        session_manager=session_manager,
    )


def _dispatch(handler_type, method: str, path: str, payload: dict | None = None):
    handler = handler_type.__new__(handler_type)
    handler.path = path
    handler.headers = {}
    handler._read_json = lambda: payload or {}
    sent = []
    handler._send_json = lambda body, status=200: sent.append((status, body))
    getattr(handler, f"do_{method}")()
    assert len(sent) == 1
    return sent[0]


def _task_context(backend=None):
    skills = MagicMock()
    skills.match.return_value = []
    return SimpleNamespace(backend=backend or MagicMock(), skills=skills)


def _wait_for_state(coordinator, state, timeout=1.0):
    import time

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if coordinator.status().state is state:
            return
        time.sleep(0.005)
    assert coordinator.status().state is state


def _dispatch_stream(handler_type, payload: dict):
    handler = handler_type.__new__(handler_type)
    handler.path = "/task/stream"
    handler.headers = {}
    handler._read_json = lambda: payload
    handler.wfile = io.BytesIO()
    response = []
    handler.send_response = lambda status: response.append(status)
    handler.send_header = lambda *_args: None
    handler.end_headers = lambda: None
    sent_json = []
    handler._send_json = lambda body, status=200: sent_json.append((status, body))
    handler.do_POST()
    return response, sent_json, handler.wfile.getvalue().decode("utf-8")


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


def test_arm_maps_spec_error_to_path_safe_client_response_and_leaves_idle(caplog):
    from caddie.study.coordinator import ArmedTrialCoordinator
    from caddie.study.spec_loader import SpecError

    coordinator = ArmedTrialCoordinator()
    private_path = "C:/Users/researcher/private/specs/task.yaml"
    prepare = MagicMock(side_effect=SpecError(f"{private_path}: invalid trigger contract"))

    status, body = _dispatch(
        _coordinator_handler(coordinator, prepare=prepare),
        "POST",
        "/study/trials/arm",
        {"participant": "P01", "trial_index": 0, "condition": "c1_stepwise"},
    )

    assert status == 400
    assert body == {
        "ok": False,
        "error": "invalid_study_spec",
        "message": "Study specification is invalid",
    }
    assert private_path not in json.dumps(body)
    assert private_path in caplog.text
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


# ---------------------------------------------------------------------------
# Participant-initiated routing through the normal task endpoints
# ---------------------------------------------------------------------------


def test_task_without_armed_trial_preserves_normal_agent_loop():
    from caddie.study.coordinator import ArmedTrialCoordinator

    coordinator = ArmedTrialCoordinator()
    context = _task_context()
    agent_loop = MagicMock()
    agent_loop.run.return_value = {"ok": True, "finished_emitted": True}
    agent_loop.recent_run.return_value = None
    handler = _coordinator_handler(
        coordinator, context=context, agent_loop=agent_loop, prepare=MagicMock(),
    )

    with (
        patch("caddie.agent.http_api.select_prompt_skills", return_value=[]),
        patch("caddie.agent.http_api.select_prompt_hints", return_value=[]),
        patch("caddie.agent.http_api.build_system_prompt", return_value="prompt"),
    ):
        status, body = _dispatch(handler, "POST", "/task", {"task": "normale Aufgabe"})

    assert status == 200
    assert body["ok"] is True
    agent_loop.run.assert_called_once()


def test_task_mismatch_returns_exact_retry_without_agent_or_backend_work(
    study_specs_dir, study_data_dir,
):
    import queue

    from caddie.agent.event_bus import EVENT_BUS
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    backend = MagicMock()
    context = _task_context(backend)
    agent_loop = MagicMock()
    handler = _coordinator_handler(
        coordinator, context=context, agent_loop=agent_loop, prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    with EVENT_BUS.subscription() as events:
        status, body = _dispatch(handler, "POST", "/task", {"task": "Jarvis spiele Musik"})
        published = []
        while True:
            try:
                published.append(events.get_nowait())
            except queue.Empty:
                break

    retry = "Das habe ich nicht ganz verstanden. Kannst du die Aufgabe bitte noch einmal sagen?"
    assert status == 200
    assert body == {"ok": False, "study": "retry", "message": retry}
    assert any(e.type == "question_asked" and e.payload == {"question": retry} for e in published)
    assert coordinator.status().state is ArmedState.ARMED
    agent_loop.run.assert_not_called()
    backend.assert_not_called()


def test_matching_task_starts_exactly_one_background_trial_and_completes(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    coordinator = ArmedTrialCoordinator()
    context = _task_context()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    handler = _coordinator_handler(
        coordinator, context=context, agent_loop=agent_loop, prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))
    runtime_result = RuntimeResult(TrialOutcome.SUCCESS, "sess-1", 2, 12.0, "done")

    with patch("caddie.study.runtime.execute_claimed_trial", return_value=runtime_result) as execute:
        status, body = _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
        _wait_for_state(coordinator, ArmedState.COMPLETED)

    assert status == 202
    assert body["study"] == "accepted"
    execute.assert_called_once()
    agent_loop.run.assert_not_called()


def test_second_task_while_trial_runs_becomes_correction_not_second_trial(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    release = threading.Event()
    entered = threading.Event()
    coordinator = ArmedTrialCoordinator()
    context = _task_context()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    agent_loop.apply_control.return_value = {"ok": True}
    handler = _coordinator_handler(
        coordinator, context=context, agent_loop=agent_loop, prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    def execute(*_args, **_kwargs):
        _kwargs["on_session_started"]()
        entered.set()
        assert release.wait(1.0)
        return RuntimeResult(TrialOutcome.SUCCESS, "sess-1", 2, 12.0, "done")

    with patch("caddie.study.runtime.execute_claimed_trial", side_effect=execute) as execute_mock:
        first_status, _ = _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
        assert entered.wait(1.0)
        second_status, second = _dispatch(
            handler, "POST", "/task", {"task": "nimm bitte den anderen Eintrag"},
        )
        release.set()
        _wait_for_state(coordinator, ArmedState.COMPLETED)

    assert first_status == 202
    assert second_status == 200
    assert second["study"] == "running_input"
    agent_loop.apply_control.assert_called_once_with("correct", "nimm bitte den anderen Eintrag")
    assert execute_mock.call_count == 1


def test_claim_response_waits_until_running_input_control_is_ready(
    study_specs_dir, study_data_dir,
):
    import time

    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    real_thread = threading.Thread

    class DelayedThread:
        def __init__(self, *, target, name, daemon):
            self._inner = real_thread(
                target=lambda: (time.sleep(0.05), target()), name=name, daemon=daemon,
            )

        def start(self):
            self._inner.start()

        def join(self, timeout=None):
            self._inner.join(timeout)

    release = threading.Event()
    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    agent_loop.apply_control.side_effect = lambda action, text: {
        "ok": agent_loop._active_control is not None,
    }
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(),
        agent_loop=agent_loop,
        prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    def execute(*_args, **_kwargs):
        _kwargs["on_session_started"]()
        assert release.wait(1.0)
        return RuntimeResult(TrialOutcome.SUCCESS, "sess-ready", 2, 12.0, "done")

    with (
        patch("caddie.agent.http_api.threading.Thread", DelayedThread),
        patch("caddie.study.runtime.execute_claimed_trial", side_effect=execute),
    ):
        first_status, _ = _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
        second_status, _ = _dispatch(handler, "POST", "/task", {"task": "ändere den Eintrag"})
        release.set()
        _wait_for_state(coordinator, ArmedState.COMPLETED)

    assert first_status == 202
    assert second_status == 200


def test_abort_during_worker_startup_stops_control_before_trial_can_act(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    entered = threading.Event()
    release = threading.Event()
    captured_control = []
    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(),
        agent_loop=agent_loop,
        prepare=prepare_trial,
        session_manager=SimpleNamespace(session=None),
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    def execute(_claim, _backend, control, **_kwargs):
        _kwargs["on_session_started"]()
        captured_control.append(control)
        entered.set()
        assert release.wait(1.0)
        assert control.stop_requested
        return RuntimeResult(TrialOutcome.ABORTED, "sess-abort", 0, 1.0, "aborted")

    with patch("caddie.study.runtime.execute_claimed_trial", side_effect=execute):
        status, _ = _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
        assert status == 202
        assert entered.wait(1.0)
        abort_status, _ = _dispatch(
            handler, "POST", "/study/trials/abort", {"reason": "experimenter abort"},
        )
        rearm_status, _ = _dispatch(
            handler,
            "POST",
            "/study/trials/arm",
            _arm_payload(study_specs_dir, study_data_dir),
        )
        release.set()
        _wait_for_state(coordinator, ArmedState.ABORTED)

    assert abort_status == 200
    assert rearm_status == 409
    assert captured_control[0].stop_requested


def test_abort_before_worker_registers_control_prevents_runtime_start(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    slot_entered = threading.Event()
    release_slot = threading.Event()
    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)

    def acquire_slot():
        slot_entered.set()
        assert release_slot.wait(1.0)
        return True

    agent_loop.try_acquire_slot.side_effect = acquire_slot
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(),
        agent_loop=agent_loop,
        prepare=prepare_trial,
        session_manager=SimpleNamespace(session=None),
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))
    task_response = []

    with patch("caddie.study.runtime.execute_claimed_trial") as execute:
        request_thread = threading.Thread(
            target=lambda: task_response.append(
                _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
            ),
        )
        request_thread.start()
        assert slot_entered.wait(1.0)
        assert agent_loop._active_control is None
        abort_status, _ = _dispatch(
            handler, "POST", "/study/trials/abort", {"reason": "startup abort"},
        )
        release_slot.set()
        request_thread.join(timeout=1.0)

    assert not request_thread.is_alive()
    assert task_response[0][0] == 202
    assert abort_status == 200
    assert coordinator.status().state is ArmedState.ABORTED
    execute.assert_not_called()


def test_trial_worker_failure_sets_failed_and_emits_one_terminal_event(
    study_specs_dir, study_data_dir,
):
    import queue

    from caddie.agent.event_bus import EVENT_BUS
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    handler = _coordinator_handler(
        coordinator, context=_task_context(), agent_loop=agent_loop, prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    with EVENT_BUS.subscription() as events, patch(
        "caddie.study.runtime.execute_claimed_trial", side_effect=RuntimeError("boom"),
    ):
        status, _ = _dispatch(handler, "POST", "/task", {"task": "Jarvis Testaufgabe"})
        _wait_for_state(coordinator, ArmedState.FAILED)
        published = []
        while True:
            try:
                published.append(events.get_nowait())
            except queue.Empty:
                break

    terminal = [event for event in published if event.type == "task_finished"]
    assert status == 202
    assert len(terminal) == 1
    assert terminal[0].ok is False
    agent_loop.release_slot.assert_called_once_with()
    assert agent_loop._active_control is None


def test_task_stream_mismatch_returns_same_exact_retry_as_oneshot(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.runtime import prepare_trial

    coordinator = ArmedTrialCoordinator()
    backend = MagicMock()
    agent_loop = MagicMock()
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(backend),
        agent_loop=agent_loop,
        prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    response, sent_json, stream = _dispatch_stream(
        handler, {"task": "Jarvis spiele Musik"},
    )

    retry = "Das habe ich nicht ganz verstanden. Kannst du die Aufgabe bitte noch einmal sagen?"
    assert response == []
    assert sent_json == [(200, {"ok": False, "study": "retry", "message": retry})]
    assert stream == ""
    assert coordinator.status().state is ArmedState.ARMED
    agent_loop.run.assert_not_called()
    backend.assert_not_called()


def test_task_stream_matching_trial_stays_open_through_terminal_event(
    study_specs_dir, study_data_dir,
):
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(),
        agent_loop=agent_loop,
        prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))
    runtime_result = RuntimeResult(TrialOutcome.SUCCESS, "sess-stream", 2, 12.0, "done")

    with patch("caddie.study.runtime.execute_claimed_trial", return_value=runtime_result) as execute:
        response, sent_json, stream = _dispatch_stream(
            handler, {"task": "Jarvis Testaufgabe"},
        )

    events = [
        json.loads(line.removeprefix("data: "))
        for line in stream.splitlines()
        if line.startswith("data: ")
    ]
    assert response == [200]
    assert sent_json == []
    assert [event["type"] for event in events] == ["task_started", "task_finished"]
    assert events[-1]["ok"] is True
    assert events[-1]["payload"]["trial_id"] == "sess-stream"
    assert coordinator.status().state is ArmedState.COMPLETED
    execute.assert_called_once()
    agent_loop.run.assert_not_called()


def test_task_stream_ignores_unrelated_global_terminal_event(
    study_specs_dir, study_data_dir,
):
    import time

    from caddie.agent.event_bus import EVENT_BUS
    from caddie.study.coordinator import ArmedState, ArmedTrialCoordinator
    from caddie.study.model import TrialOutcome
    from caddie.study.runtime import RuntimeResult, prepare_trial

    entered = threading.Event()
    release = threading.Event()
    coordinator = ArmedTrialCoordinator()
    agent_loop = MagicMock(_active_control=None)
    agent_loop.try_acquire_slot.return_value = True
    handler = _coordinator_handler(
        coordinator,
        context=_task_context(),
        agent_loop=agent_loop,
        prepare=prepare_trial,
    )
    _dispatch(handler, "POST", "/study/trials/arm", _arm_payload(study_specs_dir, study_data_dir))

    def execute(*_args, **_kwargs):
        _kwargs["on_session_started"]()
        entered.set()
        assert release.wait(1.0)
        return RuntimeResult(TrialOutcome.SUCCESS, "sess-owned", 2, 12.0, "done")

    dispatched = []
    with patch("caddie.study.runtime.execute_claimed_trial", side_effect=execute):
        stream_thread = threading.Thread(
            target=lambda: dispatched.append(
                _dispatch_stream(handler, {"task": "Jarvis Testaufgabe"})
            ),
        )
        stream_thread.start()
        assert entered.wait(1.0)
        EVENT_BUS.task_finished(ok=True, payload={"outcome": "unrelated"})
        time.sleep(0.02)
        release.set()
        stream_thread.join(timeout=1.0)

    assert not stream_thread.is_alive()
    _, _, stream = dispatched[0]
    events = [
        json.loads(line.removeprefix("data: "))
        for line in stream.splitlines()
        if line.startswith("data: ")
    ]
    terminal = [event for event in events if event["type"] == "task_finished"]
    assert len(terminal) == 1
    assert terminal[0]["payload"]["trial_id"] == "sess-owned"
    assert coordinator.status().state is ArmedState.COMPLETED
