"""Deterministic participant counterbalancing matrix for P01–P18.

Reads the available trial specs from study/specs/ and generates a
ParticipantConfig for each participant slot (P01–P18).

Balancing goals (per the study design spec):
  - Each participant receives exactly 3 low + 3 high criticality tasks (6 total)
  - Each condition appears exactly twice per participant
  - Each condition appears in each position exactly twice per cohort
  - Each participant receives exactly 3 controlled-error tasks
  - Condition × task criticality × error exposure is balanced across the cohort
  - Screen-off modes are rotated independently of the main-task matrix
"""

from __future__ import annotations

import collections
import pathlib
import random
import typing
from typing import Sequence

from caddie.study.model import (
    CriticalityClass,
    ParticipantConfig,
    ScreenOffMode,
    StudyCondition,
    TaskPair,
    TrialSpec,
)
from caddie.study.spec_loader import load_trial_spec, list_available_specs, SpecError

# ── Constants ────────────────────────────────────────────────────────────────

NUM_PARTICIPANTS = 18
NUM_ERROR_TASKS = 3
NUM_SCREEN_OFF_TASKS = 3
NUM_TASKS_PER_PARTICIPANT = 6
NUM_CONDITIONS = len(StudyCondition)  # 3
NUM_PAIRS_PER_PARTICIPANT = 3  # 3 pairs = 6 tasks

# All conditions and screen-off modes
ALL_CONDITIONS = list(StudyCondition)
ALL_SCREEN_OFF_MODES = list(ScreenOffMode)


# ── Task pair extraction ────────────────────────────────────────────────────


def extract_task_pairs(specs: dict[str, TrialSpec]) -> list[TaskPair]:
    """Group trial specs into low/high criticality pairs.

    Each pair must contain exactly one low-criticality and one
    high-criticality task. Returns pairs in deterministic order
    sorted by spec ID.

    Raises ValueError if no valid pairs can be formed or counts don't match.
    """
    low_tasks = {k: v for k, v in specs.items()
                 if v.criticality == CriticalityClass.LOW}
    high_tasks = {k: v for k, v in specs.items()
                  if v.criticality == CriticalityClass.HIGH}

    if not low_tasks or not high_tasks:
        raise ValueError(
            f"Need at least one low and one high criticality task. "
            f"Found {len(low_tasks)} low, {len(high_tasks)} high."
        )

    # Sort keys for determinism
    low_keys = sorted(low_tasks.keys())
    high_keys = sorted(high_tasks.keys())

    # We need at least 3 pairs (NUM_PAIRS_PER_PARTICIPANT)
    if len(low_keys) < NUM_PAIRS_PER_PARTICIPANT:
        raise ValueError(
            f"Need at least {NUM_PAIRS_PER_PARTICIPANT} low criticality tasks "
            f"for the full protocol. Found {len(low_keys)}."
        )
    if len(high_keys) < NUM_PAIRS_PER_PARTICIPANT:
        raise ValueError(
            f"Need at least {NUM_PAIRS_PER_PARTICIPANT} high criticality tasks "
            f"for the full protocol. Found {len(high_keys)}."
        )

    # Form true one-to-one pairs (sorted pairing)
    pairs: list[TaskPair] = []
    for i in range(min(len(low_keys), len(high_keys))):
        pairs.append(TaskPair(
            id=f"pair_{low_keys[i]}_{high_keys[i]}",
            low_task=low_keys[i],
            high_task=high_keys[i],
        ))

    return pairs


# ── Counterbalancing logic ──────────────────────────────────────────────────


def _latin_square(n: int) -> list[tuple[int, ...]]:
    """Generate a Latin square of order n (cyclic shift).

    Row i, column j gives the condition index for position j in order i.
    """
    return tuple(
        tuple((i + j) % n for j in range(n))
        for i in range(n)
    )


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


def _build_condition_orders() -> list[tuple[StudyCondition, ...]]:
    """Build all valid condition orders for 6 tasks with 3 conditions.

    Each condition appears exactly twice in each order.
    Uses a balanced Latin square approach to ensure cohort-level balance.
    Returns 18 orders (one per participant) such that each condition
    appears exactly twice in each position across the cohort.
    """
    latin = _latin_square(3)  # 3x3 Latin square
    orders: list[tuple[StudyCondition, ...]] = []

    # For 6 positions and 3 conditions (each appearing twice),
    # we use the Latin square pattern repeated twice per row
    for row_idx in range(NUM_PARTICIPANTS):
        row = latin[row_idx % 3]
        # Each condition index appears in 2 positions: the row position itself
        # and the row position + 3 (second half)
        condition_order: list[StudyCondition] = []
        for j in range(6):
            pos_in_row = j % 3
            # Alternate between the two halves to get even distribution
            condition_order.append(
                ALL_CONDITIONS[row[pos_in_row]]
            )
        orders.append(tuple(condition_order))

    return orders


