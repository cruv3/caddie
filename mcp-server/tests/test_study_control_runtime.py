"""Tests for study-control runtime event-wiring.

Verifies that the backend properly wires:
  (1) C1/C2 confirmation events through EventBus before awaiting
  (2) Current-action narration events for each visible study step
  (3) Active StudySession passed into TrialExecutor
  (4) Confirm/decline/pause/resume/stop/correction reach session/control
  (5) Completed/aborted trial clears SessionManager for next trial

These tests are intentionally written to initially fail against the
unfixed code and then prove the wiring once the fixes land.
"""

from __future__ import annotations

import json
import pathlib
import tempfile
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from unittest.mock import MagicMock, patch

import pytest

from caddie.agent.event_bus import EVENT_BUS, ToolEvent
from caddie.study.oversight import OversightManager
from caddie.study.model import (
    StudyCondition,
    StudyStep,
    StepType,
    CriticalityClass,
    TrialSpec,
)
from caddie.study.session import SessionManager


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _make_tmp_logger():
    """Create a StudyLogger in a temp directory."""
    from caddie.study.logger import StudyLogger

    base_dir = pathlib.Path(tempfile.mkdtemp())
    return StudyLogger(
        base_dir=base_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test",
        condition="c1_stepwise",
    )


def _make_step(action: str, step_type=StepType.NORMAL, **kwargs) -> StudyStep:
    return StudyStep(
        id=f"s_{action.replace(' ', '_')}",
        action=action,
        narration=f"Narration for {action}",
        step_type=step_type,
        **kwargs,
    )


# ---------------------------------------------------------------------------
# EventBus drain helper
# ---------------------------------------------------------------------------


def _drain_bus_events():
    """Return all ToolEvent dicts that were published on the EventBus
    during the current subscription window. Call this to capture events
    that were published since the last drain. The subscription must be
    active while the target code runs.

    Usage:
        q = _drain_bus_events()
        # ... code that publishes events ...
        events = q.finish()
    """
    import queue
    q: queue.Queue = queue.Queue(maxsize=1024)
    with EVENT_BUS._lock:
        EVENT_BUS._subscribers.append(q)
    try:
        return _BusDrain(q)
    except Exception:
        with EVENT_BUS._lock:
            if q in EVENT_BUS._subscribers:
                EVENT_BUS._subscribers.remove(q)
        raise


class _BusDrain:
    def __init__(self, q):
        self._q = q

    def finish(self) -> list[dict]:
        """Drain all queued events and close subscription."""
        events: list[dict] = []
        while True:
            try:
                ev = self._q.get_nowait()
                if ev is not None:
                    events.append(ev.to_dict())
            except Exception:
                break
        with EVENT_BUS._lock:
            if self._q in EVENT_BUS._subscribers:
                EVENT_BUS._subscribers.remove(self._q)
        return events


# ---------------------------------------------------------------------------
# Tests: EventBus event publishing
# ---------------------------------------------------------------------------


def test_c1_emits_confirmation_required_to_event_bus():
    """A C1 consequential step emits confirmation_required to EventBus
    before awaiting the step callback."""
    from caddie.study.logger import StudyLogger
    from caddie.study.oversight import OversightManager
    from caddie.study.model import StudyCondition

    logger = _make_tmp_logger()
    mgr = OversightManager(
        logger=logger,
        condition=StudyCondition.STEPWISE,
        step_callback=lambda s, n: OversightManager._decision_from_bool(True),
    )
    step = _make_step("open com.android.settings")
    drain = _drain_bus_events()
    mgr.confirm_consequential_step(step, step.narration)

    # EventBus should have received a confirmation_required event
    events = drain.finish()
    types = [e.get("type", "") for e in events]
    assert "confirmation_required" in types, (
        f"confirmation_required missing from bus events: {types}"
    )
    # Find the confirmation_required event and check payload has participant
    for e in events:
        if e.get("type") == "confirmation_required":
            payload = e.get("payload", {})
            # Payload should contain participant info
            assert "participant" in payload, (
                f"confirmation_required payload missing 'participant': {e}"
            )
            assert "task" in payload or "step_id" in payload, (
                f"confirmation_required payload missing task/step_id: {e}"
            )
            break
    else:
        pytest.fail("confirmation_required event not found")


