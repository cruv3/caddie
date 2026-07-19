"""Tests for caddie.study.oversight — C1/C2/C3 gate logic."""

import pathlib
import tempfile
from unittest.mock import MagicMock

from caddie.study.logger import StudyLogger, EventType
from caddie.study.model import StudyCondition, StudyStep, StepType, CriticalityClass
from caddie.study.oversight import OversightManager, OversightDecision


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_logger(base_dir):
    return StudyLogger(base_dir=base_dir, study_version="v1", participant_id="P01", session_id="s1")


def _step(action, **kwargs):
    return StudyStep(
        id=f"s_{action.replace(' ', '_')}",
        action=action,
        narration=f"Narration for {action}",
        step_type=StepType.NORMAL,
        **kwargs,
    )


def _make_manager(condition, cancelled=False, steps=None):
    base_dir = pathlib.Path(tempfile.mkdtemp())
    logger = StudyLogger(
        base_dir=base_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test",
        condition=condition.value,
    )
    mgr = OversightManager(logger=logger, condition=condition)
    if cancelled:
        mgr.cancel()
    return mgr, logger


# ---------------------------------------------------------------------------
# C1 — Stepwise
# ---------------------------------------------------------------------------


def test_c1_confirm_step():
    """In C1, confirm_step accepts the step and logs events."""
    mgr, logger = _make_manager(StudyCondition.STEPWISE)
    step = _step("click 'Send'")
    decision = mgr.confirm_consequential_step(step, step.narration)
    assert decision.confirmed is True
    assert decision.cancelled is False
    assert decision.declined is False
    # Two events logged: CONFIRMATION_SHOWN + CONFIRMATION_RESOLVED
    events = logger.get_raw_events()
    assert len(events) == 2
    assert events[0].event_type == EventType.CONFIRMATION_SHOWN
    assert events[1].event_type == EventType.CONFIRMATION_RESOLVED
    assert events[1].details["decision"] == "confirmed"


def test_c1_confirm_step_with_cancelled_manager():
    """If manager is already cancelled, confirm_step returns cancelled=True."""
    mgr, _ = _make_manager(StudyCondition.STEPWISE, cancelled=True)
    step = _step("click 'Send'")
    decision = mgr.confirm_consequential_step(step, step.narration)
    assert decision.cancelled is True
    assert decision.confirmed is False


def test_c1_multiple_steps():
    """In C1, each step independently generates its own gate events."""
    mgr, logger = _make_manager(StudyCondition.STEPWISE)
    step1 = _step("open com.android.settings")
    step2 = _step("click 'Send'")

    mgr.confirm_consequential_step(step1, step1.narration)
    mgr.confirm_consequential_step(step2, step2.narration)

    events = logger.get_raw_events()
    # 2 steps × 2 events each = 4
    assert len(events) == 4
    assert events[0].event_type == EventType.CONFIRMATION_SHOWN
    assert events[0].step_id == "s_open_com.android.settings"
    assert events[2].event_type == EventType.CONFIRMATION_SHOWN
    assert events[2].step_id == "s_click_'Send'"


def test_c1_is_cancelled():
    """is_cancelled() returns False initially."""
    mgr, _ = _make_manager(StudyCondition.STEPWISE)
    assert mgr.is_cancelled() is False

    mgr.cancel()
    assert mgr.is_cancelled() is True


# ---------------------------------------------------------------------------
# C2 — Final checkpoint (batch)
# ---------------------------------------------------------------------------


def test_c2_defers_to_batch():
    """In C2, confirm_step queues steps and returns confirmed=True."""
    mgr, logger = _make_manager(StudyCondition.FINAL_CHECKPOINT)
    step = _step("click 'Send'")
    decision = mgr.confirm_consequential_step(step, step.narration)
    assert decision.confirmed is True
    # No gate events yet — step is queued, not confirmed
    assert len(logger.get_raw_events()) == 0
    assert len(mgr.get_pending_steps()) == 1
    assert mgr.get_pending_steps()[0].id == "s_click_'Send'"


def test_c2_show_summary_accepts():
    """C2 show_summary presents the batch and logs events."""
    mgr, logger = _make_manager(StudyCondition.FINAL_CHECKPOINT)
    step1 = _step("open com.android.settings")
    step2 = _step("click 'Send'")
    mgr.confirm_consequential_step(step1, step1.narration)
    mgr.confirm_consequential_step(step2, step2.narration)

    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.confirmed is True
    events = logger.get_raw_events()
    assert len(events) == 2  # CONFIRMATION_SHOWN + CONFIRMATION_RESOLVED (one event per batch, not per step)
    assert events[0].event_type == EventType.CONFIRMATION_SHOWN
    assert events[0].step_id == "<batch>"
    assert "(batch_consequential, 2 steps)" in events[0].narration
    assert events[1].event_type == EventType.CONFIRMATION_RESOLVED
    assert events[1].details["decision"] == "confirmed"


def test_c2_empty_batch():
    """C2 with no pending steps returns confirmed immediately."""
    mgr, logger = _make_manager(StudyCondition.FINAL_CHECKPOINT)
    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.confirmed is True
    # No events for empty batch
    assert len(logger.get_raw_events()) == 0


def test_c2_cancel_during_batch():
    """If cancelled before batch, show_summary returns cancelled."""
    mgr, _ = _make_manager(StudyCondition.FINAL_CHECKPOINT, cancelled=True)
    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.cancelled is True
    assert decision.confirmed is False


