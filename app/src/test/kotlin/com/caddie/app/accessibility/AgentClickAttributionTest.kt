package com.caddie.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentClickAttributionTest {
    @Test
    fun `one expected agent click is consumed only once`() {
        var now = 1_000L
        val attribution = AgentClickAttribution(nowMillis = { now })

        attribution.expectAgentClick(validForMs = 500L)

        assertTrue(attribution.consumeExpectedAgentClick())
        assertFalse(attribution.consumeExpectedAgentClick())
    }

    @Test
    fun `expired expectation does not hide a human click`() {
        var now = 1_000L
        val attribution = AgentClickAttribution(nowMillis = { now })
        attribution.expectAgentClick(validForMs = 500L)

        now = 1_501L

        assertFalse(attribution.consumeExpectedAgentClick())
    }
}
