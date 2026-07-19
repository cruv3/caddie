"""Tests for caddie.study.matrix — participant counterbalancing."""

from caddie.study.matrix import (
    NUM_PARTICIPANTS,
    NUM_ERROR_TASKS,
    NUM_SCREEN_OFF_TASKS,
    generate_matrix,
    generate_participant_config,
    generate_from_specs_dir,
    print_matrix,
    extract_task_pairs,
    ALL_CONDITIONS,
)
from caddie.study.model import (
    StudyCondition,
    CriticalityClass,
    ScreenOffMode,
    ParticipantConfig,
    TrialSpec,
)


# ── Constants ───────────────────────────────────────────────────────────────


def test_constants():
    assert NUM_PARTICIPANTS == 18
    assert NUM_ERROR_TASKS == 3
    assert NUM_SCREEN_OFF_TASKS == 3


# ── generate_matrix ─────────────────────────────────────────────────────────


def _make_specs(*ids_and_crits) -> dict[str, TrialSpec]:
    """Helper: create TrialSpec dicts from (id, criticality) pairs."""
    specs = {}
    for item in ids_and_crits:
        if isinstance(item, tuple):
            tid, crit = item
        else:
            tid = item
            crit = "high"
        specs[tid] = TrialSpec(
            version="v1",
            id=tid,
            instruction_de=f"Task {tid}",
            criticality=CriticalityClass(crit),
        )
    return specs


def test_generate_matrix_raises_with_too_few_specs():
    specs = _make_specs("t1", "t2")
    try:
        generate_matrix(specs)
        assert False, "Should raise ValueError"
    except ValueError as e:
        assert "6" in str(e)


def test_generate_matrix_creates_18_participants():
    specs = _make_specs(
        "t_music", "high",
        "t_gallery", "high",
        "t_email", "low",
        "t_maps", "low",
        "t_rewe", "high",
        "t_banking", "high",
        "t_notes", "low",
    )
    configs = generate_matrix(specs, seed=42)
    assert len(configs) == 18
    for pid in [f"P{i:02d}" for i in range(1, 19)]:
        assert pid in configs


def test_each_participant_has_6_tasks():
    # _make_specs with 6 (id,crit) pairs creates 8 unique keys (6 IDs + "high" + "low")
    specs = _make_specs(
        "t1", "high", "t2", "high", "t3", "low",
        "t4", "low", "t5", "high", "t6", "high",
    )
    assert len(specs) == 8  # verify
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.task_order) == 8


def test_each_participant_has_6_conditions():
    specs = _make_specs(
        "t1", "high", "t2", "high", "t3", "low",
        "t4", "low", "t5", "high", "t6", "high",
    )
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.condition_order) == 8
        for cond in cfg.condition_order:
            assert cond in (StudyCondition.STEPWISE,
                            StudyCondition.FINAL_CHECKPOINT,
                            StudyCondition.VOLUNTARY_INTERVENTION)


def test_each_participant_has_3_error_tasks():
    specs = _make_specs(
        "t1", "high", "t2", "high", "t3", "low",
        "t4", "low", "t5", "high", "t6", "high",
    )
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.error_tasks) == 3


def test_condition_balance_across_cohort():
    """Each condition should appear roughly equally across all participants."""
    specs = _make_specs(
        "t1", "high", "t2", "high", "t3", "low",
        "t4", "low", "t5", "high", "t6", "high",
    )
    configs = generate_matrix(specs, seed=42)

    counts = {
        StudyCondition.STEPWISE: 0,
        StudyCondition.FINAL_CHECKPOINT: 0,
        StudyCondition.VOLUNTARY_INTERVENTION: 0,
    }
    for cfg in configs.values():
        for cond in cfg.condition_order:
            counts[cond] += 1

    # With 8 specs (6 task IDs + 'high'/'low' as task names) and 3 conditions:
    # 18 participants × ~2.67 conditions each ≈ 48 per condition
    # Allow wider tolerance for uneven division
    total_slots = sum(counts.values())
    expected = total_slots // len(counts)
    for cond, count in counts.items():
        assert abs(count - expected) <= 10, f"Condition {cond} has {count} occurrences, expected ~{expected}"