def test_c1_emits_confirmation_resolved_to_event_bus():
    """After the step callback resolves, EventBus receives
    confirmation_resolved."""
    from caddie.study.logger import StudyLogger
    from caddie.study.oversight import OversightManager
    from caddie.study.model import StudyCondition

    logger = _make_tmp_logger()
    mgr = OversightManager(
        logger=logger,
        condition=StudyCondition.STEPWISE,
        step_callback=lambda s, n: OversightManager._decision_from_bool(True),
    )
    step = _make_step("click 'Send'")
    drain = _drain_bus_events()
    mgr.confirm_consequential_step(step, step.narration)

    events = drain.finish()
    types = [e.get("type", "") for e in events]
    assert "confirmation_resolved" in types, (
        f"confirmation_resolved missing from bus events: {types}"
    )
    for e in events:
        if e.get("type") == "confirmation_resolved":
            assert e.get("ok") is True, "confirmation_resolved.ok should be True"
            break


def test_current_action_narration_emitted_to_event_bus():
    """Each visible step emits a 'current_action' (or 'narration') event
    through EventBus before executing."""
    from caddie.study.executor import TrialExecutor
    from caddie.study.oversight import OversightManager, OversightDecision
    from caddie.study.session import StudySession

    logger = _make_tmp_logger()
    oversight = OversightManager(
        logger=logger,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )

    steps = [
        _make_step("open com.android.settings", step_type=StepType.NORMAL),
        _make_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True),
    ]
    spec = TrialSpec(
        version="v1",
        id="task_test",
        instruction_de="Test",
        criticality=CriticalityClass.HIGH,
        steps=tuple(steps),
    )

    # Create a session so executor can use it
    from caddie.study.session import StudySession as SS
    from caddie.agent.run_control import RunControl
    rc = RunControl()
    session = SS(logger=logger, run_control=rc, oversight_manager=oversight)
    session.start()

    # Use FakeBackend
    class FakeBackend:
        def __init__(self):
            self._elements = [{"text": "Send", "index": 5}]
            self.calls = []

        def list_elements(self):
            return {"elements": list(self._elements)}

        def open_app(self, package_name):
            self.calls.append(("open_app", package_name))
            return {"ok": True}

        def tap_element(self, index):
            self.calls.append(("tap_element", index))
            return {"ok": True}

        def type_text(self, text, submit=False):
            self.calls.append(("type", text))
            return {"ok": True}

        def scroll(self, direction, amount=0.6):
            self.calls.append(("scroll", direction))
            return {"ok": True}

        def press_button(self, button):
            self.calls.append(("press", button))
            return {"ok": True}

        def open_url(self, url):
            self.calls.append(("open_url", url))
            return {"ok": True}

    backend = FakeBackend()
    executor = TrialExecutor(
        backend=backend,
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
        session=session,
    )
    drain = _drain_bus_events()
    result = executor.run()

    # Check that EventBus received narration events
    events = drain.finish()
    types = [e.get("type", "") for e in events]

    # At minimum we expect a 'current_action' or similar narration event
    narration_event_types = [
        t for t in types
        if t in ("current_action", "narration", "step_narration")
    ]
    assert len(narration_event_types) >= 1, (
        f"Expected at least one narration event, got types: {types}"
    )
    # Verify session is threaded through
    assert result.steps_done >= 1, f"Expected steps executed, got {result.steps_done}"


