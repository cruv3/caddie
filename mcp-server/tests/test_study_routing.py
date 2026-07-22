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
def test_match_task_fails_closed_for_empty_or_non_string_input(value):
    trigger = TriggerContract(
        reference_phrases=("Referenz",), required_concepts=(("kalender",),)
    )

    result = match_task(value, trigger)

    assert result.matched is False
    assert result.normalized_input == ""
    assert result.reason in {"empty_input", "invalid_input"}


def test_all_active_specs_match_their_exact_instruction_with_or_without_wake_word():
    router = StudyTaskRouter()
    specs = load_all_specs()

    assert len(specs) == 8
    for spec in specs.values():
        for utterance in (spec.instruction_de, f"Jarvis, {spec.instruction_de}"):
            result = router.route(utterance, spec)
            assert result.decision is RouteDecision.CLAIMED, spec.id
            assert result.spec is spec
            assert result.match is not None and result.match.matched is True


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
    assert missing_trigger.match is None and missing_trigger.spec is None
    assert retries[0] == retries[1]
    assert retries[0].decision is RouteDecision.RETRY
    assert claims[0] == claims[1]
    assert claims[0].decision is RouteDecision.CLAIMED
    assert claims[0].spec is spec
