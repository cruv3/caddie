"""Strict YAML loader and validator for trial specifications.

Loads YAML files from mcp-server/study/specs/, validates them against the
schema defined in model.TrialSpec, and returns immutable dataclass instances.

Unknown fields, duplicate IDs, missing narration, invalid gates, and missing
reset rules all raise SpecError before the phone is touched.
"""

from __future__ import annotations

import logging
import pathlib
from collections import Counter
from typing import Any

import yaml

from caddie.study.executor import _parse_action
from caddie.study.model import (
    CriticalityClass,
    ErrorVariant,
    StepType,
    StudyStep,
    TriggerContract,
    TrialOutcome,
    TrialSpec,
    VerificationRule,
)

# ── Paths ────────────────────────────────────────────────────────────────────

STUDY_SPECS_DIR = pathlib.Path(__file__).resolve().parent.parent.parent / "study" / "specs"


# ── Errors ───────────────────────────────────────────────────────────────────


class SpecError(Exception):
    """Raised when a trial spec fails validation."""


# ── Allowed schema keys ──────────────────────────────────────────────────────

_STUDY_STEP_KEYS = frozenset({
    "id",
    "action",
    "narration",
    "step_type",
    "consequential",
    "commit",
    "error_variant",
    "min_narration_ms",
})

_ERROR_VARIANT_KEYS = frozenset({
    "id",
    "field",
    "wrong_value",
    "correct_value",
    "description",
})

VALID_CHECK_TYPES = frozenset({
    "accessibility_check",
    "screenshot_match",
    "text_present",
    "text_absent",
    "field_count",
})

_VERIFICATION_KEYS = frozenset({
    "id",
    "assertion",
    "check_type",
    "parameters",
    "screenshot_evidence",
})

_TRIGGER_KEYS = frozenset({
    "reference_phrases",
    "required_concepts",
    "forbidden_concepts",
    "wake_words",
})

_TRIAL_SPEC_KEYS = frozenset({
    "version",
    "id",
    "instruction_de",
    "criticality",
    "trigger",
    "required_packages",
    "seeded_artifacts",
    "reset_checklist",
    "steps",
    "error_steps",
    "c2_summary_lines",
    "verification",
    "max_duration_s",
    "per_gate_timeout_s",
})


# ── Helper validators ────────────────────────────────────────────────────────


def _to_str(val: Any, path: str) -> str:
    """Convert a value to str, raising SpecError for non-string types."""
    if isinstance(val, str):
        return val
    if isinstance(val, (int, float)) and not isinstance(val, bool):
        return str(val)
    raise SpecError(f"{path}: expected a string or number, got {type(val).__name__}")


def _check_unknown_fields(data: dict[str, Any], expected: frozenset, path: str) -> None:
    """Raise SpecError if data contains keys not in expected."""
    extra = set(data.keys()) - expected
    if extra:
        raise SpecError(f"{path}: unknown field(s): {sorted(extra)}")


def _check_required(data: dict[str, Any], required_keys: list[str], path: str) -> None:
    """Raise SpecError if any required key is missing."""
    missing = [k for k in required_keys if k not in data]
    if missing:
        raise SpecError(f"{path}: missing required field(s): {missing}")


def _str_list(data: Any, path: str) -> list[str]:
    if data is None:
        return []
    if isinstance(data, list):
        return [str(v) for v in data]
    raise SpecError(f"{path}: expected a list, got {type(data).__name__}")


def _str_tuple(data: Any, path: str) -> tuple[str, ...]:
    return tuple(_str_list(data, path))


def _non_empty_string_tuple(
    data: Any,
    path: str,
    *,
    require_items: bool,
) -> tuple[str, ...]:
    """Strictly validate a list of non-empty strings without coercion."""
    if not isinstance(data, list):
        raise SpecError(f"{path}: expected a list, got {type(data).__name__}")
    if require_items and not data:
        raise SpecError(f"{path}: must be a non-empty list")

    values: list[str] = []
    for index, value in enumerate(data):
        item_path = f"{path}[{index}]"
        if not isinstance(value, str):
            raise SpecError(
                f"{item_path}: expected a non-empty string, "
                f"got {type(value).__name__}"
            )
        if not value.strip():
            raise SpecError(f"{item_path}: must be a non-empty string")
        values.append(value)
    return tuple(values)


