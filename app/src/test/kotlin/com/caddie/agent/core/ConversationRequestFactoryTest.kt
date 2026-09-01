package com.caddie.agent.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationRequestFactoryTest {
    @Test
    fun `prompt provider is read for each request`() {
        var prompt = "German prompt"
        val factory = ConversationRequestFactory { prompt }
        val snapshot = RunSnapshot(
            runId = RunId("run"),
            state = RunState.RUNNING,
        )

        assertEquals("German prompt", factory.create(snapshot, emptyList()).messages.first().content)

        prompt = "English prompt"

        assertEquals("English prompt", factory.create(snapshot, emptyList()).messages.first().content)
    }
}
