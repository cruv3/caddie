"""Tests for caddie.study.verification — post-trial verification."""

import pathlib
import tempfile
from unittest.mock import MagicMock

from caddie.study.logger import StudyLogger, EventType
from caddie.study.model import VerificationRule
from caddie.study.verification import (
    VerificationManager,
    VerificationOutcome,
    VerificationResult,
    VerificationSummary,
    FakeVerificationBackend,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _make_rule(check_type, screenshot_evidence=False, **params):
    return VerificationRule(
        id=f"v_{check_type}",
        assertion=f"Check {check_type}",
        check_type=check_type,
        parameters=params,
        screenshot_evidence=screenshot_evidence,
    )


def _make_manager(rules, backend=None):
    base_dir = pathlib.Path(tempfile.mkdtemp())
    logger = StudyLogger(
        base_dir=base_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test",
        condition="c1_stepwise",
    )
    if backend is None:
        backend = FakeVerificationBackend()
    return VerificationManager(
        logger=logger,
        backend=backend,
        rules=tuple(rules),
        trial_id="trial_001",
    ), logger


# ---------------------------------------------------------------------------
# FakeVerificationBackend
# ---------------------------------------------------------------------------


def test_fake_backend_defaults_to_true():
    backend = FakeVerificationBackend()
    assert backend.check_text_present("Hello") is True
    assert backend.check_text_absent("World") is True
    assert backend.check_accessibility_element("Send") is True
    assert backend.check_field_count("list", 5) is True
    assert backend.capture_screenshot() == "/tmp/fake_screenshot.png"


def test_fake_backend_set_result():
    backend = FakeVerificationBackend()
    backend.set_result("text_present", "Hello", result=False)
    assert backend.check_text_present("Hello") is False
    # Other calls still default to True
    assert backend.check_text_present("World") is True


def test_fake_backend_records_calls():
    backend = FakeVerificationBackend()
    backend.check_text_present("Hello")
    backend.capture_screenshot()
    assert ("check_text_present", "Hello") in backend.calls
    assert ("capture_screenshot",) in backend.calls


# ---------------------------------------------------------------------------
# VerificationOutcome enum
# ---------------------------------------------------------------------------


def test_verification_outcome_values():
    assert VerificationOutcome.PASS.value == "pass"
    assert VerificationOutcome.FAIL.value == "fail"
    assert VerificationOutcome.TIMEOUT.value == "timeout"
    assert VerificationOutcome.SKIP.value == "skip"


# ---------------------------------------------------------------------------
# VerificationSummary
# ---------------------------------------------------------------------------


def test_summary_all_passed():
    summary = VerificationSummary(
        trial_id="t1",
        total_rules=3,
        passed=3,
        failed=0,
        timed_out=0,
        skipped=0,
    )
    assert summary.all_passed is True
    assert summary.completion_rate == 1.0


def test_summary_with_failures():
    summary = VerificationSummary(
        trial_id="t1",
        total_rules=5,
        passed=3,
        failed=2,
        timed_out=0,
        skipped=0,
    )
    assert summary.all_passed is False
    assert summary.completion_rate == 0.6


def test_summary_all_skipped():
    summary = VerificationSummary(
        trial_id="t1",
        total_rules=3,
        passed=0,
        failed=0,
        timed_out=0,
        skipped=3,
    )
    assert summary.all_passed is True  # no non-skipped failures
    assert summary.completion_rate == 0.0


def test_summary_zero_rules():
    summary = VerificationSummary(trial_id="t1", total_rules=0)
    assert summary.completion_rate == 0.0


# ---------------------------------------------------------------------------
# VerificationManager — happy path
# ---------------------------------------------------------------------------


def test_manager_all_pass():
    backend = FakeVerificationBackend()
    backend.set_result("text_present", "Done", result=True)
    rules = [_make_rule("text_present", text="Done")]
    mgr, logger = _make_manager(rules, backend)

    summary = mgr.run()
    assert summary.all_passed is True
    assert summary.passed == 1
    assert summary.failed == 0
    assert len(summary.results) == 1
    assert summary.results[0].outcome == VerificationOutcome.PASS

    # Check logging: start + result + complete = 3 events
    events = logger.get_raw_events()
    assert len(events) == 3
    assert events[0].event_type == EventType.VERIFICATION_START
    assert events[1].event_type == EventType.VERIFICATION_RESULT
    assert events[2].event_type == EventType.VERIFICATION_COMPLETE


def test_manager_fail():
    backend = FakeVerificationBackend()
    backend.set_result("text_present", "Missing", result=False)
    rules = [_make_rule("text_present", text="Missing")]
    mgr, logger = _make_manager(rules, backend)

    summary = mgr.run()
    assert summary.all_passed is False
    assert summary.failed == 1
    assert summary.results[0].outcome == VerificationOutcome.FAIL


def test_manager_multiple_rules():
    backend = FakeVerificationBackend()
    backend.set_result("text_present", "Done", result=True)
    backend.set_result("accessibility", "Send", result=True)
    rules = [
        _make_rule("text_present", text="Done"),
        _make_rule("accessibility_check", label="Send"),
    ]
    mgr, logger = _make_manager(rules, backend)

    summary = mgr.run()
    assert summary.passed == 2
    assert summary.failed == 0


def test_manager_empty_rules():
    backend = FakeVerificationBackend()
    mgr, logger = _make_manager([], backend)

    summary = mgr.run()
    assert summary.total_rules == 0
    assert summary.passed == 0


# ---------------------------------------------------------------------------
# VerificationManager — screenshot evidence
# ---------------------------------------------------------------------------


def test_screenshot_captured_when_evidence_requested():
    backend = FakeVerificationBackend()
    rules = [_make_rule("text_present", text="Done", screenshot_evidence=True)]
    mgr, _ = _make_manager(rules, backend)

    mgr.run()
    result = mgr.get_results()[0]
    assert result.screenshot_path == "/tmp/fake_screenshot.png"
    assert backend.calls[0][0] == "check_text_present"


# ---------------------------------------------------------------------------
# VerificationManager — individual check methods
# ---------------------------------------------------------------------------


def test_check_accessibility():
    backend = FakeVerificationBackend()
    backend.set_result("accessibility", "Send", result=False)
    rules = [_make_rule("accessibility_check", label="Send")]
    mgr, _ = _make_manager(rules, backend)
    summary = mgr.run()
    assert summary.failed == 1


def test_check_text_absent():
    backend = FakeVerificationBackend()
    backend.set_result("text_absent", "Error", result=False)  # text IS present → fail
    rules = [_make_rule("text_absent", text="Error")]
    mgr, _ = _make_manager(rules, backend)
    summary = mgr.run()
    assert summary.failed == 1


def test_check_field_count():
    backend = FakeVerificationBackend()
    backend.set_result("field_count", "list", 3, result=False)
    rules = [_make_rule("field_count", container="list", count=3)]
    mgr, _ = _make_manager(rules, backend)
    summary = mgr.run()
    assert summary.failed == 1


# ---------------------------------------------------------------------------
# VerificationManager — API methods
# ---------------------------------------------------------------------------


def test_get_results():
    backend = FakeVerificationBackend()
    rules = [_make_rule("text_present", text="Done")]
    mgr, _ = _make_manager(rules, backend)
    mgr.run()
    results = mgr.get_results()
    assert len(results) == 1
    assert results[0].rule_id == "v_text_present"


def test_get_summary():
    backend = FakeVerificationBackend()
    rules = [_make_rule("text_present", text="Done")]
    mgr, _ = _make_manager(rules, backend)
    mgr.run()
    summary = mgr.get_summary()
    assert summary.passed == 1


def test_manager_repr():
    backend = FakeVerificationBackend()
    rules = [_make_rule("text_present", text="Done")]
    mgr, _ = _make_manager(rules, backend)
    assert "verification" in repr(mgr).lower()
