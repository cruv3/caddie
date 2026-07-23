"""Tests for the redesigned counterbalancing matrix: 18 schedules from 6
condition orders × 3 pair rotations.

These tests replace the old latin-square / round-robin tests and verify:
  - Exactly 6 fixed task IDs present in every participant
  - Pair preservation: tasks from the same pair share a condition
  - All 18 schedules are unique (condition_order + task_order differ)
  - Each participant has exactly 3 error tasks
  - Condition balance: 2 of each condition per participant
"""

import pathlib
import tempfile

import yaml

from caddie.study.matrix import (
    NUM_PARTICIPANTS,
    NUM_ERROR_TASKS,
    NUM_TASKS_PER_PARTICIPANT,
    NUM_CONDITIONS,
    NUM_PAIRS_PER_PARTICIPANT,
    generate_matrix,
    generate_from_specs_dir,
    _CONDITION_ORDERS,
    _PAIR_ROTATIONS,
    ALL_CONDITIONS,
)
from caddie.study.model import (
    StudyCondition,
    StudyStep,
    StepType,
    CriticalityClass,
    TrialSpec,
)
from caddie.study.spec_loader import (
    SpecError,
    load_trial_spec,
    list_available_specs,
    load_all_specs,
)


# ── Helper: create a minimal valid spec ────────────────────────────────────


def _make_spec(
    tid: str,
    criticality: str,
    error_steps: list[str] | None = None,
) -> TrialSpec:
    """Create a TrialSpec suitable for matrix tests."""
    return TrialSpec(
        version="v1",
        id=tid,
        instruction_de=f"Task {tid}",
        criticality=CriticalityClass(criticality),
        steps=(
            StudyStep(
                id="open",
                action="open com.example",
                narration="Open app",
                step_type=StepType.NORMAL,
            ),
            StudyStep(
                id="commit",
                action="click Send",
                narration="Send",
                step_type=StepType.COMMIT,
                error_variant={
                    "id": f"err_{tid}",
                    "field": "field",
                    "wrong_value": "wrong",
                    "correct_value": "correct",
                    "description": "Error",
                }
                if error_steps
                else None,
            ),
        ),
        error_steps=tuple(error_steps) if error_steps else (),
        reset_checklist=["App ist im Home-Screen"],
    )


# ── Constants validation ────────────────────────────────────────────────────


def test_constants_correct():
    """Verify the exported constants match the design."""
    assert NUM_PARTICIPANTS == 18
    assert NUM_ERROR_TASKS == 3
    assert NUM_TASKS_PER_PARTICIPANT == 6
    assert NUM_CONDITIONS == 3
    assert NUM_PAIRS_PER_PARTICIPANT == 3


def test_condition_orders_count():
    """Exactly 6 condition orders."""
    assert len(_CONDITION_ORDERS) == 6


def test_pair_rotations_count():
    """Exactly 3 pair rotations."""
    assert len(_PAIR_ROTATIONS) == 3


def test_condition_orders_have_2_each():
    """Each condition order has exactly 2 of each condition."""
    for i, order in enumerate(_CONDITION_ORDERS):
        assert len(order) == 6
        from collections import Counter
        counts = Counter(order)
        for cond in ALL_CONDITIONS:
            assert counts[cond] == 2, (
                f"Order {i}: condition {cond.value} appears "
                f"{counts[cond]} times, expected 2"
            )


def test_condition_orders_unique():
    """All 6 condition orders are unique."""
    orders_as_tuples = [tuple(c.value for c in order) for order in _CONDITION_ORDERS]
    assert len(set(orders_as_tuples)) == 6


def test_pair_rotations_unique():
    """All 3 pair rotations are unique."""
    assert len(set(_PAIR_ROTATIONS)) == 3


def test_pair_rotations_all_6_tasks():
    """Each pair rotation contains all 6 task IDs."""
    expected_tasks = {
        "task_maps_messenger",
        "task_gallery_notes",
        "task_chat_spotify",
        "task_email_calendar",
        "task_calendar_dnd",
        "task_banking_payment",
    }
    for i, rotation in enumerate(_PAIR_ROTATIONS):
        assert set(rotation) == expected_tasks, (
            f"Rotation {i}: task set mismatch"
        )


# ── Matrix generation tests ────────────────────────────────────────────────


