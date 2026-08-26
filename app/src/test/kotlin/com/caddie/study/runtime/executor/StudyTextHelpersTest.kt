package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.ErrorVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTextHelpersTest {

    // ── Element finding ──

    private val elements = listOf(
        mapOf("index" to 0, "text" to "Senden", "resource_id" to "send_btn"),
        mapOf("index" to 1, "text" to "Abbrechen", "content_description" to "Cancel"),
        mapOf("index" to 2, "text" to "Senden Bestätigung", "resource_id" to "confirm"),
    )

    @Test
    fun `find by exact resource_id`() {
        val el = StudyTextHelpers.findElement(elements, "send_btn")
        assertNotNull(el)
        assertEquals(0, el!!["index"])
    }

    @Test
    fun `find by exact text`() {
        val el = StudyTextHelpers.findElement(elements, "Abbrechen")
        assertNotNull(el)
        assertEquals(1, el!!["index"])
    }

    @Test
    fun `find by exact content_description`() {
        val el = StudyTextHelpers.findElement(elements, "Cancel")
        assertNotNull(el)
        assertEquals(1, el!!["index"])
    }

    @Test
    fun `find returns null for ambiguous substring match`() {
        // "Senden" substring matches both index 0 (exact) and index 2 (substring)
        // But exact match at index 0 wins, so this should return index 0
        val el = StudyTextHelpers.findElement(elements, "Senden")
        assertNotNull(el)
        assertEquals(0, el!!["index"])
    }

    @Test
    fun `find returns null when no match`() {
        assertNull(StudyTextHelpers.findElement(elements, "Nonexistent"))
    }

    @Test
    fun `find returns null for ambiguous substring only`() {
        val els = listOf(
            mapOf("index" to 0, "text" to "Senden A"),
            mapOf("index" to 1, "text" to "Senden B"),
        )
        assertNull(StudyTextHelpers.findElement(els, "Senden"))
    }

    @Test
    fun `find first returns first match regardless of ambiguity`() {
        val els = listOf(
            mapOf("index" to 0, "text" to "Senden A"),
            mapOf("index" to 1, "text" to "Senden B"),
        )
        val el = StudyTextHelpers.findFirstElement(els, "Senden")
        assertNotNull(el)
        assertEquals(0, el!!["index"])
    }

    // ── Error injection ──

    private val variant = ErrorVariant(
        id = "ev1", field = "amount",
        correctValue = "50.00", wrongValue = "500.00",
        description = "wrong amount",
    )

    @Test
    fun `inject error replaces correct with wrong`() {
        val action = "input text '50.00'"
        val (injected, ok) = StudyTextHelpers.injectError(action, variant)
        assertTrue(ok)
        assertEquals("input text '500.00'", injected)
    }

    @Test
    fun `inject error returns false when value not present`() {
        val action = "input text 'Hello'"
        val (injected, ok) = StudyTextHelpers.injectError(action, variant)
        assertFalse(ok)
        assertEquals(action, injected)
    }

    @Test
    fun `inject error does not match within larger identifier`() {
        val variant2 = variant.copy(correctValue = "50", wrongValue = "99")
        // "50" inside "500" should NOT match due to word boundary
        val (injected, ok) = StudyTextHelpers.replaceExactErrorValue("input text '500.00'", "50", "99")
        assertFalse(ok)
    }

    @Test
    fun `inject error with variable expansion`() {
        val variant2 = ErrorVariant(
            id = "ev2", field = "name",
            correctValue = "{name}", wrongValue = "WrongName",
            description = "wrong name",
        )
        val vars = mapOf("name" to "Alice")
        // Action is already expanded by the executor before injection
        val action = "input text 'Alice'"
        val (injected, ok) = StudyTextHelpers.injectError(action, variant2, expand = { StudyTextHelpers.expandVariables(it, vars) })
        assertTrue(ok)
        assertEquals("input text 'WrongName'", injected)
    }

    @Test
    fun `hour variant produces display candidate`() {
        val candidates = StudyTextHelpers.participantCopyReplacementCandidates("hour_5", "hour_7")
        assertEquals(1, candidates.size)
        assertEquals("5" to "7", candidates[0])
    }

    @Test
    fun `decimal variant produces comma candidate`() {
        val candidates = StudyTextHelpers.participantCopyReplacementCandidates("3.5", "9.9")
        assertEquals(1, candidates.size)
        assertEquals("3,5" to "9,9", candidates[0])
    }

    @Test
    fun `non technical values produce no candidates`() {
        assertEquals(0, StudyTextHelpers.participantCopyReplacementCandidates("Max", "Moritz").size)
    }

    // ── Variable expansion ──

    @Test
    fun `expand variables replaces placeholders`() {
        val vars = mapOf("name" to "Alice", "city" to "Berlin")
        assertEquals("Hello Alice from Berlin", StudyTextHelpers.expandVariables("Hello {name} from {city}", vars))
    }

    @Test
    fun `expand variables with no placeholders returns unchanged`() {
        assertEquals("Hello World", StudyTextHelpers.expandVariables("Hello World", emptyMap()))
    }

    @Test
    fun `song title capture removes recommendation wrapper and artist`() {
        assertEquals(
            "As It Was",
            StudyTextHelpers.normalizeCapturedValue(
                "song_title",
                "Song-Tipp: As It Was von Harry Styles.",
            ),
        )
        assertEquals(
            "As It Was",
            StudyTextHelpers.normalizeCapturedValue("song_title", "As It Was von Harry Styles"),
        )
    }

    @Test
    fun `capture normalization leaves exact titles and other variables unchanged`() {
        assertEquals("As It Was", StudyTextHelpers.normalizeCapturedValue("song_title", "As It Was"))
        assertEquals(
            "As It Was von Harry Styles",
            StudyTextHelpers.normalizeCapturedValue("message", "As It Was von Harry Styles"),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun `expand unknown variable throws`() {
        StudyTextHelpers.expandVariables("Hello {unknown}", emptyMap())
    }

    @Test
    fun `shift time minutes positive`() {
        assertEquals("10:30", StudyTextHelpers.shiftTimeMinutes("10:15", 15))
    }

    @Test
    fun `shift time minutes negative`() {
        assertEquals("10:05", StudyTextHelpers.shiftTimeMinutes("10:15", -10))
    }

    @Test
    fun `shift time wraps around midnight`() {
        assertEquals("00:05", StudyTextHelpers.shiftTimeMinutes("23:55", 10))
    }

    // ── Confirmation text formatting ──

    @Test
    fun `format tap confirmation text`() {
        assertEquals("Auf „Senden“ tippen", StudyTextHelpers.formatConfirmationText("click 'Senden'"))
    }

    @Test
    fun `format type confirmation text`() {
        assertEquals("Text eingeben: „Hello“", StudyTextHelpers.formatConfirmationText("input text 'Hello'"))
    }

    @Test
    fun `format open app confirmation text`() {
        assertEquals("App öffnen: Sparkasse", StudyTextHelpers.formatConfirmationText("open com.caddie.studybank"))
    }

    @Test
    fun `format press back confirmation text`() {
        assertEquals("Tastatur schließen", StudyTextHelpers.formatConfirmationText("press BACK"))
    }

    @Test
    fun `format scroll confirmation text falls through to raw action`() {
        // Python _format_confirmation_text has no scroll branch → returns raw action
        assertEquals("scroll down", StudyTextHelpers.formatConfirmationText("scroll down"))
    }

    @Test
    fun `format hides technical identifiers in tap`() {
        // resource_id is technical
        val ct = StudyTextHelpers.formatConfirmationText("click 'com.example:id/send_btn'")
        assertEquals("Ausgewähltes Element öffnen", ct)
    }

    @Test
    fun `format google maps route`() {
        val action = "open_url https://www.google.com/maps/dir/Berlin/Hamburg"
        val ct = StudyTextHelpers.formatConfirmationText(action)
        assertEquals("ÖPNV-Route Berlin → Hamburg öffnen", ct)
    }

    @Test
    fun `google maps route returns null for non maps url`() {
        assertNull(StudyTextHelpers.googleMapsRoute("open_url https://example.com/page"))
    }

    @Test
    fun `participant confirmation text keeps non technical copy`() {
        assertEquals("Auf Senden tippen", StudyTextHelpers.participantConfirmationText("Auf Senden tippen", "click 'Senden'"))
    }

    @Test
    fun `participant confirmation text falls back when technical`() {
        val ct = StudyTextHelpers.participantConfirmationText("click com.example:id/btn", "click 'com.example:id/btn'")
        assertEquals("Ausgewähltes Element öffnen", ct)
    }

    @Test
    fun `is technical detects package names`() {
        assertTrue(StudyTextHelpers.isTechnicalConfirmationText("com.example.app"))
    }

    @Test
    fun `is technical detects resource ids`() {
        assertTrue(StudyTextHelpers.isTechnicalConfirmationText("@id/send_button"))
    }

    @Test
    fun `is technical false for plain text`() {
        assertFalse(StudyTextHelpers.isTechnicalConfirmationText("Auf Senden tippen"))
    }

    @Test
    fun `app display name maps known packages`() {
        assertEquals("Telegram", StudyTextHelpers.appDisplayName("com.caddie.studytelegram"))
        assertEquals("Kalender", StudyTextHelpers.appDisplayName("com.caddie.studycalendar"))
    }

    @Test
    fun `app display name strips component suffix`() {
        assertEquals("Sparkasse", StudyTextHelpers.appDisplayName("com.caddie.studybank/.MainActivity"))
    }

    @Test
    fun `app display name returns package for unknown`() {
        assertEquals("com.unknown.app", StudyTextHelpers.appDisplayName("com.unknown.app"))
    }
}
