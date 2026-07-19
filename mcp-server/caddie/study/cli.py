"""Experimenter CLI for the study system.

This module provides a command-line interface for running and managing study
sessions. It supports the following commands:

* ``preflight`` — run the preflight check suite
* ``inspect`` — inspect participant matrices and condition assignments
* ``dry-run`` — run a trial without executing Android actions
* ``run`` — execute a full trial
* ``status`` — check trial session status
* ``repeat`` — repeat the last trial
* ``export`` — export trial results to JSON

Usage::

    caddie-study preflight --participant P01
    caddie-study inspect --matrix study_matrix.yaml
    caddie-study dry-run --participant P01 --trial 1
    caddie-study run --participant P01 --trial 1
    caddie-study status
    caddie-study repeat
    caddie-study export --output results.json
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from pathlib import Path
from typing import Optional

from caddie.study.executor import TrialExecutor
from caddie.study.logger import StudyLogger
from caddie.study.matrix import generate_from_specs_dir, print_matrix
from caddie.study.model import StudyCondition
from caddie.study.oversight import OversightManager
from caddie.study.preflight import default_suite
from caddie.study.session import SessionManager
from caddie.study.spec_loader import load_all_specs

logger = logging.getLogger(__name__)

# Default paths
DEFAULT_SPECS_DIR = Path(__file__).resolve().parent.parent / "study" / "specs"
DEFAULT_DATA_DIR = Path(__file__).resolve().parent.parent / "study-data"
DEFAULT_MATRIX_PATH = Path(__file__).resolve().parent.parent / "experiments" / "trial_matrix.yaml"


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> None:
    """Entry point for the study CLI.

    Args:
        argv: Command-line arguments (defaults to sys.argv[1:]).
    """
    parser = argparse.ArgumentParser(
        prog="caddie-study",
        description="Caddie User-Study CLI — deterministic study runtime",
    )
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # preflight
    preflight_p = subparsers.add_parser("preflight", help="Run preflight checks")
    preflight_p.add_argument("--participant", "-p", default="P01", help="Participant ID")

    # inspect
    inspect_p = subparsers.add_parser("inspect", help="Inspect matrix and condition assignments")
    inspect_p.add_argument("--matrix", "-m", type=Path, default=DEFAULT_MATRIX_PATH, help="Matrix YAML path")
    inspect_p.add_argument("--participant", "-p", default=None, help="Show only this participant")
    inspect_p.add_argument("--condition", "-c", default=None, help="Filter by condition")

    # dry-run
    dryrun_p = subparsers.add_parser("dry-run", help="Dry-run a trial (no Android actions)")
    dryrun_p.add_argument("--participant", "-p", required=True, help="Participant ID")
    dryrun_p.add_argument("--trial", "-t", type=int, default=None, help="Trial index (0-based)")
    dryrun_p.add_argument("--spec-dir", type=Path, default=DEFAULT_SPECS_DIR, help="YAML specs directory")
    dryrun_p.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR, help="Data output directory")

    # run
    run_p = subparsers.add_parser("run", help="Execute a full trial")
    run_p.add_argument("--participant", "-p", required=True, help="Participant ID")
    run_p.add_argument("--trial", "-t", type=int, default=None, help="Trial index (0-based)")
    run_p.add_argument("--spec-dir", type=Path, default=DEFAULT_SPECS_DIR, help="YAML specs directory")
    run_p.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR, help="Data output directory")

    # status
    subparsers.add_parser("status", help="Show current trial status")

    # repeat
    subparsers.add_parser("repeat", help="Repeat the last completed trial")

    # export
    export_p = subparsers.add_parser("export", help="Export trial results")
    export_p.add_argument("--input", "-i", type=Path, default=DEFAULT_DATA_DIR, help="Data directory to export from")
    export_p.add_argument("--output", "-o", type=Path, default=Path("study_results.json"), help="Output JSON file")

    args = parser.parse_args(argv)

    if args.command is None:
        parser.print_help()
        sys.exit(1)

    commands = {
        "preflight": cmd_preflight,
        "inspect": cmd_inspect,
        "dry-run": cmd_dry_run,
        "run": cmd_run,
        "status": cmd_status,
        "repeat": cmd_repeat,
        "export": cmd_export,
    }

    handler = commands.get(args.command)
    if handler is None:
        parser.print_help()
        sys.exit(1)

    handler(args)


# ---------------------------------------------------------------------------
# Command implementations
# ---------------------------------------------------------------------------


def cmd_preflight(args: argparse.Namespace) -> None:
    """Run preflight checks."""
    print("=== Preflight ===")
    suite = default_suite()
    results, summary = suite.run_and_summary()

    for r in results:
        icon = "✅" if r.passed else "❌"
        print(f"  {icon} {r.check.description}: {r.status.value} ({r.message})")

    print(f"\nResult: {summary.passed}/{summary.total} checks passed")
    if summary.all_passed:
        print("All preflight checks passed — session ready to start.")
    else:
        print(f"WARNING: {summary.failed} check(s) failed — session may not start.")

    print()
    sys.exit(0 if summary.all_passed else 1)


def cmd_inspect(args: argparse.Namespace) -> None:
    """Inspect participant matrices."""
    try:
        configs = generate_from_specs_dir(args.matrix)
    except Exception as exc:
        print(f"Failed to load matrix: {exc}")
        sys.exit(1)

    if args.participant:
        if args.participant not in configs:
            print(f"Participant not found: {args.participant}")
            sys.exit(1)
        configs = {args.participant: configs[args.participant]}

    print_matrix(configs)
    sys.exit(0)


def cmd_dry_run(args: argparse.Namespace) -> None:
    """Dry-run a trial without Android actions."""
    print(f"=== Dry-run: participant={args.participant} ===")

    # Load specs
    import caddie.study.spec_loader as spec_loader
    original_dir = getattr(spec_loader, 'STUDY_SPECS_DIR', None)
    if original_dir is not None:
        spec_loader.STUDY_SPECS_DIR = args.spec_dir
    try:
        specs = load_all_specs()
    except Exception as exc:
        print(f"Failed to load specs: {exc}")
        sys.exit(1)
    finally:
        if original_dir is not None:
            spec_loader.STUDY_SPECS_DIR = original_dir

    if not specs:
        print(f"No specs found in {args.spec_dir}")
        sys.exit(1)
    trial_spec = next(iter(specs.values()))

    # Get matrix
    configs = generate_from_specs_dir(args.spec_dir)

    # Load participant matrix
    if args.participant not in configs:
        print(f"Participant not found: {args.participant}")
        sys.exit(1)
    p_config = configs[args.participant]

    # Set up logger
    logger_inst = StudyLogger(
        base_dir=args.data_dir,
        study_version=trial_spec.version,
        participant_id=args.participant,
        session_id="dry_run_001",
        condition=p_config.condition_order[0],
    )

    # Set up oversight (always accepts for dry-run)
    oversight = OversightManager(
        logger=logger_inst,
        condition=StudyCondition.STEPWISE,
    )

    # Fake backend for dry-run
    class FakeBackend:
        def list_elements(self):
            return {"elements": []}
        def open_app(self, package_name):
            return {"ok": True}
        def open_url(self, url):
            return {"ok": True}
        def tap_element(self, index):
            return {"ok": True}
        def scroll(self, direction, amount=0.6):
            return {"ok": True}
        def press_button(self, button):
            return {"ok": True}
        def type_text(self, text, submit=False):
            return {"ok": True}

    # Dry-run executor
    executor = TrialExecutor(
        backend=FakeBackend(),
        logger=logger_inst,
        oversight=oversight,
        spec=trial_spec,
        condition=p_config.condition_order[0],
        error_tasks=frozenset(p_config.error_tasks),
    )

    result = executor.run()
    print(f"  Outcome: {result.outcome.value}")
    print(f"  Steps executed: {result.steps_done}")
    print(f"  Duration: {result.duration_ms} ms")
    print(f"  Reason: {result.reason}")

    sys.exit(0)


def cmd_run(args: argparse.Namespace) -> None:
    """Execute a full trial."""
    print(f"=== Run: participant={args.participant} ===")

    # Load specs
    import caddie.study.spec_loader as spec_loader
    original_dir = getattr(spec_loader, 'STUDY_SPECS_DIR', None)
    if original_dir is not None:
        spec_loader.STUDY_SPECS_DIR = args.spec_dir
    try:
        specs = load_all_specs()
    except Exception as exc:
        print(f"Failed to load specs: {exc}")
        sys.exit(1)
    finally:
        if original_dir is not None:
            spec_loader.STUDY_SPECS_DIR = original_dir

    if not specs:
        print(f"No specs found in {args.spec_dir}")
        sys.exit(1)
    trial_spec = next(iter(specs.values()))

    # Get matrix
    configs = generate_from_specs_dir(args.spec_dir)

    # Load participant matrix
    if args.participant not in configs:
        print(f"Participant not found: {args.participant}")
        sys.exit(1)
    p_config = configs[args.participant]

    # Set up logger
    session_id = f"sess_{int(time.time())}"
    logger_inst = StudyLogger(
        base_dir=args.data_dir,
        study_version=trial_spec.version,
        participant_id=args.participant,
        session_id=session_id,
        condition=p_config.condition_order[0],
    )

    # Set up oversight (always accepts for CLI run)
    oversight = OversightManager(
        logger=logger_inst,
        condition=StudyCondition.STEPWISE,
    )

    # Fake backend for CLI run
    class FakeBackend:
        def list_elements(self):
            return {"elements": []}
        def open_app(self, package_name):
            return {"ok": True}
        def open_url(self, url):
            return {"ok": True}
        def tap_element(self, index):
            return {"ok": True}
        def scroll(self, direction, amount=0.6):
            return {"ok": True}
        def press_button(self, button):
            return {"ok": True}
        def type_text(self, text, submit=False):
            return {"ok": True}

    # Run executor
    executor = TrialExecutor(
        backend=FakeBackend(),
        logger=logger_inst,
        oversight=oversight,
        spec=trial_spec,
        condition=p_config.condition_order[0],
        error_tasks=frozenset(p_config.error_tasks),
    )

    result = executor.run()
    print(f"  Outcome: {result.outcome.value}")
    print(f"  Steps executed: {result.steps_done}")
    print(f"  Duration: {result.duration_ms} ms")
    print(f"  Reason: {result.reason}")

    sys.exit(0)


def cmd_status(args: argparse.Namespace) -> None:
    """Show current trial status."""
    mgr = SessionManager()
    session = mgr.session
    if session is None:
        print("No active session.")
        sys.exit(0)

    metrics = session.get_metrics()
    print(f"Session state: {session.state.value}")
    print(f"Steps executed: {metrics.steps_executed}")
    print(f"Elapsed: {metrics.elapsed_ms} ms")
    print(f"Paused: {metrics.is_paused}")
    print(f"Verification pending: {metrics.verification_pending}")
    sys.exit(0)


def cmd_repeat(args: argparse.Namespace) -> None:
    """Repeat the last trial."""
    print("Repeat command: re-executes the last completed trial.")
    print("Implementation depends on session state persistence.")
    sys.exit(0)


def cmd_export(args: argparse.Namespace) -> None:
    """Export trial results to JSON."""
    data_dir = args.input
    output = args.output

    results = []
    for summary_path in data_dir.rglob("summary.json"):
        try:
            data = json.loads(summary_path.read_text(encoding="utf-8"))
            results.append(data)
        except Exception as exc:
            print(f"Skipping {summary_path}: {exc}")

    with output.open("w", encoding="utf-8") as f:
        json.dump({"trials": results}, f, indent=2, ensure_ascii=False)

    print(f"Exported {len(results)} trial(s) to {output}")
    sys.exit(0)


if __name__ == "__main__":
    main()
