package com.caddie.app.studyportal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.study.store.StudyRoomDatabase
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.gateway.NativeStudyReadiness
import com.caddie.app.androidRuntimeProcess
import com.caddie.study.runtime.audit.ControlledErrorRecorderSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that Android can compose the complete native portal backend. */
@RunWith(AndroidJUnit4::class)
class StudyPortalRuntimeTest {

    @Test
    fun applicationOwnsOneCoordinatorForPortalAndAgentServices() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val application = context.applicationContext as com.caddie.app.CaddieApplication

        assertSame(
            application.runtimeProcess.studyCoordinator,
            context.androidRuntimeProcess().studyCoordinator,
        )
    }

    @Test
    fun compositionLoadsTheFrozenSpecsAndCreatesAParticipantSession() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StudyRoomDatabase::class.java).build()
        val runtime = StudyPortalRuntime.create(
            context,
            database,
            ArmedTrialCoordinator(),
            controlledErrorRecorder = ControlledErrorRecorderSlot(),
        )

        try {
            val sessionId = runtime.api.createLiveSession("P01")
            val assignment = runtime.api.getSessionAssignment(sessionId)

            assertEquals(6, (assignment["task_order"] as List<*>).size)
            assertEquals(6, (assignment["condition_order"] as List<*>).size)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun portalArmsTheCoordinatorOwnedByTheAndroidRuntimeProcess() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, StudyRoomDatabase::class.java).build()
        val coordinator = ArmedTrialCoordinator()
        val runtime = StudyPortalRuntime.create(
            context,
            database,
            coordinator,
            readinessProvider = {
                NativeStudyReadiness(
                    accessibilityConnected = true,
                    modelGatewayConnected = true,
                    mcpConnected = true,
                    studySpecsLoaded = false,
                )
            },
            controlledErrorRecorder = ControlledErrorRecorderSlot(),
        )

        try {
            val sessionId = runtime.api.createLiveSession("P01")
            runtime.api.transition(sessionId, "start_consent", 0)
            runtime.api.transition(sessionId, "consent_confirmed", 1)
            runtime.api.transition(sessionId, "training_confirmed", 2)

            runtime.api.prepareNextTrial(sessionId, 3)

            assertEquals(
                ArmedTrialCoordinator.ArmedState.ARMED,
                coordinator.status().state,
            )
            assertEquals("P01", coordinator.status().participantId)
            assertEquals(sessionId.toString(), coordinator.status().studyRunId)
        } finally {
            runtime.close()
        }
    }
}
