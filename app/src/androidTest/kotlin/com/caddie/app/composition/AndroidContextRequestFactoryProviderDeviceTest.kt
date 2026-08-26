package com.caddie.app.composition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ConversationRequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies production assets can supply normal-mode context on a real device. */
@RunWith(AndroidJUnit4::class)
class AndroidContextRequestFactoryProviderDeviceTest {
    @Test
    @LargeTest
    fun productionProviderInjectsLocalSkillAndGuidanceReplay() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AndroidContextRequestFactoryProvider.production(context)
        try {
            val factory = provider.forTask(
                "öffne kalender",
                ConversationRequestFactory("normal prompt"),
            )
            val request = factory.create(
                RunSnapshot(
                    runId = RunId("device-context"),
                    state = RunState.RUNNING,
                    messages = listOf(
                        AgentMessage(AgentMessage.Role.USER, "öffne kalender"),
                    ),
                ),
                emptyList(),
            )

            assertTrue(request.messages.any { "reference-only" in it.content })
            assertTrue(request.messages.any {
                "apps.open_google_calendar" in it.content
            })
            assertTrue(request.messages.any {
                "Replay guidance apps.open_google_calendar@legacy" in it.content
            })
        } finally {
            provider.close()
        }
    }
}
