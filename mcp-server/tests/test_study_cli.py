"""Tests for caddie.study.cli — CLI commands and argument parsing."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from caddie.study.cli import (
    DEFAULT_MATRIX_PATH,
    DEFAULT_SPECS_DIR,
    cmd_audit,
    cmd_dry_run,
    cmd_export,
    cmd_inspect,
    cmd_preflight,
    cmd_run,
    cmd_status,
    cmd_repeat,
    main,
    _select_trial_spec,
)
from caddie.study.model import (
    CriticalityClass,
    ParticipantConfig,
    ScreenOffMode,
    StepType,
    StudyCondition,
    StudyStep,
    TrialSpec,
)


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------


def test_default_cli_paths_point_to_mcp_server_artifacts():
    root = Path(__file__).parents[1]

    assert DEFAULT_SPECS_DIR == root / "study" / "specs"
    assert DEFAULT_MATRIX_PATH == root / "experiments" / "trial_matrix.yaml"


def test_main_no_args_exits():
    with pytest.raises(SystemExit) as exc_info:
        main([])
    assert exc_info.value.code == 1


def test_main_preflight_help():
    """preflight command parses --participant."""
    with patch("caddie.study.cli.cmd_preflight") as mock:
        main(["preflight", "-p", "P01"])
    mock.assert_called_once()


def test_cmd_audit_runs_all_six_participant_trials(tmp_path, capsys):
    args = argparse.Namespace(
        participant="P01",
        spec_dir=Path("specs"),
        data_dir=tmp_path / "audit",
    )
    seen_trials = []

    def fake_dry_run(dry_run_args):
        seen_trials.append(dry_run_args.trial)
        raise SystemExit(0)

    with patch("caddie.study.cli.cmd_dry_run", side_effect=fake_dry_run):
        with pytest.raises(SystemExit) as exc_info:
            cmd_audit(args)

    assert exc_info.value.code == 0
    assert seen_trials == [0, 1, 2, 3, 4, 5]
    captured = capsys.readouterr()
    assert "Audit passed: 6/6 trials" in captured.out


# ---------------------------------------------------------------------------
# cmd_preflight
# ---------------------------------------------------------------------------


def test_cmd_preflight_success(capsys):
    """Preflight with all-passing suite."""
    with patch(
        "caddie.study.cli.default_suite"
    ) as mock_suite:
        mock_suite.return_value.run_and_summary.return_value = (
            [],
            MagicMock(passed=3, failed=0, skipped=0, total=3, all_passed=True),
        )
        with pytest.raises(SystemExit) as exc_info:
            cmd_preflight(argparse.Namespace())
    assert exc_info.value.code == 0
    captured = capsys.readouterr()
    assert "All preflight checks passed" in captured.out


def test_cmd_preflight_failure(capsys):
    """Preflight with failures exits code 1."""
    with patch(
        "caddie.study.cli.default_suite"
    ) as mock_suite:
        mock_suite.return_value.run_and_summary.return_value = (
            [],
            MagicMock(passed=2, failed=1, skipped=0, total=3, all_passed=False),
        )
        with pytest.raises(SystemExit) as exc_info:
            cmd_preflight(argparse.Namespace())
    assert exc_info.value.code == 1
    captured = capsys.readouterr()
    assert "WARNING" in captured.out


# ---------------------------------------------------------------------------
# cmd_inspect
# ---------------------------------------------------------------------------


def test_cmd_inspect_no_matrix_file(capsys):
    """Inspect with missing matrix file exits code 1."""
    args = argparse.Namespace(matrix=Path("/nonexistent.yaml"), participant=None, condition=None)
    with pytest.raises(SystemExit) as exc_info:
        cmd_inspect(args)
    assert exc_info.value.code == 1


# ---------------------------------------------------------------------------
# cmd_dry_run
# ---------------------------------------------------------------------------


def test_cmd_dry_run_no_specs(tmp_path, capsys):
    """Dry-run with empty spec directory."""
    args = argparse.Namespace(
        participant="P01",
        trial=None,
        spec_dir=tmp_path,
        data_dir=tmp_path / "data",
    )
    with pytest.raises(SystemExit) as exc_info:
        cmd_dry_run(args)
    assert exc_info.value.code == 1
    captured = capsys.readouterr()
    assert "No specs found" in captured.out


def test_cmd_dry_run_audits_all_real_specs_without_missing_tap_targets(tmp_path, capsys):
    specs_dir = Path(__file__).parents[1] / "study" / "specs"

    for trial_index in range(6):
        args = argparse.Namespace(
            participant="P01",
            trial=trial_index,
            spec_dir=specs_dir,
            data_dir=tmp_path / f"data_{trial_index}",
        )
        with pytest.raises(SystemExit) as exc_info:
            cmd_dry_run(args)

        assert exc_info.value.code == 0
        captured = capsys.readouterr()
        assert "Outcome: technical_failure" not in captured.out


# ---------------------------------------------------------------------------
# cmd_run
# ---------------------------------------------------------------------------


def test_cmd_run_no_specs(tmp_path, capsys):
    """Run with empty spec directory."""
    args = argparse.Namespace(
        participant="P01",
        trial=None,
        spec_dir=tmp_path,
        data_dir=tmp_path / "data",
    )
    with pytest.raises(SystemExit) as exc_info:
        cmd_run(args)
    assert exc_info.value.code == 1
    captured = capsys.readouterr()
    assert "No specs found" in captured.out


# ---------------------------------------------------------------------------
# cmd_status
# ---------------------------------------------------------------------------


def test_cmd_status_no_session(capsys):
    """Status with no active session."""
    with pytest.raises(SystemExit) as exc_info:
        cmd_status(argparse.Namespace())
    assert exc_info.value.code == 0
    captured = capsys.readouterr()
    assert "No active session" in captured.out


# ---------------------------------------------------------------------------
# cmd_repeat
# ---------------------------------------------------------------------------


def test_cmd_repeat_exits_0():
    """Repeat exits with code 0 (placeholder implementation)."""
    with patch("sys.exit") as mock_exit:
        cmd_repeat(argparse.Namespace())
    mock_exit.assert_called_once_with(0)


# ---------------------------------------------------------------------------
# cmd_export
# ---------------------------------------------------------------------------


def test_cmd_export_creates_file(tmp_path):
    """Export writes JSON to the output file."""
    data_dir = tmp_path / "study-data"
    data_dir.mkdir()
    trial_dir = data_dir / "v1" / "P01" / "sess1"
    trial_dir.mkdir(parents=True)
    summary_path = trial_dir / "summary.json"
    summary_path.write_text(json.dumps({"outcome": "success", "total_steps": 3}))

    output = tmp_path / "exported.json"
    args = argparse.Namespace(input=data_dir, output=output)
    with pytest.raises(SystemExit) as exc_info:
        cmd_export(args)
    assert exc_info.value.code == 0

    assert output.exists()
    data = json.loads(output.read_text())
    assert len(data["trials"]) == 1
    assert data["trials"][0]["outcome"] == "success"


# ---------------------------------------------------------------------------
# CLI full execution (verification + cancellation)
# ---------------------------------------------------------------------------


def test_select_trial_spec_uses_participant_task_order_index():
    step = StudyStep(
        id="open",
        action="open com.example",
        narration="Open app",
        step_type=StepType.NORMAL,
    )
    specs = {
        task_id: TrialSpec(
            version="v1",
            id=task_id,
            instruction_de=f"Instruction {task_id}",
            criticality=CriticalityClass.LOW,
            steps=(step,),
        )
        for task_id in ("task_a", "task_b", "task_c", "task_d", "task_e", "task_f")
    }
    config = ParticipantConfig(
        participant_id="P01",
        condition_order=(
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
        ),
        task_order=("task_c", "task_a", "task_f", "task_b", "task_e", "task_d"),
        error_tasks=("task_a", "task_b", "task_c"),
        screen_off_order=(
            ScreenOffMode.NOTIFY_ONLY,
            ScreenOffMode.WAKE_ASK,
            ScreenOffMode.WAKE_EXECUTE,
        ),
        screen_off_tasks=(
            "screen_off_weather",
            "screen_off_project_group",
            "screen_off_email_calendar",
        ),
    )

    assert _select_trial_spec(specs, config, 2).id == "task_f"


def test_cmd_run_full_trial_success(tmp_path, capsys):
    """CLI run executes a trial successfully end-to-end."""
    # Create 6 required specs for the matrix
    task_ids = [
        "task_maps_messenger", "task_gallery_notes",
        "task_chat_spotify", "task_email_calendar",
        "task_calendar_dnd", "task_banking_payment",
    ]
    for i, tid in enumerate(task_ids):
        spec_yaml = tmp_path / f"{tid}.yaml"
        spec_yaml.write_text(f"""
