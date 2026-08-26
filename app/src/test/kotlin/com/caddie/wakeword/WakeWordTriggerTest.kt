package com.caddie.wakeword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTriggerTest {
    @Test
    fun `fires at threshold and honors refractory period`() {
        val trigger = WakeWordTrigger(threshold = 0.18f, refractorySamples = 8)

        assertFalse(trigger.evaluate(score = 0.179f, newSamples = 4))
        assertTrue(trigger.evaluate(score = 0.18f, newSamples = 4))
        assertFalse(trigger.evaluate(score = 0.9f, newSamples = 4))
        assertTrue(trigger.evaluate(score = 0.9f, newSamples = 4))
    }

    @Test
    fun `reset allows immediate detection`() {
        val trigger = WakeWordTrigger(threshold = 0.18f, refractorySamples = 100)
        assertTrue(trigger.evaluate(score = 0.5f, newSamples = 4))

        trigger.reset()

        assertTrue(trigger.evaluate(score = 0.5f, newSamples = 4))
    }
}
