package com.caddie.app.composition

import com.caddie.app.AgentLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeFactoryLanguageTest {
    @Test
    fun `normal prompt requires German user-facing output when German is selected`() {
        val prompt = NativeRuntimeFactory.normalSystemPrompt(AgentLanguage.German)

        assertTrue(prompt.contains("natural German phrase"))
        assertTrue(prompt.contains("must be natural German"))
    }

    @Test
    fun `normal prompt requires English user-facing output when English is selected`() {
        val prompt = NativeRuntimeFactory.normalSystemPrompt(AgentLanguage.English)

        assertTrue(prompt.contains("natural English phrase"))
        assertTrue(prompt.contains("must be natural English"))
    }
}