def _safe_bool(val: Any, default: bool) -> bool:
    """Strictly parse a boolean value, avoiding Python's bool('false') == True.

    Raises SpecError for unrecognized string values (e.g. "perhaps", "FALSE ").
    """
    if val is None:
        return default
    if isinstance(val, bool):
        return val
    low = str(val).strip().lower()
    if low in ("true", "1", "yes"):
        return True
    if low in ("false", "0", "no"):
        return False
    raise SpecError(f"Invalid boolean value: {val!r} (expected true/false/yes/no/1/0)")


def _safe_int(val: Any, default: int = 0) -> int:
    """Strictly parse an integer value.

    Rejects booleans (since ``isinstance(True, int)`` is ``True`` in Python)
    and non-integral floats (MAJOR: review #23).  Accepts strings that
    represent whole numbers.

    Raises SpecError for unrecognized values.
    """
    if isinstance(val, bool):
        raise SpecError(f"Integer field received boolean: {val!r}")
    if isinstance(val, int):
        return val
    if isinstance(val, float):
        if val != int(val):
            raise SpecError(
                f"Integer field received non-integral float: {val!r}"
            )
        return int(val)
    if isinstance(val, str):
        try:
            return int(val)
        except ValueError:
            raise SpecError(f"Invalid integer string: {val!r}")
    raise SpecError(f"Invalid integer type: {type(val).__name__} ({val!r})")


# ── Parsers ──────────────────────────────────────────────────────────────────


def _parse_trigger(trigger: Any, path: str) -> TriggerContract:
    if not isinstance(trigger, dict):
        raise SpecError(f"{path}: expected a mapping, got {type(trigger).__name__}")

    _check_unknown_fields(trigger, _TRIGGER_KEYS, path)
    _check_required(trigger, ["reference_phrases", "required_concepts"], path)

    reference_phrases = _non_empty_string_tuple(
        trigger["reference_phrases"],
        f"{path}.reference_phrases",
        require_items=True,
    )

    groups_raw = trigger["required_concepts"]
    if not isinstance(groups_raw, list):
        raise SpecError(
            f"{path}.required_concepts: expected a list, "
            f"got {type(groups_raw).__name__}"
        )
    if not groups_raw:
        raise SpecError(f"{path}.required_concepts: must be a non-empty list")
    required_concepts = tuple(
        _non_empty_string_tuple(
            group,
            f"{path}.required_concepts[{index}]",
            require_items=True,
        )
        for index, group in enumerate(groups_raw)
    )

    forbidden_concepts = _non_empty_string_tuple(
        trigger.get("forbidden_concepts", []),
        f"{path}.forbidden_concepts",
        require_items=False,
    )
    wake_words = _non_empty_string_tuple(
        trigger.get("wake_words", ["jarvis", "caddie"]),
        f"{path}.wake_words",
        require_items=False,
    )
    return TriggerContract(
        reference_phrases=reference_phrases,
        required_concepts=required_concepts,
        forbidden_concepts=forbidden_concepts,
        wake_words=wake_words,
    )


def _parse_error_variant(ev: dict[str, Any], path: str) -> ErrorVariant:
    _check_unknown_fields(ev, _ERROR_VARIANT_KEYS, path)
    _check_required(ev, ["id", "field", "wrong_value", "correct_value", "description"], path)
    return ErrorVariant(
        id=str(ev["id"]),
        field=str(ev["field"]),
        wrong_value=str(ev["wrong_value"]),
        correct_value=str(ev["correct_value"]),
        description=str(ev["description"]),
    )


