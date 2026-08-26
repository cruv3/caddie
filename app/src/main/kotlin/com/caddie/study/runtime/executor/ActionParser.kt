package com.caddie.study.runtime.executor

/**
 * Parses deterministic study action strings into typed descriptors.
 *
 * Ported from `caddie.study.executor._parse_action`. Supported formats:
 *
 * - `"open <package>"`           → OPEN_APP
 * - `"open_url <url>"`           → OPEN_URL
 * - `"click first '<label>'"`    → TAP_FIRST
 * - `"click '<label>'"`          → TAP
 * - `"click <label>"`            → TAP
 * - `"input text '<text>'"`      → TYPE
 * - `"input text '<text>' (submit)"` → TYPE + submit
 * - `"replace text '<text>'"`    → REPLACE
 * - `"capture transit arrival as '<name>'"`   → CAPTURE_TRANSIT_ARRIVAL
 * - `"capture transit duration as '<name>'"`  → CAPTURE_TRANSIT_DURATION
 * - `"capture song recommendation as '<name>'"` → CAPTURE_SONG_RECOMMENDATION
 * - `"scroll <direction>"`      → SCROLL
 * - `"press <button>"`          → PRESS
 *
 * Throws [IllegalArgumentException] for unrecognised formats.
 */
object ActionParser {

    enum class ActionType(val wireValue: String) {
        OPEN_APP("open_app"),
        OPEN_URL("open_url"),
        TAP("tap"),
        TAP_FIRST("tap_first"),
        TYPE("type"),
        REPLACE("replace"),
        CAPTURE_TRANSIT_ARRIVAL("capture_transit_arrival"),
        CAPTURE_TRANSIT_DURATION("capture_transit_duration"),
        CAPTURE_SONG_RECOMMENDATION("capture_song_recommendation"),
        SCROLL("scroll"),
        PRESS("press"),
    }

    data class ParsedAction(
        val type: ActionType,
        val descriptor: String,
        val submit: Boolean = false,
    )

    fun parse(action: String): ParsedAction {
        val a = action.trim()
        fun invalid(): Nothing = throw IllegalArgumentException("Unrecognised action format: '$a'")

        if (a.startsWith("open ")) {
            val target = a.substring(5).trim()
            if (target.isEmpty()) invalid()
            return ParsedAction(ActionType.OPEN_APP, target)
        }

        if (a.startsWith("open_url ")) {
            val url = a.substring(9).trim()
            if (url.isEmpty()) invalid()
            return ParsedAction(ActionType.OPEN_URL, url)
        }

        // click first '<label>'
        if (a.startsWith("click first '") || a.startsWith("click first \"")) {
            val quote = a[12]
            val end = a.indexOf(quote, 13)
            if (end == -1 || end != a.length - 1) invalid()
            val label = a.substring(13, end)
            if (label.isEmpty()) invalid()
            return ParsedAction(ActionType.TAP_FIRST, label)
        }

        // click '<label>'
        if (a.startsWith("click '") || a.startsWith("click \"")) {
            val quote = a[6]
            val end = a.indexOf(quote, 7)
            if (end == -1 || end != a.length - 1) invalid()
            val label = a.substring(7, end)
            if (label.isEmpty()) invalid()
            return ParsedAction(ActionType.TAP, label)
        }

        // backward-compatible "tap '<label>'"
        if (a.startsWith("tap '") || a.startsWith("tap \"")) {
            val quote = a[4]
            val end = a.indexOf(quote, 5)
            if (end == -1 || end != a.length - 1) invalid()
            val label = a.substring(5, end)
            if (label.isEmpty()) invalid()
            return ParsedAction(ActionType.TAP, label)
        }

        if (a.startsWith("click ")) {
            val label = a.substring(6).trim().trim('\'', '"')
            return ParsedAction(ActionType.TAP, label)
        }

        // input text '<text>' [(submit)]
        if (a.startsWith("input text '") || a.startsWith("input text \"")) {
            val quote = a[11]
            val rest = a.substring(12)
            val end = rest.indexOf(quote)
            if (end == -1) invalid()
            val text = rest.substring(0, end)
            val extra = rest.substring(end + 1).trim()
            if (extra != "" && extra != "(submit)") invalid()
            return ParsedAction(ActionType.TYPE, text, submit = extra == "(submit)")
        }

        if (a.startsWith("input text ")) {
            val parts = a.substring(11).split(" ", limit = 2)
            val text = parts[0].trim('\'', '"')
            val extra = if (parts.size > 1) parts[1] else ""
            return ParsedAction(ActionType.TYPE, text, submit = "(submit)" in extra)
        }

        // replace text '<text>' [(submit)]
        if (a.startsWith("replace text '") || a.startsWith("replace text \"")) {
            val quote = a[13]
            val rest = a.substring(14)
            val end = rest.indexOf(quote)
            if (end == -1) invalid()
            val text = rest.substring(0, end)
            val extra = rest.substring(end + 1).trim()
            if (extra != "" && extra != "(submit)") invalid()
            return ParsedAction(ActionType.REPLACE, text, submit = extra == "(submit)")
        }

        // capture transit arrival as '<name>'
        if (a.startsWith("capture transit arrival as '") || a.startsWith("capture transit arrival as \"")) {
            val quote = a[27]
            val end = a.indexOf(quote, 28)
            if (end == -1 || end != a.length - 1) invalid()
            val name = a.substring(28, end)
            if (name.isEmpty()) invalid()
            return ParsedAction(ActionType.CAPTURE_TRANSIT_ARRIVAL, name)
        }

        // capture transit duration as '<name>'
        if (a.startsWith("capture transit duration as '") || a.startsWith("capture transit duration as \"")) {
            val quote = a[28]
            val end = a.indexOf(quote, 29)
            if (end == -1 || end != a.length - 1) invalid()
            val name = a.substring(29, end)
            if (name.isEmpty()) invalid()
            return ParsedAction(ActionType.CAPTURE_TRANSIT_DURATION, name)
        }

        // capture song recommendation as '<name>'
        if (a.startsWith("capture song recommendation as '") || a.startsWith("capture song recommendation as \"")) {
            val quote = a[31]
            val end = a.indexOf(quote, 32)
            if (end == -1 || end != a.length - 1) invalid()
            val name = a.substring(32, end)
            if (name.isEmpty()) invalid()
            return ParsedAction(ActionType.CAPTURE_SONG_RECOMMENDATION, name)
        }

        if (a.startsWith("scroll ")) {
            val dir = a.substring(7).trim()
            if (dir !in setOf("up", "down", "left", "right")) invalid()
            return ParsedAction(ActionType.SCROLL, dir)
        }

        if (a.startsWith("press ")) {
            val btn = a.substring(6).trim()
            if (btn !in setOf("BACK", "HOME", "RECENT")) invalid()
            return ParsedAction(ActionType.PRESS, btn)
        }

        invalid()
    }
}
