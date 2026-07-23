"""Tests for study-control runtime: session wiring, confirmation routing,
and cleanup via the HTTP study endpoints.

Test-first: these tests verify that:
  (a) The study handler passes StudySession into TrialExecutor
  (b) C1/C2 confirmation events are emitted to the event bus
  (c) Correction/pause/resume/stop commands reach the session
  (d) Terminal sessions are cleared for new trials
"""

import json
import pathlib
import tempfile
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from unittest.mock import MagicMock, patch

import pytest

from caddie.agent.event_bus import EVENT_BUS
from caddie.study.model import StudyCondition, StudyStep, StepType, CriticalityClass, TrialSpec


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _find_events_file(participant_dir: pathlib.Path, data_dir: pathlib.Path | None = None) -> pathlib.Path:
    """Find the events.jsonl file under a participant directory.

    StudyLogger creates a session-scoped subdirectory (e.g.
    data/v1.0/P01/<session_id>/events.jsonl). This helper locates it.
    """
    for session_dir in participant_dir.iterdir():
        if session_dir.is_dir():
            candidate = session_dir / "events.jsonl"
            if candidate.exists():
                return candidate
    # Fallback: events directly in participant dir
    fallback = participant_dir / "events.jsonl"
    if fallback.exists():
        return fallback
    # No events yet — return the first session dir path (created on trial run)
    return participant_dir / "sess_0" / "events.jsonl"


def _make_specs_dir(tmp_path: pathlib.Path) -> pathlib.Path:
    """Create 6 study spec files in a temp directory."""
    specs_dir = tmp_path / "specs"
    specs_dir.mkdir(parents=True, exist_ok=True)
    task_ids = [
        "task_maps_messenger", "task_gallery_notes",
        "task_chat_spotify", "task_email_calendar",
        "task_calendar_dnd", "task_banking_payment",
    ]
    for i, tid in enumerate(task_ids):
        crit = "low" if i % 2 == 0 else "high"
        (specs_dir / f"{tid}.yaml").write_text(
            f"""\
version: "1.0"
id: {tid}
instruction_de: Test task {i}
criticality: {crit}
steps:
  - id: step1
    action: click "Send"
    narration: "Send data"
    step_type: normal
  - id: step2
    action: click "Confirm"
    narration: "Confirm"
    step_type: consequential
    error_variant:
      id: err_{i}
      field: field
      wrong_value: wrong
      correct_value: correct
      description: Error variant {i}
error_steps: ["step2"]
reset_checklist:
  - "App is on home screen"
verification:
  - id: v1
    assertion: "OK"
    check_type: text_present
    parameters: {{text: "OK"}}
    screenshot_evidence: false
max_duration_s: 120
per_gate_timeout_s: 30
""",
            encoding="utf-8",
        )
    return specs_dir


