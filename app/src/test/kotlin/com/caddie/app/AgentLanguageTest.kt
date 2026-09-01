package com.caddie.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentLanguageTest {
    @Test
    fun `persisted language values decode and unknown values preserve German default`() {
        assertEquals(AgentLanguage.German, AgentLanguage.fromStoredValue("de"))
        assertEquals(AgentLanguage.English, AgentLanguage.fromStoredValue("en"))
        assertEquals(AgentLanguage.German, AgentLanguage.fromStoredValue("unknown"))
        assertEquals(AgentLanguage.German, AgentLanguage.fromStoredValue(null))
    }
}
