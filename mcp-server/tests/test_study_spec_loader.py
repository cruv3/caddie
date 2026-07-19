"""Tests for caddie.study.spec_loader — YAML loading and validation."""

import tempfile
import pathlib

import yaml

from caddie.study.spec_loader import (
    SpecError,
    load_trial_spec,
    list_available_specs,
    load_all_specs,
)


# ── Helper: create a minimal valid spec YAML ────────────────────────────────


def _make_yaml(data: dict) -> str:
    return yaml.dump(data, default_flow_style=False)


def _temp_yaml(data: dict) -> pathlib.Path:
    f = tempfile.NamedTemporaryFile(mode="w", suffix=".yaml", delete=False)
    yaml.dump(data, f)
    f.close()
    return pathlib.Path(f.name)


def _valid_minimal() -> dict:
    return {
        "version": "v1",
        "id": "task_test",
        "instruction_de": "Teste die App.",
        "criticality": "high",
        "steps": [
            {"id": "open", "action": "open", "narration": "App oeffnen...", "step_type": "normal"},
            {"id": "send", "action": "click Send", "narration": "Senden...", "step_type": "consequential", "consequential": True},
        ],
        "error_steps": [],
        "verification": [
            {"id": "v1", "assertion": "Erfolg", "check_type": "text_present"},
        ],
    }


# ── Successful loads ────────────────────────────────────────────────────────


def test_load_valid_spec():
    data = _valid_minimal()
    path = _temp_yaml(data)
    spec = load_trial_spec(path)
    assert spec.id == "task_test"
    assert spec.version == "v1"
    assert len(spec.steps) == 2
    assert spec.steps[0].step_type.value == "normal"
    assert spec.steps[1].consequential is True


def test_load_spec_with_error_variant_in_step():
    data = _valid_minimal()
    data["steps"].append({
        "id": "err_step",
        "action": "input text 'Wrong'",
        "narration": "Falscher Wert...",
        "step_type": "consequential",
        "error_variant": {"id": "ev1", "field": "amount", "wrong_value": "10", "correct_value": "20", "description": "Falsch"},
    })
    data["error_steps"] = ["err_step"]
    path = _temp_yaml(data)
    spec = load_trial_spec(path)
    assert len(spec.error_steps) == 1
    assert spec.error_steps[0] == "err_step"
    err_step = next(s for s in spec.steps if s.id == "err_step")
    assert err_step.error_variant is not None
    assert err_step.error_variant.wrong_value == "10"


def test_load_spec_string_error_variant_ref():
    data = _valid_minimal()
    data["steps"][1]["error_variant"] = "err_ref_1"
    data["error_steps"] = ["send"]  # error variant on 'send' requires listing in error_steps
    path = _temp_yaml(data)
    spec = load_trial_spec(path)
    assert spec.steps[1].error_variant is not None
    assert spec.steps[1].error_variant.id == "err_ref_1"
    assert spec.steps[1].error_variant.field == "err_ref_1"
    assert spec.steps[1].error_variant.wrong_value == "err_ref_1"
    assert spec.steps[1].error_variant.correct_value == "err_ref_1"


def test_load_spec_from_known_path():
    """Load the real task_music_playlist.yaml from study/specs."""
    base = pathlib.Path(__file__).resolve().parent.parent / "study" / "specs"
    music = base / "task_music_playlist.yaml"
    if music.exists():
        spec = load_trial_spec(music)
        assert spec.id == "task_music_playlist"
        assert len(spec.steps) == 6


# ── Validation errors ──────────────────────────────────────────────────────


def test_error_missing_version():
    data = _valid_minimal()
    del data["version"]
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "version" in str(e)


def test_error_missing_id():
    data = _valid_minimal()
    del data["id"]
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "id" in str(e)


def test_error_missing_instruction_de():
    data = _valid_minimal()
    del data["instruction_de"]
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "instruction_de" in str(e)


def test_error_missing_criticality():
    data = _valid_minimal()
    del data["criticality"]
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "criticality" in str(e)


def test_error_invalid_criticality():
    data = _valid_minimal()
    data["criticality"] = "ultra"
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "criticality" in str(e)


def test_error_empty_narration():
    data = _valid_minimal()
    data["steps"][0]["narration"] = ""
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "narration" in str(e)


def test_error_unknown_field():
    data = _valid_minimal()
    data["unknown_field"] = "oops"
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "unknown_field" in str(e)


def test_error_unknown_step_field():
    data = _valid_minimal()
    data["steps"][0]["weird_key"] = "value"
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "weird_key" in str(e)


def test_error_invalid_step_type():
    data = _valid_minimal()
    data["steps"][0]["step_type"] = "funky"
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "step_type" in str(e)


def test_error_empty_steps_list():
    data = _valid_minimal()
    data["steps"] = []
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "steps" in str(e)


def test_error_duplicate_step_ids():
    data = _valid_minimal()
    data["steps"].append({"id": "open", "action": "x", "narration": "dup", "step_type": "normal"})
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "duplicate" in str(e)


def test_error_error_step_not_in_steps():
    data = _valid_minimal()
    data["error_steps"] = ["nonexistent_step"]
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "nonexistent_step" in str(e)


def test_error_max_duration_zero():
    data = _valid_minimal()
    data["max_duration_s"] = 0
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "max_duration_s" in str(e)


def test_error_per_gate_timeout_negative():
    data = _valid_minimal()
    data["per_gate_timeout_s"] = -5
    path = _temp_yaml(data)
    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        assert "per_gate_timeout_s" in str(e)


def test_error_file_not_found():
    try:
        load_trial_spec("/nonexistent/path.yaml")
        assert False, "Should raise SpecError"
    except SpecError:
        pass  # Expected


# ── list_available_specs / load_all_specs ───────────────────────────────────


def test_list_available_specs_returns_files():
    specs = list_available_specs()
    # At least task_music_playlist.yaml should exist
    ids = {s.stem for s in specs}
    assert "task_music_playlist" in ids


def test_load_all_specs_returns_dict():
    specs = load_all_specs()
    assert "task_music_playlist" in specs
    assert specs["task_music_playlist"].id == "task_music_playlist"