def _parse_study_step(step: dict[str, Any], path: str) -> StudyStep:
    _check_unknown_fields(step, _STUDY_STEP_KEYS, path)
    _check_required(step, ["id", "action", "narration", "step_type"], path)

    narration = str(step["narration"])
    if not narration.strip():
        raise SpecError(f"{path}: narration must not be empty")

    action = str(step["action"])
    try:
        _parse_action(action)
    except (ValueError, IndexError) as error:
        raise SpecError(f"{path}: {error}") from error

    step_type_str = str(step["step_type"])
    try:
        step_type = StepType(step_type_str)
    except ValueError:
        raise SpecError(f"{path}: invalid step_type '{step_type_str}', must be one of {list(StepType)}")

    # Derive consequential/commit from step_type; explicit flags must not contradict
    expected_consequential = step_type in (StepType.CONSEQUENTIAL, StepType.COMMIT)
    expected_commit = step_type == StepType.COMMIT
    explicit_consequential = step.get("consequential")
    explicit_commit = step.get("commit")
    if explicit_consequential is not None:
        exp_val = _safe_bool(explicit_consequential, expected_consequential)
        if exp_val != expected_consequential:
            raise SpecError(
                f"{path}: step_type '{step_type_str}' implies consequential={expected_consequential}, "
                f"but explicit consequential={exp_val}"
            )
        consequential = exp_val
    else:
        consequential = expected_consequential

    if explicit_commit is not None:
        exp_val = _safe_bool(explicit_commit, expected_commit)
        if exp_val != expected_commit:
            raise SpecError(
                f"{path}: step_type '{step_type_str}' implies commit={expected_commit}, "
                f"but explicit commit={exp_val}"
            )
        commit = exp_val
    else:
        commit = expected_commit

    error_variant: ErrorVariant | None = None
    if step.get("error_variant"):
        ev_raw = step["error_variant"]
        if isinstance(ev_raw, dict):
            error_variant = _parse_error_variant(ev_raw, f"{path}.error_variant")
        elif isinstance(ev_raw, str):
            error_variant = ErrorVariant(
                id=ev_raw,
                field=ev_raw,
                wrong_value=ev_raw,
                correct_value=ev_raw,
                description=ev_raw,
            )
        else:
            raise SpecError(f"{path}.error_variant: expected dict or string, got {type(ev_raw).__name__}")

    min_narration_ms = _safe_int(step.get("min_narration_ms", 800))
    if min_narration_ms < 0:
        raise SpecError(f"{path}: min_narration_ms must be >= 0")

    return StudyStep(
        id=str(step["id"]),
        action=action,
        narration=narration,
        step_type=step_type,
        consequential=consequential,
        commit=commit,
        error_variant=error_variant,
        min_narration_ms=min_narration_ms,
    )


def _parse_verification(v: dict[str, Any], path: str) -> VerificationRule:
    _check_unknown_fields(v, _VERIFICATION_KEYS, path)
    _check_required(v, ["id", "assertion", "check_type"], path)
    check_type = str(v["check_type"])
    if check_type not in VALID_CHECK_TYPES:
        raise SpecError(
            f"{path}: invalid check_type '{check_type}', "
            f"must be one of {sorted(VALID_CHECK_TYPES)}"
        )
    # Validate parameters is a mapping
    raw_params = v.get("parameters", {})
    if raw_params is None:
        raw_params = {}
    if not isinstance(raw_params, dict):
        raise SpecError(f"{path}: parameters must be a mapping, got {type(raw_params).__name__}")
    params = dict(raw_params)

    # text_present/text_absent: require 'text' in parameters (reject, no fallback)
    if check_type == "text_present" and "text" not in params:
        raise SpecError(f"{path}: 'text' parameter required for check_type 'text_present'")
    if check_type == "text_absent" and "text" not in params:
        raise SpecError(f"{path}: 'text' parameter required for check_type 'text_absent'")

    if check_type == "field_count":
        has_label = "container_label" in params or "view_id" in params
        has_expected = "expected" in params or "expected_count" in params
        if not has_label:
            raise SpecError(
                f"{path}: 'container_label' or 'view_id' parameter required for check_type 'field_count'"
            )
        if not has_expected:
            raise SpecError(
                f"{path}: 'expected' or 'expected_count' parameter required for check_type 'field_count'"
            )
    return VerificationRule(
        id=str(v["id"]),
        assertion=str(v["assertion"]),
        check_type=check_type,
        parameters=params,
        screenshot_evidence=_safe_bool(v.get("screenshot_evidence"), True),
    )


