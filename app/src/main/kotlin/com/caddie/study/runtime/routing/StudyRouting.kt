package com.caddie.study.runtime.routing

import com.caddie.study.runtime.model.RuntimeStudyCondition
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import java.text.Normalizer

/**
 * Deterministic utterance routing for an armed study task, ported from
 * `caddie.study.routing` (Python).
 *
 * All matching is pure: no side effects, no I/O. [StudyTaskRouter] routes
 * one utterance against one armed [TrialSpec]'s [TriggerContract].
 */
object StudyRouting {

    /** Decision returned by [StudyTaskRouter.route]. */
    enum class RouteDecision(val wireValue: String) {
        PASS_THROUGH("pass_through"),
        RETRY("retry"),
        CLAIMED("claimed");

        companion object {
            fun fromWire(v: String): RouteDecision =
                entries.first { it.wireValue == v }
        }
    }

    /** Raw match outcome against a [TriggerContract]. */
    data class MatchResult(
        val matched: Boolean,
        val normalizedInput: String,
        val matchedConcepts: List<String>,
        val missingConcepts: List<List<String>>,
        val forbiddenMatches: List<String>,
        val reason: String,
    )

    /** Final routing result: decision + optional match + optional spec. */
    data class RouteResult(
        val decision: RouteDecision,
        val match: MatchResult?,
        val spec: TrialSpec?,
    )

    /**
     * Canonical, token-oriented German text. Umlauts and their ASCII
     * spellings intentionally share the ASCII expansion so trigger metadata
     * may use either representation. Wake words are stripped from the front.
     */
    fun normalizeText(text: String?, wakeWords: List<String> = emptyList()): String {
        if (text.isNullOrEmpty()) return ""
        var normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        normalized = normalized
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        // collapse any run of non-word chars (incl. underscore) to a single space
        normalized = NON_WORD.replace(normalized, " ")
        normalized = normalized.trim().replace(MULTISPACE, " ")
        // strip wake word prefix (longest-first)
        val sortedWakes = wakeWords
            .map { normalizeText(it) }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
        for (wake in sortedWakes) {
            if (normalized == wake || normalized.startsWith("$wake ")) {
                return normalized.substring(wake.length).trim()
            }
        }
        return normalized
    }

