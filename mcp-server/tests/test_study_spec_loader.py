"""Tests for caddie.study.spec_loader — YAML loading and validation."""

import tempfile
import pathlib

import pytest
import yaml

from caddie.study.spec_loader import (
    SpecError,
    load_trial_spec,
    list_available_specs,
    load_all_specs,
)


_EXPECTED_ACTIVE_TRIGGER_GROUPS = {
    "task_banking_transfer": (
        ("rechnungsdaten", "rechnung"),
        ("überweisung", "ueberweisung", "überweisungsformular", "ueberweisungsformular"),
    ),
    "task_calendar_dnd": (
        ("prüfung", "pruefung", "exam"),
        ("kalender", "termin"),
        ("nicht stören", "nicht stoeren", "dnd"),
    ),
    "task_chat_notes": (
        ("letzte nachricht", "nachricht"),
        ("projektgruppe",),
        ("checkliste", "notiz"),
    ),
    "task_email_calendar": (
        ("projektsitzung", "sitzung"),
        ("verschiebe", "verschieben", "verlege", "verlegen"),
        ("15:00", "15 uhr"),
        ("speichere", "speichern"),
    ),
    "task_gallery_notes": (
        ("whiteboard", "foto", "bild"),
        ("projektsitzung", "projekt"),
        ("notiz", "notizen"),
        ("übertrage", "uebertrage", "übertragen", "uebertragen"),
    ),
    "task_maps_messenger": (
        ("ankunftszeit", "ankunft"),
        (
            "öffentlichen mitteln",
            "oeffentlichen mitteln",
            "öffentliche verkehrsmittel",
            "oeffentliche verkehrsmittel",
            "öpnv",
            "oepnv",
        ),
        ("sende", "senden", "schicke", "schicken"),
    ),
    "task_music_playlist": (
        ("drei lieder", "3 lieder", "drei songs", "3 songs"),
        ("playlist", "wiedergabeliste"),
        ("chat", "nachricht"),
    ),
    "task_rewe_shopping": (
        ("drei produkte", "3 produkte", "produkte"),
        ("mengenangaben", "mengen", "menge"),
        ("warenkorb", "einkaufswagen"),
        ("füge", "fuege", "hinzufügen", "hinzufuegen"),
    ),
}


# ── Helper: create a minimal valid spec YAML ────────────────────────────────


def _make_yaml(data: dict) -> str:
    if "verification" in data and isinstance(data["verification"], list):
        for v in data["verification"]:
            if isinstance(v, dict) and v.get("check_type") in ("text_present", "text_absent"):
                v.setdefault("parameters", {})["text"] = "expected"
    return yaml.dump(data, default_flow_style=False)


def _temp_yaml(data: dict) -> pathlib.Path:
    # Ensure verification checks have text parameter and reset_checklist is present
    if "verification" in data and isinstance(data["verification"], list):
        for v in data["verification"]:
            if isinstance(v, dict) and v.get("check_type") in ("text_present", "text_absent"):
                v.setdefault("parameters", {})["text"] = "expected"
    if "reset_checklist" not in data:
        data["reset_checklist"] = ["App ist im Home-Screen"]
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
        "trigger": {
            "reference_phrases": ["Teste die App."],
            "required_concepts": [["testen", "test"]],
            "forbidden_concepts": ["abbrechen"],
            "wake_words": ["jarvis", "caddie"],
        },
        "reset_checklist": ["App ist im Home-Screen"],
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


def test_loaded_trigger_values_are_immutable_tuples():
    spec = load_trial_spec(_temp_yaml(_valid_minimal()))

    assert spec.trigger is not None
    assert spec.trigger.reference_phrases == ("Teste die App.",)
    assert spec.trigger.required_concepts == (("testen", "test"),)
    assert spec.trigger.forbidden_concepts == ("abbrechen",)
    assert spec.trigger.wake_words == ("jarvis", "caddie")
    assert isinstance(spec.trigger.reference_phrases, tuple)
    assert isinstance(spec.trigger.required_concepts, tuple)
    assert isinstance(spec.trigger.required_concepts[0], tuple)


@pytest.mark.parametrize(
    ("task_id", "expected_groups"),
    _EXPECTED_ACTIVE_TRIGGER_GROUPS.items(),
)
def test_active_spec_trigger_metadata_is_audited(task_id, expected_groups):
    base = pathlib.Path(__file__).resolve().parent.parent / "study" / "specs"
    spec = load_trial_spec(base / f"{task_id}.yaml")

    assert spec.trigger is not None
    assert spec.instruction_de in spec.trigger.reference_phrases
    assert set(expected_groups) <= set(spec.trigger.required_concepts)


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


def test_error_missing_trigger():
    data = _valid_minimal()
    del data["trigger"]

    with pytest.raises(SpecError, match="trigger"):
        load_trial_spec(_temp_yaml(data))


@pytest.mark.parametrize("reference_phrases", [[], [""], ["   "], [7]])
def test_error_invalid_trigger_reference_phrases(reference_phrases):
    data = _valid_minimal()
    data["trigger"]["reference_phrases"] = reference_phrases

    with pytest.raises(SpecError, match=r"trigger\.reference_phrases"):
        load_trial_spec(_temp_yaml(data))


@pytest.mark.parametrize(
    "required_concepts",
    [[], ["test"], [[]], [[""]], [["   "]], [[7]]],
)
def test_error_invalid_trigger_required_concepts(required_concepts):
    data = _valid_minimal()
    data["trigger"]["required_concepts"] = required_concepts

    with pytest.raises(SpecError, match=r"trigger\.required_concepts"):
        load_trial_spec(_temp_yaml(data))


@pytest.mark.parametrize("field", ["forbidden_concepts", "wake_words"])
@pytest.mark.parametrize("value", [[""], ["   "], [7]])
def test_error_invalid_optional_trigger_string_lists(field, value):
    data = _valid_minimal()
    data["trigger"][field] = value

    with pytest.raises(SpecError, match=rf"trigger\.{field}"):
        load_trial_spec(_temp_yaml(data))


def test_error_unknown_trigger_field():
    data = _valid_minimal()
    data["trigger"]["match_everything"] = True

    with pytest.raises(SpecError, match="match_everything"):
        load_trial_spec(_temp_yaml(data))


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