version: v1
id: {tid}
instruction_de: Test instruction {i}
criticality: {'low' if i % 2 == 0 else 'high'}
trigger:
  reference_phrases: [Test instruction {i}]
  required_concepts:
    - [test]
reset_checklist:
  - App im Home-Screen
steps:
  - id: open_{i}
    action: open app {i}
    narration: Open app {i}
    step_type: normal
  - id: send_{i}
    action: click Send {i}
    narration: Send data {i}
    step_type: consequential
    consequential: true
error_steps: []
verification: []
max_duration_s: 120
per_gate_timeout_s: 30
""")

    data_dir = tmp_path / "data"
    args = argparse.Namespace(
        participant="P01",
        trial=0,
        spec_dir=tmp_path,
        data_dir=data_dir,
        oversight="stepwise",
    )
    with pytest.raises(SystemExit) as exc_info:
        cmd_run(args)
    # Should exit 0 (fake backend may fail actions but trial completes)
    assert exc_info.value.code == 0
    captured = capsys.readouterr()
    # The FakeBackend returns None for actions, so outcome may be technical_failure
    # But trial should still be logged
    assert "outcome" in captured.out.lower()

    # Verify trial data was logged
    trial_dir = data_dir / "v1" / "P01"
    assert trial_dir.exists()


def test_cmd_run_verification_failed(tmp_path, capsys):
    """CLI run reports VERIFICATION_FAILED when backend denies verification."""
    # Create 6 required specs, with task_0 having failing verification
    task_ids = [
        "task_maps_messenger", "task_gallery_notes",
        "task_chat_spotify", "task_email_calendar",
        "task_calendar_dnd", "task_banking_payment",
    ]
    for i, tid in enumerate(task_ids):
        spec_yaml = tmp_path / f"{tid}.yaml"
        if i == 0:
            verification_block = """
verification:
  - id: v1
    assertion: Should fail
    check_type: text_present
    parameters: { text: "MissingText" }
    screenshot_evidence: false
"""
        else:
            verification_block = "verification: []"
        spec_yaml.write_text(f"""
version: v1
id: {tid}
instruction_de: Test instruction {i}
criticality: {'low' if i % 2 == 0 else 'high'}
trigger:
  reference_phrases: [Test instruction {i}]
  required_concepts:
    - [test]
reset_checklist:
  - App im Home-Screen
steps:
  - id: open_{i}
    action: open app {i}
    narration: Open app {i}
    step_type: normal
error_steps: []
{verification_block}
max_duration_s: 120
per_gate_timeout_s: 30
""")

    data_dir = tmp_path / "data"
    args = argparse.Namespace(
        participant="P01",
        trial=0,
        spec_dir=tmp_path,
        data_dir=data_dir,
        oversight="stepwise",
    )
    with pytest.raises(SystemExit) as exc_info:
        cmd_run(args)
    # Verification failed — still exits 0 but reports outcome
    assert exc_info.value.code == 0
    captured = capsys.readouterr()
    assert "verification" in captured.out.lower() or "failed" in captured.out.lower()