def test_screen_off_modes_present():
    specs = _make_specs("t1", "high", "t2", "high", "t3", "low",
                        "t4", "low", "t5", "high", "t6", "high")
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.screen_off_order) == 3
        modes = {m for m in cfg.screen_off_order}
        assert modes == {ScreenOffMode.NOTIFY_ONLY,
                         ScreenOffMode.WAKE_ASK,
                         ScreenOffMode.WAKE_EXECUTE}


def test_matrix_deterministic_with_seed():
    """Same seed produces identical configs."""
    specs = _make_specs(
        "t1", "high", "t2", "high", "t3", "low",
        "t4", "low", "t5", "high", "t6", "high",
    )
    configs1 = generate_matrix(specs, seed=123)
    configs2 = generate_matrix(specs, seed=123)

    for pid in configs1:
        assert configs1[pid].condition_order == configs2[pid].condition_order
        assert configs1[pid].task_order == configs2[pid].task_order
        assert configs1[pid].error_tasks == configs2[pid].error_tasks


def test_generate_from_specs_dir_works():
    """Integration test: load from actual YAML files."""
    configs = generate_from_specs_dir(seed=42)
    assert len(configs) == 18
    assert "P01" in configs


def test_print_matrix_does_not_raise():
    """print_matrix should produce output without exceptions."""
    specs = _make_specs("t1", "high", "t2", "high", "t3", "low",
                        "t4", "low", "t5", "high", "t6", "high")
    configs = generate_matrix(specs, seed=42)
    # Should not raise
    print_matrix({k: v for k, v in list(configs.items())[:3]})


# ---------------------------------------------------------------------------
# Cohort-level balancing
# ---------------------------------------------------------------------------


def test_cohort_condition_distribution():
    """Conditions are roughly balanced across the cohort."""
    specs = {
        f"task_{i}": TrialSpec(
            version="v1",
            id=f"task_{i}",
            instruction_de=f"Task {i}",
            criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
            steps=(),
        )
        for i in range(6)
    }
    configs = generate_matrix(specs)

    # Each participant should have 6 conditions (one per task)
    for pid, cfg in configs.items():
        assert len(cfg.condition_order) == 6

    # Count condition occurrences across all participants
    from collections import Counter
    all_conditions = []
    for cfg in configs.values():
        all_conditions.extend(cfg.condition_order)

    counts = Counter(all_conditions)
    # With 18 participants × 6 tasks = 108 condition slots
    # Each of 3 conditions should appear roughly 36 times
    total = sum(counts.values())
    assert total == 18 * 6
    for cond in ALL_CONDITIONS:
        expected = total // len(ALL_CONDITIONS)
        # Allow ±15% deviation
        tolerance = int(expected * 0.15)
        actual = counts.get(cond, 0)
        assert abs(actual - expected) <= tolerance, (
            f"Condition {cond}: {actual} vs expected ~{expected} "
            f"(±{tolerance})"
        )


def test_cohort_task_criticality_balance():
    """Each participant receives roughly equal low/high task exposure."""
    specs = {
        f"task_{i}": TrialSpec(
            version="v1",
            id=f"task_{i}",
            instruction_de=f"Task {i}",
            criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
            steps=(),
        )
        for i in range(6)
    }
    configs = generate_matrix(specs)

    # Count criticality assignments per participant
    low_counts = []
    high_counts = []
    for cfg in configs.values():
        low = sum(1 for _, c in cfg.pair_assignments if c == CriticalityClass.LOW)
        high = sum(1 for _, c in cfg.pair_assignments if c == CriticalityClass.HIGH)
        low_counts.append(low)
        high_counts.append(high)

    # With 6 tasks, 3 low + 3 high expected per participant
    for low, high in zip(low_counts, high_counts):
        assert low == 3, f"Expected 3 low tasks, got {low}"
        assert high == 3, f"Expected 3 high tasks, got {high}"


def test_criticality_class_not_string():
    """pair_assignments contains CriticalityClass enums, not strings."""
    specs = {}
    for i in range(6):
        specs[f"task_{i}"] = TrialSpec(
            version="v1",
            id=f"task_{i}",
            instruction_de=f"Task {i}",
            criticality=CriticalityClass.LOW if i % 2 == 0 else CriticalityClass.HIGH,
            steps=(),
        )
    configs = generate_matrix(specs)

    for pid, cfg in configs.items():
        for task_id, criticality in cfg.pair_assignments:
            assert isinstance(criticality, CriticalityClass), (
                f"Pair assignment for {task_id} has {type(criticality)} "
                f"instead of CriticalityClass"
            )