def _validate_error_steps(steps: tuple[StudyStep, ...], error_step_ids: tuple[str, ...], path: str) -> None:
    """Ensure every error_step ID exists in steps, has a complete ErrorVariant,
    and every step with error_variant is listed in error_steps."""
    step_map = {s.id: s for s in steps}
    step_ids = set(step_map.keys())

    # Check for duplicate error_step IDs
    err_counts = Counter(error_step_ids)
    dup_err = {id_val for id_val, cnt in err_counts.items() if cnt > 1}
    if dup_err:
        raise SpecError(f"{path}: duplicate error_step IDs: {sorted(dup_err)}")

    # Every error_step ID must exist in steps
    for eid in error_step_ids:
        if eid not in step_ids:
            raise SpecError(f"{path}: error_step '{eid}' not found in steps")

    # Every step with error_variant must be in error_steps
    steps_with_variant = {s.id for s in steps if s.error_variant is not None}
    for sid in steps_with_variant:
        if sid not in error_step_ids:
            raise SpecError(
                f"{path}: step '{sid}' has error_variant but is not listed in error_steps"
            )

    # Every error step must have a complete ErrorVariant (non-empty id, field, values)
    error_ids_seen: set[str] = set()
    for eid in error_step_ids:
        step = step_map[eid]
        ev = step.error_variant
        if ev is None:
            raise SpecError(f"{path}: error_step '{eid}' has no error_variant")
        if not ev.id or not ev.field:
            raise SpecError(
                f"{path}: error_step '{eid}' has incomplete error_variant "
                f"(id={ev.id!r}, field={ev.field!r})"
            )
        # Unique error variant IDs
        if ev.id in error_ids_seen:
            raise SpecError(f"{path}: duplicate error variant ID '{ev.id}'")
        error_ids_seen.add(ev.id)
        # String refs have all fields equal to the ref ID; dict refs must differ
        if ev.wrong_value == ev.correct_value and ev.id == ev.wrong_value:
            # String error reference — accept (all fields are the ref ID)
            pass
        elif ev.wrong_value == ev.correct_value:
            raise SpecError(
                f"{path}: error_step '{eid}' has wrong_value == correct_value: '{ev.wrong_value}'"
            )


def _validate_step_ids_unique(steps: tuple[StudyStep, ...], path: str) -> None:
    ids = [s.id for s in steps]
    counts = Counter(ids)
    duplicates = {id_val for id_val, cnt in counts.items() if cnt > 1}
    if duplicates:
        raise SpecError(f"{path}: duplicate step IDs: {sorted(duplicates)}")


# ── Public API ───────────────────────────────────────────────────────────────


