"""Deterministic participant counterbalancing matrix for P01–P18.

Reads the available trial specs from study/specs/ and generates a
ParticipantConfig for each participant slot (P01–P18).

Balancing goals (per the study design spec):
  - Each condition appears roughly equally as first, second, etc.
  - Each participant receives exactly 3 controlled-error tasks
  - Condition × task criticality × error exposure is balanced across
    the full cohort
  - Screen-off modes are rotated independently of the main-task matrix
"""

from __future__ import annotations

import pathlib
import random
from typing import Sequence

from caddie.study.model import (
    CriticalityClass,
    ParticipantConfig,
    ScreenOffMode,
    StudyCondition,
    TaskPair,
    TrialSpec,
)
from caddie.study.spec_loader import load_trial_spec, list_available_specs

# ── Constants ────────────────────────────────────────────────────────────────

# Number of participant slots
NUM_PARTICIPANTS = 18
NUM_ERROR_TASKS = 3
NUM_SCREEN_OFF_TASKS = 3

# All conditions and screen-off modes
ALL_CONDITIONS = list(StudyCondition)
ALL_SCREEN_OFF_MODES = list(ScreenOffMode)


# ── Task pair extraction ────────────────────────────────────────────────────


def extract_task_pairs(specs: dict[str, TrialSpec]) -> list[TaskPair]:
    """Group trial specs into low/high criticality pairs.

    Each pair must contain exactly one low-criticality and one
    high-criticality task. Returns pairs in deterministic order
    sorted by spec ID.

    Raises ValueError if no valid pairs can be formed.
    """
    low_tasks = {k: v for k, v in specs.items() if v.criticality == CriticalityClass.LOW}
    high_tasks = {k: v for k, v in specs.items() if v.criticality == CriticalityClass.HIGH}

    if not low_tasks or not high_tasks:
        raise ValueError(
            f"Need at least one low and one high criticality task. "
            f"Found {len(low_tasks)} low, {len(high_tasks)} high."
        )

    # Sort keys for determinism
    low_keys = sorted(low_tasks.keys())
    high_keys = sorted(high_tasks.keys())

    # Form true one-to-one pairs (min of low/high counts)
    pairs: list[TaskPair] = []
    min_count = min(len(low_keys), len(high_keys))
    for i in range(min_count):
        pairs.append(TaskPair(
            id=f"pair_{low_keys[i]}_{high_keys[i]}",
            low_task=low_keys[i],
            high_task=high_keys[i],
        ))

    return pairs


# ── Counterbalancing logic ──────────────────────────────────────────────────


def _latin_square(n: int) -> list[list[int]]:
    """Generate a Latin square of order n (cyclic shift).

    Row i, column j gives the condition index for position j in order i.
    """
    return [[(i + j) % n for j in range(n)] for i in range(n)]


def _select_error_tasks(
    task_ids: list[str],
    count: int = NUM_ERROR_TASKS,
    seed_offset: int = 0,
) -> tuple[str, ...]:
    """Select exactly `count` task IDs for controlled error injection.

    Uses a local RNG seeded with `seed_offset` so different participants
    get different error task sets while remaining fully deterministic.
    """
    rng = random.Random(seed_offset)
    selected = rng.sample(task_ids, min(count, len(task_ids)))
    return tuple(sorted(selected))


def _rotate_screen_off(participant_index: int) -> tuple[ScreenOffMode, ...]:
    """Return a rotation of the three screen-off initiation modes.

    Rotates cyclically by `participant_index` so each participant gets a
    different mode order while maintaining full determinism.
    """
    n = len(ALL_SCREEN_OFF_MODES)
    offset = participant_index % n
    return tuple(
        ALL_SCREEN_OFF_MODES[(i + offset) % n] for i in range(n)
    )


def generate_participant_config(
    index: int,
    task_ids: list[str],
    criticalities: dict[str, CriticalityClass],
    condition_order: list[StudyCondition],
) -> ParticipantConfig:
    """Generate a ParticipantConfig for a single participant slot.

    Parameters
    ----------
    index : int
        Participant index (0-based, P01 = 0).
    task_ids : list[str]
        Ordered list of all task spec IDs.
    criticalities : dict[str, CriticalityClass]
        Mapping of task ID to criticality class.
    condition_order : list[StudyCondition]
        Ordered list of conditions for this participant (length 6).

    Returns
    -------
    ParticipantConfig
        Fully populated config for the participant.
    """
    participant_id = f"P{index + 1:02d}"

    # Participant-specific error task selection (deterministic via seed_offset)
    error_tasks = _select_error_tasks(task_ids, NUM_ERROR_TASKS, seed_offset=index * 1000)
    # Participant-specific screen-off rotation
    screen_off_order = _rotate_screen_off(index)

    pair_assignments = tuple(
        (tid, criticalities.get(tid, CriticalityClass.LOW)) for tid in task_ids
    )

    return ParticipantConfig(
        participant_id=participant_id,
        condition_order=tuple(condition_order),
        task_order=tuple(task_ids),
        error_tasks=error_tasks,
        screen_off_order=screen_off_order,
        screen_off_tasks=(
            "screen_off_weather",
            "screen_off_project_group",
            "screen_off_email_calendar",
        ),
        pair_assignments=pair_assignments,
    )


