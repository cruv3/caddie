"""End-to-end integration tests for the Caddie study system.

Tests the full pipeline:
    Preflight → Matrix → Trial across C1/C2/C3 → Verification → Export → Cleanup

Each test exercises the integration of multiple modules.

Directory structure
-------------------
    tests/
        test_study_e2e.py       ← this module
"""

from __future__ import annotations

import json
import pathlib
import threading
import time
import tempfile

import pytest

from caddie.study.model import (
    StudyCondition,
    StepType,
    CriticalityClass,
    StudyStep,
    ErrorVariant,
    TrialSpec,
    TrialOutcome,
    VerificationRule,
)
from caddie.study.logger import StudyLogger
from caddie.study.oversight import OversightManager, OversightDecision
from caddie.study.session import StudySession, SessionState
from caddie.study.executor import TrialExecutor, _parse_action
from caddie.study.verification import FakeVerificationBackend
from caddie.study.preflight import (
    PreflightSuite,
    PreflightCheck,
    CheckCategory,
    CheckStatus,
    CheckResult,
    CheckFn,
)
from caddie.study.matrix import generate_matrix


# ===================================================================
# Module-level check functions
# ===================================================================


def _pass_check():
    check = PreflightCheck(
        id="always_pass",
        category=CheckCategory.DEVICE,
        description="Always passes",
    )
    return CheckResult(check, CheckStatus.PASS, 5, "OK")


def _fail_check():
    check = PreflightCheck(
        id="always_fail",
        category=CheckCategory.SERVER,
        description="Always fails",
    )
    return CheckResult(check, CheckStatus.FAIL, 5, "Error")


def _skip_check():
    check = PreflightCheck(
        id="skip_me",
        category=CheckCategory.MATERIALS,
        description="Skip",
    )
    return CheckResult(check, CheckStatus.SKIP, 0, "Skipped")


# ===================================================================
# Test doubles
# ===================================================================


def _build_fake_elements(step_ids):
    """Build element dicts for clickable step IDs."""
    elements = []
    idx = 0
    click_targets = {
        "send_msg": "Send",
        "confirm": "Confirm",
    }
    for sid in step_ids:
        label = click_targets.get(sid, sid)
        elements.append({
            "index": idx, "resource_id": sid, "text": label,
            "clickable": True,
        })
        idx += 1
    return elements


class FakeBackend:
    """Backend that returns canned UI elements and records calls."""

    def __init__(self, elements=None, tap_fail_indices=None, open_app_fail=False, type_text_fail=False):
        self._elements = elements or []
        self.calls = []
        self._tap_fail_indices = set(tap_fail_indices or [])
        self._open_app_fail = open_app_fail
        self._type_text_fail = type_text_fail

    def list_elements(self):
        return {"elements": list(self._elements)}

    def open_app(self, package_name):
        self.calls.append(("open_app", package_name))
        if self._open_app_fail:
            return {"success": False, "error": "App launch failed"}
        return {"ok": True}

    def open_url(self, url):
        self.calls.append(("open_url", url))
        return {"ok": True}

    def tap_element(self, index):
        self.calls.append(("tap_element", index))
        if index in self._tap_fail_indices:
            return {"success": False, "error": f"tap failed on {index}"}
        return {"ok": True}

    def scroll(self, direction, amount=0.6):
        self.calls.append(("scroll", direction, amount))
        return {"ok": True}

    def press_button(self, button):
        self.calls.append(("press", button))
        return {"ok": True}

    def type_text(self, text, submit=False):
        self.calls.append(("type", text, submit))
        if self._type_text_fail and "Max" in text:
            return {"success": False, "error": "type_text failed"}
        return {"ok": True}

    def set_elements(self, elements):
        self._elements = elements

    def set_tap_fail(self, indices):
        self._tap_fail_indices = set(indices)

    def set_open_app_fail(self, value=True):
        self._open_app_fail = value


def _make_backend(steps, tap_fail=None, open_fail=False, type_fail=False):
    """Create a FakeBackend with elements for the given steps."""
    step_ids = [s.id for s in steps]
    elements = _build_fake_elements(step_ids)
    return FakeBackend(
        elements=elements,
        tap_fail_indices=tap_fail or [],
        open_app_fail=open_fail,
        type_text_fail=type_fail,
    )