def _make_all_specs() -> dict[str, TrialSpec]:
    """Build the 6 required specs for matrix testing."""
    return {
        "task_maps_messenger": _make_spec("task_maps_messenger", "low", ["commit"]),
        "task_gallery_notes": _make_spec("task_gallery_notes", "high", ["commit"]),
        "task_chat_spotify": _make_spec("task_chat_spotify", "low", ["commit"]),
        "task_email_calendar": _make_spec("task_email_calendar", "high", ["commit"]),
        "task_calendar_dnd": _make_spec("task_calendar_dnd", "low", ["commit"]),
        "task_banking_payment": _make_spec("task_banking_payment", "high", ["commit"]),
    }


def test_generate_matrix_returns_18():
    """generate_matrix returns exactly 18 participant configs."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    assert len(configs) == 18


def test_each_participant_has_6_tasks():
    """Each participant receives exactly 6 tasks."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        assert len(cfg.task_order) == 6, f"{pid}: got {len(cfg.task_order)}"


def test_each_participant_has_6_conditions():
    """Each participant receives exactly 6 conditions."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        assert len(cfg.condition_order) == 6, f"{pid}: got {len(cfg.condition_order)}"


def test_each_participant_has_3_error_tasks():
    """Each participant has exactly 3 error tasks."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        assert len(cfg.error_tasks) == 3, f"{pid}: got {len(cfg.error_tasks)}"


def test_each_task_in_all_participants():
    """All 6 tasks appear in every participant's task order."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    expected = frozenset(specs.keys())
    for pid, cfg in sorted(configs.items()):
        assert set(cfg.task_order) == expected, (
            f"{pid}: missing or extra tasks: {set(cfg.task_order) ^ expected}"
        )


def test_each_condition_twice_per_participant():
    """Each condition appears exactly twice per participant."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    from collections import Counter
    for pid, cfg in sorted(configs.items()):
        cond_counts = Counter(cfg.condition_order)
        for cond in ALL_CONDITIONS:
            assert cond_counts[cond] == 2, (
                f"{pid}: {cond.value} appears {cond_counts[cond]} times"
            )


def test_pair_preservation():
    """Tasks from the same pair always share a condition.

    Pair A: task_maps_messenger + task_gallery_notes
    Pair B: task_chat_spotify + task_email_calendar
    Pair C: task_calendar_dnd + task_banking_payment
    """
    specs = _make_all_specs()
    configs = generate_matrix(specs)

    pair_map = {
        "task_maps_messenger": "A",
        "task_gallery_notes": "A",
        "task_chat_spotify": "B",
        "task_email_calendar": "B",
        "task_calendar_dnd": "C",
        "task_banking_payment": "C",
    }

    for pid, cfg in sorted(configs.items()):
        for pos in range(len(cfg.task_order)):
            task = cfg.task_order[pos]
            pair = pair_map[task]
            # Check that ALL tasks in the same pair have the same condition
            pair_conds = set()
            for pos2 in range(len(cfg.task_order)):
                task2 = cfg.task_order[pos2]
                if pair_map[task2] == pair:
                    pair_conds.add(cfg.condition_order[pos2])
            assert len(pair_conds) == 1, (
                f"{pid}: Pair {pair} tasks span multiple conditions: {pair_conds}"
            )


def test_all_18_schedules_unique():
    """No two participants share the same (condition_order, task_order)."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)

    schedules: dict[tuple, str] = {}
    for pid, cfg in sorted(configs.items()):
        schedule_key = (
            tuple(cfg.condition_order),
            cfg.task_order,
        )
        assert schedule_key not in schedules, (
            f"Duplicate schedule for {pid}: {schedule_key} "
            f"already assigned to {schedules[schedule_key]}"
        )
        schedules[schedule_key] = pid

    assert len(schedules) == 18, f"Only {len(schedules)} unique schedules"


def test_3_condition_orders_used():
    """All 6 condition orders and all 3 pair rotations are used."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)

    used_cond_orders = set()
    used_rotations = set()
    for pid, cfg in sorted(configs.items()):
        used_cond_orders.add(tuple(cfg.condition_order))
        used_rotations.add(cfg.task_order)

    assert len(used_cond_orders) == 6, (
        f"Only {len(used_cond_orders)} condition orders used"
    )
    assert len(used_rotations) == 3, (
        f"Only {len(used_rotations)} rotations used"
    )


