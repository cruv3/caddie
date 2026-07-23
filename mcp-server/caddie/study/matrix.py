"""Deterministic participant counterbalancing matrix for P01–P18.

Reads the available trial specs from study/specs/ and generates a
ParticipantConfig for each participant slot (P01–P18).

Balancing goals:
  - 6 fixed tasks grouped into 3 pairs (A, B, C), each pair = 1 low + 1 high
  - Every participant performs all 6 tasks exactly once
  - 6 condition orders (fixed: C1,C1,C2,C2,C3,C3 with different condition
    assignments to Pair A/B/C positions)
  - 3 pair rotations (swap low/high positions within Pair A or Pair B,
    but both tasks of a pair always share the same condition)
  - 6 x 3 = 18 unique schedules
  - Each participant gets exactly 3 controlled-error tasks (round-robin
    balanced across the cohort)
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


# ── Fixed task pairs ───────────────────────────────────────────────────────

# Pair A: low = maps_messenger (low), high = gallery_notes (high)
# Pair B: low = chat_spotify (low), high = email_calendar (high)
# Pair C: low = calendar_dnd (low), high = banking_payment (high)

# 6 condition orders: all permutations of (Pair A → cond, Pair B → cond, Pair C → cond)
# Each order maps to 2 of each condition across the 6 task positions.
# The task order is always [A-low, A-high, B-low, B-high, C-low, C-high].
_CONDITION_ORDERS: list[tuple[StudyCondition, ...]] = [
    # Order 0:  A→C1, B→C2, C→C3
    (
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
    ),
    # Order 1:  A→C1, B→C3, C→C2
    (
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
    ),
    # Order 2:  A→C2, B→C1, C→C3
    (
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
    ),
    # Order 3:  A→C2, B→C3, C→C1
    (
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
    ),
    # Order 4:  A→C3, B→C1, C→C2
    (
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
    ),
    # Order 5:  A→C3, B→C2, C→C1
    (
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.VOLUNTARY_INTERVENTION,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.FINAL_CHECKPOINT,
        StudyCondition.STEPWISE,
        StudyCondition.STEPWISE,
    ),
]

# 3 pair rotations: swap which task from a pair occupies which position.
# The condition order (always C1,C1,C2,C2,C3,C3) is fixed; the rotation
# changes which task lands in which condition but does NOT change the
# condition assigned to a pair — both tasks of Pair A always receive C1,
# both of Pair B always receive C2, both of Pair C always receive C3.
#
# Rotation 0: default — Pair A positions 0,1 = [maps, gallery]
# Rotation 1: swap Pair A positions → [gallery, maps] (both still C1)
# Rotation 2: swap Pair B positions → [email, chat] (both still C2)
_PAIR_ROTATIONS: list[tuple[str, ...]] = [
    # Rotation 0: [maps, gallery, chat, email, calendar, banking]
    (
        "task_maps_messenger",
        "task_gallery_notes",
        "task_chat_spotify",
        "task_email_calendar",
        "task_calendar_dnd",
        "task_banking_payment",
    ),
    # Rotation 1: swap Pair A → [gallery, maps, chat, email, calendar, banking]
    (
        "task_gallery_notes",
        "task_maps_messenger",
        "task_chat_spotify",
        "task_email_calendar",
        "task_calendar_dnd",
        "task_banking_payment",
    ),
    # Rotation 2: swap Pair B → [maps, gallery, email, chat, calendar, banking]
    (
        "task_maps_messenger",
        "task_gallery_notes",
        "task_email_calendar",
        "task_chat_spotify",
        "task_calendar_dnd",
        "task_banking_payment",
    ),
]


# ── Helper functions ────────────────────────────────────────────────────────


def _select_error_tasks(
    task_ids: list[str],
    count: int = NUM_ERROR_TASKS,
    seed_offset: int = 0,
) -> tuple[str, ...]:
    """Select exactly ``count`` task IDs for controlled error injection.

    Uses a deterministic round-robin schedule so each task ID is an error
    task for exactly ``18 // 6 = 3`` of the 18 participants (or as evenly
    distributed as possible when count ≠ 1).

    Parameters
    ----------
    task_ids : list[str]
        All task IDs available for error injection (typically all 6).
    count : int
        Number of error tasks per participant (always 3).
    seed_offset : int
        Participant index (0–17) used as round-robin offset.

    Returns
    -------
    tuple[str, ...]
        Exactly ``count`` sorted task IDs.
    """
    n_tasks = len(task_ids)
    if n_tasks == 0:
        return ()
    if n_tasks <= count:
        return tuple(sorted(task_ids))
    # Round-robin: participant i gets tasks at positions
    # (i*count) mod n_tasks, ((i*count)+1) mod n_tasks, ..., ((i*count)+count-1) mod n_tasks.
    # Over 18 participants this distributes each task exactly 9 times.
    offset = (seed_offset * count) % n_tasks
    selected = [task_ids[(offset + j) % n_tasks] for j in range(count)]
    return tuple(sorted(selected))


def _rotate_screen_off(participant_index: int) -> tuple[ScreenOffMode, ...]:
    """Return a rotation of the three screen-off initiation modes.

    Rotates cyclically by ``participant_index`` so each participant gets a
    different mode order while maintaining full determinism.
    """
    n = len(ALL_SCREEN_OFF_MODES)
    offset = participant_index % n
    return tuple(
        ALL_SCREEN_OFF_MODES[(i + offset) % n] for i in range(n)
    )


def generate_participant_config(
    participant_id: str,
    task_ids: tuple[str, ...],
    criticalities: dict[str, CriticalityClass],
    condition_order: tuple[StudyCondition, ...],
    specs: dict[str, TrialSpec] | None = None,
) -> ParticipantConfig:
    """Generate a ParticipantConfig for a single participant slot.

    Parameters
    ----------
    participant_id : str
        Participant ID (e.g. "P01").
    task_ids : tuple[str, ...]
        Ordered list of exactly 6 task spec IDs.
    criticalities : dict[str, CriticalityClass]
        Mapping of task ID to criticality class.
    condition_order : tuple[StudyCondition, ...]
        Ordered list of conditions for this participant (length 6).
    specs : dict[str, TrialSpec] | None
        Trial specs for error-step lookup.

    Returns
    -------
    ParticipantConfig
        Fully populated config for the participant.
    """
    # Participant-specific error task selection — rotate seed per
    # participant so each gets a different but deterministic set of
    # 3 error tasks.
    participant_num = int(participant_id[1:]) - 1
    if specs:
        tasks_with_errors = sorted(
            tid for tid in task_ids
            if specs.get(tid) and specs[tid].error_steps
        )
    else:
        tasks_with_errors = list(task_ids)
    error_tasks = _select_error_tasks(
        tasks_with_errors if tasks_with_errors else list(task_ids),
        NUM_ERROR_TASKS,
        seed_offset=participant_num,
    )
    screen_off_order = _rotate_screen_off(int(participant_id[1:]) - 1)

    pair_assignments = tuple(
        (tid, criticalities.get(tid, CriticalityClass.LOW))
        for tid in task_ids
    )

    return ParticipantConfig(
        participant_id=participant_id,
        condition_order=condition_order,
        task_order=task_ids,
        error_tasks=error_tasks,
        screen_off_order=screen_off_order,
        screen_off_tasks=(
            "screen_off_weather",
            "screen_off_project_group",
            "screen_off_email_calendar",
        ),
        pair_assignments=pair_assignments,
    )


# ── Matrix generation ───────────────────────────────────────────────────────


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

    low_keys = sorted(low_tasks.keys())
    high_keys = sorted(high_tasks.keys())

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

    pairs: list[TaskPair] = []
    for i in range(min(len(low_keys), len(high_keys))):
        pairs.append(TaskPair(
            id=f"pair_{low_keys[i]}_{high_keys[i]}",
            low_task=low_keys[i],
            high_task=high_keys[i],
        ))

    return pairs


def generate_matrix(
    specs: dict[str, TrialSpec],
    seed: int | None = None,
) -> dict[str, ParticipantConfig]:
    """Generate the full P01–P18 participant matrix.

    Produces exactly 18 unique schedules from 6 condition orders x 3
    pair rotations. Every participant receives all 6 tasks (3 low + 3
    high criticality), each condition appears exactly twice, and exactly
    3 tasks are scheduled for error injection.

    Parameters
    ----------
    specs : dict[str, TrialSpec]
        Mapping of trial spec ID to validated TrialSpec.
    seed : int | None
        Random seed (unused; matrix is fully deterministic).

    Returns
    -------
    dict[str, ParticipantConfig]
        P01–P18 participant configs.

    Raises
    ------
    ValueError
        If the 6 expected spec IDs are not present.
    """
    # Validate that exactly the 6 expected task specs exist
    expected_task_ids = ("task_maps_messenger", "task_gallery_notes",
                         "task_chat_spotify", "task_email_calendar",
                         "task_calendar_dnd", "task_banking_payment")
    for tid in expected_task_ids:
        if tid not in specs:
            raise ValueError(f"Missing required spec: {tid}")

    criticalities = {tid: specs[tid].criticality for tid in expected_task_ids}
    configs: dict[str, ParticipantConfig] = {}

    for participant_idx in range(NUM_PARTICIPANTS):
        condition_order_idx = participant_idx % len(_CONDITION_ORDERS)
        pair_rotation_idx = participant_idx // len(_CONDITION_ORDERS)

        condition_order = _CONDITION_ORDERS[condition_order_idx]
        task_ids = _PAIR_ROTATIONS[pair_rotation_idx]
        participant_id = f"P{participant_idx + 1:02d}"

        config = generate_participant_config(
            participant_id=participant_id,
            task_ids=task_ids,
            criticalities=criticalities,
            condition_order=condition_order,
            specs=specs,
        )
        configs[config.participant_id] = config

    # Validate cohort-level invariants
    _validate_cohort_balance(configs, expected_task_ids)

    return configs


def _validate_cohort_balance(
    configs: dict[str, ParticipantConfig],
    all_task_ids: tuple[str, ...],
) -> None:
    """Validate cohort-level balance invariants after matrix generation.

    Checks:
    - 18 participants generated
    - Exactly 6 tasks per participant
    - Exactly 2 of each condition per participant
    - Exactly 3 error tasks per participant
    - All task IDs are known
    """
    assert len(configs) == NUM_PARTICIPANTS, (
        f"Expected {NUM_PARTICIPANTS} participants, got {len(configs)}"
    )

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


# ── Public API ───────────────────────────────────────────────────────────────


def generate_from_specs_dir(
    specs_dir: pathlib.Path | str | None = None,
    seed: int | None = None,
) -> dict[str, ParticipantConfig]:
    """Generate the full matrix from YAML files in the specs directory.

    This is the convenience entry point for the experimenter CLI:
    load specs, validate, and produce the matrix.

    Parameters
    ----------
    specs_dir : pathlib.Path | str | None
        Directory containing study spec YAML files. Defaults to the
        standard study/specs directory.
    seed : int | None
        Random seed (unused; matrix is deterministic).

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
    print("CADDIE STUDY - PARTICIPANT COUNTERBALANCING MATRIX")
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
    try:
        configs = generate_from_specs_dir()
        print_matrix(configs)
    except ValueError as e:
        print(f"Not enough specs loaded: {e}")
        print("Create at least 6 trial spec YAML files in mcp-server/study/specs/")
    except SpecError as e:
        print(f"Spec validation failed: {e}")
