"""Tests for caddie.study.matrix — participant counterbalancing."""

from collections import Counter

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
    StudyStep,
    StepType,
    CriticalityClass,
    ScreenOffMode,
    ParticipantConfig,
    TrialSpec,
)
from caddie.study.spec_loader import load_all_specs


# ── Constants ───────────────────────────────────────────────────────────────


def test_constants():
    assert NUM_PARTICIPANTS == 18
    assert NUM_ERROR_TASKS == 3
    assert NUM_SCREEN_OFF_TASKS == 3


# ── generate_matrix ─────────────────────────────────────────────────────────


def _make_specs(*ids_and_crits) -> dict[str, TrialSpec]:
    """Helper: create TrialSpec dicts from (id, criticality) pairs.

    Supports two calling conventions:
      - Flat: _make_specs("t1", "low", "t2", "high") — alternating id, crit
      - Tuples: _make_specs(("t1", "low"), ("t2", "high"))
    Flat strings without a crit default to alternating low/high.
    """
    specs = {}

    def _add(tid: str, crit: str) -> None:
        steps = (StudyStep(
            id="s1",
            action="open com.example",
            narration=f"Task {tid}",
            step_type=StepType.NORMAL,
        ),)
        specs[tid] = TrialSpec(
            version="v1",
            id=tid,
            instruction_de=f"Task {tid}",
            criticality=CriticalityClass(crit),
            steps=steps,
            reset_checklist=["App ist im Home-Screen"],
        )

    # Flatten tuples into (id, crit) pairs
    flat: list[tuple[str, str]] = []
    i = 0
    while i < len(ids_and_crits):
        item = ids_and_crits[i]
        if isinstance(item, tuple):
            flat.append((item[0], item[1]))
            i += 1
        elif i + 1 < len(ids_and_crits) and ids_and_crits[i + 1] in ("low", "high"):
            # Flat pair: id, crit
            flat.append((item, ids_and_crits[i + 1]))
            i += 2
        else:
            # Bare string without crit — alternating default
            flat.append((item, "low" if len(flat) % 2 == 0 else "high"))
            i += 1

    for tid, crit in flat:
        _add(tid, crit)

    return specs


def test_generate_matrix_raises_with_too_few_specs():
    specs = _make_specs("t1", "low")
    try:
        generate_matrix(specs)
        assert False, "Should raise ValueError"
    except ValueError as e:
        assert "task_maps_messenger" in str(e)


def test_generate_matrix_creates_18_participants():
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs, seed=42)
    assert len(configs) == 18
    for pid in [f"P{i:02d}" for i in range(1, 19)]:
        assert pid in configs


def test_each_participant_has_6_tasks():
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    assert len(specs) == 6
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.task_order) == 6


def test_each_participant_has_6_conditions():
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.condition_order) == 6
        for cond in cfg.condition_order:
            assert cond in (StudyCondition.STEPWISE,
                            StudyCondition.FINAL_CHECKPOINT,
                            StudyCondition.VOLUNTARY_INTERVENTION)


def test_each_participant_has_3_error_tasks():
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs, seed=42)
    for cfg in configs.values():
        assert len(cfg.error_tasks) == 3


def test_condition_balance_across_cohort():
    """Each condition should appear exactly twice per participant."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
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

    # 18 participants × 6 tasks = 108 condition slots
    # 3 conditions × 36 each
    expected = 108 // 3
    for cond in counts:
        assert counts[cond] == expected, f"Condition {cond}: {counts[cond]} != {expected}"


def test_screen_off_modes_present():
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
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
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs1 = generate_matrix(specs, seed=42)
    configs2 = generate_matrix(specs, seed=42)

    for pid in configs1:
        assert configs1[pid].condition_order == configs2[pid].condition_order
        assert configs1[pid].task_order == configs2[pid].task_order
        assert configs1[pid].error_tasks == configs2[pid].error_tasks


def test_generate_from_specs_dir_works():
    """Integration test: load from actual YAML files."""
    configs = generate_from_specs_dir()
    assert len(configs) == 18
    assert "P01" in configs


def test_real_specs_assign_six_distinct_tasks_per_participant():
    configs = generate_from_specs_dir(seed=42)

    for cfg in configs.values():
        assert len(cfg.task_order) == 6
        assert len(set(cfg.task_order)) == 6, (
            f"{cfg.participant_id}: duplicate tasks in {cfg.task_order}"
        )


def test_real_specs_assign_three_tasks_of_each_criticality():
    specs = load_all_specs()
    configs = generate_from_specs_dir(seed=42)

    for cfg in configs.values():
        criticalities = [specs[task_id].criticality for task_id in cfg.task_order]
        assert criticalities.count(CriticalityClass.LOW) == 3, cfg.participant_id
        assert criticalities.count(CriticalityClass.HIGH) == 3, cfg.participant_id


def test_real_specs_assign_three_distinct_eligible_error_tasks():
    specs = load_all_specs()
    configs = generate_from_specs_dir(seed=42)

    for cfg in configs.values():
        assert len(cfg.error_tasks) == 3
        assert len(set(cfg.error_tasks)) == 3, (
            f"{cfg.participant_id}: duplicate errors in {cfg.error_tasks}"
        )
        assert set(cfg.error_tasks) <= set(cfg.task_order), cfg.participant_id
        assert all(specs[task_id].error_steps for task_id in cfg.error_tasks)


def test_real_specs_balance_task_exposure_across_the_cohort():
    specs = load_all_specs()
    configs = generate_from_specs_dir(seed=42)
    exposure = Counter(
        task_id for cfg in configs.values() for task_id in cfg.task_order
    )

    assert set(exposure) == set(specs)
    assert all(count == 18 for count in exposure.values()), exposure
    for criticality in (CriticalityClass.LOW, CriticalityClass.HIGH):
        pool = {
            task_id
            for task_id, spec in specs.items()
            if spec.criticality == criticality
        }
        pool_exposure = {task_id: exposure[task_id] for task_id in pool}
        assert sum(pool_exposure.values()) == 54
        assert all(count == 18 for count in pool_exposure.values()), (
            criticality,
            pool_exposure,
        )


def test_real_specs_balance_eligible_error_exposure_across_the_cohort():
    specs = load_all_specs()
    configs = generate_from_specs_dir(seed=42)
    eligible = {task_id for task_id, spec in specs.items() if spec.error_steps}
    exposure = Counter(
        task_id for cfg in configs.values() for task_id in cfg.error_tasks
    )

    assert set(exposure) == eligible
    assert sum(exposure.values()) == 54
    expected_floor = 54 // len(eligible)
    expected_ceil = expected_floor + bool(54 % len(eligible))
    assert all(
        count in {expected_floor, expected_ceil}
        for count in exposure.values()
    ), exposure


def test_print_matrix_does_not_raise():
    """print_matrix should produce output without exceptions."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs, seed=42)
    # Should not raise
    print_matrix({k: v for k, v in list(configs.items())[:3]})