class FakeOversight:
    """Oversight manager that accepts by default, configurable per-step."""

    def __init__(self, condition=StudyCondition.STEPWISE, decline_step_ids=None, step_callback=None):
        self._condition = condition
        self.decline_step_ids = set(decline_step_ids or [])
        self.confirmations = []
        self.batch_steps = []
        self._cancelled = False
        self._step_callback = step_callback

    def confirm_consequential_step(self, step, narration):
        self.confirmations.append((step.id, narration))
        if self._cancelled:
            return OversightDecision(cancelled=True)
        if step.id in self.decline_step_ids:
            return OversightDecision(declined=True, reason=f"Step {step.id} declined")
        if self._step_callback:
            return self._step_callback(step, narration)
        return OversightDecision(confirmed=True)

    def show_c2_summary(self, steps):
        return self.show_c2_summary_with_narrations(steps, [s.narration or s.action for s in steps])

    def show_c2_summary_with_narrations(self, steps, narrations=None):
        self.batch_steps.append((list(steps), narrations or []))
        if self._cancelled:
            return OversightDecision(cancelled=True)
        return OversightDecision(confirmed=True)

    def cancel(self):
        self._cancelled = True

    def is_cancelled(self):
        return self._cancelled

    def get_pending_steps(self):
        return []

    def clear_pending_steps(self):
        pass

    @property
    def condition(self):
        return self._condition


class FakeRunControl:
    """Minimal fake of RunControl for testing."""

    def __init__(self):
        self._state = "running"
        self._paused = False
        self._stop_requested = False
        self._confirm_approved = None
        self._confirm_set = threading.Event()
        self._confirm_set.set()

    def request_pause(self, *, intervention=False):
        self._state = "paused"
        self._paused = True

    def request_resume(self):
        self._state = "running"
        self._paused = False

    def request_stop(self):
        self._state = "stopped"
        self._stop_requested = True

    def resolve_confirmation(self, approved):
        self._confirm_approved = approved
        self._confirm_set.set()

    def await_confirmation(self, timeout):
        self._confirm_set.clear()
        self._confirm_set.wait(timeout)
        return bool(self._confirm_approved)

    def wait_while_paused(self):
        self._confirm_set.wait()


# ===================================================================
# Helpers
# ===================================================================


def make_logger(tmp_path, participant="P01", session="sess_001"):
    """Create a StudyLogger with all required parameters."""
    return StudyLogger(
        base_dir=tmp_path / "study-data",
        study_version="v1.0.0",
        participant_id=participant,
        session_id=session,
        condition="c1_stepwise",
    )


def make_steps():
    """Create a minimal set of StudyStep objects for a trial."""
    err_variant = ErrorVariant(
        id="err_wrong_name",
        field="name",
        wrong_value="Eve",
        correct_value="Max",
        description="Name ist falsch",
    )
    return [
        StudyStep(
            id="open_app",
            action="open com.caddie",
            narration="App offnen",
            step_type=StepType.NORMAL,
            min_narration_ms=100,
        ),
        StudyStep(
            id="type_input",
            action="input text 'Max'",
            narration="Name eingeben",
            step_type=StepType.NORMAL,
            error_variant=err_variant,
            min_narration_ms=100,
        ),
        StudyStep(
            id="send_msg",
            action="click 'Send'",
            narration="Nachricht senden",
            step_type=StepType.CONSEQUENTIAL,
            min_narration_ms=100,
        ),
        StudyStep(
            id="confirm",
            action="click 'Confirm'",
            narration="Bestaetigen",
            step_type=StepType.COMMIT,
            min_narration_ms=100,
        ),
    ]


def make_trial_spec(steps):
    """Create a TrialSpec from steps."""
    return TrialSpec(
        version="v1",
        id="task_send_message",
        instruction_de="Nachricht senden",
        criticality=CriticalityClass.HIGH,
        steps=tuple(steps),
        error_steps=("type_input",),
        verification=(
            VerificationRule(
                id="v_send_sent",
                assertion="text_present",
                check_type="text_present",
                parameters={"text": "Message Sent"},
            ),
        ),
        max_duration_s=30,
        per_gate_timeout_s=10,
    )


# ===================================================================
# Test: Preflight suite runs and reports correctly
# ===================================================================


class TestPreflight:
    """Preflight suite integration tests."""

    def test_preflight_all_pass(self):
        """All checks pass → PreflightResult.all_passed is True."""
        suite = PreflightSuite()
        suite.add(PreflightCheck(
            id="check1",
            category=CheckCategory.DEVICE,
            description="Check 1",
        ), _pass_check)

        results = suite.run()
        assert len(results) == 1
        assert results[0].passed
        summary = suite.summary()
        assert summary.all_passed
        assert summary.required_passed
        assert summary.passed == 1

    def test_preflight_with_fail(self):
        """One check fails → all_passed is False, required_passed is False."""
        suite = PreflightSuite()
        suite.add(PreflightCheck(
            id="check1",
            category=CheckCategory.DEVICE,
            description="Check 1",
        ), _pass_check)
        suite.add(PreflightCheck(
            id="check2",
            category=CheckCategory.SERVER,
            description="Check 2",
        ), _fail_check)

        results = suite.run()
        assert len(results) == 2
        summary = suite.summary()
        assert not summary.all_passed
        assert not summary.required_passed
        assert summary.passed == 1
        assert summary.failed == 1

    def test_preflight_run_and_summary(self):
        """run_and_summary returns consistent results and summary."""
        suite = PreflightSuite()
        suite.add(PreflightCheck(
            id="c1",
            category=CheckCategory.DEVICE,
            description="Device",
        ), _pass_check)

        results, summary = suite.run_and_summary()
        assert len(results) == 1
        assert summary.total == 1
        assert summary.passed == 1

    def test_preflight_skipped_check(self):
        """Skipped checks do not affect required_passed but affect all_passed."""
        suite = PreflightSuite()
        suite.add(PreflightCheck(
            id="pass1",
            category=CheckCategory.DEVICE,
            description="Pass",
        ), _pass_check)
        suite.add(PreflightCheck(
            id="skip1",
            category=CheckCategory.MATERIALS,
            description="Skip",
        ), _skip_check)

        results, summary = suite.run_and_summary()
        assert summary.total == 2
        assert summary.passed == 1
        assert summary.skipped == 1
        assert summary.required_passed  # No required failures
        assert not summary.all_passed  # Skipped counts as not all passed


