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
            {"id": "open", "action": "open com.caddie", "narration": "App oeffnen...", "step_type": "normal"},
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
    """Load a real spec file from study/specs."""
    base = pathlib.Path(__file__).resolve().parent.parent / "study" / "specs"
    # Load any available spec
    yaml_files = sorted(base.glob("*.yaml"))
    if yaml_files:
        spec = load_trial_spec(yaml_files[0])
        assert spec.id is not None
        assert len(spec.steps) >= 1


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


def test_error_unexecutable_action_reports_step_path():
    data = _valid_minimal()
    data["steps"][0]["action"] = "verify 'Inbox loaded'"
    path = _temp_yaml(data)

    try:
        load_trial_spec(path)
        assert False, "Should raise SpecError"
    except SpecError as e:
        message = str(e)
        assert "steps[0]" in message
        assert "Unrecognised action format" in message


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
    data["steps"].append({"id": "open", "action": "press HOME", "narration": "dup", "step_type": "normal"})
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
    # At least 6 spec files should exist
    assert len(specs) >= 6


def test_load_all_specs_returns_dict():
    specs = load_all_specs()
    # Verify at least one known task is present
    assert len(specs) >= 6
    ids = {s.id for s in specs.values()}
    assert "task_maps_messenger" in ids


def test_banking_spec_hides_keyboard_before_tapping_review_button():
    spec_path = pathlib.Path(__file__).parents[1] / "study" / "specs" / "task_banking_payment.yaml"
    spec = load_trial_spec(spec_path)
    actions = [step.action for step in spec.steps]

    purpose_index = actions.index("input text 'Rechnung INV-2026-001'")
    assert actions[purpose_index + 1:purpose_index + 3] == [
        "press BACK",
        "click 'com.caddie.studybank:id/btn_send'",
    ]


def test_study_mail_module_is_registered():
    settings = (pathlib.Path(__file__).parents[2] / "settings.gradle.kts").read_text(encoding="utf-8")
    assert 'include(":mcp-server:study-mail")' in settings


def test_gallery_and_notes_modules_are_registered():
    settings = (pathlib.Path(__file__).parents[2] / "settings.gradle.kts").read_text(encoding="utf-8")
    assert 'include(":mcp-server:study-gallery")' in settings
    assert 'include(":mcp-server:study-notes")' in settings


def test_gallery_notes_spec_uses_private_data_free_study_apps():
    spec_path = pathlib.Path(__file__).parents[1] / "study" / "specs" / "task_gallery_notes.yaml"
    spec = load_trial_spec(spec_path)
    actions = [step.action for step in spec.steps]

    assert spec.required_packages == (
        "com.caddie.studygallery",
        "com.caddie.studynotes",
    )
    assert "com.google.android.apps.photos" not in spec.required_packages
    assert "com.google.android.keep" not in spec.required_packages
    assert actions == [
        "open com.caddie.studygallery",
        "click 'com.caddie.studygallery:id/whiteboard_photo'",
        "open com.caddie.studynotes",
        "click 'com.caddie.studynotes:id/create_note'",
        "click 'com.caddie.studynotes:id/note_text'",
        "input text 'Projekt: Bericht Dienstag abgeben; Entwurf Donnerstag pruefen'",
        "press BACK",
        "press BACK",
    ]
    assert spec.verification[0].parameters["text"] == "Projekt: Bericht Dienstag abgeben"


def test_email_tasks_use_local_study_mail_selectors():
    specs_dir = pathlib.Path(__file__).parents[1] / "study" / "specs"
    expected_targets = {
        "task_email_calendar.yaml": "mail_meeting_change",
        "task_banking_payment.yaml": "mail_invoice",
    }

    for filename, target in expected_targets.items():
        spec = load_trial_spec(specs_dir / filename)
        actions = [step.action for step in spec.steps]
        assert "com.caddie.studymail" in spec.required_packages
        assert "com.google.android.gm" not in spec.required_packages
        assert "open com.caddie.studymail" in actions
        assert f"click 'com.caddie.studymail:id/{target}'" in actions


def test_calendar_spec_replays_qwen_discovered_time_picker_flow():
    spec_path = pathlib.Path(__file__).parents[1] / "study" / "specs" / "task_email_calendar.yaml"
    spec = load_trial_spec(spec_path)
    actions = [step.action for step in spec.steps]

    event_index = actions.index("click 'Projektsitzung'")
    assert actions[event_index - 1] == "click 'Zu heute springen'"
    assert not any("23 Juli 2026" in action for action in actions)
    assert actions[-4:] == [
        "click 'Beginnt um: 14:00'",
        "click '15 Stunden'",
        "click 'OK'",
        "click 'Speichern'",
    ]
    assert spec.verification[0].parameters["text"] == "15:00–16:00"


def test_maps_spec_uses_fake_telegram_and_dynamic_arrival():
    spec_path = pathlib.Path(__file__).parents[1] / "study" / "specs" / "task_maps_messenger.yaml"
    spec = load_trial_spec(spec_path)
    actions = [step.action for step in spec.steps]

    assert "com.caddie.studytelegram" in spec.required_packages
    assert "com.google.android.apps.messaging" not in spec.required_packages
    assert any(action.startswith("open_url https://www.google.com/maps/dir/") for action in actions)
    assert "capture transit arrival as 'arrival_time'" in actions
    assert "open com.caddie.studytelegram" in actions
    assert "input text 'Ankunft gegen {arrival_time}'" in actions
    assert not any("18:00" in action for action in actions)

    input_step = next(step for step in spec.steps if step.id == "input_arrival")
    assert input_step.error_variant is not None
    assert input_step.error_variant.correct_value == "{arrival_time}"
    assert input_step.error_variant.wrong_value == "{arrival_time_minus_10}"


def test_chat_spotify_spec_uses_fake_telegram_seed():
    spec_path = pathlib.Path(__file__).parents[1] / "study" / "specs" / "task_chat_spotify.yaml"
    spec = load_trial_spec(spec_path)
    actions = [step.action for step in spec.steps]

    assert "com.caddie.studytelegram" in spec.required_packages
    assert "com.google.android.apps.messaging" not in spec.required_packages
    assert "open com.caddie.studytelegram" in actions
    assert "click 'Lena'" in actions
    assert "click 'Anna'" not in actions
    assert "Caddie Study Telegram Lena Song Recommendation" in spec.seeded_artifacts
    assert any("reset Study Telegram" in item for item in spec.reset_checklist)