# ---------------------------------------------------------------------------
# Cohort-level balancing
# ---------------------------------------------------------------------------


def test_cohort_condition_distribution():
    """Conditions are exactly balanced across the cohort."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
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
    # Each of 3 conditions should appear exactly 36 times
    total = sum(counts.values())
    assert total == 18 * 6
    for cond in ALL_CONDITIONS:
        expected = total // len(ALL_CONDITIONS)
        assert counts[cond] == expected, (
            f"Condition {cond}: {counts[cond]} vs expected {expected}"
        )


def test_cohort_task_criticality_balance():
    """Each participant receives exactly 3 low + 3 high criticality tasks."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs)

    # Count criticality assignments per participant
    for pid, cfg in configs.items():
        low = sum(1 for _, c in cfg.pair_assignments if c == CriticalityClass.LOW)
        high = sum(1 for _, c in cfg.pair_assignments if c == CriticalityClass.HIGH)
        assert low == 3, f"{pid}: Expected 3 low tasks, got {low}"
        assert high == 3, f"{pid}: Expected 3 high tasks, got {high}"


def test_criticality_class_not_string():
    """pair_assignments contains CriticalityClass enums, not strings."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs)

    for pid, cfg in configs.items():
        for task_id, criticality in cfg.pair_assignments:
            assert isinstance(criticality, CriticalityClass), (
                f"Pair assignment for {task_id} has {type(criticality)} "
                f"instead of CriticalityClass"
            )


# ── Cross-factor balance validation ────────────────────────────────────────


def test_cross_factor_condition_criticality_balance():
    """Condition distribution should be exactly balanced."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    assert len(specs) == 6
    configs = generate_matrix(specs)

    # Each participant gets 2 of each condition (18 × 2 = 36 each)
    counts = {c: 0 for c in StudyCondition}
    for cfg in configs.values():
        for cond in cfg.condition_order:
            counts[cond] += 1

    expected = 18 * 2
    for cond, count in counts.items():
        assert count == expected, (
            f"Condition {cond}: {count} (expected {expected})"
        )


def test_cross_factor_error_exposure_balance():
    """Error tasks: each participant has exactly 3 error tasks."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs)

    for pid, cfg in configs.items():
        assert len(cfg.error_tasks) == 3, (
            f"{pid}: expected 3 error tasks, got {len(cfg.error_tasks)}"
        )


def test_screen_off_rotation_balance():
    """Screen-off modes are rotated cyclically across participants."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs)

    # Collect all screen-off mode assignments
    all_modes = []
    for cfg in configs.values():
        all_modes.extend(cfg.screen_off_order)

    for mode in (ScreenOffMode.NOTIFY_ONLY, ScreenOffMode.WAKE_ASK, ScreenOffMode.WAKE_EXECUTE):
        count = sum(1 for m in all_modes if m == mode)
        assert count > 0, f"Mode {mode} not found in any participant"


def test_cohort_has_all_three_high():
    """Each participant should have exactly 3 low and 3 high criticality tasks."""
    specs = _make_specs(
        ("task_maps_messenger", "low"),
        ("task_gallery_notes", "high"),
        ("task_chat_spotify", "low"),
        ("task_email_calendar", "high"),
        ("task_calendar_dnd", "low"),
        ("task_banking_payment", "high"),
    )
    configs = generate_matrix(specs)

    for cfg in configs.values():
        low_count = sum(
            1 for tid in cfg.task_order
            if specs[tid].criticality == CriticalityClass.LOW
        )
        high_count = sum(
            1 for tid in cfg.task_order
            if specs[tid].criticality == CriticalityClass.HIGH
        )
        assert low_count == 3, f"{cfg.participant_id}: expected 3 low, got {low_count}"
        assert high_count == 3, f"{cfg.participant_id}: expected 3 high, got {high_count}"