def test_3_error_tasks_per_participant():
    """Every participant has exactly 3 error tasks, all in task_order."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        assert len(cfg.error_tasks) == 3
        for et in cfg.error_tasks:
            assert et in cfg.task_order, f"{pid}: error task {et} not in task_order"


def test_all_tasks_have_error_variant():
    """Every task in the 6-task set has at least one error step in its spec."""
    specs = _make_all_specs()
    for tid, spec in specs.items():
        assert len(spec.error_steps) >= 1, f"{tid}: no error steps"


def test_deterministic_with_seed():
    """Same seed produces identical results."""
    specs = _make_all_specs()
    configs1 = generate_matrix(specs, seed=42)
    configs2 = generate_matrix(specs, seed=42)
    for pid in configs1:
        assert configs1[pid].task_order == configs2[pid].task_order
        assert configs1[pid].condition_order == configs2[pid].condition_order
        assert configs1[pid].error_tasks == configs2[pid].error_tasks


# ── Integration: load from YAML specs dir ──────────────────────────────────


def test_generate_from_specs_dir_returns_18():
    """Integration: load from actual YAML files and produce 18 configs."""
    configs = generate_from_specs_dir()
    assert len(configs) == 18
    assert "P01" in configs
    assert "P18" in configs


def test_yaml_specs_all_6_tasks():
    """All 6 expected task IDs are present in the specs directory."""
    specs = load_all_specs()
    expected = {
        "task_maps_messenger",
        "task_gallery_notes",
        "task_chat_spotify",
        "task_email_calendar",
        "task_calendar_dnd",
        "task_banking_payment",
    }
    assert set(specs.keys()) == expected, f"Actual keys: {set(specs.keys())}"


def test_no_legacy_tasks_remain():
    """REWE, old gallery-messenger, old music-playlist, old banking-transfer are gone."""
    specs = load_all_specs()
    legacy = {"task_rewe_shopping", "task_music_playlist",
              "task_chat_notes", "task_gallery_messenger",
              "task_banking_transfer"}
    assert set(specs.keys()).isdisjoint(legacy), (
        f"Legacy tasks still present: {set(specs.keys()) & legacy}"
    )


# ── Criticality balance ────────────────────────────────────────────────────


def test_3_low_3_high_per_participant():
    """Each participant gets 3 low + 3 high criticality tasks."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        low = sum(1 for tid in cfg.task_order
                  if specs[tid].criticality == CriticalityClass.LOW)
        high = sum(1 for tid in cfg.task_order
                   if specs[tid].criticality == CriticalityClass.HIGH)
        assert low == 3, f"{pid}: {low} low tasks"
        assert high == 3, f"{pid}: {high} high tasks"


# ── Error exposure balance ─────────────────────────────────────────────────


def test_error_tasks_unique_per_participant():
    """Every participant has 3 unique error task IDs."""
    specs = _make_all_specs()
    configs = generate_matrix(specs)
    for pid, cfg in sorted(configs.items()):
        assert len(set(cfg.error_tasks)) == 3, (
            f"{pid}: error tasks not unique: {cfg.error_tasks}"
        )


def test_error_tasks_deterministic_per_participant():
    """Two matrix generations produce identical error tasks."""
    specs = _make_all_specs()
    configs1 = generate_matrix(specs, seed=42)
    configs2 = generate_matrix(specs, seed=42)
    for pid in sorted(configs1):
        assert configs1[pid].error_tasks == configs2[pid].error_tasks


def test_error_tasks_balanced_across_cohort():
    """Each of the 6 tasks is an error task for ≈6 participants
    (±1 difference).

    With 18 participants × 3 error tasks = 54 error-task slots and 6 tasks,
    ideal balance is 54 / 6 = 9 per task.  The rotated-seed scheme produces
    a near-perfect distribution: each task appears as an error task for
    exactly 9 participants.
    """
    specs = _make_all_specs()
    configs = generate_matrix(specs)

    from collections import Counter
    error_counts: Counter = Counter()
    for cfg in configs.values():
        for tid in cfg.error_tasks:
            error_counts[tid] += 1

    for tid in specs:
        count = error_counts.get(tid, 0)
        assert count == 9, (
            f"Task '{tid}' is an error task for {count} participants, "
            f"expected 9 (54 slots / 6 tasks)"
        )
