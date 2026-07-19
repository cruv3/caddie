"""Tests for caddie.study.screen_off — screen-off micro-trials."""

from __future__ import annotations

import threading
import time

import pytest

from caddie.study.model import InitiationResult, ScreenOffMode
from caddie.study.screen_off import (
    ScreenOffConfig,
    ScreenOffManager,
    ScreenOffResult,
    run_screen_off_block,
)
from caddie.study.logger import StudyLogger


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def make_logger(tmp_path) -> StudyLogger:
    return StudyLogger(
        base_dir=tmp_path / "study-data",
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        condition="c1_stepwise",
    )


# ---------------------------------------------------------------------------
# ScreenOffConfig
# ---------------------------------------------------------------------------


def test_screen_off_config_repr():
    config = ScreenOffConfig(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
    )
    assert "notify_only" in repr(config)
    assert "drt_001" in repr(config)


# ---------------------------------------------------------------------------
# ScreenOffResult
# ---------------------------------------------------------------------------


def test_screen_off_result_success():
    result = ScreenOffResult(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
        display_was_on=False,
        wait_seconds=60,
        result=InitiationResult.SUCCESS,
        elapsed_ms=61000,
        message="OK",
    )
    assert result.success is True
    assert result.result is InitiationResult.SUCCESS


def test_screen_off_result_wake_failed():
    result = ScreenOffResult(
        mode=ScreenOffMode.WAKE_ASK,
        task_id="drt_002",
        display_was_on=False,
        wait_seconds=60,
        result=InitiationResult.WAKE_FAILED,
        elapsed_ms=62000,
        message="Wake failed",
    )
    assert result.success is False


# ---------------------------------------------------------------------------
# ScreenOffManager — NOTIFY_ONLY
# ---------------------------------------------------------------------------


def test_notify_only_no_backend(tmp_path):
    """NOTIFY_ONLY with no backend configured returns success (dry-run)."""
    logger = make_logger(tmp_path)
    mgr = ScreenOffManager(logger, wait_seconds=0)
    config = ScreenOffConfig(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
    )
    result = mgr.run(config)
    assert result.mode is ScreenOffMode.NOTIFY_ONLY
    assert result.task_id == "drt_001"
    assert result.success is True
    assert "dry-run" in result.message


def test_notify_only_with_backend_success(tmp_path):
    """NOTIFY_ONLY with a working backend posts successfully."""
    logger = make_logger(tmp_path)
    call_count = 0

    def notify():
        nonlocal call_count
        call_count += 1
        return True

    mgr = ScreenOffManager(logger, wait_seconds=0, notification_fn=notify)
    config = ScreenOffConfig(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
    )
    result = mgr.run(config)
    assert result.success is True
    assert call_count == 1


def test_notify_only_with_backend_failure(tmp_path):
    """NOTIFY_ONLY with a failing backend returns WAKE_FAILED."""
    logger = make_logger(tmp_path)

    def notify():
        return False

    mgr = ScreenOffManager(logger, wait_seconds=0, notification_fn=notify)
    config = ScreenOffConfig(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
    )
    result = mgr.run(config)
    assert result.result is InitiationResult.WAKE_FAILED


# ---------------------------------------------------------------------------
# ScreenOffManager — WAKE_ASK
# ---------------------------------------------------------------------------


def test_wake_ask_no_backend(tmp_path):
    """WAKE_ASK with no backend returns success (dry-run)."""
    logger = make_logger(tmp_path)
    mgr = ScreenOffManager(logger, wait_seconds=0)
    config = ScreenOffConfig(
        mode=ScreenOffMode.WAKE_ASK,
        task_id="drt_002",
    )
    result = mgr.run(config)
    assert result.success is True
    assert "dry-run" in result.message


def test_wake_ask_success(tmp_path):
    """WAKE_ASK with a working backend returns success."""
    logger = make_logger(tmp_path)

    def wake():
        return True

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake)
    config = ScreenOffConfig(
        mode=ScreenOffMode.WAKE_ASK,
        task_id="drt_002",
    )
    result = mgr.run(config)
    assert result.success is True


def test_wake_ask_failure(tmp_path):
    """WAKE_ASK with a failing backend returns WAKE_FAILED."""
    logger = make_logger(tmp_path)

    def wake():
        return False

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake)
    config = ScreenOffConfig(
        mode=ScreenOffMode.WAKE_ASK,
        task_id="drt_002",
    )
    result = mgr.run(config)
    assert result.result is InitiationResult.WAKE_FAILED


# ---------------------------------------------------------------------------
# ScreenOffManager — WAKE_EXECUTE
# ---------------------------------------------------------------------------


