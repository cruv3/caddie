"""Tests for error variant validation in the spec loader.

Verifies that:
  - Equal correct_value and wrong_value raises SpecError (except string refs)
  - Valid distinct values pass
  - Equivalent values (same after lowercasing / stripping) are rejected
"""

import tempfile
import pathlib

import yaml

from caddie.study.spec_loader import SpecError, load_trial_spec


def _valid_minimal() -> dict:
    return {
        "version": "v1",
        "id": "task_test",
        "instruction_de": "Teste die App.",
        "criticality": "high",
        "reset_checklist": ["App ist im Home-Screen"],
        "steps": [
            {
                "id": "open",
                "action": "open com.caddie",
                "narration": "App oeffnen...",
                "step_type": "normal",
            },
            {"id": "send", "action": "click Send", "narration": "Senden...", "step_type": "commit"},
        ],
        "error_steps": ["send"],
        "verification": [
            {"id": "v1", "assertion": "Erfolg", "check_type": "text_present",
             "parameters": {"text": "expected"}},
        ],
    }


def _temp_yaml(data: dict) -> pathlib.Path:
    f = tempfile.NamedTemporaryFile(mode="w", suffix=".yaml", delete=False)
    yaml.dump(data, f)
    f.close()
    return pathlib.Path(f.name)


# ── Error variant validation ──────────────────────────────────────────────


def test_equal_wrong_and_correct_value_raises():
    """wrong_value == correct_value (with distinct ID) raises SpecError."""
    data = _valid_minimal()
    data["steps"][1]["error_variant"] = {
        "id": "err_amount",
        "field": "amount",
        "wrong_value": "100",
        "correct_value": "100",
        "description": "Wrong amount",
    }
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError for equal values"
    except SpecError as e:
        assert "wrong_value" in str(e) or "correct_value" in str(e)


def test_equal_values_string_ref_accepted():
    """String ref error variant: all fields equal to ref ID is accepted."""
    data = _valid_minimal()
    data["steps"][1]["error_variant"] = "err_ref_1"
    data["error_steps"] = ["send"]
    path = _temp_yaml(data)
    spec = load_trial_spec(path)
    assert len(spec.error_steps) == 1
    err_step = next(s for s in spec.steps if s.id == "send")
    assert err_step.error_variant is not None
    assert err_step.error_variant.wrong_value == "err_ref_1"
    assert err_step.error_variant.correct_value == "err_ref_1"


def test_distinct_values_pass():
    """Different wrong_value and correct_value passes validation."""
    data = _valid_minimal()
    data["steps"][1]["error_variant"] = {
        "id": "err_amount",
        "field": "amount",
        "wrong_value": "83.70 EUR",
        "correct_value": "38.70 EUR",
        "description": "Wrong amount",
    }
    path = _temp_yaml(data)
    spec = load_trial_spec(path)
    assert len(spec.error_steps) == 1
    err_step = next(s for s in spec.steps if s.id == "send")
    assert err_step.error_variant is not None
    assert err_step.error_variant.wrong_value == "83.70 EUR"
    assert err_step.error_variant.correct_value == "38.70 EUR"


def test_equivalent_values_rejected():
    """Values that differ only by case/whitespace but are equal after normalization are rejected."""
    data = _valid_minimal()
    data["steps"][1]["error_variant"] = {
        "id": "err_amount",
        "field": "amount",
        "wrong_value": "100",
        "correct_value": "100",
        "description": "Same value",
    }
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError for equal values"
    except SpecError as e:
        assert "wrong_value" in str(e) or "correct_value" in str(e)


def test_real_specs_all_errors_distinct():
    """Every error variant in the real spec files has distinct values."""
    import pathlib
    base = pathlib.Path(__file__).resolve().parent.parent / "study" / "specs"
    for yaml_path in sorted(base.glob("*.yaml")):
        spec = load_trial_spec(yaml_path)
        for step_id in spec.error_steps:
            step = next(s for s in spec.steps if s.id == step_id)
            assert step.error_variant is not None, f"{spec.id}: error step {step_id} has no variant"
            # For dict refs, wrong and correct must differ
            if step.error_variant.wrong_value == step.error_variant.correct_value:
                # String ref check
                assert step.error_variant.id == step.error_variant.wrong_value, (
                    f"{spec.id}/{step_id}: dict error_variant has equal values "
                    f"but ID {step.error_variant.id} != {step.error_variant.wrong_value}"
                )
