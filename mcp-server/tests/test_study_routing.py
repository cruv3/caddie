from dataclasses import replace

import pytest

from caddie.study.model import TriggerContract
from caddie.study.routing import (
    RouteDecision,
    StudyTaskRouter,
    match_task,
    normalize_text,
)
from caddie.study.spec_loader import load_all_specs


TASK_UTTERANCES = {
    "task_banking_transfer": (
        "Bitte trage die Rechnungsdaten in das Ueberweisungsformular ein.",
        "Trage die Rechnungsdaten in das Ueberweisungsformular ein, nicht in den Kalender.",
    ),
    "task_calendar_dnd": (
        "Finde die Pruefung morgen im Kalender und schalte Nicht stoeren fuer diese Zeit ein.",
        "Finde die Pruefung im Kalender und aktiviere Nicht stoeren, nicht die Projektsitzung.",
    ),
    "task_chat_notes": (
        "Uebernimm die letzte Nachricht aus der Projektgruppe als Notiz.",
        "Uebernimm die letzte Nachricht aus der Projektgruppe als Checkliste, ohne Fotos.",
    ),
    "task_email_calendar": (
        "Verlege die heutige Sitzung auf 15 Uhr und speichere sie.",
        "Verschiebe die heutige Projektsitzung auf 15 Uhr und speichere, aber aktiviere kein DND.",
    ),
    "task_gallery_messenger": (
        "Schicke drei Bilder mit einem Gruss an die Projektgruppe.",
        "Sende drei Fotos mit einer Begruessung an die Projektgruppe, nicht in eine Playlist.",
    ),
    "task_maps_messenger": (
        "Sende die Ankunft mit dem OePNV.",
        "Sende die Ankunftszeit mit oeffentlichen Mitteln, aber keine Fotos.",
    ),
    "task_music_playlist": (
        "Schreibe drei Songs als Nachricht in die Wiedergabeliste.",
        "Schreibe drei Lieder als Chat-Nachricht in die Playlist, nicht in den Warenkorb.",
    ),
    "task_rewe_shopping": (
        "Fuege drei Produkte mit Menge in den Einkaufswagen.",
        "Fuege drei Produkte mit Mengenangaben in den Warenkorb, nicht in den Kalender.",
    ),
}


def test_match_task_normalizes_case_punctuation_whitespace_and_wake_word():
    trigger = TriggerContract(
        reference_phrases=("Prüfung im Kalender",),
        required_concepts=(("prüfung",), ("kalender",)),
        wake_words=("jarvis",),
    )

    result = match_task("  JARVIS, finde die PRUEFUNG---im   KALENDER! ", trigger)

    assert result.matched is True
    assert result.normalized_input == "finde die pruefung im kalender"
    assert result.matched_concepts == ("prüfung", "kalender")
    assert result.reason == "matched"


def test_normalize_text_treats_all_punctuation_as_token_separators():
    assert normalize_text("Nicht_stören/bitte!") == "nicht stoeren bitte"


def test_normalize_text_removes_the_most_specific_matching_wake_prefix():
    assert (
        normalize_text("Jarvis Caddie, öffne den Kalender", ("jarvis", "jarvis caddie"))
        == "oeffne den kalender"
    )


@pytest.mark.parametrize(
    ("source", "expected"),
    [
        ("Überweisung", "ueberweisung"),
        ("ueberweisung", "ueberweisung"),
        ("FÖRDERUNG", "foerderung"),
        ("foerderung", "foerderung"),
        ("füge", "fuege"),
        ("fuege", "fuege"),
        ("Gruß", "gruss"),
        ("gruss", "gruss"),
    ],
)
def test_normalize_text_makes_german_umlaut_spellings_equivalent(source, expected):
    assert normalize_text(source) == expected


def test_match_task_matches_multiword_and_hyphenated_concepts_at_boundaries():
    trigger = TriggerContract(
        reference_phrases=("Nicht stören",),
        required_concepts=(("nicht stören",), ("projekt gruppe", "projektgruppe")),
    )

    result = match_task("Nicht-stoeren fuer die Projektgruppe", trigger)

    assert result.matched is True
    assert result.matched_concepts == ("nicht stören", "projektgruppe")


def test_match_task_requires_a_match_from_every_concept_group_and_accepts_synonyms():
    trigger = TriggerContract(
        reference_phrases=("Referenz",),
        required_concepts=(("rechnung", "rechnungsdaten"), ("überweisung", "transfer")),
    )

    missing = match_task("Rechnungsdaten pruefen", trigger)
    matched = match_task("Rechnungsdaten fuer den Transfer", trigger)

    assert missing.matched is False
    assert missing.missing_concepts == (("überweisung", "transfer"),)
    assert missing.reason == "missing_required_concepts"
    assert matched.matched is True
    assert matched.matched_concepts == ("rechnungsdaten", "transfer")


def test_match_task_forbidden_concepts_win_over_required_matches():
    trigger = TriggerContract(
        reference_phrases=("Referenz",),
        required_concepts=(("rechnung",), ("überweisung",)),
        forbidden_concepts=("kalender",),
    )

    result = match_task("Rechnung fuer die Ueberweisung im Kalender", trigger)

    assert result.matched is False
    assert result.forbidden_matches == ("kalender",)
    assert result.reason == "forbidden_concept"


def test_match_task_does_not_match_concepts_inside_unrelated_words():
    trigger = TriggerContract(
        reference_phrases=("Referenz",),
        required_concepts=(("kalender",),),
        forbidden_concepts=("prüfung",),
    )

    result = match_task("Der kalendern pruefungstermin ist sichtbar", trigger)

    assert result.matched is False
    assert result.matched_concepts == ()
    assert result.forbidden_matches == ()
    assert result.missing_concepts == (("kalender",),)


