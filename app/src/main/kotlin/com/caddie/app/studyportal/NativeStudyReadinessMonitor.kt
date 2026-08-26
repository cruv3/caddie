package com.caddie.app.studyportal

import com.caddie.study.gateway.NativeStudyReadiness
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException

/** Publishes non-blocking native study readiness from process-owned health checks. */
class NativeStudyReadinessMonitor(
    private val accessibilityConnected: () -> Boolean,
    private val modelGatewayCheck: suspend () -> Boolean,
    private val mcpCheck: suspend () -> Boolean,
) {
    private val remote = AtomicReference(RemoteCapabilities())

    suspend fun refreshRemoteCapabilities() {
        remote.set(RemoteCapabilities())
        remote.set(
            RemoteCapabilities(
                modelGatewayConnected = safeCheck(modelGatewayCheck),
                mcpConnected = safeCheck(mcpCheck),
            ),
        )
    }

    fun current(studySpecsLoaded: Boolean): NativeStudyReadiness {
        val remoteCapabilities = remote.get()
        return NativeStudyReadiness(
            accessibilityConnected = accessibilityConnected(),
            modelGatewayConnected = remoteCapabilities.modelGatewayConnected,
            mcpConnected = remoteCapabilities.mcpConnected,
            studySpecsLoaded = studySpecsLoaded,
        )
    }

    private suspend fun safeCheck(check: suspend () -> Boolean): Boolean =
        try {
            check()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }

    private data class RemoteCapabilities(
        val modelGatewayConnected: Boolean = false,
        val mcpConnected: Boolean = false,
    )
}
