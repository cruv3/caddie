package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.ErrorVariant
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Pure helpers for element resolution, participant-facing text formatting,
 * error injection, and variable expansion.
 *
 * Ported from the standalone functions in `caddie.study.executor`.
 * All functions are deterministic and side-effect-free.
 */
object StudyTextHelpers {

    // ── Element finding ──

    /**
     * Find an element matching [label] with structured priority.
     *
     * Resolution order:
     * 1. Exact resource_id match
     * 2. Exact text/content_description match
     * 3. Substring match (case-insensitive) — returns null if ambiguous
     *
     * Returns null for zero or multiple ambiguous matches.
     */
    fun findElement(elements: List<Map<String, Any?>>, label: String): Map<String, Any?>? {
        val labelLower = label.lowercase()
        val normalizedLabel = normalize(label)

        // 1. Exact resource_id
        for (el in elements) {
            val rid = (el["resource_id"] as? String ?: "").lowercase()
            if (rid == labelLower) return el
        }

        // 2. Exact text/content_description
        for (el in elements) {
            val text = normalize(el["text"])
            val desc = normalize(el["content_description"])
            if (text == normalizedLabel || desc == normalizedLabel) return el
        }

        // 3. Substring — check ambiguity
        val matches = elements.filter { el ->
            val text = normalize(el["text"])
            val desc = normalize(el["content_description"])
            val rid = (el["resource_id"] as? String ?: "").lowercase()
            normalizedLabel in text || normalizedLabel in desc || labelLower in rid
        }
        return if (matches.size == 1) matches[0] else null
    }

    /** Find the first element matching label text, description, or resource ID. */
    fun findFirstElement(elements: List<Map<String, Any?>>, label: String): Map<String, Any?>? {
        val labelLower = label.lowercase()
        val normalizedLabel = normalize(label)
        for (el in elements) {
            val text = normalize(el["text"])
            val desc = normalize(el["content_description"])
            val rid = (el["resource_id"] as? String ?: "").lowercase()
            if (normalizedLabel in text || normalizedLabel in desc || labelLower in rid) return el
        }
        return null
    }

    private fun normalize(value: Any?): String =
        value?.toString().orEmpty().lowercase().trim().replace(Regex("\\s+"), " ")

    // ── Confirmation text formatting ──

    private val APP_DISPLAY_NAMES = mapOf(
        "com.caddie.studytelegram" to "Telegram",
        "com.caddie.studybank" to "Sparkasse",
        "com.caddie.studycalendar" to "Kalender",
        "com.caddie.studymail" to "E-Mail",
        "com.caddie.studygallery" to "Galerie",
        "com.caddie.studynotes" to "Notizen",
        "com.caddie.studymusic" to "Spotify",
    )

    fun appDisplayName(packageName: String): String {
        val pkg = packageName.split("/", limit = 2)[0]
        return APP_DISPLAY_NAMES[pkg] ?: pkg
    }

    /** Return whether text exposes a technical Android identifier. */
    fun isTechnicalConfirmationText(text: String): Boolean =
        TECHNICAL_PATTERNS.any { it.containsMatchIn(text) }

    /** Build a neutral participant-facing description of an effective action. */
    fun formatConfirmationText(action: String, expand: (String) -> String = { it }): String {
        val route = googleMapsRoute(action)
        if (route != null) {
            val (origin, destination) = route
            return "ÖPNV-Route $origin → $destination öffnen"
        }
        val parsed = ActionParser.parse(action)
        val value = parsed.descriptor
        return when (parsed.type) {
            ActionParser.ActionType.CAPTURE_TRANSIT_DURATION -> "Reisedauer aus der geöffneten ÖPNV-Route lesen"
            ActionParser.ActionType.CAPTURE_TRANSIT_ARRIVAL -> "Ankunftszeit aus der geöffneten ÖPNV-Route lesen"
            ActionParser.ActionType.CAPTURE_SONG_RECOMMENDATION -> "Liedempfehlung aus dem geöffneten Chat lesen"
            ActionParser.ActionType.TYPE -> "Text eingeben: „$value“"
            ActionParser.ActionType.TAP, ActionParser.ActionType.TAP_FIRST -> {
                if (isTechnicalConfirmationText(value)) "Ausgewähltes Element öffnen"
                else "Auf „$value“ tippen"
            }
            ActionParser.ActionType.PRESS -> if (value == "BACK") "Tastatur schließen" else "Taste $value"
            ActionParser.ActionType.OPEN_APP -> "App öffnen: ${appDisplayName(value)}"
            else -> action
        }
    }

