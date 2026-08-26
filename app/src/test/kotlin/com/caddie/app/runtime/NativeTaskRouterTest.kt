package com.caddie.app.runtime

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeTaskRouterTest {
    @Test
    fun `study outcome owns the utterance without normal dispatch`() = runTest {
        var normalCalls = 0

        val outcome = NativeTaskRouter().dispatchStudyFirst(
            study = { StudyRouteOutcome.Retry },
            normal = { normalCalls += 1 },
        )

        assertEquals(NativeTaskDispatch.Study(StudyRouteOutcome.Retry), outcome)
        assertEquals(0, normalCalls)
    }

    @Test
    fun `study pass through dispatches exactly once to native normal runtime`() = runTest {
        var normalCalls = 0

        val outcome = NativeTaskRouter().dispatchStudyFirst(
            study = { StudyRouteOutcome.PassThrough },
            normal = { normalCalls += 1 },
        )

        assertEquals(NativeTaskDispatch.Normal, outcome)
        assertEquals(1, normalCalls)
    }
}
