"""Tests for caddie.study.logger — JSONL event logging and trial summaries."""

import json
import time
import uuid
import pathlib

import pytest

from caddie.study.logger import (
    STUDY_VERSION,
    EventType,
    MonotonicClock,
    StudyEvent,
    StudyLogger,
    create_study_logger,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def event_dir(tmp_path) -> pathlib.Path:
    """Temporary study-data directory for logger tests."""
    d = tmp_path / "study-data" / "v1.0.0"
    d.mkdir(parents=True, exist_ok=True)
    return d


@pytest.fixture
def logger(event_dir):
    return StudyLogger(
        base_dir=event_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_test_001",
        condition="c1_stepwise",
    )


# ---------------------------------------------------------------------------
# STUDY_VERSION
# ---------------------------------------------------------------------------


def test_study_version_is_string():
    assert isinstance(STUDY_VERSION, str)
    assert len(STUDY_VERSION) > 0


# ---------------------------------------------------------------------------
# EventType
# ---------------------------------------------------------------------------


def test_event_type_trial_start():
    assert EventType.TRIAL_START == "trial_start"


def test_event_type_all_includes_all():
    """All constants are present in the ALL frozenset."""
    all_events = {
        EventType.SESSION_STARTED, EventType.SESSION_PAUSED,
        EventType.SESSION_RESUMED, EventType.SESSION_CANCELLED,
        EventType.SESSION_FAILED, EventType.SESSION_ENDED,
        EventType.TRIAL_START, EventType.STEP_START, EventType.STEP_FINISH,
        EventType.TRIAL_COMPLETE,
        EventType.CONFIRMATION_SHOWN, EventType.CONFIRMATION_RESOLVED,
        EventType.PAUSE, EventType.RESUME, EventType.STOP,
        EventType.CORRECTION, EventType.TOUCH_INTERVENTION,
        EventType.ERROR_INJECTED,
        EventType.VERIFICATION_START, EventType.VERIFICATION_COMPLETE, EventType.VERIFICATION_RESULT,
        EventType.SCREEN_OFF_INITIATION,
        EventType.TECHNICAL_FAILURE, EventType.REPEAT, EventType.EXCLUSION,
        EventType.SCREENSHOT_CAPTURED, EventType.EXPERIMENTER_ACTION,
    }
    assert EventType.ALL == all_events


# ---------------------------------------------------------------------------
# StudyEvent
# ---------------------------------------------------------------------------


def test_study_event_creation(logger):
    ctx = logger._event_context()
    event = StudyEvent(
        event_type=EventType.STEP_START,
        timestamp="2026-07-20T10:00:00+00:00",
        elapsed_ms=123.4,
        study_version=ctx["study_version"],
        participant_id=ctx["participant_id"],
        session_id=ctx["session_id"],
        step_id="open_chat",
        narration="Chat oeffnen...",
    )
    assert event.event_type == EventType.STEP_START
    assert event.step_id == "open_chat"
    assert event.narration == "Chat oeffnen..."


def test_study_event_defaults():
    event = StudyEvent(
        event_type=EventType.TRIAL_START,
        timestamp="2026-07-20T10:00:00+00:00",
        elapsed_ms=0.0,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
    )
    assert event.block is None
    assert event.task_id is None
    assert event.condition is None
    assert event.trial_id is None
    assert event.step_id is None
    assert event.narration is None
    assert event.action is None
    assert event.details == {}


def test_study_event_unknown_type_raises():
    with pytest.raises(ValueError, match="Unknown event type"):
        StudyEvent(
            event_type="funky_type",
            timestamp="2026-07-20T10:00:00+00:00",
            elapsed_ms=0.0,
            study_version="v1.0.0",
            participant_id="P01",
            session_id="sess_001",
        )


def test_study_event_to_dict(logger):
    event = StudyEvent(
        event_type=EventType.STEP_START,
        timestamp="2026-07-20T10:00:00+00:00",
        elapsed_ms=100.0,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        step_id="s1",
        narration="Test narration",
        details={"key": "value"},
    )
    d = event.to_dict()
    assert d["event_type"] == EventType.STEP_START
    assert d["step_id"] == "s1"
    assert d["narration"] == "Test narration"
    assert d["details"] == {"key": "value"}
    assert d["study_version"] == "v1.0.0"


def test_study_event_to_json_line(logger):
    event = StudyEvent(
        event_type=EventType.STEP_START,
        timestamp="2026-07-20T10:00:00+00:00",
        elapsed_ms=100.0,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
    )
    line = event.to_json_line()
    d = json.loads(line)
    assert d["event_type"] == EventType.STEP_START
    assert d["participant_id"] == "P01"


# ---------------------------------------------------------------------------
# MonotonicClock
# ---------------------------------------------------------------------------


def test_monotonic_clock_start_near_zero():
    clock = MonotonicClock()
    # Immediately after creation, elapsed should be very close to 0
    assert clock.elapsed_ms() < 10  # within 10 ms


def test_monotonic_clock_increases():
    clock = MonotonicClock()
    time.sleep(0.05)  # 50 ms
    elapsed = clock.elapsed_ms()
    assert elapsed >= 40  # allow some jitter


def test_monotonic_clock_reset():
    clock = MonotonicClock()
    time.sleep(0.05)
    clock.reset()
    # After reset, elapsed should be near zero again
    assert clock.elapsed_ms() < 20


def test_monotonic_clock_thread_safe():
    """Multiple threads calling elapsed_ms() should not raise."""
    clock = MonotonicClock()

    def read():
        for _ in range(100):
            _ = clock.elapsed_ms()

    import threading
    threads = [threading.Thread(target=read) for _ in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()


# ---------------------------------------------------------------------------
# StudyLogger — creation and paths
# ---------------------------------------------------------------------------


def test_logger_creates_directories(event_dir):
    logger = StudyLogger(
        base_dir=event_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        condition="c1_stepwise",
    )
    assert logger.participant_dir.exists()
    assert logger.screenshots_dir.exists()
    assert logger.events_path.exists()
    assert logger.events_path.read_text() == ""


def test_logger_paths_correct(tmp_path):
    base_dir = tmp_path / "study-data"
    logger = StudyLogger(
        base_dir=base_dir,
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        condition="c1_stepwise",
    )
    expected = base_dir / "v1.0.0" / "P01" / "sess_001"
    assert logger.events_path == expected / "events.jsonl"
    assert logger.screenshots_dir == expected / "screenshots"


# ---------------------------------------------------------------------------
# StudyLogger — writing and reading events
# ---------------------------------------------------------------------------


def test_log_writes_jsonl_line(logger):
    logger.log(
        EventType.TRIAL_START,
        task_id="task_music",
        trial_id="trial_001",
        block="main",
        variant="normal",
    )
    lines = logger.get_raw_events()
    assert len(lines) == 1
    assert lines[0].event_type == EventType.TRIAL_START
    assert lines[0].task_id == "task_music"
    assert lines[0].trial_id == "trial_001"


def test_log_multiple_events(logger):
    for i in range(5):
        logger.log(
            EventType.STEP_START,
            step_id=f"step_{i}",
            narration=f"Schritt {i}",
        )
    events = logger.get_raw_events()
    assert len(events) == 5
    assert all(e.event_type == EventType.STEP_START for e in events)


def test_log_event_has_required_fields(logger):
    logger.log(EventType.STEP_START, step_id="s1", narration="Test")
    events = logger.get_raw_events()
    assert len(events) == 1
    e = events[0]
    assert e.study_version == "v1.0.0"
    assert e.participant_id == "P01"
    assert e.session_id == "sess_test_001"
    assert e.condition == "c1_stepwise"
    assert isinstance(e.elapsed_ms, float)
    assert e.elapsed_ms >= 0


def test_log_event_timestamp_is_iso(tmp_path):
    logger = StudyLogger(
        base_dir=tmp_path / "study-data" / "v1.0.0",
        study_version="v1.0.0",
        participant_id="P01",
        session_id="sess_001",
        condition="c1_stepwise",
    )
    logger.log(EventType.TRIAL_START)
    events = logger.get_raw_events()
    # ISO format should parse without error
    datetime_ = events[0].timestamp
    # Just check it contains standard ISO markers
    assert "T" in datetime_ or "Z" in datetime_


def test_log_event_elapsed_time_increases(logger):
    import time
    logger.log(EventType.STEP_START, step_id="s1")
    time.sleep(0.02)
    logger.log(EventType.STEP_FINISH, step_id="s1")
    events = logger.get_raw_events()
    assert events[1].elapsed_ms > events[0].elapsed_ms


# ---------------------------------------------------------------------------
# StudyLogger — trial lifecycle
# ---------------------------------------------------------------------------


def test_trial_start_returns_id(logger):
    trial_id = logger.trial_start("task_music")
    assert trial_id is not None
    assert trial_id.startswith("trial_")
    events = logger.get_raw_events()
    assert events[-1].event_type == EventType.TRIAL_START
    assert events[-1].task_id == "task_music"
    assert events[-1].trial_id == trial_id


def test_trial_start_auto_increments(logger):
    t1 = logger.trial_start("task_music")
    t2 = logger.trial_start("task_gallery")
    assert t1 != t2
    # Extract numeric part
    n1 = int(t1.split("_")[1])
    n2 = int(t2.split("_")[1])
    assert n2 == n1 + 1


def test_trial_complete_logs_event(logger):
    trial_id = logger.trial_start("task_music")
    logger.trial_complete(
        trial_id,
        outcome="success",
        total_steps=6,
        errors_injected=1,
        duration_ms=12345.0,
    )
    events = logger.get_raw_events()
    complete_events = [e for e in events if e.event_type == EventType.TRIAL_COMPLETE]
    assert len(complete_events) == 1
    d = complete_events[0].details
    assert d["outcome"] == "success"
    assert d["total_steps"] == 6


# ---------------------------------------------------------------------------
# StudyLogger — step events
# ---------------------------------------------------------------------------


def test_step_start_and_finish(logger):
    logger.step_start("open_chat", "Chat oeffnen...")
    logger.step_finish("open_chat", action="open_app")
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.STEP_START
    assert events[0].step_id == "open_chat"
    assert events[0].narration == "Chat oeffnen..."
    assert events[1].event_type == EventType.STEP_FINISH
    assert events[1].action == "open_app"


# ---------------------------------------------------------------------------
# StudyLogger — confirmation events
# ---------------------------------------------------------------------------


def test_confirmation_shown_and_resolved(logger):
    logger.confirmation_shown("send_msg", "Nachricht senden?")
    logger.confirmation_resolved(
        "send_msg",
        accepted=True,
        response_latency_ms=2300.0,
    )
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.CONFIRMATION_SHOWN
    assert events[1].event_type == EventType.CONFIRMATION_RESOLVED
    assert events[1].details["accepted"] is True
    assert events[1].details["response_latency_ms"] == 2300.0


def test_confirmation_resolved_denied(logger):
    logger.confirmation_resolved(
        "send_msg",
        accepted=False,
    )
    events = logger.get_raw_events()
    assert events[0].details["accepted"] is False


# ---------------------------------------------------------------------------
# StudyLogger — interventions
# ---------------------------------------------------------------------------


def test_intervention_pause(logger):
    logger.intervention("pause", latency_ms=500.0)
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.PAUSE
    assert events[0].details["intervention_latency_ms"] == 500.0


def test_intervention_stop(logger):
    logger.intervention("stop")
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.STOP


def test_intervention_correction(logger):
    logger.intervention("correction", details={"from": "Alice", "to": "Bob"})
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.CORRECTION
    assert events[0].details["from"] == "Alice"
    assert events[0].details["to"] == "Bob"


# ---------------------------------------------------------------------------
# StudyLogger — error injection
# ---------------------------------------------------------------------------


def test_error_injected(logger):
    logger.error_injected(
        "enter_amount",
        error_variant_id="ev_wrong_amount",
        field="amount",
        wrong_value="50.00",
        correct_value="100.00",
    )
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.ERROR_INJECTED
    d = events[0].details
    assert d["error_variant_id"] == "ev_wrong_amount"
    assert d["wrong_value"] == "50.00"
    assert d["correct_value"] == "100.00"


# ---------------------------------------------------------------------------
# StudyLogger — verification
# ---------------------------------------------------------------------------


def test_verification_result_passed(logger):
    logger.verification_result("v1", passed=True)
    events = logger.get_raw_events()
    assert events[0].details["passed"] is True


def test_verification_result_failed(logger):
    logger.verification_result("v1", passed=False, screenshot_path="screenshots/err.png")
    events = logger.get_raw_events()
    assert events[0].details["passed"] is False
    assert events[0].details["screenshot_path"] == "screenshots/err.png"


# ---------------------------------------------------------------------------
# StudyLogger — screen-off
# ---------------------------------------------------------------------------


def test_screen_off_initiation_notify_only(logger):
    logger.screen_off_initiation(
        "task_weather",
        mode="notify_only",
        display_was_off=True,
        wake_succeeded=True,
    )
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.SCREEN_OFF_INITIATION
    d = events[0].details
    assert d["mode"] == "notify_only"
    assert d["display_was_off"] is True


def test_screen_off_initiation_wake_failed(logger):
    logger.screen_off_initiation(
        "task_weather",
        mode="wake_ask",
        display_was_off=True,
        wake_succeeded=False,
    )
    events = logger.get_raw_events()
    assert events[0].details["wake_succeeded"] is False


# ---------------------------------------------------------------------------
# StudyLogger — failure/meta events
# ---------------------------------------------------------------------------


def test_technical_failure(logger):
    logger.technical_failure(error="Accessibility service unavailable")
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.TECHNICAL_FAILURE
    assert "Accessibility" in events[0].details["error"]


def test_repeat(logger):
    logger.repeat("trial_001", reason="Replay target mismatch")
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.REPEAT
    assert events[0].details["repeat_reason"] == "Replay target mismatch"


def test_exclusion(logger):
    logger.exclusion(reason="Participant spoke solution")
    events = logger.get_raw_events()
    assert events[0].event_type == EventType.EXCLUSION
    assert events[0].details["exclusion_reason"] == "Participant spoke solution"


# ---------------------------------------------------------------------------
# StudyLogger — screenshot tracking
# ---------------------------------------------------------------------------


def test_screenshot_captured_returns_path(logger):
    rel = logger.screenshot_captured("pre_trial")
    assert rel.startswith("screenshots/")
    assert rel.endswith(".png")
    events = logger.get_raw_events()
    assert events[-1].event_type == EventType.SCREENSHOT_CAPTURED
    assert events[-1].details["screenshot_label"] == "pre_trial"


# ---------------------------------------------------------------------------
# StudyLogger — write_summary
# ---------------------------------------------------------------------------


def test_write_summary_creates_file(logger):
    trial_id = logger.trial_start("task_music")
    for i in range(3):
        logger.step_start(f"s{i}", f"Step {i}")
        logger.step_finish(f"s{i}", action=f"action_{i}")
    logger.confirmation_shown("s2", "Confirm?")
    logger.confirmation_resolved("s2", accepted=True, response_latency_ms=2000)

    summary_path = logger.write_summary(
        outcome="success",
        total_steps=3,
        errors_injected=0,
        duration_ms=15000.0,
        trial_ids=[trial_id],
        verification_results={"v1": True},
        screenshots=["screenshots/p1.png"],
        notes="Pilot trial, passed.",
    )

    assert summary_path.exists()
    data = json.loads(summary_path.read_text(encoding="utf-8"))
    assert data["outcome"] == "success"
    assert data["total_steps"] == 3
    assert data["participant_id"] == "P01"
    assert data["condition"] == "c1_stepwise"
    assert data["step_event_count"] == 6  # 3 start + 3 finish
    assert data["confirmation_event_count"] == 2
    assert data["screenshots"] == ["screenshots/p1.png"]
    assert data["raw_events_path"] == "P01/sess_test_001/events.jsonl"


def test_write_summary_includes_metadata(logger):
    trial_id = logger.trial_start("task_music")
    logger.write_summary(
        outcome="success",
        trial_ids=[trial_id],
        metadata={"git_commit": "abc123", "apk_hash": "def456"},
    )
    data = json.loads(logger.session_dir.joinpath("summary.json").read_text())
    assert data["metadata"]["git_commit"] == "abc123"
    assert data["metadata"]["apk_hash"] == "def456"


# ---------------------------------------------------------------------------
# StudyLogger — get_raw_events from file
# ---------------------------------------------------------------------------


def test_get_raw_events_reads_from_file(logger):
    logger.log(EventType.TRIAL_START, task_id="task_music")
    logger.log(EventType.STEP_START, step_id="s1", narration="Test")
    events = logger.get_raw_events()
    assert len(events) == 2
    assert events[0].task_id == "task_music"
    assert events[1].step_id == "s1"


def test_get_raw_events_empty_file(tmp_path):
    logger = StudyLogger(
        base_dir=tmp_path / "study-data" / "v1.0.0",
        study_version="v1.0.0",
        participant_id="P02",
        session_id="sess_002",
        condition="c2_final_checkpoint",
    )
    assert logger.get_raw_events() == []


# ---------------------------------------------------------------------------
# create_study_logger factory
# ---------------------------------------------------------------------------


def test_create_study_logger_generates_uuid_session(event_dir):
    log = create_study_logger(
        base_dir=event_dir,
        study_version="v1.0.0",
        participant_id="P03",
        condition="c3_voluntary_intervention",
    )
    # session_id should be auto-generated
    assert len(log._session_id) == 12  # hex[:12]


def test_create_study_logger_with_session_id(event_dir):
    log = create_study_logger(
        base_dir=event_dir,
        study_version="v1.0.0",
        participant_id="P03",
        session_id="custom_sess",
        condition="c3_voluntary_intervention",
    )
    assert log._session_id == "custom_sess"


# ── Concurrency and correlation ────────────────────────────────────────────


def test_logger_concurrent_reads_during_writes(tmp_path):
    """Concurrent reads via get_raw_events should not raise while writes are happening."""
    import threading
    import time

    logger = StudyLogger(
        base_dir=tmp_path,
        study_version="v1",
        participant_id="P01",
        session_id="s1",
    )

    errors = []

    def writer():
        try:
            logger.trial_start("task_1", trial_id="trial_1")
            logger.step_start("step_open", "App öffnen", trial_id="trial_1", task_id="task_1")
            time.sleep(0.05)
            logger.step_finish("step_open", trial_id="trial_1", task_id="task_1")
            logger.trial_complete("trial_1", "completed", task_id="task_1")
        except Exception as e:
            errors.append(str(e))

    def reader():
        try:
            for _ in range(20):
                events = logger.get_raw_events()
                time.sleep(0.01)
        except Exception as e:
            errors.append(str(e))

    t_writer = threading.Thread(target=writer)
    t_reader = threading.Thread(target=reader)

    t_writer.start()
    t_reader.start()
    t_reader.join(timeout=5)
    t_writer.join(timeout=5)

    assert not errors, f"Concurrent access errors: {errors}"
    events = logger.get_raw_events()
    assert len(events) >= 3  # trial_start, step_start, step_finish


def test_trial_complete_preserves_task_id(tmp_path):
    """trial_complete should accept and log task_id."""
    logger = StudyLogger(
        base_dir=tmp_path,
        study_version="v1",
        participant_id="P01",
        session_id="s1",
    )
    logger.trial_start("task_bank", trial_id="trial_001")
    logger.step_start("step_open", "Bank öffnen", trial_id="trial_001", task_id="task_bank")
    logger.step_finish("step_open", trial_id="trial_001", task_id="task_bank")
    logger.trial_complete("trial_001", "completed", task_id="task_bank")

    events = logger.get_raw_events()
    # Find the trial_complete event
    complete_events = [e for e in events if e.event_type == EventType.TRIAL_COMPLETE]
    assert len(complete_events) == 1
    assert complete_events[0].trial_id == "trial_001"
    assert complete_events[0].task_id == "task_bank"


def test_screenshot_captured_uses_trial_id_and_uuid(tmp_path):
    """screenshot_captured should include trial_id and UUID in filename."""
    logger = StudyLogger(
        base_dir=tmp_path,
        study_version="v1",
        participant_id="P01",
        session_id="s1",
    )
    path = logger.screenshot_captured("post_step", trial_id="trial_001")

    assert "screenshots/" in path
    assert "P01" in path
    assert "trial_001" in path
    # UUID should be present (8 hex chars)
    parts = path.split("/")[-1]  # filename
    uuid_part = parts.split("_")[-1].replace(".png", "")
    assert len(uuid_part) == 8, f"Expected 8-char UUID, got {uuid_part}"
    assert all(c in "0123456789abcdef" for c in uuid_part.lower()), "Invalid hex UUID"


def test_screenshot_captured_unique_names(tmp_path):
    """Consecutive screenshots with same label should produce unique filenames."""
    logger = StudyLogger(
        base_dir=tmp_path,
        study_version="v1",
        participant_id="P01",
        session_id="s1",
    )
    paths = set()
    for i in range(10):
        p = logger.screenshot_captured("verify", trial_id="trial_001")
        paths.add(p)

    assert len(paths) == 10, f"Expected 10 unique paths, got {len(paths)}"
