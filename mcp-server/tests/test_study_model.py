"""Tests for caddie.study.model — enums, frozen dataclasses, defaults."""

from caddie.study.model import (
    CriticalityClass,
    ErrorVariant,
    InitiationResult,
    ParticipantConfig,
    ScreenOffMode,
    StepType,
    StudyCondition,
    StudyStep,
    TaskPair,
    TrialOutcome,
    TrialSpec,
    VerificationRule,
)


# ── Enum value tests ────────────────────────────────────────────────────────


def test_study_condition_values():
    assert StudyCondition.STEPWISE == "c1_stepwise"
    assert StudyCondition.FINAL_CHECKPOINT == "c2_final_checkpoint"
    assert StudyCondition.VOLUNTARY_INTERVENTION == "c3_voluntary_intervention"


def test_step_type_values():
    assert StepType.NORMAL == "normal"
    assert StepType.CONSEQUENTIAL == "consequential"
    assert StepType.COMMIT == "commit"


def test_criticality_class_values():
    assert CriticalityClass.LOW == "low"
    assert CriticalityClass.HIGH == "high"


def test_screen_off_mode_values():
    assert ScreenOffMode.NOTIFY_ONLY == "notify_only"
    assert ScreenOffMode.WAKE_ASK == "wake_ask"
    assert ScreenOffMode.WAKE_EXECUTE == "wake_execute"


def test_trial_outcome_values():
    outcomes = {o.value for o in TrialOutcome}
    expected = {
        "success", "error_injected", "verification_failed",
        "confirmation_timeout", "aborted", "technical_failure",
        "participant_stop", "repeated",
    }
    assert outcomes == expected


# ── StudyStep — defaults and immutability ───────────────────────────────────


def test_study_step_defaults():
    step = StudyStep(
        id="open_chat",
        action="com.caddie/.MainActivity",
        narration="Chat wird geoeffnet...",
        step_type=StepType.NORMAL,
    )
    assert step.consequential is False
    assert step.commit is False
    assert step.error_variant is None
    assert step.min_narration_ms == 800


def test_study_step_defaults_explicit():
    """consequential and commit are derived from step_type (R5 MAJOR #1)."""
    step = StudyStep(
        id="send_msg",
        action="click 'Send'",
        narration="Nachricht senden...",
        step_type=StepType.CONSEQUENTIAL,
    )
    assert step.consequential is True  # derived from CONSEQUENTIAL
    assert step.commit is False


def test_study_step_explicit_consequential_and_commit():
    step = StudyStep(
        id="confirm_transfer",
        action="click 'Confirm'",
        narration="Ueberweisung bestaetigen...",
        step_type=StepType.COMMIT,
        consequential=True,
        commit=True,
    )
    assert step.consequential is True
    assert step.commit is True


def test_study_step_explicit_overrides():
    """Explicit consequential/commit are overridden by step_type derivation."""
    step = StudyStep(
        id="search",
        action="input text 'Milk'",
        narration="Produkt suchen...",
        step_type=StepType.NORMAL,
        consequential=True,
        commit=False,
        min_narration_ms=400,
    )
    assert step.consequential is False  # derived from NORMAL
    assert step.commit is False
    assert step.min_narration_ms == 400


def test_study_step_with_error_variant():
    ev = ErrorVariant(
        id="err_wrong_recipient",
        field="recipient",
        wrong_value="Alice",
        correct_value="Bob",
        description="Empfaenger ist falsch",
    )
    step = StudyStep(
        id="enter_recipient",
        action="input text 'Alice'",
        narration="Empfaenger eingeben...",
        step_type=StepType.CONSEQUENTIAL,
        error_variant=ev,
    )
    assert step.error_variant is not None
    assert step.error_variant.field == "recipient"


def test_study_step_is_frozen():
    step = StudyStep(
        id="s1",
        action="x",
        narration="y",
        step_type=StepType.NORMAL,
    )
    try:
        step.id = "modified"
        assert False, "Should not be mutable"
    except Exception:
        pass  # Expected


# ── ErrorVariant ────────────────────────────────────────────────────────────


def test_error_variant_creation():
    ev = ErrorVariant(
        id="err_1",
        field="amount",
        wrong_value="50.00",
        correct_value="100.00",
        description="Betrag ist falsch",
    )
    assert ev.field == "amount"
    assert ev.wrong_value == "50.00"
    assert ev.correct_value == "100.00"


# ── VerificationRule ────────────────────────────────────────────────────────


def test_verification_rule_defaults():
    rule = VerificationRule(
        id="v1",
        assertion="Text ist sichtbar",
        check_type="text_present",
        parameters={"text": "Hello"},
    )
    assert rule.screenshot_evidence is True


# ── TrialSpec ───────────────────────────────────────────────────────────────


def test_trial_spec_minimal():
    steps = (StudyStep(
        id="s1",
        action="click 'Send'",
        narration="Send message",
        step_type=StepType.NORMAL,
    ),)
    spec = TrialSpec(
        version="v1",
        id="task_test",
        instruction_de="Test instruction",
        criticality=CriticalityClass.HIGH,
        steps=steps,
    )
    assert spec.required_packages == ()
    assert spec.seeded_artifacts == ()
    assert spec.reset_checklist == ()
    assert len(spec.steps) == 1
    assert spec.error_steps == ()
    assert spec.c2_summary_lines == ()
    assert spec.verification == ()
    assert spec.max_duration_s == 300
    assert spec.per_gate_timeout_s == 30


# ── ParticipantConfig ───────────────────────────────────────────────────────


def test_participant_config_structure():
    cfg = ParticipantConfig(
        participant_id="P01",
        condition_order=(
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
            StudyCondition.STEPWISE,
            StudyCondition.FINAL_CHECKPOINT,
            StudyCondition.VOLUNTARY_INTERVENTION,
        ),
        task_order=("t1", "t2", "t3", "t4", "t5", "t6"),
        error_tasks=("t2", "t4", "t6"),
        screen_off_order=(ScreenOffMode.NOTIFY_ONLY, ScreenOffMode.WAKE_ASK, ScreenOffMode.WAKE_EXECUTE),
        screen_off_tasks=("so1", "so2", "so3"),
    )
    assert cfg.participant_id == "P01"
    assert len(cfg.condition_order) == 6
    assert len(cfg.task_order) == 6
    assert len(cfg.error_tasks) == 3