def test_executor_receives_session():
    """TrialExecutor is created with an active StudySession so it can
    call mark_step_executed, request_pause, etc."""
    from caddie.study.executor import TrialExecutor

    logger = _make_tmp_logger()
    oversight = OversightManager(
        logger=logger,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )
    steps = [_make_step("open com.android.settings")]
    spec = TrialSpec(
        version="v1",
        id="task_test",
        instruction_de="Test",
        criticality=CriticalityClass.HIGH,
        steps=tuple(steps),
    )

    from caddie.agent.run_control import RunControl
    from caddie.study.session import StudySession as SS
    rc = RunControl()
    session = SS(logger=logger, run_control=rc, oversight_manager=oversight)
    session.start()

    class FakeBackend:
        def list_elements(self):
            return {"elements": []}
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

    executor = TrialExecutor(
        backend=FakeBackend(),
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
        session=session,
    )

    # The executor should have the session attached
    assert executor._session is session, "Session not passed to executor"


# ---------------------------------------------------------------------------
# Tests: control commands reach session
# ---------------------------------------------------------------------------


def test_confirm_reaches_session():
    """session.resolve_confirmation(True) updates RunControl state."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import StudySession

    logger = _make_tmp_logger()
    rc = RunControl()
    om = MagicMock()
    session = StudySession(logger, rc, om)
    session.start()
    session.resolve_confirmation(True)
    assert rc._confirm_approved is True


def test_agent_control_confirm_falls_back_to_active_study_session():
    """HTTP /control confirm reaches study-only runs without an AgentLoop run."""
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.run_control import RunControl

    manager = SessionManager.instance()
    manager.clear()
    logger = _make_tmp_logger()
    run_control = RunControl()
    session = manager.create(
        logger=logger,
        run_control=run_control,
        oversight_manager=MagicMock(),
    )
    session.start()

    loop = AgentLoop.__new__(AgentLoop)
    loop._active_control = None
    loop._voice_hold = False
    loop._events = MagicMock()

    try:
        result = loop.apply_control("confirm")
    finally:
        manager.clear()

    assert result == {"ok": True, "action": "confirm", "state": "running"}
    assert run_control._confirm_approved is True


def test_decline_reaches_session():
    """session.resolve_confirmation(False) → await_confirmation returns False."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import StudySession

    logger = _make_tmp_logger()
    rc = RunControl()
    session = StudySession(logger, rc, MagicMock())
    session.start()
    session.resolve_confirmation(False)
    assert session.wait_for_confirmation(timeout=0.5) is False


def test_pause_and_resume_reach_session():
    """pause/resume transitions the session state correctly."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import StudySession, SessionState

    logger = _make_tmp_logger()
    rc = RunControl()
    session = StudySession(logger, rc, MagicMock())
    session.start()
    assert session.state is SessionState.RUNNING
    session.pause()
    assert session.state is SessionState.PAUSED
    session.resume()
    assert session.state is SessionState.RUNNING


def test_stop_clears_session():
    """request_stop or complete marks session terminal."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import StudySession, SessionState

    logger = _make_tmp_logger()
    rc = RunControl()
    session = StudySession(logger, rc, MagicMock())
    session.start()
    session.complete()
    assert session.is_terminal
    assert session.state is SessionState.COMPLETED


def test_correction_reaches_run_control():
    """set_correction stores text and flags intervention."""
    from caddie.agent.run_control import RunControl

    rc = RunControl()
    rc.set_correction("nimm das andere Restaurant")
    assert rc.take_correction() == "nimm das andere Restaurant"
    assert rc.has_intervention


# ---------------------------------------------------------------------------
# Tests: session cleanup after terminal
# ---------------------------------------------------------------------------