    /** Keep explicit participant copy unless it exposes a technical identifier. */
    fun participantConfirmationText(text: String, effectiveAction: String): String {
        if (!isTechnicalConfirmationText(text)) return text
        val fallback = formatConfirmationText(effectiveAction)
        if (!isTechnicalConfirmationText(fallback)) return fallback
        return "Ausgewählte Aktion ausführen"
    }

    /** Return decoded (origin, destination) from a Google Maps dir URL, or null. */
    fun googleMapsRoute(action: String): Pair<String, String>? {
        val parsed = try {
            ActionParser.parse(action)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (parsed.type != ActionParser.ActionType.OPEN_URL) return null
        val url = parsed.descriptor
        val marker = "/maps/dir/"
        val path = try {
            java.net.URI(url).path ?: return null
        } catch (e: Exception) {
            return null
        }
        val markerIdx = path.indexOf(marker)
        if (markerIdx == -1) return null
        val parts = path.substring(markerIdx + marker.length)
            .split("/")
            .filter { it.isNotEmpty() }
            .map { URLDecoder.decode(it, "UTF-8") }
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    // ── Error injection ──

    /**
     * Replace [correctValue] with [wrongValue] in [text] exactly once.
     * The match must not be part of a larger identifier (word-boundary guard).
     * Returns (newText, wasReplaced).
     */
    fun replaceExactErrorValue(text: String, correctValue: String, wrongValue: String): Pair<String, Boolean> {
        val pattern = Regex("(?<![A-Za-z0-9_])${Regex.escape(correctValue)}(?![A-Za-z0-9_])")
        val match = pattern.find(text) ?: return text to false
        return (text.substring(0, match.range.first) + wrongValue + text.substring(match.range.last + 1)) to true
    }

    /**
     * Narrow display-value pairs for established technical variants
     * (e.g. hour_5 → "5", decimal 3.5 → "3,5").
     */
    fun participantCopyReplacementCandidates(correctValue: String, wrongValue: String): List<Pair<String, String>> {
        val hourPattern = Regex("hour_(\\d+)")
        val correctHour = hourPattern.matchEntire(correctValue)
        val wrongHour = hourPattern.matchEntire(wrongValue)
        if (correctHour != null && wrongHour != null) {
            return listOf(correctHour.groupValues[1] to wrongHour.groupValues[1])
        }
        val decimalPattern = Regex("\\d+\\.\\d+")
        if (decimalPattern.matches(correctValue) && decimalPattern.matches(wrongValue)) {
            return listOf(correctValue.replace(".", ",") to wrongValue.replace(".", ","))
        }
        return emptyList()
    }

    /**
     * Inject an error variant into an action string.
     *
     * Replaces correct_value with wrong_value (also tries URL-decoded forms).
     * When [participantCopy] is true, also tries narrow display-value candidates.
     * Returns (injectedAction, wasInjected).
     */
    fun injectError(
        action: String,
        errorVariant: ErrorVariant,
        expand: (String) -> String = { it },
        participantCopy: Boolean = false,
    ): Pair<String, Boolean> {
        val cv = expand(errorVariant.correctValue)
        val wv = expand(errorVariant.wrongValue)
        for ((correctValue, wrongValue) in listOf(cv to wv, urlDecode(cv) to urlDecode(wv))) {
            val (injected, ok) = replaceExactErrorValue(action, correctValue, wrongValue)
            if (ok) return injected to true
        }
        if (participantCopy) {
            for ((correctValue, wrongValue) in participantCopyReplacementCandidates(cv, wv)) {
                val (injected, ok) = replaceExactErrorValue(action, correctValue, wrongValue)
                if (ok) return injected to true
            }
        }
        return action to false
    }

    private fun urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

    // ── Variable expansion ──

    private val VAR_PATTERN = Regex("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")

    /**
     * Replace `{name}` placeholders with captured study variables.
     * Throws [NoSuchElementException] if a variable is unknown.
     */
    fun expandVariables(text: String, variables: Map<String, String>): String {
        return VAR_PATTERN.replace(text) { m ->
            val name = m.groupValues[1]
            variables[name] ?: throw NoSuchElementException("Unknown study variable: $name")
        }
    }

    /** Converts the seeded chat recommendation into the title expected by Study Music. */
    fun normalizeCapturedValue(name: String, value: String): String {
        if (name != "song_title") return value
        val title = value
            .trim()
            .replace(Regex("^Song-Tipp:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+von\\s+[^.]+\\.?$", RegexOption.IGNORE_CASE), "")
            .trim()
        return title.ifEmpty { value }
    }

    /** Shift an HH:MM clock time by [deltaMinutes] without attaching a date. */
    fun shiftTimeMinutes(value: String, deltaMinutes: Int): String {
        val parts = value.split(":", limit = 2)
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val total = (hours * 60 + minutes + deltaMinutes).mod(24 * 60)
        val h = total / 60
        val mm = total % 60
        return "%02d:%02d".format(h, mm)
    }

    // ── Technical identifier detection patterns ──

    private val TECHNICAL_KNOWN_PACKAGE = Regex(
        "(?<![@.A-Za-z0-9_])(?:com|org|net|edu|gov|io|android|de|dev)(?:\\.[A-Za-z_][A-Za-z0-9_]*)+\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TECHNICAL_COMPONENT = Regex(
        "\\b(?:[a-z][a-z0-9_]*\\.)+[a-z][a-z0-9_]*/(?:\\.[A-Za-z_][A-Za-z0-9_]*|[A-Za-z_][A-Za-z0-9_]*)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TECHNICAL_RESOURCE_ID = Regex(
        "(?<![A-Za-z0-9_])(?:@\\+?)?id/[A-Za-z_][A-Za-z0-9_]*\\b|(?<![@.A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_.]*:id/[A-Za-z_][A-Za-z0-9_]*\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TECHNICAL_RELATIVE_COMPONENT = Regex(
        "(?<![A-Za-z0-9_])\\.[A-Z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)+\\b",
    )
    private val TECHNICAL_THREE_SEGMENT_CLASS = Regex(
        "(?<![@.A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_]+(?:\\.[A-Za-z_][A-Za-z0-9_]*)+\\.[A-Z][A-Za-z0-9_]*\\b(?!\\.)",
    )
    private val TECHNICAL_TWO_SEGMENT_CAMELCASE = Regex(
        "(?<![@.A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_]+\\.[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+\\b(?!\\.)",
    )
    private val TECHNICAL_DOTTED_CONTEXT = Regex(
        "\\b(?:App\\s+öffnen\\s*:\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+|Auf\\s+(?:„[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+“|\"[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+\"|[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+)\\s+tippen)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TECHNICAL_ACTIVITY_CLASS = Regex(
        "\\b(?:activity(?!-)|[A-Za-z_][A-Za-z0-9_]*activity[A-Za-z0-9_]*)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TECHNICAL_ACTION_PREFIX = Regex(
        "^\\s*(?:click|open|open_url|input\\s+text|press|scroll|capture\\s+transit)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val TECHNICAL_PATTERNS = listOf(
        TECHNICAL_KNOWN_PACKAGE,
        TECHNICAL_DOTTED_CONTEXT,
        TECHNICAL_TWO_SEGMENT_CAMELCASE,
        TECHNICAL_THREE_SEGMENT_CLASS,
        TECHNICAL_COMPONENT,
        TECHNICAL_RESOURCE_ID,
        TECHNICAL_RELATIVE_COMPONENT,
        TECHNICAL_ACTIVITY_CLASS,
        TECHNICAL_ACTION_PREFIX,
    )
}