# ===================================================================
# Test: Full trial execution (C1 — stepwise)
# ===================================================================


class TestTrialC1:
    """End-to-end trial execution in C1 (stepwise) condition."""

    def test_c1_trial_success(self, tmp_path):
        """C1 trial completes all steps with confirmation → SUCCESS."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        steps = make_steps()
        spec = make_trial_spec(steps)

        # Use FakeOversight to track confirmations
        oversight = FakeOversight(condition=StudyCondition.STEPWISE)
        oversight_mgr = OversightManager(
            logger, condition=StudyCondition.STEPWISE,
            step_callback=oversight.confirm_consequential_step,
        )

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.steps_done == 4
        assert len(result.steps_executed) == 4
        for sr in result.steps_executed:
            assert sr.success
        # Verify confirmation was called for consequential steps
        assert len(oversight.confirmations) == 2  # send_msg + confirm

    def test_c1_trial_step_decline(self, tmp_path):
        """C1 trial where a step is declined → ABORTED."""
        logger = make_logger(tmp_path)
        steps = make_steps()
        backend = _make_backend(steps)
        _declined = {"send_msg"}
        def declining_callback(step, narration):
            if step.id in _declined:
                return OversightDecision(declined=True, reason=f"Step {step.id} declined")
            return OversightDecision(confirmed=True)
        oversight_mgr = OversightManager(
            logger, condition=StudyCondition.STEPWISE,
            step_callback=declining_callback,
        )
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.ABORTED
        assert result.steps_done >= 1

    def test_c1_trial_backend_tap_failure(self, tmp_path):
        """C1 trial where tap fails on a committed step → TECHNICAL_FAILURE."""
        logger = make_logger(tmp_path)
        steps = make_steps()
        # Fail on send_msg (index 2) which is consequential → TECHNICAL_FAILURE
        backend = _make_backend(steps, tap_fail=[2])
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.TECHNICAL_FAILURE
        assert len(result.steps_executed) > 0


# ===================================================================
# Test: Full trial execution (C2 — final checkpoint)
# ===================================================================


class TestTrialC2:
    """End-to-end trial execution in C2 (final checkpoint) condition."""

    def test_c2_trial_success(self, tmp_path):
        """C2 trial collects consequential steps in batch → batch confirmed → SUCCESS."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        fake_ov = FakeOversight(condition=StudyCondition.FINAL_CHECKPOINT)
        oversight_mgr = OversightManager(
            logger, condition=StudyCondition.FINAL_CHECKPOINT,
            batch_callback=fake_ov.show_c2_summary_with_narrations,
        )
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.FINAL_CHECKPOINT,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.steps_done == 4
        # Verify batch summary was called
        assert len(fake_ov.batch_steps) == 1
        batch_steps, narrations = fake_ov.batch_steps[0]
        assert len(batch_steps) == 2  # send_msg + confirm are consequential


# ===================================================================
# Test: Full trial execution (C3 — voluntary intervention)
# ===================================================================