def load_trial_spec(filepath: pathlib.Path | str) -> TrialSpec:
    """Load and validate a single trial spec YAML file.

    Parameters
    ----------
    filepath : pathlib.Path | str
        Absolute or relative path to the YAML file.

    Returns
    -------
    TrialSpec
        Validated, immutable trial specification.

    Raises
    ------
    SpecError
        If the file fails any validation check.
    """
    filepath = pathlib.Path(filepath).resolve()
    if not filepath.exists():
        raise SpecError(f"Spec file not found: {filepath}")

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f)
    except yaml.YAMLError as e:
        raise SpecError(f"YAML parse error in {filepath}: {e}")

    if not isinstance(raw, dict):
        raise SpecError(f"{filepath}: top-level must be a YAML mapping, got {type(raw).__name__}")

    _check_unknown_fields(raw, _TRIAL_SPEC_KEYS, str(filepath))
    _check_required(
        raw,
        ["version", "id", "instruction_de", "criticality", "trigger"],
        str(filepath),
    )

    trigger = _parse_trigger(raw["trigger"], f"{filepath}.trigger")

    # Criticality
    try:
        criticality = CriticalityClass(str(raw["criticality"]))
    except ValueError:
        raise SpecError(f"{filepath}: invalid criticality '{raw['criticality']}', must be 'low' or 'high'")

    # Instruction
    instruction_de = str(raw["instruction_de"])
    if not instruction_de.strip():
        raise SpecError(f"{filepath}: instruction_de must not be empty")

    # Lists
    required_packages = _str_tuple(raw.get("required_packages"), f"{filepath}.required_packages")
    seeded_artifacts = _str_tuple(raw.get("seeded_artifacts"), f"{filepath}.seeded_artifacts")
    reset_checklist = _str_tuple(raw.get("reset_checklist"), f"{filepath}.reset_checklist")
    if not reset_checklist:
        raise SpecError(f"{filepath}: 'reset_checklist' must not be empty")
    c2_summary_lines = _str_tuple(raw.get("c2_summary_lines"), f"{filepath}.c2_summary_lines")

    # Steps
    steps_raw = raw.get("steps")
    if not steps_raw or not isinstance(steps_raw, list):
        raise SpecError(f"{filepath}: 'steps' must be a non-empty list")

    try:
        steps = tuple(
            _parse_study_step(s, f"{filepath}.steps[{i}]") for i, s in enumerate(steps_raw)
        )
    except (TypeError, AttributeError) as e:
        raise SpecError(f"{filepath}.steps: non-mapping entry - {e}")
    _validate_step_ids_unique(steps, f"{filepath}.steps")

    # Error steps
    error_step_ids = _str_tuple(raw.get("error_steps"), f"{filepath}.error_steps")
    # Check for duplicate error_step IDs
    err_counts = Counter(error_step_ids)
    err_dups = {eid for eid, cnt in err_counts.items() if cnt > 1}
    if err_dups:
        raise SpecError(f"{filepath}.error_steps: duplicate error step IDs: {sorted(err_dups)}")
    _validate_error_steps(steps, error_step_ids, f"{filepath}")

    # Verification
    verification_raw = raw.get("verification")
    if verification_raw:
        if not isinstance(verification_raw, list):
            raise SpecError(f"{filepath}.verification: expected a list")
        try:
            verification = tuple(
                _parse_verification(v, f"{filepath}.verification[{i}]") for i, v in enumerate(verification_raw)
            )
        except (TypeError, AttributeError) as e:
            raise SpecError(f"{filepath}.verification: non-mapping entry - {e}")
        # Validate unique verification rule IDs
        vrule_ids = [vr.id for vr in verification]
        vrule_counts = Counter(vrule_ids)
        vrule_dups = {vid for vid, cnt in vrule_counts.items() if cnt > 1}
        if vrule_dups:
            raise SpecError(f"{filepath}.verification: duplicate rule IDs: {sorted(vrule_dups)}")
    else:
        verification = ()

    # Duration gates — strict integer validation (MAJOR: #23)
    max_duration_s = _safe_int(raw.get("max_duration_s", 300))
    if max_duration_s <= 0:
        raise SpecError(f"{filepath}: max_duration_s must be > 0")

    per_gate_timeout_s = _safe_int(raw.get("per_gate_timeout_s", 30))
    if per_gate_timeout_s <= 0:
        raise SpecError(f"{filepath}: per_gate_timeout_s must be > 0")

    return TrialSpec(
        version=str(raw["version"]),
        id=str(raw["id"]),
        instruction_de=instruction_de,
        criticality=criticality,
        trigger=trigger,
        required_packages=required_packages,
        seeded_artifacts=seeded_artifacts,
        reset_checklist=reset_checklist,
        steps=steps,
        error_steps=error_step_ids,
        c2_summary_lines=c2_summary_lines,
        verification=verification,
        max_duration_s=max_duration_s,
        per_gate_timeout_s=per_gate_timeout_s,
    )


def list_available_specs() -> list[pathlib.Path]:
    """Return sorted list of valid YAML files in the specs directory."""
    if not STUDY_SPECS_DIR.exists():
        return []
    return sorted(STUDY_SPECS_DIR.glob("*.yaml")) + sorted(STUDY_SPECS_DIR.glob("*.yml"))


def load_all_specs() -> dict[str, TrialSpec]:
    """Load every YAML in the specs directory. Returns {spec_id: TrialSpec}.

    Raises SpecError on the first validation failure encountered.
    """
    result: dict[str, TrialSpec] = {}
    for path in list_available_specs():
        spec = load_trial_spec(path)
        if spec.id in result:
            raise SpecError(f"Duplicate spec ID '{spec.id}' in {path}")
        result[spec.id] = spec
    return result
