package com.caddie.app.studyportal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStudyReadinessMonitorTest {
    @Test
    fun snapshotCombinesLiveCapabilitiesWithRefreshedGatewayHealth() = runTest {
        var accessibility = false
        var gateway = true
        var mcp = true
        val monitor = NativeStudyReadinessMonitor(
            accessibilityConnected = { accessibility },
            modelGatewayCheck = { gateway },
            mcpCheck = { mcp },
        )

        assertFalse(monitor.current(studySpecsLoaded = true).isReady)

        monitor.refreshRemoteCapabilities()
        accessibility = true
        assertTrue(monitor.current(studySpecsLoaded = true).isReady)

        mcp = false
        monitor.refreshRemoteCapabilities()
        assertTrue(monitor.current(studySpecsLoaded = true).isReady)
        mcp = true
        gateway = false
        monitor.refreshRemoteCapabilities()
        assertTrue(monitor.current(studySpecsLoaded = true).isReady)
    }

    @Test
    fun remoteHealthRefreshDoesNotBlockDeterministicStudyReadiness() = runTest {
        val checkStarted = CompletableDeferred<Unit>()
        val releaseCheck = CompletableDeferred<Unit>()
        var suspendMcp = false
        val monitor = NativeStudyReadinessMonitor(
            accessibilityConnected = { true },
            modelGatewayCheck = { true },
            mcpCheck = {
                if (suspendMcp) {
                    checkStarted.complete(Unit)
                    releaseCheck.await()
                }
                true
            },
        )
        monitor.refreshRemoteCapabilities()
        assertTrue(monitor.current(studySpecsLoaded = true).isReady)

        suspendMcp = true
        val refresh = launch { monitor.refreshRemoteCapabilities() }
        checkStarted.await()

        assertTrue(monitor.current(studySpecsLoaded = true).isReady)
        releaseCheck.complete(Unit)
        refresh.join()
        assertTrue(monitor.current(studySpecsLoaded = true).isReady)
    }
}
