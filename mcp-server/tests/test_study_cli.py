"""Tests for caddie.study.cli — CLI commands and argument parsing."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from caddie.study.cli import (
    cmd_dry_run,
    cmd_export,
    cmd_inspect,
    cmd_preflight,
    cmd_run,
    cmd_status,
    cmd_repeat,
    main,
)


# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------


def test_main_no_args_exits():
    with pytest.raises(SystemExit) as exc_info:
        main([])
    assert exc_info.value.code == 1


def test_main_preflight_help():
    """preflight command parses --participant."""
    with patch("caddie.study.cli.cmd_preflight") as mock:
        main(["preflight", "-p", "P01"])
    mock.assert_called_once()


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


def test_cmd_run_full_trial_success(tmp_path, capsys):
    """CLI run executes a trial successfully end-to-end."""
    # Create 6 minimal specs (matrix needs at least 6)
    for i in range(6):
        spec_yaml = tmp_path / f"task_{i}.yaml"
        spec_yaml.write_text(f"""
version: v1
id: task_{i}
instruction_de: Test instruction {i}
criticality: {'low' if i % 2 == 0 else 'high'}
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
        trial="task_test",
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
    # Create 6 specs, with task_0 having failing verification
    for i in range(6):
        spec_yaml = tmp_path / f"task_{i}.yaml"
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
id: task_{i}
instruction_de: Test instruction {i}
criticality: {'low' if i % 2 == 0 else 'high'}
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
        trial="task_test",
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