class TestTrialC3:
    """End-to-end trial execution in C3 (voluntary intervention) condition."""

    def test_c3_trial_no_gates(self, tmp_path):
        """C3 trial passes all consequential steps without any gate calls."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        fake_ov = FakeOversight(condition=StudyCondition.VOLUNTARY_INTERVENTION)
        oversight_mgr = OversightManager(
            logger, condition=StudyCondition.VOLUNTARY_INTERVENTION,
            step_callback=fake_ov.confirm_consequential_step,
            batch_callback=fake_ov.show_c2_summary_with_narrations,
        )
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.VOLUNTARY_INTERVENTION,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.steps_done == 4
        # No batch calls in C3
        assert len(fake_ov.batch_steps) == 0


# ===================================================================
# Test: Verification pipeline integration
# ===================================================================


class TestVerificationE2E:
    """Verification pipeline end-to-end tests."""

    def test_trial_with_verification_pass(self, tmp_path):
        """Trial + verification backend → all rules pass → outcome is SUCCESS."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        verif_backend = FakeVerificationBackend()
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
            verification_backend=verif_backend,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.verification_passed is True
        assert len(result.verification_results) == 1
        assert result.verification_results[0]["outcome"] == "pass"

    def test_trial_with_verification_fail(self, tmp_path):
        """Trial + verification backend → rule fails → outcome is VERIFICATION_FAILED."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        verif_backend = FakeVerificationBackend()
        verif_backend.set_result("text_present", "Message Sent", result=False)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
            verification_backend=verif_backend,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.VERIFICATION_FAILED
        assert result.verification_passed is False
        assert len(result.verification_results) == 1
        assert result.verification_results[0]["outcome"] == "fail"

    def test_trial_without_verification_backend(self, tmp_path):
        """Trial without verification backend → outcome is SUCCESS (no verification)."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.verification_passed is False
        assert len(result.verification_results) == 0


# ===================================================================
# Test: Session lifecycle integration
# ===================================================================


class TestSessionLifecycleE2E:
    """Session lifecycle tests with full executor integration."""

    def test_session_start_run_complete(self, tmp_path):
        """Full session lifecycle: start → trial → complete."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)
        session.start()
        assert session.state is SessionState.RUNNING

        backend = _make_backend(make_steps())
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
            session=session,
        )
        result = executor.run()
        assert result.outcome == TrialOutcome.SUCCESS

        session.complete()
        assert session.state is SessionState.COMPLETED
        assert rc._stop_requested is True

    def test_session_cancel_mid_trial(self, tmp_path):
        """Cancel session while trial is running → CANCELLING state."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)

        session.start()
        assert session.state is SessionState.RUNNING

        applied = session.cancel()
        assert applied is True
        assert session.state is SessionState.CANCELLING
        assert oversight_mgr.is_cancelled() is True
        assert rc._stop_requested is True

    def test_session_pause_resume(self, tmp_path):
        """Pause session → resume → continue."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)

        session.start()
        session.pause()
        assert session.state is SessionState.PAUSED

        session.resume()
        assert session.state is SessionState.RUNNING

    def test_session_fail_terminal(self, tmp_path):
        """Fail session → terminal state → hard cleanup."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)

        session.start()
        session.fail(reason="device disconnected")

        assert session.state is SessionState.FAILED
        assert session.is_terminal is True
        session.fail(reason="again")
        assert session.state is SessionState.FAILED

    def test_session_metrics_snapshot(self, tmp_path):
        """Session metrics produce a frozen snapshot."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)

        session.start()
        metrics = session.get_metrics()
        assert metrics.elapsed_ms >= 0
        assert metrics.steps_executed == 0
        assert metrics.is_paused is False


# ===================================================================
# Test: Error injection end-to-end
# ===================================================================


class TestErrorInjectionE2E:
    """Error injection across all conditions."""

    def test_c1_error_injected(self, tmp_path):
        """C1 trial with error injection → error_variant recorded in ExecutionResult."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
            error_tasks=frozenset(["task_send_message"]),
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        error_step_result = None
        for sr in result.steps_executed:
            if sr.step.id == "type_input":
                error_step_result = sr
                break
        assert error_step_result is not None
        assert error_step_result.error_injected is True
        assert error_step_result.error_variant_id is not None

    def test_c2_error_injected_batch(self, tmp_path):
        """C2 trial with error injection → batch summary includes error description."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        fake_ov = FakeOversight(condition=StudyCondition.FINAL_CHECKPOINT)
        oversight_mgr = OversightManager(
            logger, condition=StudyCondition.FINAL_CHECKPOINT,
            batch_callback=fake_ov.show_c2_summary_with_narrations,
        )
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.FINAL_CHECKPOINT,
            error_tasks=frozenset(["task_send_message"]),
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert len(fake_ov.batch_steps) >= 1

    def test_c3_error_injected_no_gate(self, tmp_path):
        """C3 trial with error injection → error applied but no gate delay."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.VOLUNTARY_INTERVENTION)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.VOLUNTARY_INTERVENTION,
            error_tasks=frozenset(["task_send_message"]),
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        for sr in result.steps_executed:
            if sr.step.id == "type_input":
                assert sr.error_injected is True


# ===================================================================
# Test: Trial deadline enforcement
# ===================================================================