def test_wake_execute_no_backend(tmp_path):
    """WAKE_EXECUTE with no backend returns success (dry-run)."""
    logger = make_logger(tmp_path)
    mgr = ScreenOffManager(logger, wait_seconds=0)
    config = ScreenOffConfig(
        mode=ScreenOffMode.WAKE_EXECUTE,
        task_id="drt_003",
        step_index=0,
    )
    result = mgr.run(config)
    assert result.success is True
    assert "dry-run" in result.message


# ---------------------------------------------------------------------------
# ScreenOffManager — check_display
# ---------------------------------------------------------------------------


def test_check_display_fn_called(tmp_path):
    """The check_display_fn is called to determine display state."""
    logger = make_logger(tmp_path)
    display_states = [True, False]
    call_count = 0

    def check_display():
        nonlocal call_count
        state = display_states[call_count]
        call_count += 1
        return state

    mgr = ScreenOffManager(logger, wait_seconds=0, check_display_fn=check_display)
    config = ScreenOffConfig(
        mode=ScreenOffMode.NOTIFY_ONLY,
        task_id="drt_001",
    )
    result = mgr.run(config)
    assert call_count >= 1


# ---------------------------------------------------------------------------
# run_screen_off_block
# ---------------------------------------------------------------------------


def test_run_screen_off_block_multiple(tmp_path):
    """Running a block of multiple micro-trials."""
    logger = make_logger(tmp_path)
    mgr = ScreenOffManager(logger, wait_seconds=0)

    configs = [
        ScreenOffConfig(mode=ScreenOffMode.NOTIFY_ONLY, task_id="drt_001"),
        ScreenOffConfig(mode=ScreenOffMode.WAKE_ASK, task_id="drt_002"),
    ]

    results = run_screen_off_block(mgr, configs)
    assert len(results) == 2
    assert results[0].task_id == "drt_001"
    assert results[1].task_id == "drt_002"


# ---------------------------------------------------------------------------
# WAKE_ASK with confirmation callback
# ---------------------------------------------------------------------------


def test_wake_ask_confirmation_accepted(tmp_path):
    """WAKE_ASK with accepted confirmation returns success."""
    logger = make_logger(tmp_path)

    def wake():
        return True

    def confirm():
        return True

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake, show_confirmation_fn=confirm)
    config = ScreenOffConfig(mode=ScreenOffMode.WAKE_ASK, task_id="drt_002")
    result = mgr.run(config)
    assert result.success is True
    assert "confirmation accepted" in result.message.lower()


def test_wake_ask_confirmation_declined(tmp_path):
    """WAKE_ASK with declined confirmation returns NO_RESPONSE."""
    logger = make_logger(tmp_path)

    def wake():
        return True

    def confirm():
        return False

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake, show_confirmation_fn=confirm)
    config = ScreenOffConfig(mode=ScreenOffMode.WAKE_ASK, task_id="drt_002")
    result = mgr.run(config)
    assert result.result is InitiationResult.NO_RESPONSE


# ---------------------------------------------------------------------------
# WAKE_EXECUTE with step callback
# ---------------------------------------------------------------------------


def test_wake_execute_step_success(tmp_path):
    """WAKE_EXECUTE with successful step execution returns success."""
    logger = make_logger(tmp_path)

    def wake():
        return True

    def execute():
        return True

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake, execute_step_fn=execute)
    config = ScreenOffConfig(mode=ScreenOffMode.WAKE_EXECUTE, task_id="drt_003")
    result = mgr.run(config)
    assert result.success is True


def test_wake_execute_step_failure(tmp_path):
    """WAKE_EXECUTE with failed step execution returns WAKE_FAILED."""
    logger = make_logger(tmp_path)

    def wake():
        return True

    def execute():
        return False

    mgr = ScreenOffManager(logger, wait_seconds=0, wake_display_fn=wake, execute_step_fn=execute)
    config = ScreenOffConfig(mode=ScreenOffMode.WAKE_EXECUTE, task_id="drt_003")
    result = mgr.run(config)
    assert result.result is InitiationResult.WAKE_FAILED


# ---------------------------------------------------------------------------
# Cancellation
# ---------------------------------------------------------------------------


def test_cancel_during_run(tmp_path):
    """Cancel interrupts a running screen-off micro-trial."""
    logger = make_logger(tmp_path)
    blocked = threading.Event()

    def notify():
        blocked.set()
        time.sleep(2)  # Simulate long notification
        return True

    mgr = ScreenOffManager(logger, wait_seconds=10, notification_fn=notify)

    def start_and_cancel():
        mgr.run(ScreenOffConfig(mode=ScreenOffMode.NOTIFY_ONLY, task_id="drt_001"))

    t = threading.Thread(target=start_and_cancel)
    t.start()
    blocked.wait()
    time.sleep(0.1)
    mgr.cancel()
    t.join(timeout=3)
    assert t.is_alive() is False
