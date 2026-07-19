"""Tests for caddie.study.executor — deterministic step execution."""

import pathlib
from unittest.mock import MagicMock, patch

from caddie.study.executor import (
    TrialExecutor,
    TrialResult,
    ExecutionResult,
    StudyBackendProtocol,
    execute_trial,
    _parse_action,
    _find_element,
)
from caddie.study.oversight import OversightDecision
from caddie.study.logger import StudyLogger, EventType
from caddie.study.model import (
    TrialOutcome,
    TrialSpec,
    StudyCondition,
    StepType,
    CriticalityClass,
    StudyStep,
    ErrorVariant,
    VerificationRule,
)


# ---------------------------------------------------------------------------
# Test doubles
# ---------------------------------------------------------------------------


class FakeBackend:
    """Minimal backend that records calls and returns canned elements."""

    def __init__(self, elements=None):
        self._elements = elements or []
        self.calls = []

    def list_elements(self):
        return {"elements": list(self._elements)}

    def open_app(self, package_name):
        self.calls.append(("open_app", package_name))
        return {"ok": True}

    def open_url(self, url):
        self.calls.append(("open_url", url))
        return {"ok": True}

    def tap_element(self, index):
        self.calls.append(("tap_element", index))
        return {"ok": True}

    def scroll(self, direction, amount=0.6):
        self.calls.append(("scroll", direction, amount))
        return {"ok": True}

    def press_button(self, button):
        self.calls.append(("press", button))
        return {"ok": True}

    def type_text(self, text, submit=False):
        self.calls.append(("type", text, submit))
        return {"ok": True}

    def set_elements(self, elements):
        self._elements = elements


class FakeOversight:
    """Oversight that always accepts."""

    def __init__(self, accept=True, decline_step_ids=None, decline_summary=False, logger=None):
        self.accept = accept
        self.decline_step_ids = set(decline_step_ids or [])
        self.decline_summary = decline_summary
        self.confirmations = []
        self.summary_steps = []
        self.cancelled = False
        self._logger = logger

    def confirm_consequential_step(self, step, narration):
        if self._logger:
            self._logger.confirmation_shown(step.id, "Test step")
        self.confirmations.append((step.id, narration))
        if step.id in self.decline_step_ids:
            decision = OversightDecision(declined=True, reason=f"Step {step.id} declined")
        elif self.cancelled:
            decision = OversightDecision(cancelled=True)
        else:
            decision = OversightDecision(confirmed=True)
        if self._logger:
            self._logger.confirmation_resolved(
                step.id,
                accepted=decision.confirmed,
            )
        return decision

    def is_cancelled(self) -> bool:
        return self.cancelled

    def show_c2_summary(self, steps):
        self.summary_steps.extend(steps)
        if self.decline_summary:
            return OversightDecision(declined=True, reason="Summary declined")
        if self.cancelled:
            return OversightDecision(cancelled=True)
        return OversightDecision(confirmed=True)

    def is_cancelled(self):
        return self.cancelled


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _make_spec(steps=None, error_steps=None, verification=None):
    return TrialSpec(
        version="v1",
        id="task_test",
        instruction_de="Test task",
        criticality=CriticalityClass.HIGH,
        steps=steps or (),
        error_steps=error_steps or (),
        verification=verification or (),
    )


def _step(action, narration="Test step", step_type=StepType.NORMAL, **kwargs):
    return StudyStep(
        id=f"s_{action.replace(' ', '_')}",
        action=action,
        narration=narration,
        step_type=step_type,
        **kwargs,
    )


def _make_executor(
    backend=None,
    oversight=None,
    steps=None,
    error_steps=None,
    condition=StudyCondition.STEPWISE,
    spec=None,
    error_tasks=frozenset(),
):
    import tempfile
    base_dir = pathlib.Path(tempfile.mkdtemp())
    logger = StudyLogger(
        base_dir=base_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test",
        condition="c1_stepwise",
    )
    if backend is None:
        backend = FakeBackend()
    if oversight is None:
        oversight = FakeOversight()
    if spec is None:
        spec = _make_spec(steps=steps, error_steps=error_steps)
    return TrialExecutor(
        backend=backend,
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=condition,
        error_tasks=error_tasks,
    )


# ---------------------------------------------------------------------------
# _parse_action tests
# ---------------------------------------------------------------------------


