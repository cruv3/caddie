"""Tests for caddie.study.session — session lifecycle and RunControl ownership."""

from __future__ import annotations

import pathlib
import threading
import time

import pytest

from caddie.study.session import SessionManager, SessionState, StudySession
from caddie.study.logger import StudyLogger
from caddie.study.oversight import OversightManager
from caddie.study.model import StudyCondition


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


class FakeRunControl:
    """Minimal fake of RunControl for testing."""

    def __init__(self) -> None:
        self._state = "running"
        self._paused = False
        self._stop_requested = False
        self._confirm_approved: bool | None = None
        self._confirm_set = threading.Event()
        self._confirm_set.set()

    def request_pause(self, *, intervention: bool = False) -> None:
        self._state = "paused"
        self._paused = True

    def request_resume(self) -> None:
        self._state = "running"
        self._paused = False

    def request_stop(self) -> None:
        self._state = "stopped"
        self._stop_requested = True

    def resolve_confirmation(self, approved: bool) -> None:
        self._confirm_approved = approved
        self._confirm_set.set()

    def await_confirmation(self, timeout: float) -> bool:
        self._confirm_set.clear()
        self._confirm_set.wait(timeout)
        return bool(self._confirm_approved)

    def wait_while_paused(self) -> None:
        self._confirm_set.wait()


def make_logger(tmp_path) -> StudyLogger:
    return StudyLogger(
        base_dir=tmp_path / "study-data",
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        condition="c1_stepwise",
    )


# ---------------------------------------------------------------------------
# StudySession — lifecycle transitions
# ---------------------------------------------------------------------------