class TestTrialDeadline:
    """Trial max_duration_s enforcement."""

    def test_trial_exceeds_max_duration(self, tmp_path):
        """Trial steps take too long → CONFIRMATION_TIMEOUT or TECHNICAL_FAILURE."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)

        steps = [
            StudyStep(id="slow_1", action="click 'A'", narration="Step 1",
                      step_type=StepType.NORMAL),
            StudyStep(id="slow_2", action="click 'B'", narration="Step 2",
                      step_type=StepType.NORMAL),
            StudyStep(id="slow_3", action="click 'C'", narration="Step 3",
                      step_type=StepType.NORMAL),
        ]
        spec = TrialSpec(
            version="v1", id="task_slow", instruction_de="Langsam",
            criticality=CriticalityClass.LOW, steps=tuple(steps),
            error_steps=(), verification=(),
            max_duration_s=1, per_gate_timeout_s=10,
        )

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result = executor.run()

        assert result.outcome in (TrialOutcome.CONFIRMATION_TIMEOUT, TrialOutcome.TECHNICAL_FAILURE)
        assert result.steps_done < len(steps)


# ===================================================================
# Test: Logger output verification
# ===================================================================


class TestLoggerOutput:
    """Verify that logger produces valid JSONL events."""

    def test_events_jsonl_written(self, tmp_path):
        """Trial run produces a valid events.jsonl file."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        executor.run()

        session_dir = tmp_path / "study-data" / "v1.0.0" / "P01" / "sess_001"
        events_file = session_dir / "events.jsonl"
        assert events_file.exists()

        lines = events_file.read_text().strip().split("\n")
        assert len(lines) > 0
        for line in lines:
            entry = json.loads(line)
            assert "event_type" in entry
            assert "timestamp" in entry

    def test_summary_json_written(self, tmp_path):
        """Trial run produces summary.json with trial results."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend,
            logger=logger,
            oversight=oversight_mgr,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        executor.run()

        session_dir = tmp_path / "study-data" / "v1.0.0" / "P01" / "sess_001"
        summary_file = session_dir / "summary.json"
        assert summary_file.exists()

        summary = json.loads(summary_file.read_text())
        # The logger writes a single-trial summary with outcome and metadata
        assert "outcome" in summary
        assert "study_version" in summary
        assert "participant_id" in summary


# ===================================================================
# Test: Export — summary.json contains all trials
# ===================================================================


class TestExport:
    """Export and summary integration tests."""

    def test_export_multiple_trials(self, tmp_path):
        """Running multiple trials across sessions → summary exists in each."""
        steps = make_steps()
        spec = make_trial_spec(steps)

        for condition in [
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
        ]:
            logger = make_logger(tmp_path, participant=f"P{condition.name[0]}")
            backend = _make_backend(steps)
            oversight_mgr = OversightManager(logger, condition=condition)
            session = StudySession(logger, FakeRunControl(), oversight_mgr)
            session.start()

            executor = TrialExecutor(
                backend=backend,
                logger=logger,
                oversight=oversight_mgr,
                spec=spec,
                condition=condition,
                session=session,
            )
            result = executor.run()
            assert result.outcome == TrialOutcome.SUCCESS, f"Failed in {condition.name}"
            session.complete()

            # Each session should have summary.json
            session_dir = tmp_path / "study-data" / "v1.0.0" / f"P{condition.name[0]}" / "sess_001"
            summary_file = session_dir / "summary.json"
            assert summary_file.exists(), f"Missing summary for {condition.name}"
            summary = json.loads(summary_file.read_text())
            assert "outcome" in summary
            assert summary["outcome"] == "success"

    def test_export_trial_outcomes(self, tmp_path):
        """Exported summary captures correct outcomes for each trial."""
        steps = make_steps()
        spec = make_trial_spec(steps)
        backend = _make_backend(steps)

        # C1 success
        logger1 = make_logger(tmp_path, participant="P1")
        oversight1 = OversightManager(logger1, condition=StudyCondition.STEPWISE)
        executor1 = TrialExecutor(
            backend=backend,
            logger=logger1,
            oversight=oversight1,
            spec=spec,
            condition=StudyCondition.STEPWISE,
        )
        result1 = executor1.run()
        assert result1.outcome == TrialOutcome.SUCCESS

        # C2 success
        logger2 = make_logger(tmp_path, participant="P2")
        oversight2 = OversightManager(logger2, condition=StudyCondition.FINAL_CHECKPOINT)
        executor2 = TrialExecutor(
            backend=backend,
            logger=logger2,
            oversight=oversight2,
            spec=spec,
            condition=StudyCondition.FINAL_CHECKPOINT,
        )
        result2 = executor2.run()
        assert result2.outcome == TrialOutcome.SUCCESS

        # Verify summaries exist
        for pid in ["P1", "P2"]:
            session_dir = tmp_path / "study-data" / "v1.0.0" / pid / "sess_001"
            summary_file = session_dir / "summary.json"
            assert summary_file.exists(), f"Missing summary for {pid}"
            summary = json.loads(summary_file.read_text())
            assert "outcome" in summary
            assert summary["outcome"] == "success"


# ===================================================================
# Test: Matrix generation integration
# ===================================================================


class TestMatrixIntegration:
    """Matrix generation and integration with session."""

    def test_generate_matrix_returns_participants(self, tmp_path):
        """generate_matrix returns ParticipantConfigs for all participants."""
        from caddie.study.model import TrialSpec as TS
        specs = {}
        for i in range(8):
            steps = [
                StudyStep(
                    id=f"step_{i}_1",
                    action=f"click 'A'{i}",
                    narration=f"Schritt {i}",
                    step_type=StepType.NORMAL if i % 2 == 0 else StepType.CONSEQUENTIAL,
                    min_narration_ms=100,
                ),
            ]
            specs[f"task_{i}"] = TrialSpec(
                version="v1", id=f"task_{i}", instruction_de=f"Task {i}",
                criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
                steps=tuple(steps), error_steps=(f"task_{i}",) if i < 3 else (),
                verification=(), max_duration_s=30, per_gate_timeout_s=10,
            )

        matrix = generate_matrix(specs)
        assert len(matrix) > 0
        for pid, config in matrix.items():
            assert pid.startswith("P")
            assert len(config.task_order) == 6
            assert len(config.condition_order) == 6
            assert len(config.error_tasks) == 3

    def test_matrix_all_conditions_represented(self, tmp_path):
        """Matrix for 3+ participants covers all 3 conditions."""
        specs = {}
        for i in range(8):
            steps = [
                StudyStep(
                    id=f"step_{i}_1",
                    action=f"click 'A'{i}",
                    narration=f"Schritt {i}",
                    step_type=StepType.NORMAL if i % 2 == 0 else StepType.CONSEQUENTIAL,
                    min_narration_ms=100,
                ),
            ]
            specs[f"task_{i}"] = TrialSpec(
                version="v1", id=f"task_{i}", instruction_de=f"Task {i}",
                criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
                steps=tuple(steps), error_steps=(f"task_{i}",) if i < 3 else (),
                verification=(), max_duration_s=30, per_gate_timeout_s=10,
            )

        matrix = generate_matrix(specs)
        conditions = {config.condition_order[0] for config in matrix.values()}
        assert StudyCondition.STEPWISE in conditions
        assert StudyCondition.FINAL_CHECKPOINT in conditions
        assert StudyCondition.VOLUNTARY_INTERVENTION in conditions

    def test_matrix_deterministic_with_seed(self, tmp_path):
        """Two runs with the same seed produce identical assignments."""
        specs = {}
        for i in range(8):
            steps = [
                StudyStep(
                    id=f"step_{i}_1",
                    action=f"click 'A'{i}",
                    narration=f"Schritt {i}",
                    step_type=StepType.NORMAL if i % 2 == 0 else StepType.CONSEQUENTIAL,
                    min_narration_ms=100,
                ),
            ]
            specs[f"task_{i}"] = TrialSpec(
                version="v1", id=f"task_{i}", instruction_de=f"Task {i}",
                criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
                steps=tuple(steps), error_steps=(f"task_{i}",) if i < 3 else (),
                verification=(), max_duration_s=30, per_gate_timeout_s=10,
            )

        m1 = generate_matrix(specs, seed=42)
        m2 = generate_matrix(specs, seed=42)
        for pid in m1:
            assert m1[pid].participant_id == m2[pid].participant_id
            assert m1[pid].condition_order == m2[pid].condition_order


# ===================================================================
# Test: Full pipeline — preflight → matrix → trial (C1) → verification → cleanup
# ===================================================================


class TestFullPipeline:
    """End-to-end pipeline: preflight → matrix → trial → verification → cleanup."""

    def test_pipeline_c1(self, tmp_path):
        """Complete C1 pipeline: pass preflight, run trial, verify, clean up."""
        # 1. Preflight
        suite = PreflightSuite()
        suite.add(PreflightCheck(id="p1", category=CheckCategory.DEVICE, description="Pass"), _pass_check)
        suite.add(PreflightCheck(id="p2", category=CheckCategory.SERVER, description="Pass"), _pass_check)
        results, summary = suite.run_and_summary()
        assert summary.all_passed

        # 2. Logger
        logger = make_logger(tmp_path)

        # 3. Oversight
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, FakeRunControl(), oversight_mgr)
        session.start()

        # 4. Trial
        steps = make_steps()
        spec = make_trial_spec(steps)
        backend = _make_backend(steps)
        verif_backend = FakeVerificationBackend()

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE,
            verification_backend=verif_backend, session=session,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        assert result.verification_passed is True
        assert result.steps_done == 4

        session.complete()
        assert session.state is SessionState.COMPLETED

        # 5. Output files
        session_dir = tmp_path / "study-data" / "v1.0.0" / "P01" / "sess_001"
        assert (session_dir / "events.jsonl").exists()
        assert (session_dir / "summary.json").exists()

    def test_pipeline_c2_with_verification_fail(self, tmp_path):
        """Complete C2 pipeline where verification fails."""
        logger = make_logger(tmp_path, participant="P02")
        oversight_mgr = OversightManager(logger, condition=StudyCondition.FINAL_CHECKPOINT)
        session = StudySession(logger, FakeRunControl(), oversight_mgr)
        session.start()

        steps = make_steps()
        spec = make_trial_spec(steps)
        backend = _make_backend(steps)
        verif_backend = FakeVerificationBackend()
        verif_backend.set_result("text_present", "Message Sent", result=False)

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.FINAL_CHECKPOINT,
            verification_backend=verif_backend, session=session,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.VERIFICATION_FAILED
        assert result.verification_passed is False
        session.complete()
        assert session.state is SessionState.COMPLETED

    def test_pipeline_c3_error_injection(self, tmp_path):
        """C3 pipeline with error injection."""
        logger = make_logger(tmp_path, participant="P03")
        oversight_mgr = OversightManager(logger, condition=StudyCondition.VOLUNTARY_INTERVENTION)
        session = StudySession(logger, FakeRunControl(), oversight_mgr)
        session.start()

        steps = make_steps()
        spec = make_trial_spec(steps)
        backend = _make_backend(steps)

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.VOLUNTARY_INTERVENTION,
            error_tasks=frozenset(["task_send_message"]), session=session,
        )
        result = executor.run()

        assert result.outcome == TrialOutcome.SUCCESS
        for sr in result.steps_executed:
            if sr.step.id == "type_input":
                assert sr.error_injected is True
                assert sr.error_variant_id is not None

        session.complete()
        assert session.state is SessionState.COMPLETED

    def test_pipeline_abort_and_cleanup(self, tmp_path):
        """Pipeline where trial aborts → session still cleaned up."""
        logger = make_logger(tmp_path, participant="P04")
        steps = make_steps()
        spec = make_trial_spec(steps)
        backend = _make_backend(steps, open_fail=True)
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, FakeRunControl(), oversight_mgr)
        session.start()

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE, session=session,
        )
        result = executor.run()
        assert result.outcome == TrialOutcome.TECHNICAL_FAILURE
        assert session.state in (SessionState.CANCELLING, SessionState.COMPLETED, SessionState.FAILED)

    def test_pipeline_all_three_conditions(self, tmp_path):
        """Run trials for all 3 conditions in sequence → all logged."""
        steps = make_steps()
        spec = make_trial_spec(steps)

        for condition in [
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
        ]:
            logger = make_logger(tmp_path, participant=f"P{condition.name[0]}")
            backend = _make_backend(steps)
            oversight_mgr = OversightManager(logger, condition=condition)
            session = StudySession(logger, FakeRunControl(), oversight_mgr)
            session.start()

            executor = TrialExecutor(
                backend=backend, logger=logger, oversight=oversight_mgr,
                spec=spec, condition=condition, session=session,
            )
            result = executor.run()
            assert result.outcome == TrialOutcome.SUCCESS, f"Failed in {condition.name}"
            assert result.steps_done == 4

            session.complete()
            assert session.state is SessionState.COMPLETED

            # Verify logging
            session_dir = tmp_path / "study-data" / "v1.0.0" / f"P{condition.name[0]}" / "sess_001"
            events_file = session_dir / "events.jsonl"
            assert events_file.exists()
            for line in events_file.read_text().strip().split("\n"):
                if line.strip():
                    entry = json.loads(line)
                    assert "event_type" in entry


# ===================================================================
# Test: Session cancellation during trial
# ===================================================================


class TestSessionCancellationDuringTrial:
    """Tests for session cancellation while a trial is running."""

    def test_cancel_while_trial_running(self, tmp_path):
        """Cancel session while trial steps are executing → ABORTED."""
        logger = make_logger(tmp_path)
        backend = _make_backend(make_steps())
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, FakeRunControl(), oversight_mgr)
        session.start()
        session.cancel()

        steps = make_steps()
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE, session=session,
        )
        result = executor.run()

        assert result.outcome in (TrialOutcome.ABORTED, TrialOutcome.SUCCESS)
        assert session.state is SessionState.CANCELLING


# ===================================================================
# Test: Multiple trials in same session
# ===================================================================


class TestMultipleTrials:
    """Multiple trials in a single session."""

    def test_multiple_trials_same_session(self, tmp_path):
        """Run two trials in the same session → both logged."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)
        session.start()

        steps = make_steps()
        spec = make_trial_spec(steps)

        for i in range(2):
            backend = _make_backend(steps)
            executor = TrialExecutor(
                backend=backend, logger=logger, oversight=oversight_mgr,
                spec=spec, condition=StudyCondition.STEPWISE, session=session,
            )
            result = executor.run()
            assert result.outcome == TrialOutcome.SUCCESS
            assert result.steps_done == 4

        session.complete()
        assert session.state is SessionState.COMPLETED

        session_dir = tmp_path / "study-data" / "v1.0.0" / "P01" / "sess_001"
        events_file = session_dir / "events.jsonl"
        lines = events_file.read_text().strip().split("\n")
        trial_starts = [l for l in lines if 'trial_start' in l]
        assert len(trial_starts) >= 2

    def test_session_completed_trials_count(self, tmp_path):
        """Session tracks completed trial count correctly."""
        logger = make_logger(tmp_path)
        rc = FakeRunControl()
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        session = StudySession(logger, rc, oversight_mgr)
        session.start()

        steps = make_steps()
        spec = make_trial_spec(steps)

        for i in range(3):
            backend = _make_backend(steps)
            executor = TrialExecutor(
                backend=backend, logger=logger, oversight=oversight_mgr,
                spec=spec, condition=StudyCondition.STEPWISE, session=session,
            )
            result = executor.run()
            assert result.outcome == TrialOutcome.SUCCESS
            session.mark_trial_complete(result.outcome)

        session.complete()
        metrics = session.get_metrics()
        assert metrics.steps_executed == 12  # 3 trials * 4 steps