def test_parse_action_open_app():
    assert _parse_action("open com.android.settings") == ("open_app", "com.android.settings")


def test_parse_action_open_url():
    assert _parse_action("open_url https://example.com") == ("open_url", "https://example.com")


def test_parse_action_tap_label():
    assert _parse_action("click 'Send'") == ("tap", "Send")
    assert _parse_action("click \"Send\"") == ("tap", "Send")
    assert _parse_action("click Send") == ("tap", "Send")


def test_parse_action_type_text():
    assert _parse_action("input text 'Hello'") == ("type", "Hello")
    assert _parse_action("input text 'Hello' (submit)") == ("type", "Hello", "submit")


def test_parse_action_scroll():
    assert _parse_action("scroll down") == ("scroll", "down")
    assert _parse_action("scroll up") == ("scroll", "up")


def test_parse_action_press():
    assert _parse_action("press BACK") == ("press", "BACK")
    assert _parse_action("press HOME") == ("press", "HOME")


def test_parse_action_unrecognised():
    try:
        _parse_action("unknown action")
        assert False, "Should raise ValueError"
    except ValueError as e:
        assert "Unrecognised action" in str(e)


# ---------------------------------------------------------------------------
# _find_element tests
# ---------------------------------------------------------------------------


def test_find_element_by_text():
    elements = [{"text": "Send", "index": 5}]
    el = _find_element(elements, "Send")
    assert el is not None
    assert el["index"] == 5


def test_find_element_by_content_description():
    elements = [{"content_description": "Settings", "index": 10}]
    el = _find_element(elements, "settings")
    assert el is not None


def test_find_element_by_resource_id():
    elements = [{"resource_id": "com.app:id/button", "index": 3}]
    el = _find_element(elements, "button")
    assert el is not None


def test_find_element_case_insensitive():
    elements = [{"text": "SEND", "index": 7}]
    el = _find_element(elements, "send")
    assert el is not None


def test_find_element_not_found():
    elements = [{"text": "Send", "index": 5}]
    el = _find_element(elements, "Cancel")
    assert el is None


# ---------------------------------------------------------------------------
# TrialExecutor — basic execution
# ---------------------------------------------------------------------------


