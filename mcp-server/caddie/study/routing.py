"""Pure deterministic matching for an armed study task."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
import re
import unicodedata

from caddie.study.model import TrialSpec, TriggerContract


class RouteDecision(StrEnum):
    PASS_THROUGH = "pass_through"
    RETRY = "retry"
    CLAIMED = "claimed"


@dataclass(frozen=True, slots=True)
class MatchResult:
    matched: bool
    normalized_input: str
    matched_concepts: tuple[str, ...]
    missing_concepts: tuple[tuple[str, ...], ...]
    forbidden_matches: tuple[str, ...]
    reason: str


@dataclass(frozen=True, slots=True)
class RouteResult:
    decision: RouteDecision
    match: MatchResult | None
    spec: TrialSpec | None


def normalize_text(text: object, wake_words: tuple[str, ...] = ()) -> str:
    """Return canonical, token-oriented German text.

    German umlauts and their ASCII spellings intentionally share the ASCII
    expansion so that the trigger metadata may use either representation.
    """
    if not isinstance(text, str):
        return ""
    normalized = unicodedata.normalize("NFKC", text).casefold()
    normalized = (
        normalized.replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
    )
    normalized = re.sub(r"[\W_]+", " ", normalized, flags=re.UNICODE)
    normalized = " ".join(normalized.split())
    for wake in sorted(
        (normalize_text(wake_word) for wake_word in wake_words),
        key=len,
        reverse=True,
    ):
        if wake and (normalized == wake or normalized.startswith(f"{wake} ")):
            return normalized[len(wake) :].strip()
    return normalized


def _joined_intra_word_text(text: object) -> str:
    """Canonicalize text while removing punctuation/format characters.

    This deliberately preserves real whitespace boundaries.  It is only used
    for forbidden concepts, so a hidden in-word separator cannot evade a
    safety exclusion without broadening ordinary required-concept matching.
    """
    if not isinstance(text, str):
        return ""
    canonical = unicodedata.normalize("NFKC", text).casefold()
    canonical = (
        canonical.replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
    )
    characters: list[str] = []
    for character in canonical:
        if character.isspace():
            characters.append(" ")
        elif unicodedata.category(character).startswith("P") or unicodedata.category(
            character
        ) == "Cf":
            continue
        else:
            characters.append(character)
    return " ".join("".join(characters).split())


def _contains_phrase(text: str, phrase: str) -> bool:
    return bool(phrase) and f" {phrase} " in f" {text} "


def match_task(text: object, trigger: TriggerContract) -> MatchResult:
    """Match text against one trigger contract without side effects."""
    if not isinstance(text, str):
        return MatchResult(
            False, "", (), trigger.required_concepts, (), "invalid_input"
        )

    normalized_input = normalize_text(text, trigger.wake_words)
    if not normalized_input:
        return MatchResult(
            False, "", (), trigger.required_concepts, (), "invalid_input"
        )

    matched_concepts: list[str] = []
    missing_concepts: list[tuple[str, ...]] = []
    for group in trigger.required_concepts:
        representative = next(
            (
                concept
                for concept in group
                if _contains_phrase(normalized_input, normalize_text(concept))
            ),
            None,
        )
        if representative is None:
            missing_concepts.append(group)
        else:
            matched_concepts.append(representative)

    joined_input = _joined_intra_word_text(text)
    forbidden_matches: list[str] = []
    seen_forbidden: set[str] = set()
    for concept in trigger.forbidden_concepts:
        normalized_concept = normalize_text(concept)
        joined_concept = _joined_intra_word_text(concept)
        if (
            _contains_phrase(normalized_input, normalized_concept)
            or _contains_phrase(joined_input, joined_concept)
        ) and normalized_concept not in seen_forbidden:
            forbidden_matches.append(concept)
            seen_forbidden.add(normalized_concept)
    if forbidden_matches:
        reason = "forbidden_concept"
    elif missing_concepts:
        reason = "missing_required_concepts"
    else:
        reason = "matched"
    return MatchResult(
        matched=reason == "matched",
        normalized_input=normalized_input,
        matched_concepts=tuple(matched_concepts),
        missing_concepts=tuple(missing_concepts),
        forbidden_matches=tuple(forbidden_matches),
        reason=reason,
    )


class StudyTaskRouter:
    """Route text only to the supplied, currently armed task."""

    def route(self, text: object, armed_spec: TrialSpec | None) -> RouteResult:
        if armed_spec is None:
            return RouteResult(RouteDecision.PASS_THROUGH, None, None)
        if armed_spec.trigger is None:
            return RouteResult(
                RouteDecision.RETRY,
                MatchResult(
                    False,
                    normalize_text(text),
                    (),
                    (),
                    (),
                    "missing_trigger_contract",
                ),
                None,
            )
        match = match_task(text, armed_spec.trigger)
        if not match.matched:
            return RouteResult(RouteDecision.RETRY, match, None)
        return RouteResult(RouteDecision.CLAIMED, match, armed_spec)
