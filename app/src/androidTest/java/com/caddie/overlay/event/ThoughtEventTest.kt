package com.caddie.overlay.event

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThoughtEventTest {

    @Test
    fun currentActionCarriesStudyNarration() {
        val event = ThoughtEvent.parse(
            """{"type":"current_action","payload":{"narration":"Öffne Maps"}}"""
        )

        assertEquals(ThoughtEvent.CurrentAction("Öffne Maps"), event)
    }
}