def test_session_start_idle_to_running(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    om = OversightManager(logger, condition=StudyCondition.STEPWISE)
    session = StudySession(logger, rc, om)
    session.start()
    assert session.state is SessionState.RUNNING


def test_session_start_already_running_raises(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    with pytest.raises(RuntimeError):
        session.start()


def test_session_pause(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.pause()
    assert session.state is SessionState.PAUSED


def test_session_resume(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.pause()
    session.resume()
    assert session.state is SessionState.RUNNING


def test_session_cancel(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.cancel()
    assert session.state is SessionState.CANCELLING


def test_session_complete(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.complete()
    assert session.state is SessionState.COMPLETED
    assert session.is_terminal


def test_session_fail(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.fail("something went wrong")
    assert session.state is SessionState.FAILED
    assert session.is_terminal


# ---------------------------------------------------------------------------
# StudySession — progress tracking
# ---------------------------------------------------------------------------


def test_mark_step_executed(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.mark_step_executed(0)
    assert session.steps_executed == 1
    assert session.current_step_index == 0
    session.mark_step_executed(1)
    assert session.steps_executed == 2
    assert session.current_step_index == 1


def test_update_oversight_pending(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.update_oversight_pending(3)
    assert session.pending_oversight_steps == 3


def test_mark_verification_pending(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.mark_verification_pending()
    assert session.verification_pending is True


# ---------------------------------------------------------------------------
# StudySession — confirm/touch handling
# ---------------------------------------------------------------------------


def test_resolve_confirmation(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    session = StudySession(logger, rc, OversightManager(logger))
    session.start()
    session.resolve_confirmation(True)
    assert rc._confirm_approved is True


def test_wait_for_confirmation_approved(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    session = StudySession(logger, rc, OversightManager(logger))
    session.start()
    session.resolve_confirmation(True)
    assert session.wait_for_confirmation(timeout=0.5) is True


def test_wait_for_confirmation_denied(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    session = StudySession(logger, rc, OversightManager(logger))
    session.start()
    session.resolve_confirmation(False)
    assert session.wait_for_confirmation(timeout=0.5) is False


# ---------------------------------------------------------------------------
# StudySession — hard cleanup
# ---------------------------------------------------------------------------


def test_cleanup_calls_stop_on_run(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    session = StudySession(logger, rc, OversightManager(logger))
    session.start()
    session.complete()
    assert rc._stop_requested is True


def test_cleanup_ensures_single_call(tmp_path):
    logger = make_logger(tmp_path)
    session = StudySession(logger, FakeRunControl(), OversightManager(logger))
    session.start()
    session.complete()
    # Second call should be a no-op
    session.complete()
    assert session.state is SessionState.COMPLETED


# ---------------------------------------------------------------------------
# SessionMetrics — snapshot
# ---------------------------------------------------------------------------


def test_metrics_snapshot(tmp_path):
    logger = make_logger(tmp_path)
    rc = FakeRunControl()
    session = StudySession(logger, rc, OversightManager(logger))
    session.start()
    session.mark_step_executed(0)
    session.update_oversight_pending(2)
    session.mark_verification_pending()
    session.pause()
    from caddie.study.session import SessionMetrics
    metrics = SessionMetrics.snapshot(session)
    assert metrics.steps_executed == 1
    assert metrics.pending_oversight_steps == 2
    assert metrics.verification_pending is True
    assert metrics.is_paused is True
    assert metrics.current_step_index == 0


# ---------------------------------------------------------------------------
# SessionManager — singleton-like lifecycle
# ---------------------------------------------------------------------------


def test_session_manager_create(tmp_path):
    logger = make_logger(tmp_path)
    mgr = SessionManager()
    session = mgr.create(
        logger=logger,
        run_control=FakeRunControl(),
        oversight_manager=OversightManager(logger),
    )
    assert session is not None
    assert mgr.session is session


def test_session_manager_double_create_raises(tmp_path):
    logger = make_logger(tmp_path)
    mgr = SessionManager()
    mgr.create(
        logger=logger,
        run_control=FakeRunControl(),
        oversight_manager=OversightManager(logger),
    )
    with pytest.raises(RuntimeError):
        mgr.create(
            logger=logger,
            run_control=FakeRunControl(),
            oversight_manager=OversightManager(logger),
        )


def test_session_manager_clear(tmp_path):
    logger = make_logger(tmp_path)
    mgr = SessionManager()
    mgr.create(
        logger=logger,
        run_control=FakeRunControl(),
        oversight_manager=OversightManager(logger),
    )
    assert mgr.session is not None
    mgr.clear()
    assert mgr.session is None


# ---------------------------------------------------------------------------
# Concurrent pause/session access
# ---------------------------------------------------------------------------


def test_session_concurrent_pause_and_step():
    """Session handles concurrent pause requests and step marking."""
    import threading
    from caddie.study.logger import StudyLogger
    from caddie.study.model import StudyCondition
    from caddie.study.oversight import OversightManager
    from caddie.study.session import StudySession
    from caddie.agent.run_control import RunControl

    logger = StudyLogger(base_dir=pathlib.Path("tmp"))
    run_control = RunControl()
    oversight = OversightManager(logger, StudyCondition.STEPWISE)
    session = StudySession(logger, run_control, oversight)
    session.start()

    errors = []

    def pause_then_resume():
        try:
            import time
            time.sleep(0.05)
            session.request_pause()
            time.sleep(0.1)
            session.resume()
        except Exception as e:
            errors.append(e)

    def mark_steps():
        try:
            import time
            for i in range(5):
                time.sleep(0.05)
                session.mark_step_executed(i)
        except Exception as e:
            errors.append(e)

    t1 = threading.Thread(target=pause_then_resume)
    t2 = threading.Thread(target=mark_steps)
    t1.start()
    t2.start()
    t1.join(timeout=2)
    t2.join(timeout=2)

    assert len(errors) == 0, f"Concurrent errors: {errors}"
    session.complete()


# ---------------------------------------------------------------------------
# Logger trial-ID uniqueness under concurrent access
# ---------------------------------------------------------------------------


def test_logger_unique_trial_ids():
    """Multiple concurrent trial_start calls produce unique IDs."""
    import threading
    from caddie.study.logger import StudyLogger

    logger = StudyLogger(base_dir=pathlib.Path("tmp"))
    trial_ids = []
    errors = []

    def start_trials(count):
        ids = []
        try:
            for _ in range(count):
                tid = logger.trial_start(
                    task_id=f"task_{threading.current_thread().name}",
                )
                ids.append(tid)
        except Exception as e:
            errors.append(e)
        trial_ids.extend(ids)

    threads = [threading.Thread(target=start_trials, args=(10,)) for _ in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=5)

    assert len(errors) == 0, f"Errors: {errors}"
    assert len(trial_ids) == 40
    assert len(set(trial_ids)) == 40, f"Duplicate trial IDs: {len(trial_ids) - len(set(trial_ids))} duplicates"