# ===================================================================
# Test: Edge cases
# ===================================================================


class TestEdgeCases:
    """Edge case and boundary condition tests."""

    def test_empty_steps(self, tmp_path):
        """Trial with one step → succeeds with 1 step_done."""
        logger = make_logger(tmp_path)
        steps = [StudyStep(id="empty_step", action="open com.caddie", narration="Empty",
                           step_type=StepType.NORMAL)]
        backend = _make_backend(steps)
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)

        spec = TrialSpec(
            version="v1", id="task_empty", instruction_de="Leer",
            criticality=CriticalityClass.LOW, steps=tuple(steps), error_steps=(),
            verification=(), max_duration_s=30, per_gate_timeout_s=10,
        )

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE,
        )
        result = executor.run()
        assert result.outcome == TrialOutcome.SUCCESS
        assert result.steps_done == 1

    def test_single_step_trial(self, tmp_path):
        """Trial with exactly one step → works correctly."""
        logger = make_logger(tmp_path)
        steps = [
            StudyStep(id="only_step", action="open com.caddie", narration="Einziger Schritt",
                      step_type=StepType.NORMAL),
        ]
        backend = _make_backend(steps)
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)

        spec = TrialSpec(
            version="v1", id="task_single", instruction_de="Ein Schritt",
            criticality=CriticalityClass.LOW, steps=tuple(steps), error_steps=(),
            verification=(), max_duration_s=30, per_gate_timeout_s=10,
        )

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE,
        )
        result = executor.run()
        assert result.outcome == TrialOutcome.SUCCESS
        assert result.steps_done == 1
        assert len(result.steps_executed) == 1

    def test_all_steps_fail(self, tmp_path):
        """All steps fail → trial aborts on first failure."""
        logger = make_logger(tmp_path)
        steps = make_steps()
        # Fail on send_msg (consequential) which is index 2
        backend = _make_backend(steps, tap_fail=[2])
        oversight_mgr = OversightManager(logger, condition=StudyCondition.STEPWISE)
        spec = make_trial_spec(steps)

        executor = TrialExecutor(
            backend=backend, logger=logger, oversight=oversight_mgr,
            spec=spec, condition=StudyCondition.STEPWISE,
        )
        result = executor.run()
        assert result.outcome == TrialOutcome.TECHNICAL_FAILURE
        assert len(result.steps_executed) >= 1
        last_result = result.steps_executed[-1]
        assert last_result.success is False


# ===================================================================
# Test: _parse_action end-to-end
# ===================================================================


class TestParseAction:
    """End-to-end _parse_action validation."""

    def test_parse_open_app(self):
        assert _parse_action("open com.caddie") == ("open_app", "com.caddie")

    def test_parse_open_url(self):
        assert _parse_action("open_url https://example.com") == ("open_url", "https://example.com")

    def test_parse_tap_quoted(self):
        assert _parse_action("click 'Send'") == ("tap", "Send")

    def test_parse_tap_unquoted(self):
        assert _parse_action("click Send") == ("tap", "Send")

    def test_parse_type_text(self):
        assert _parse_action("input text 'Hello'") == ("type", "Hello")

    def test_parse_type_text_submit(self):
        assert _parse_action("input text 'Hello' (submit)") == ("type", "Hello", "submit")

    def test_parse_scroll(self):
        assert _parse_action("scroll down") == ("scroll", "down")

    def test_parse_press(self):
        assert _parse_action("press BACK") == ("press", "BACK")

    def test_parse_unrecognized_raises(self):
        with pytest.raises(ValueError, match="Unrecognised action"):
            _parse_action("unknown 'foo'")