def test_c2_clear_pending():
    """clear_pending_steps empties the queue."""
    mgr, _ = _make_manager(StudyCondition.FINAL_CHECKPOINT)
    mgr.confirm_consequential_step(_step("click 'Send'"), _step("click 'Send'").narration)
    assert len(mgr.get_pending_steps()) == 1
    mgr.clear_pending_steps()
    assert len(mgr.get_pending_steps()) == 0


# ---------------------------------------------------------------------------
# C3 — Voluntary intervention
# ---------------------------------------------------------------------------


def test_c3_no_gates():
    """In C3, confirm_step always passes through without logging."""
    mgr, logger = _make_manager(StudyCondition.VOLUNTARY_INTERVENTION)
    step = _step("click 'Send'")
    decision = mgr.confirm_consequential_step(step, step.narration)
    assert decision.confirmed is True
    # No events in C3
    assert len(logger.get_raw_events()) == 0


def test_c3_multiple_steps():
    """C3 allows arbitrary number of steps without logging."""
    mgr, logger = _make_manager(StudyCondition.VOLUNTARY_INTERVENTION)
    for i in range(5):
        s = _step(f"action {i}"); mgr.confirm_consequential_step(s, s.narration)
    assert len(logger.get_raw_events()) == 0


def test_c3_show_summary():
    """C3 show_summary returns confirmed immediately."""
    mgr, logger = _make_manager(StudyCondition.VOLUNTARY_INTERVENTION)
    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.confirmed is True
    assert len(logger.get_raw_events()) == 0


# ---------------------------------------------------------------------------
# Mixed condition transitions
# ---------------------------------------------------------------------------


def test_c1_to_c2_pending_empty():
    """C1 show_summary returns confirmed without queueing."""
    mgr, logger = _make_manager(StudyCondition.STEPWISE)
    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.confirmed is True
    assert len(logger.get_raw_events()) == 0


def test_manager_repr():
    """__repr__ shows condition and cancelled state."""
    mgr, _ = _make_manager(StudyCondition.STEPWISE)
    assert repr(mgr) == "OversightManager(condition='c1_stepwise', cancelled=False)"
    mgr.cancel()
    assert repr(mgr) == "OversightManager(condition='c1_stepwise', cancelled=True)"


# ---------------------------------------------------------------------------
# _extract_action_description
# ---------------------------------------------------------------------------


def test_extract_action_open_app():
    from caddie.study.oversight import _extract_action_description
    assert _extract_action_description("open com.android.settings") == "open com.android.settings"


def test_extract_action_tap():
    from caddie.study.oversight import _extract_action_description
    assert _extract_action_description("click 'Send'") == "tap Send"


def test_extract_action_type():
    from caddie.study.oversight import _extract_action_description
    assert _extract_action_description("input text 'Hello' (submit)") == "type Hello"


def test_extract_action_scroll():
    from caddie.study.oversight import _extract_action_description
    assert _extract_action_description("scroll down") == "scroll down"


# ---------------------------------------------------------------------------
# Oversight callbacks (Android bridge integration)
# ---------------------------------------------------------------------------


def test_step_callback_declines():
    """A step_callback that declines results in a declined decision."""
    mgr, logger = _make_manager(StudyCondition.STEPWISE)

    def decline_callback(step, narration):
        return OversightDecision(declined=True, reason="User declined")

    mgr._step_callback = decline_callback
    step = _step("open com.android.settings")
    decision = mgr.confirm_consequential_step(step, step.narration)
    assert decision.declined is True
    assert decision.reason == "User declined"
    events = logger.get_raw_events()
    assert len(events) == 2
    assert events[1].details["decision"] == "declined"


def test_batch_callback_declines():
    """A batch_callback that declines results in a declined batch decision."""
    mgr, logger = _make_manager(StudyCondition.FINAL_CHECKPOINT)

    def decline_batch(steps):
        return OversightDecision(declined=True, reason="Batch declined")

    mgr._batch_callback = decline_batch
    mgr.confirm_consequential_step(_step("open settings"), "")
    mgr.confirm_consequential_step(_step("click 'Send'"), "")
    decision = mgr.show_c2_summary(mgr.get_pending_steps())
    assert decision.declined is True
    events = logger.get_raw_events()
    assert len(events) == 2
    assert events[1].details["decision"] == "declined"


# ---------------------------------------------------------------------------
# Cancellation fall-through (not accepted as confirmed)
# ---------------------------------------------------------------------------


def test_c1_cancellation_not_confirmed(tmp_path):
    """When C1 callback returns cancelled=True, the gate returns cancelled, not confirmed."""
    logger = make_logger(tmp_path)

    def cancel_callback(step, narration):
        return OversightDecision(cancelled=True)

    mgr = OversightManager(logger, StudyCondition.STEPWISE, step_callback=cancel_callback)
    decision = mgr.confirm_consequential_step(_step("test"), "Test step")

    assert decision.cancelled is True
    assert decision.confirmed is False
    assert mgr.is_cancelled() is True
    # Only one confirmation_resolved log (from gate), not duplicated
    events = logger.get_raw_events()
    resolved = [e for e in events if e.event_type == "confirmation_resolved"]
    assert len(resolved) == 1
    assert resolved[0].details["decision"] == "cancelled"