def test_executor_open_app():
    executor = _make_executor(
        steps=[_step("open com.android.settings")],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 1


def test_executor_tap_element():
    backend = FakeBackend(elements=[{"text": "Send", "index": 42}])
    executor = _make_executor(
        backend=backend,
        steps=[_step("click 'Send'", step_type=StepType.NORMAL)],
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 1
    assert backend.calls == [("tap_element", 42)]


def test_executor_type_text():
    executor = _make_executor(
        steps=[_step("input text 'Hello'")],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 1


def test_executor_scroll():
    executor = _make_executor(
        steps=[_step("scroll down")],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 1


def test_executor_press_button():
    executor = _make_executor(
        steps=[_step("press BACK")],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 1


def test_executor_multiple_steps():
    executor = _make_executor(
        steps=[
            _step("open com.android.settings", step_type=StepType.NORMAL),
            _step("scroll down", step_type=StepType.NORMAL),
        ],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 2


def test_executor_target_not_found_aborts():
    """If a tap target is not on screen, the trial aborts."""
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    executor = _make_executor(
        backend=backend,
        steps=[_step("click 'Cancel'", step_type=StepType.NORMAL)],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "technical_failure"
    assert "performed" in result.reason
    assert result.steps_done == 0


# ---------------------------------------------------------------------------
# C1 — Stepwise oversight
# ---------------------------------------------------------------------------


def test_c1_consequential_step_accepted():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight(accept=True)
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert len(oversight.confirmations) == 1
    assert oversight.confirmations[0][0] == "s_click_'Send'"


def test_c1_consequential_step_declined():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight(accept=True, decline_step_ids={"s_click_'Send'"})
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "aborted"
    assert "declined" in result.reason


# ---------------------------------------------------------------------------
# C2 — Final checkpoint
# ---------------------------------------------------------------------------


def test_c2_batch_summary_executed():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight()
    steps = [
        _step("open com.android.settings", step_type=StepType.CONSEQUENTIAL, consequential=True),
        _step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True),
    ]
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        condition=StudyCondition.FINAL_CHECKPOINT,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 2
    # C2 summary should contain both steps
    assert len(oversight.summary_steps) == 2
    assert any(s.id == "s_open_com.android.settings" for s in oversight.summary_steps)
    assert any(s.id == "s_click_'Send'" for s in oversight.summary_steps)


def test_c2_summary_declined():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight(decline_summary=True)
    steps = [
        _step("open com.android.settings", step_type=StepType.CONSEQUENTIAL, consequential=True),
    ]
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        condition=StudyCondition.FINAL_CHECKPOINT,
    )
    result = executor.run()
    assert result.outcome.value == "aborted"


# ---------------------------------------------------------------------------
# C3 — Voluntary intervention
# ---------------------------------------------------------------------------


def test_c3_no_gates():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight()
    steps = [
        _step("open com.android.settings", step_type=StepType.CONSEQUENTIAL),
        _step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True),
    ]
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.steps_done == 2
    # No confirmations in C3
    assert len(oversight.confirmations) == 0


# ---------------------------------------------------------------------------
# User cancellation
# ---------------------------------------------------------------------------


def test_executor_cancelled_before_start():
    backend = FakeBackend()
    oversight = FakeOversight()
    oversight.cancelled = True
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=[_step("open com.android.settings")],
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "aborted"


# ---------------------------------------------------------------------------
# Error injection
# ---------------------------------------------------------------------------


def test_error_injected_on_error_step():
    ev = ErrorVariant(
        id="ev_wrong",
        field="amount",
        wrong_value="75",
        correct_value="50",
        description="Falscher Betrag",
    )
    steps = [
        _step("open com.android.settings", step_type=StepType.CONSEQUENTIAL),
        _step("input text '50'", step_type=StepType.CONSEQUENTIAL, error_variant=ev),
    ]
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight()
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        error_steps={"s_input_text_'50'"},
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset({"task_test"}),
    )
    result = executor.run()
    assert result.outcome.value == "success"
    # Check that error event was logged
    events = logger_events(executor._logger)
    error_events = [e for e in events if e.event_type == EventType.ERROR_INJECTED]
    assert len(error_events) == 1
    d = error_events[0].details
    assert d["field"] == "amount"
    assert d["wrong_value"] == "75"
    assert d["correct_value"] == "50"


def test_error_not_injected_on_non_error_step():
    steps = [
        _step("open com.android.settings", step_type=StepType.CONSEQUENTIAL),
    ]
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    oversight = FakeOversight()
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        error_steps={"nonexistent_step"},
        condition=StudyCondition.STEPWISE,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    events = logger_events(executor._logger)
    error_events = [e for e in events if e.event_type == EventType.ERROR_INJECTED]
    assert len(error_events) == 0


def test_error_injection_replaces_correct_value():
    """The wrong_value is actually substituted into the backend action call."""
    ev = ErrorVariant(
        id="ev_wrong_recipient",
        field="recipient",
        wrong_value="anna@example.com",
        correct_value="max@example.com",
        description="Falscher Empfänger",
    )
    steps = [
        _step("input text 'max@example.com'", step_type=StepType.NORMAL, error_variant=ev),
    ]
    backend = FakeBackend(elements=[])
    oversight = FakeOversight()
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        error_steps={"s_input_text_'max@example.com'"},
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset({"task_test"}),
    )
    result = executor.run()
    assert result.outcome.value == "success"
    # FakeBackend.type_text recorded the wrong_value
    type_calls = [c for c in backend.calls if c[0] == "type"]
    assert len(type_calls) == 1
    assert type_calls[0][1] == "anna@example.com"


def test_error_not_injected_when_task_not_in_error_tasks():
    """Error variant is only injected when trial is in error_tasks."""
    ev = ErrorVariant(
        id="ev_wrong",
        field="amount",
        wrong_value="50",
        correct_value="100",
        description="Falscher Betrag",
    )
    steps = [
        _step("input text '100'", step_type=StepType.NORMAL),
    ]
    backend = FakeBackend(elements=[])
    oversight = FakeOversight()
    executor = _make_executor(
        backend=backend,
        oversight=oversight,
        steps=steps,
        error_steps={"s_input_text_'100'"},
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset({"task_different"}),  # not our task
    )
    result = executor.run()
    assert result.outcome.value == "success"
    # No ERROR_INJECTED event
    events = logger_events(executor._logger)
    error_events = [e for e in events if e.event_type == EventType.ERROR_INJECTED]
    assert len(error_events) == 0
    # Backend received correct_value, not wrong_value
    type_calls = [c for c in backend.calls if c[0] == "type"]
    assert type_calls[0][1] == "100"


# ---------------------------------------------------------------------------
# Verification integration
# ---------------------------------------------------------------------------


def test_verification_runs_post_trial():
    """Verification is executed after successful steps and results are captured."""
    from caddie.study.model import VerificationRule
    from caddie.study.verification import FakeVerificationBackend

    rules = (
        VerificationRule(
            id="v1",
            assertion="Success text is visible",
            check_type="text_present",
            parameters={"text": "Success"},
        ),
    )
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    verif_backend = FakeVerificationBackend()
    verif_backend.set_result("text_present", "Success", result=True)
    oversight = FakeOversight()
    spec = _make_spec(
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
        verification=rules,
    )
    executor = TrialExecutor(
        backend=backend,
        logger=_____________________________________________make_fake_logger(),
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset({"task_test"}),
        verification_backend=verif_backend,
    )
    result = executor.run()
    assert result.outcome.value == "success"
    assert result.verification_passed is True
    assert len(result.verification_results) == 1
    assert result.verification_results[0]["outcome"] == "pass"


def test_verification_fails_on_missing_text():
    """Verification fails when required text is not on screen."""
    from caddie.study.model import VerificationRule
    from caddie.study.verification import FakeVerificationBackend

    rules = (
        VerificationRule(
            id="v1",
            assertion="NotOnScreen text is visible",
            check_type="text_present",
            parameters={"text": "NotOnScreen"},
        ),
    )
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    verif_backend = FakeVerificationBackend()
    verif_backend.set_result("text_present", "NotOnScreen", result=False)
    oversight = FakeOversight()
    spec = _make_spec(
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
        verification=rules,
    )
    executor = TrialExecutor(
        backend=backend,
        logger=_____________________________________________make_fake_logger(),
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset({"task_test"}),
        verification_backend=verif_backend,
    )
    result = executor.run()
    assert result.outcome.value == "verification_failed"
    assert result.verification_passed is False
    assert result.verification_results[0]["outcome"] == "fail"


# ---------------------------------------------------------------------------
# ExecutionResult
# ---------------------------------------------------------------------------


def test_execution_result_defaults():
    r = ExecutionResult(success=True, step=_step("open x"))
    assert r.error is None
    assert r.action is None
    assert r.resolved_index is None
    assert r.elapsed_ms == 0.0


# ---------------------------------------------------------------------------
# execute_trial convenience function
# ---------------------------------------------------------------------------


def test_execute_trial_convenience():
    backend = FakeBackend(elements=[{"text": "Send", "index": 5}])
    spec = _make_spec(
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
    )
    result = execute_trial(
        backend=backend,
        logger=_____________________________________________make_fake_logger(),
        oversight=FakeOversight(),
        spec=spec,
        condition=StudyCondition.STEPWISE,
    )
    assert result.outcome.value == "success"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def logger_events(logger) -> list:
    """Read raw events from a logger."""
    return logger.get_raw_events()


def _____________________________________________make_fake_logger():
    import tempfile
    return StudyLogger(
        base_dir=pathlib.Path(tempfile.mkdtemp()),
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test",
        condition="c1_stepwise",
    )


# ---------------------------------------------------------------------------
# Duplicate confirmation events
# ---------------------------------------------------------------------------


def test_no_duplicate_confirmation_logging():
    """C1 gate produces exactly one confirmation_shown and one confirmation_resolved."""
    logger = _____________________________________________make_fake_logger()
    backend = FakeBackend()
    oversight = FakeOversight(accept=True, logger=logger)

    spec = _make_spec(
        steps=[_step("click 'Send'", step_type=StepType.CONSEQUENTIAL, consequential=True)],
    )
    executor = TrialExecutor(
        backend=backend,
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=StudyCondition.STEPWISE,
        error_tasks=frozenset(),
    )
    result = executor.run()
    # Fake backend may fail actions but the trial should still complete
    assert result.outcome in (TrialOutcome.SUCCESS, TrialOutcome.TECHNICAL_FAILURE)

    events = logger.get_raw_events()
    shown = [e for e in events if e.event_type == "confirmation_shown"]
    resolved = [e for e in events if e.event_type == "confirmation_resolved"]
    # Only one each from the gate — executor no longer duplicates
    assert len(shown) == 1
    assert len(resolved) == 1