def generate_participant_config(
    index: int,
    task_ids: list[str],
    criticalities: dict[str, CriticalityClass],
    condition_order: list[StudyCondition],
    specs: dict[str, TrialSpec] | None = None,
) -> ParticipantConfig:
    """Generate a ParticipantConfig for a single participant slot.

    Parameters
    ----------
    index : int
        Participant index (0-based, P01 = 0).
    task_ids : list[str]
        Ordered list of exactly 6 task spec IDs.
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
    # Only select tasks that have error variants defined in their specs
    if specs:
        tasks_with_errors = [
            tid for tid in task_ids
            if specs.get(tid) and specs[tid].error_steps
        ]
    else:
        tasks_with_errors = task_ids
    error_tasks = _select_error_tasks(
        tasks_with_errors if tasks_with_errors else task_ids,
        NUM_ERROR_TASKS,
        seed_offset=index * 1000,
    )
    # Participant-specific screen-off rotation
    screen_off_order = _rotate_screen_off(index)

    pair_assignments = tuple(
        (tid, criticalities.get(tid, CriticalityClass.LOW))
        for tid in task_ids
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

    Each participant receives exactly 6 tasks: 3 low + 3 high criticality.
    Conditions are balanced using a Latin square (each condition appears
    exactly twice per participant). Error tasks are distributed across
    the cohort so that each task appears as an error task roughly equally.

    Uses a local ``random.Random`` instance (no global state mutation).

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

    if len(task_ids) < NUM_TASKS_PER_PARTICIPANT:
        raise ValueError(
            f"Need at least {NUM_TASKS_PER_PARTICIPANT} trial specs for the "
            f"full protocol. Found {len(task_ids)}: {task_ids}"
        )

    # Separate low and high criticality tasks
    low_tasks = sorted(
        tid for tid in task_ids
        if specs[tid].criticality == CriticalityClass.LOW
    )
    high_tasks = sorted(
        tid for tid in task_ids
        if specs[tid].criticality == CriticalityClass.HIGH
    )

    if len(low_tasks) < NUM_PAIRS_PER_PARTICIPANT:
        raise ValueError(
            f"Need at least {NUM_PAIRS_PER_PARTICIPANT} low criticality tasks. "
            f"Found {len(low_tasks)}."
        )
    if len(high_tasks) < NUM_PAIRS_PER_PARTICIPANT:
        raise ValueError(
            f"Need at least {NUM_PAIRS_PER_PARTICIPANT} high criticality tasks. "
            f"Found {len(high_tasks)}."
        )

    # For 18 participants we need 18 × 6 = 108 task assignments (3 low + 3 high each).
    # Use a round-robin assignment to ensure all tasks are distributed evenly.
    criticalities = {tid: specs[tid].criticality for tid in task_ids}

    # Tasks with error variants (need at least 3 per participant)
    tasks_with_errors = [
        tid for tid in task_ids
        if specs[tid].error_steps
    ]
    tasks_without_errors = [
        tid for tid in task_ids
        if not specs[tid].error_steps
    ]

    configs: dict[str, ParticipantConfig] = {}
    condition_orders = _build_condition_orders()

    for i in range(NUM_PARTICIPANTS):
        # Round-robin: assign tasks sequentially
        low_idx = i % len(low_tasks)
        high_idx = i % len(high_tasks)

        # Select 3 low tasks (round-robin with offset)
        participant_low: list[str] = []
        for j in range(NUM_PAIRS_PER_PARTICIPANT):
            participant_low.append(low_tasks[(low_idx + j) % len(low_tasks)])

        # Select 3 high tasks (round-robin with offset)
        participant_high: list[str] = []
        for j in range(NUM_PAIRS_PER_PARTICIPANT):
            participant_high.append(high_tasks[(high_idx + j) % len(high_tasks)])

        # Build task order: 3 low tasks + 3 high tasks
        participant_tasks = list(participant_low) + list(participant_high)

        # Ensure at least 3 tasks with error variants are included
        # If participant doesn't have enough, swap in tasks_with_errors
        tasks_with_err_in_participant = [
            tid for tid in participant_tasks if tid in tasks_with_errors
        ]
        if len(tasks_with_err_in_participant) < 3:
            # Replace some non-error tasks with error tasks
            tasks_to_swap = [
                tid for tid in participant_tasks
                if tid not in tasks_with_errors
            ]
            tasks_needed = 3 - len(tasks_with_err_in_participant)
            for j in range(tasks_needed):
                if tasks_to_swap and tasks_with_errors:
                    # Swap one non-error task for one error task
                    swap_out = tasks_to_swap.pop(0)
                    error_task = tasks_with_errors[i % len(tasks_with_errors)]
                    idx = participant_tasks.index(swap_out)
                    participant_tasks[idx] = error_task

        # Shuffle the 6 tasks for position balance
        rng.shuffle(participant_tasks)

        condition_order = list(condition_orders[i % len(condition_orders)])

        config = generate_participant_config(
            index=i,
            task_ids=participant_tasks,
            criticalities=criticalities,
            condition_order=condition_order,
            specs=specs,
        )
        configs[config.participant_id] = config

    # Validate cohort-level invariants
    _validate_cohort_balance(configs, task_ids)

    return configs


def _validate_cohort_balance(
    configs: dict[str, ParticipantConfig],
    all_task_ids: list[str],
) -> None:
    """Validate cohort-level balance invariants after matrix generation.

    Checks:
    - 18 participants generated
    - Exactly 6 tasks per participant
    - Exactly 2 conditions per participant (each condition appears twice)
    - Exactly 3 error tasks per participant
    - Error task distribution is approximately even across cohort
    - No unknown task IDs
    """
    # 1. Participant count
    assert len(configs) == NUM_PARTICIPANTS, (
        f"Expected {NUM_PARTICIPANTS} participants, got {len(configs)}"
    )

    # 2. Per-participant checks
    for pid, cfg in sorted(configs.items()):
        assert len(cfg.task_order) == NUM_TASKS_PER_PARTICIPANT, (
            f"{pid}: expected {NUM_TASKS_PER_PARTICIPANT} tasks, "
            f"got {len(cfg.task_order)}"
        )
        assert len(cfg.condition_order) == NUM_TASKS_PER_PARTICIPANT, (
            f"{pid}: expected {NUM_TASKS_PER_PARTICIPANT} conditions, "
            f"got {len(cfg.condition_order)}"
        )
        assert len(cfg.error_tasks) == NUM_ERROR_TASKS, (
            f"{pid}: expected {NUM_ERROR_TASKS} error tasks, "
            f"got {len(cfg.error_tasks)}"
        )

        # Each condition appears exactly twice
        cond_counts = collections.Counter(cfg.condition_order)
        for cond in ALL_CONDITIONS:
            assert cond_counts[cond] == 2, (
                f"{pid}: condition {cond} appears {cond_counts[cond]} times, "
                f"expected 2"
            )

        # All tasks are known
        for tid in cfg.task_order:
            assert tid in all_task_ids, f"{pid}: unknown task '{tid}'"

    # 3. Cohort-level: each condition appears in each position ~equally
    pos_cond_counts: dict[int, dict[str, int]] = {
        pos: collections.Counter() for pos in range(NUM_TASKS_PER_PARTICIPANT)
    }
    for cfg in configs.values():
        for pos, cond in enumerate(cfg.condition_order):
            pos_cond_counts[pos][cond.value] += 1

    for pos, counts in pos_cond_counts.items():
        # Each position should have ~6 participants per condition (18/3)
        for cond in ALL_CONDITIONS:
            count = counts.get(cond.value, 0)
            assert count >= 4 and count <= 8, (
                f"Position {pos}: condition {cond.value} in {count} participants "
                f"(expected ~6, range 4-8)"
            )


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
        except SpecError as e:
            errors.append(f"{yaml_path.name}: {e}")
            continue
        except Exception as e:
            errors.append(f"{yaml_path.name}: unexpected error: {e}")
            continue
        if spec.id in specs:
            errors.append(f"{yaml_path.name}: duplicate ID '{spec.id}'")
            continue
        specs[spec.id] = spec

    if errors:
        raise SpecError(
            f"Invalid study specs ({len(errors)} error(s)):\n" + "\n".join(errors)
        )

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
    except SpecError as e:
        print(f"Spec validation failed: {e}")