    /**
     * Canonicalize text while removing punctuation/format characters but
     * preserving real whitespace boundaries. Used only for forbidden
     * concepts so a hidden in-word separator cannot evade a safety
     * exclusion without broadening ordinary required-concept matching.
     */
    fun joinedIntraWordText(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var canonical = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        canonical = canonical
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        val sb = StringBuilder(canonical.length)
        for (ch in canonical) {
            val cat = Character.getType(ch).toByte()
            when {
                ch.isWhitespace() -> sb.append(' ')
                isPunctuationOrFormat(cat) -> Unit // skip
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim().replace(MULTISPACE, " ")
    }

    fun containsPhrase(text: String, phrase: String): Boolean =
        phrase.isNotEmpty() && " $phrase " in " $text "

    /**
     * Match a multiword phrase across obfuscated in-word separators.
     * Comparisons are bounded by whitespace-delimited words so a compact
     * phrase cannot match inside unrelated surrounding words.
     */
    fun containsCompactMultiwordPhrase(text: String, phrase: String): Boolean {
        if (phrase.isEmpty() || !phrase.any { it.isWhitespace() }) return false
        val compactPhrase = joinedIntraWordText(phrase).replace(" ", "")
        if (compactPhrase.isEmpty()) return false
        val words = joinedIntraWordText(text).split(' ').filter { it.isNotEmpty() }
        for (start in words.indices) {
            val candidate = StringBuilder()
            for (word in words.subList(start, words.size)) {
                candidate.append(word)
                val compact = candidate.toString()
                if (compact == compactPhrase) return true
                if (compact.length >= compactPhrase.length) break
            }
        }
        return false
    }

    /** Match text against one trigger contract without side effects. */
    fun matchTask(text: String?, trigger: TriggerContract): MatchResult {
        if (text.isNullOrEmpty()) {
            return MatchResult(false, "", emptyList(), trigger.requiredConcepts, emptyList(), "invalid_input")
        }
        val normalizedInput = normalizeText(text, trigger.wakeWords)
        if (normalizedInput.isEmpty()) {
            return MatchResult(false, "", emptyList(), trigger.requiredConcepts, emptyList(), "invalid_input")
        }
        val matched = mutableListOf<String>()
        val missing = mutableListOf<List<String>>()
        for (group in trigger.requiredConcepts) {
            val representative = group.firstOrNull { concept ->
                containsPhrase(normalizedInput, normalizeText(concept))
            }
            if (representative == null) missing.add(group) else matched.add(representative)
        }
        val joinedInput = joinedIntraWordText(text)
        val forbidden = mutableListOf<String>()
        val seen = HashSet<String>()
        for (concept in trigger.forbiddenConcepts) {
            val normConcept = normalizeText(concept)
            val joinedConcept = joinedIntraWordText(concept)
            if ((containsPhrase(normalizedInput, normConcept) ||
                    containsPhrase(joinedInput, joinedConcept) ||
                    containsCompactMultiwordPhrase(text, concept)) && normConcept !in seen
            ) {
                forbidden.add(concept)
                seen.add(normConcept)
            }
        }
        val referencePhraseMatched = missing.isNotEmpty() &&
            matchesReferencePhrase(normalizedInput, trigger.referencePhrases)
        val reason = when {
            forbidden.isNotEmpty() -> "forbidden_concept"
            referencePhraseMatched -> "reference_phrase_match"
            missing.isNotEmpty() -> "missing_required_concepts"
            else -> "matched"
        }
        return MatchResult(
            matched = reason == "matched" || reason == "reference_phrase_match",
            normalizedInput = normalizedInput,
            matchedConcepts = matched,
            missingConcepts = missing,
            forbiddenMatches = forbidden,
            reason = reason,
        )
    }

    /** Tolerates clipped wake-word handoff and small speech-recognition substitutions. */
    private fun matchesReferencePhrase(input: String, references: List<String>): Boolean {
        val inputTokens = input.split(' ').filter(String::isNotEmpty).toSet()
        return references.any { reference ->
            val referenceTokens = normalizeText(reference).split(' ')
                .filter(String::isNotEmpty)
                .toSet()
            referenceTokens.size >= MIN_REFERENCE_TOKENS &&
                referenceTokens.intersect(inputTokens).size.toDouble() / referenceTokens.size >=
                MIN_REFERENCE_TOKEN_COVERAGE
        }
    }
}

/** Routes utterances only to the supplied, currently armed task. */
class StudyTaskRouter {
    fun route(text: String?, armedSpec: TrialSpec?): StudyRouting.RouteResult {
        if (armedSpec == null) {
            return StudyRouting.RouteResult(
                StudyRouting.RouteDecision.PASS_THROUGH, null, null,
            )
        }
        val trigger = armedSpec.trigger
        if (trigger == null) {
            return StudyRouting.RouteResult(
                StudyRouting.RouteDecision.RETRY,
                StudyRouting.MatchResult(
                    false,
                    StudyRouting.normalizeText(text),
                    emptyList(), emptyList(), emptyList(),
                    "missing_trigger_contract",
                ),
                null,
            )
        }
        val match = StudyRouting.matchTask(text, trigger)
        if (!match.matched) {
            return StudyRouting.RouteResult(StudyRouting.RouteDecision.RETRY, match, null)
        }
        return StudyRouting.RouteResult(StudyRouting.RouteDecision.CLAIMED, match, armedSpec)
    }
}

// ── helpers ──

private val NON_WORD = Regex("[\\W_]+")
private val MULTISPACE = Regex(" {2,}")
private const val MIN_REFERENCE_TOKENS = 8
private const val MIN_REFERENCE_TOKEN_COVERAGE = 0.70

private fun isPunctuationOrFormat(category: Byte): Boolean {
    // Character.PARAGRAPH_SEPARATOR .. Character.OTHER_SYMBOL are punctuation; Cf is FORMAT
    if (category == Character.FORMAT) return true
    // punctuation categories: DASH_PUNCTUATION, START_PUNCTUATION, END_PUNCTUATION,
    // CONNECTOR_PUNCTUATION, OTHER_PUNCTUATION, INITIAL_QUOTE_PUNCTUATION, FINAL_QUOTE_PUNCTUATION
    return category in PUNCT_CATEGORIES
}

private val PUNCT_CATEGORIES = setOf<Byte>(
    Character.DASH_PUNCTUATION,
    Character.START_PUNCTUATION,
    Character.END_PUNCTUATION,
    Character.CONNECTOR_PUNCTUATION,
    Character.OTHER_PUNCTUATION,
    Character.INITIAL_QUOTE_PUNCTUATION,
    Character.FINAL_QUOTE_PUNCTUATION,
)

/** Marker kept for symmetry with the Python module; conditions aren't used in routing. */
@Suppress("unused")
private val UNUSED_CONDITION = RuntimeStudyCondition.VOLUNTARY_INTERVENTION