@pytest.mark.parametrize("value", ["", "   ", None, 42, object()])
def test_match_task_fails_closed_with_complete_audit_data_for_invalid_input(value):
    trigger = TriggerContract(
        reference_phrases=("Referenz",),
        required_concepts=(("kalender",), ("prüfung", "exam")),
    )

    result = match_task(value, trigger)

    assert result.matched is False
    assert result.normalized_input == ""
    assert result.missing_concepts == trigger.required_concepts
    assert result.reason == "invalid_input"


def test_all_active_specs_match_their_exact_instruction_with_or_without_wake_word():
    router = StudyTaskRouter()
    specs = load_all_specs()

    assert len(specs) == 8
    for spec in specs.values():
        for utterance in (
            spec.instruction_de,
            f"Jarvis, {spec.instruction_de}",
            f"Caddie, {spec.instruction_de}",
        ):
            result = router.route(utterance, spec)
            assert result.decision is RouteDecision.CLAIMED, spec.id
            assert result.spec is spec
            assert result.match is not None and result.match.matched is True


def test_every_other_active_instruction_retries_for_each_armed_spec():
    router = StudyTaskRouter()
    specs = load_all_specs()

    for armed_id, armed_spec in specs.items():
        for other_id, other_spec in specs.items():
            if other_id == armed_id:
                continue
            result = router.route(other_spec.instruction_de, armed_spec)
            assert result.decision is RouteDecision.RETRY, f"{armed_id} accepted {other_id}"
            assert result.match is not None


def test_every_active_spec_accepts_its_task_specific_natural_paraphrase():
    router = StudyTaskRouter()
    specs = load_all_specs()

    assert set(TASK_UTTERANCES) == set(specs)
    for task_id, (natural_paraphrase, _) in TASK_UTTERANCES.items():
        result = router.route(natural_paraphrase, specs[task_id])
        assert result.decision is RouteDecision.CLAIMED, task_id


def test_every_active_spec_rejects_its_task_specific_forbidden_utterance():
    router = StudyTaskRouter()
    specs = load_all_specs()

    for task_id, (_, forbidden_utterance) in TASK_UTTERANCES.items():
        result = router.route(forbidden_utterance, specs[task_id])
        assert result.decision is RouteDecision.RETRY, task_id
        assert result.match is not None
        assert result.match.reason == "forbidden_concept"


def test_calendar_dnd_natural_ascii_paraphrase_matches():
    spec = load_all_specs()["task_calendar_dnd"]

    result = StudyTaskRouter().route(
        "Jarvis, finde die pruefung morgen im kalender und schalte nicht stoeren fuer diese zeit ein.",
        spec,
    )

    assert result.decision is RouteDecision.CLAIMED


def test_router_considers_only_the_supplied_armed_spec():
    specs = load_all_specs()

    result = StudyTaskRouter().route(
        specs["task_banking_transfer"].instruction_de,
        specs["task_calendar_dnd"],
    )

    assert result.decision is RouteDecision.RETRY
    assert result.spec is None
    assert result.match is not None
    assert result.match.reason == "missing_required_concepts"


def test_router_decisions_are_exact_and_repeated_results_are_deterministic():
    spec = load_all_specs()["task_calendar_dnd"]
    router = StudyTaskRouter()

    pass_through = router.route("anything", None)
    missing_trigger = router.route("anything", replace(spec, trigger=None))
    retries = [router.route("unrelated request", spec) for _ in range(2)]
    claims = [router.route(spec.instruction_de, spec) for _ in range(2)]

    assert pass_through.decision is RouteDecision.PASS_THROUGH
    assert pass_through.match is None and pass_through.spec is None
    assert missing_trigger.decision is RouteDecision.RETRY
    assert missing_trigger.spec is None
    assert missing_trigger.match is not None
    assert missing_trigger.match.reason == "missing_trigger_contract"
    assert missing_trigger.match.normalized_input == "anything"
    assert retries[0] == retries[1]
    assert retries[0].decision is RouteDecision.RETRY
    assert claims[0] == claims[1]
    assert claims[0].decision is RouteDecision.CLAIMED
    assert claims[0].spec is spec


@pytest.mark.parametrize("separator", ["-", "\u00ad", "\u200b"])
def test_router_rejects_forbidden_concepts_hidden_by_intra_word_separators(separator):
    spec = load_all_specs()["task_calendar_dnd"]
    utterance = (
        f"Finde die Pruefung im Kalender und aktiviere Nicht stoeren; "
        f"starte keine Ueber{separator}weisung."
    )

    result = StudyTaskRouter().route(utterance, spec)

    assert result.decision is RouteDecision.RETRY
    assert result.match is not None
    assert result.match.reason == "forbidden_concept"
    assert result.match.forbidden_matches == ("überweisung",)


def test_joined_forbidden_detection_keeps_an_ordinary_allowed_calendar_request_claimed():
    spec = load_all_specs()["task_calendar_dnd"]

    result = StudyTaskRouter().route(
        "Finde die Pruefung im Kalender und aktiviere Nicht stoeren fuer diese Zeit.",
        spec,
    )

    assert result.decision is RouteDecision.CLAIMED


def test_forbidden_matches_are_deduplicated_by_normalized_equivalence():
    trigger = TriggerContract(
        reference_phrases=("Referenz",),
        required_concepts=(("kalender",),),
        forbidden_concepts=("überweisung", "ueberweisung"),
    )

    result = match_task("Kalender und Ueberweisung", trigger)

    assert result.forbidden_matches == ("überweisung",)
