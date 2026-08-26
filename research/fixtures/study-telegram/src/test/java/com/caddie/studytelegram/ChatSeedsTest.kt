package com.caddie.studytelegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.Locale

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChatSeedsTest {

    @Test
    fun `0 times remain ASCII with a non-Latin default locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(
                Locale.Builder()
                    .setLanguage("ar")
                    .setRegion("EG")
                    .setUnicodeLocaleKeyword("nu", "arab")
                    .build(),
            )

            assertFalse("%02d:%02d".format(9, 0).matches(Regex("[0-9]{2}:[0-9]{2}")))

            assertTrue(
                ChatSeeds.messages(ChatId.LENA).all { it.time.matches(Regex("[0-9]{2}:[0-9]{2}")) },
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `histories have the requested message counts`() {
        assertEquals(48, ChatSeeds.messages(ChatId.LENA).size)
        assertEquals(48, ChatSeeds.messages(ChatId.ANNA).size)
        assertEquals(16, ChatSeeds.messages(ChatId.ANNE).size)
        assertEquals(16, ChatSeeds.messages(ChatId.ANNI).size)
        assertEquals(16, ChatSeeds.messages(ChatId.MILA).size)
        assertEquals(16, ChatSeeds.messages(ChatId.JONAS).size)
        assertEquals(15, ChatSeeds.messages(ChatId.PROJECT).size)
    }

    @Test
    fun `Lena has one precise recommendation hidden from the preview`() {
        val recommendations = ChatSeeds.messages(ChatId.LENA)
            .filter { it.text.startsWith("Song-Tipp:") }

        assertEquals(listOf("Song-Tipp: As It Was von Harry Styles."), recommendations.map { it.text })
        assertFalse(ChatSeeds.chat(ChatId.LENA).preview.contains("As It Was"))
    }

    @Test
    fun `every history includes both directions across multiple dates`() {
        ChatId.entries.forEach { id ->
            val history = ChatSeeds.messages(id)

            assertTrue("$id needs an incoming message", history.any { it.incoming })
            assertTrue("$id needs an outgoing message", history.any { !it.incoming })
            assertTrue("$id needs multiple dates", history.map { it.dateLabel }.distinct().size >= 2)
        }
    }

    @Test
    fun `Anna ends by asking about the Deutz arrival time`() {
        assertEquals(
            "Sag mir kurz, wann du ungefähr in Deutz ankommst.",
            ChatSeeds.messages(ChatId.ANNA).last().text,
        )
    }

    @Test
    fun `chat summaries match their chat identifiers`() {
        ChatId.entries.forEach { id ->
            val summary = ChatSeeds.chat(id)

            assertEquals(id, summary.id)
            assertEquals(ChatSeeds.messages(id).last().text, summary.preview)
        }
    }

    @Test
    fun `message histories cannot be mutated by callers`() {
        val history = ChatSeeds.messages(ChatId.PROJECT)
        val original = history.toList()
        var mutationSucceeded = false

        try {
            (history as MutableList).add(ChatMessage("Injected", false, "Heute", "12:00"))
            mutationSucceeded = true
        } catch (_: UnsupportedOperationException) {
            // The public history must remain read-only even when cast to a mutable Java/Kotlin list.
        } finally {
            if (mutationSucceeded) {
                (history as MutableList).removeAt(history.lastIndex)
            }
        }

        if (mutationSucceeded) fail("Callers must not be able to mutate a chat history")
        assertEquals(original, ChatSeeds.messages(ChatId.PROJECT))
    }
}