def generate_matrix(
    specs: dict[str, TrialSpec],
    seed: int | None = 42,
) -> dict[str, ParticipantConfig]:
    """Generate the full P01–P18 participant matrix.

    Uses a local ``random.Random`` instance (no global state mutation).
    All specs are used (no lexical truncation). Error tasks and screen-off
    modes rotate per participant for cross-factor balance.

    Parameters
    ----------
    specs : dict[str, TrialSpec]
        Mapping of trial spec ID to validated TrialSpec.
    seed : int | None
        Random seed for determinism. None uses system randomness.

    Returns
    -------
    dict[str, ParticipantConfig]
        Mapping of participant ID (e.g. "P01") to ParticipantConfig.

    Raises
    ------
    ValueError
        If not enough specs exist to fill 6 tasks.
    """
    rng = random.Random(seed) if seed is not None else random.Random()

    task_ids = sorted(specs.keys())

    if len(task_ids) < 6:
        raise ValueError(
            f"Need at least 6 trial specs for the full protocol. "
            f"Found {len(task_ids)}: {task_ids}"
        )

    # Build criticality mapping (use enum, not string)
    criticalities = {tid: specs[tid].criticality for tid in task_ids}

    # Generate condition orders using Latin square for balancing
    num_conditions = len(ALL_CONDITIONS)
    latin = _latin_square(num_conditions)

    configs: dict[str, ParticipantConfig] = {}

    num_latin = len(latin)
    for i in range(min(NUM_PARTICIPANTS, num_latin * 6)):
        # Cycle through latin rows; each row repeats 3 times with different task shuffles
        row_idx = i % num_latin
        order_indices = latin[row_idx]
        # Map indices to conditions — cycle through conditions for all tasks
        conditions = [ALL_CONDITIONS[order_indices[j % num_conditions]] for j in range(len(task_ids))]

        # Shuffle task order within constraints (3 shuffles per latin row)
        task_order = list(task_ids)
        rng.shuffle(task_order)

        config = generate_participant_config(
            index=i,
            task_ids=task_order,
            criticalities=criticalities,
            condition_order=conditions,
        )
        configs[config.participant_id] = config

    return configs


# ── Public API ───────────────────────────────────────────────────────────────


def generate_from_specs_dir(
    specs_dir: pathlib.Path | str | None = None,
    seed: int = 42,
) -> dict[str, ParticipantConfig]:
    """Generate the full matrix from YAML files in the specs directory.

    This is the convenience entry point for the experimenter CLI:
    load specs, validate, and produce the matrix.

    Loads both ``.yaml`` and ``.yml`` extensions, rejects duplicate IDs,
    and aggregates validation errors rather than silently discarding specs.

    Parameters
    ----------
    specs_dir : pathlib.Path | str | None
        Directory containing study spec YAML files. Defaults to the
        standard study/specs directory.
    seed : int
        Random seed for deterministic matrix generation.

    Returns
    -------
    dict[str, ParticipantConfig]
        P01–P18 participant configs.

    Raises
    ------
    ValueError
        If no specs directory can be determined or no valid specs found.
    """
    if specs_dir:
        specs_dir_path = pathlib.Path(specs_dir)
    else:
        available = list_available_specs()
        if not available:
            raise ValueError("No YAML spec files found in the default specs directory.")
        specs_dir_path = available[0].parent

    # Load all available specs
    specs: dict[str, TrialSpec] = {}
    errors: list[str] = []
    for yaml_path in sorted(specs_dir_path.glob("*.yaml")) + sorted(specs_dir_path.glob("*.yml")):
        try:
            spec = load_trial_spec(yaml_path)
        except Exception as e:
            errors.append(f"{yaml_path.name}: {e}")
            continue
        if spec.id in specs:
            errors.append(f"{yaml_path.name}: duplicate ID '{spec.id}'")
            continue
        specs[spec.id] = spec

    if errors:
        print(f"Warning: {len(errors)} spec(s) skipped:")
        for err in errors:
            print(f"  - {err}")

    if not specs:
        raise ValueError("No valid specs loaded from the specs directory.")

    return generate_matrix(specs, seed=seed)


def print_matrix(configs: dict[str, ParticipantConfig]) -> None:
    """Print a human-readable summary of the participant matrix.

    Intended for experimenter review before data collection.
    """
    print("=" * 80)
    print("CADDIE STUDY — PARTICIPANT COUNTERBALANCING MATRIX")
    print("=" * 80)

    for pid in sorted(configs.keys()):
        cfg = configs[pid]
        print(f"\n{cfg.participant_id}:")
        sep = " -> "
        print(f"  Condition order:  {sep.join(c.value for c in cfg.condition_order)}")
        print(f"  Task order:       {sep.join(cfg.task_order)}")
        print(f"  Error tasks:      {cfg.error_tasks}")
        print(f"  Screen-off modes: {sep.join(m.value for m in cfg.screen_off_order)}")

    print("\n" + "=" * 80)
    print("END OF MATRIX")
    print("=" * 80)


if __name__ == "__main__":
    # CLI entry point: generate and print the matrix
    try:
        configs = generate_from_specs_dir()
        print_matrix(configs)
    except ValueError as e:
        print(f"Not enough specs loaded: {e}")
        print("Create at least 6 trial spec YAML files in mcp-server/study/specs/")