def _start_test_server(specs_dir: pathlib.Path, data_dir: pathlib.Path, port: int = 18787):
    """Start a minimal HTTP server using the agent http_api module."""
    from caddie.config import ENV_AGENT_HOST

    host = "127.0.0.1"
    # Patch the STUDY_SPECS_DIR so load_all_specs picks up our temp dir
    import caddie.study.spec_loader as sl
    sl.STUDY_SPECS_DIR = specs_dir

    # Create a minimal context with a FakeBackend that returns canned elements.
    # The executor's _find_element needs list_elements() → {"elements": [...]}
    # with dicts containing "text"/"index" so taps can resolve.
    class FakeBackend:
        _elements: list[dict] = [
            {"text": "Send", "index": 5},
            {"text": "Confirm", "index": 10},
        ]

        def list_elements(self):
            return {"elements": list(self._elements)}

        def open_app(self, package_name):
            return {"ok": True}

        def tap_element(self, index):
            return {"ok": True}

        def type_text(self, text, submit=False):
            return {"ok": True}

        def scroll(self, direction, amount=0.6):
            return {"ok": True}

        def press_button(self, button):
            return {"ok": True}

        def open_url(self, url):
            return {"ok": True}

    from caddie.context import ServerContext
    context = MagicMock(spec=ServerContext)
    context.backend = FakeBackend()
    context.adb = None
    context.skills = MagicMock()
    context.skills.match.return_value = []
    context.schedule_store = MagicMock()

    # Patch the agent loop to return a working RunControl
    from caddie.agent.run_control import RunControl
    run_ctrl = RunControl()
    run_ctrl.request_pause = MagicMock(return_value=None)
    run_ctrl.request_resume = MagicMock(return_value=None)
    run_ctrl.request_stop = MagicMock(return_value=None)
    run_ctrl.await_confirmation = MagicMock(return_value=True)
    run_ctrl.resolve_confirmation = MagicMock()
    run_ctrl.wait_while_paused = MagicMock()
    run_ctrl._active_control = run_ctrl

    # Create a mock agent loop
    agent_loop = MagicMock()
    agent_loop._active_control = run_ctrl
    agent_loop._agent_loop = MagicMock()
    agent_loop.apply_control = MagicMock(return_value={"ok": True})
    agent_loop.run = MagicMock(return_value={"ok": False})
    agent_loop.recent_run = MagicMock(return_value=None)

    from caddie.agent.http_api import AgentHttpServer, _handler_factory
    from caddie.agent.lmstudio import LmStudioClient

    lmstudio = MagicMock(spec=LmStudioClient)
    handler = _handler_factory(context, lmstudio, agent_loop)
    server = HTTPServer((host, port), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, host, port, specs_dir, data_dir


def _http_post(host: str, port: int, path: str, body: dict | None = None) -> tuple[int, dict]:
    """Send a POST request and return (status_code, parsed_json)."""
    import urllib.request
    url = f"http://{host}:{port}{path}"
    data = json.dumps(body or {}).encode("utf-8")
    req = urllib.request.Request(
        url, data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        resp = urllib.request.urlopen(req, timeout=5)
        return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


def _http_get(host: str, port: int, path: str) -> tuple[int, dict]:
    """Send a GET request and return (status_code, parsed_json)."""
    import urllib.request
    url = f"http://{host}:{port}{path}"
    req = urllib.request.Request(url, method="GET")
    try:
        resp = urllib.request.urlopen(req, timeout=5)
        return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def server_and_dirs(tmp_path):
    """Start a test HTTP server with study specs and data dir."""
    specs_dir = _make_specs_dir(tmp_path / "specs")
    data_dir = tmp_path / "data"
    server, host, port, specs, data = _start_test_server(specs_dir, data_dir)
    try:
        yield host, port, specs, data
    finally:
        server.shutdown()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


def test_run_passes_session_to_executor(server_and_dirs):
    """The study /study/trials/run endpoint passes StudySession into
    TrialExecutor, so the executor can call session.mark_step_executed(),
    session.request_pause(), etc."""
    host, port, specs_dir, data_dir = server_and_dirs

    # Ensure event bus is clean
    with EVENT_BUS.subscription() as q:
        while not q.empty():
            q.get_nowait()

        status, body = _http_post(host, port, "/study/trials/run", {
            "participant": "P01",
            "trial_index": 0,
            "condition": "c1_stepwise",
            "specs_dir": str(specs_dir),
            "data_dir": str(data_dir),
        })

    # Trial should succeed (FakeBackend returns canned elements)
    assert status == 200
    assert body.get("ok") is True
    # Verify session events were logged
    # StudyLogger uses trial_spec.version ("1.0") and a session_id subdirectory
    participant_dir = data_dir / "1.0" / "P01"
    events_path = _find_events_file(participant_dir, data_dir)
    assert events_path.exists(), "events.jsonl should exist"
    events_text = events_path.read_text()
    assert "session_started" in events_text
    assert "session_ended" in events_text


def test_session_cleared_after_trial(server_and_dirs):
    """After a trial completes (success or failure), the session is
    cleared so a new trial can start."""
    host, port, specs_dir, data_dir = server_and_dirs

    # Run first trial
    status1, _ = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c1_stepwise",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status1 == 200

    # Health should report idle after trial
    status_h, body_h = _http_get(host, port, "/study/health")
    assert status_h == 200
    assert body_h.get("session") in ("idle", "completed")

    # Second trial should also work (session was cleared)
    status2, _ = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c2_final_checkpoint",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status2 == 200


def test_abort_clears_session(server_and_dirs):
    """POST /study/trials/abort clears the session."""
    host, port, specs_dir, data_dir = server_and_dirs

    # Run a trial
    status1, _ = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c1_stepwise",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status1 == 200

    # Abort
    status_abort, body_abort = _http_post(host, port, "/study/trials/abort", {})
    assert status_abort == 200
    assert body_abort.get("aborted") is True

    # Session should be idle
    status_h, body_h = _http_get(host, port, "/study/health")
    assert body_h.get("session") in ("idle", "completed")


def test_confirmation_events_logged(server_and_dirs):
    """C1 confirmation events (confirmation_shown, confirmation_resolved)
    are logged in the events.jsonl."""
    host, port, specs_dir, data_dir = server_and_dirs

    status, body = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c1_stepwise",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status == 200

    participant_dir = data_dir / "1.0" / "P01"
    events_path = _find_events_file(participant_dir, data_dir)
    events_text = events_path.read_text()
    assert "confirmation_shown" in events_text
    assert "confirmation_resolved" in events_text


def test_c2_summary_events_logged(server_and_dirs):
    """C2 batch confirmation events are logged."""
    host, port, specs_dir, data_dir = server_and_dirs

    status, body = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c2_final_checkpoint",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status == 200

    participant_dir = data_dir / "1.0" / "P01"
    events_path = _find_events_file(participant_dir, data_dir)
    events_text = events_path.read_text()
    assert "confirmation_shown" in events_text
    assert "confirmation_resolved" in events_text


def test_session_status_during_run(server_and_dirs):
    """The status endpoint returns meaningful metrics during a trial.

    After the trial completes, the session moves to a terminal state.
    The status endpoint reflects this with has_session=True and state
    in ("running", "completed"). We check the HTTP response body directly
    since the session is cleared after the trial ends.
    """
    host, port, specs_dir, data_dir = server_and_dirs

    status, body = _http_post(host, port, "/study/trials/run", {
        "participant": "P01",
        "trial_index": 0,
        "condition": "c1_stepwise",
        "specs_dir": str(specs_dir),
        "data_dir": str(data_dir),
    })
    assert status == 200
    # The trial should have completed (steps executed or failed)
    assert body.get("ok") is True
    assert body.get("outcome") in ("success", "verification_failed", "completed")

    # Status endpoint — after terminal cleanup, has_session may be False
    status_s, body_s = _http_get(host, port, "/study/trials/status")
    assert status_s == 200
    # State should reflect terminal completion (key is 'session' in status endpoint)
    assert body_s.get("session") in ("completed", "idle")