def test_completed_trial_clears_session_manager():
    """After trial.complete(), SessionManager.clear() allows a new trial."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import SessionManager, StudySession
    from caddie.study.oversight import OversightManager
    from caddie.study.model import StudyCondition

    logger = _make_tmp_logger()
    SessionManager().clear()

    rc = RunControl()
    om = OversightManager(logger, condition=StudyCondition.STEPWISE)
    session = StudySession(logger, rc, om)

    mgr = SessionManager()
    # Simulate creating a session via SessionManager
    mgr._session = session
    session.start()
    session.complete()

    # Session is terminal, but SessionManager still holds reference
    assert mgr.session is session
    assert mgr.session.is_terminal

    # After clear, a new session can be created
    mgr.clear()
    assert mgr.session is None

    # New session creation should succeed
    logger2 = _make_tmp_logger()
    om2 = OversightManager(logger2, condition=StudyCondition.STEPWISE)
    new_session = mgr.create(
        logger=logger2,
        run_control=RunControl(),
        oversight_manager=om2,
    )
    assert new_session is not None
    assert mgr.session is new_session


def test_aborted_trial_clears_session_manager():
    """After trial abort, SessionManager can accept a new session."""
    from caddie.agent.run_control import RunControl
    from caddie.study.session import SessionManager, StudySession
    from caddie.study.oversight import OversightManager
    from caddie.study.model import StudyCondition

    logger = _make_tmp_logger()
    SessionManager().clear()

    rc = RunControl()
    om = OversightManager(logger, condition=StudyCondition.STEPWISE)
    session = StudySession(logger, rc, om)

    mgr = SessionManager()
    mgr._session = session
    session.start()
    session.cancel()

    assert session.is_terminal or session.state.value == "cancelling"
    mgr.clear()
    assert mgr.session is None

    # New session should be creatable
    logger2 = _make_tmp_logger()
    om2 = OversightManager(logger2, condition=StudyCondition.STEPWISE)
    new_session = mgr.create(
        logger=logger2,
        run_control=RunControl(),
        oversight_manager=om2,
    )
    assert new_session is not None


# ---------------------------------------------------------------------------
# Tests: event payload richness
# ---------------------------------------------------------------------------


def test_confirmation_event_has_useful_payload():
    """confirmation_required payload contains participant/task/condition
    plus action detail."""
    from caddie.study.logger import StudyLogger
    from caddie.study.oversight import OversightManager
    from caddie.study.model import StudyCondition

    logger = _make_tmp_logger()
    mgr = OversightManager(
        logger=logger,
        condition=StudyCondition.STEPWISE,
        step_callback=lambda s, n: OversightManager._decision_from_bool(True),
    )
    drain = _drain_bus_events()
    step = _make_step("tap 'Send'")
    mgr.confirm_consequential_step(step, step.narration)

    events = drain.finish()
    for e in events:
        if e.get("type") == "confirmation_required":
            payload = e.get("payload", {})
            # Must have participant
            assert "participant" in payload
            # Must have task or step info
            assert "task" in payload or "step_id" in payload
            # Must have action detail
            assert "action" in payload or "description" in payload or "step_id" in payload
            break
    else:
        pytest.fail("confirmation_required event not found")


def test_narration_event_has_step_detail():
    """Current-action narration event includes step_id and narration text."""
    from caddie.study.executor import TrialExecutor
    from caddie.study.oversight import OversightManager
    from caddie.study.session import StudySession
    from caddie.study.model import StudyCondition, StepType

    logger = _make_tmp_logger()
    oversight = OversightManager(
        logger=logger,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )

    step = StudyStep(
        id="s_open",
        action="open com.android.settings",
        narration="Einstellungen öffnen",
        step_type=StepType.NORMAL,
    )
    spec = TrialSpec(
        version="v1",
        id="task_test",
        instruction_de="Test",
        criticality=CriticalityClass.HIGH,
        steps=(step,),
    )

    from caddie.agent.run_control import RunControl
    rc = RunControl()
    session = StudySession(logger, rc, oversight)
    session.start()

    class FB:
        def list_elements(self):
            return {"elements": []}
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

    executor = TrialExecutor(
        backend=FB(),
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
        session=session,
    )
    drain = _drain_bus_events()
    executor.run()

    events = drain.finish()
    narration_types = [
        e.get("type", "")
        for e in events
        if e.get("type") in ("current_action", "narration", "step_narration")
    ]
    assert len(narration_types) >= 1, f"Expected narration events, got: {narration_types}"

    # Find a narration event and check it has useful detail
    for e in events:
        if e.get("type") in ("current_action", "narration", "step_narration"):
            payload = e.get("payload", {})
            # Should contain step detail
            assert "step_id" in payload or "action" in payload or "narration" in payload
            break
