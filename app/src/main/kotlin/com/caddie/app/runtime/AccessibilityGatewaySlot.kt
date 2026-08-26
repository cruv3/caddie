package com.caddie.app.runtime

import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.SnapshotCompleteness
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.VerificationDecision

/** Delegates to the Accessibility gateway owned by the current service connection. */
internal class AccessibilityGatewaySlot : ExecutionGateway {
    private var connection: Any? = null
    private var gateway: ExecutionGateway? = null

    @Synchronized
    fun connect(connection: Any, gateway: ExecutionGateway) {
        this.connection = connection
        this.gateway = gateway
    }

    @Synchronized
    fun disconnect(connection: Any) {
        if (this.connection === connection) {
            this.connection = null
            gateway = null
        }
    }

    @get:Synchronized
    val isConnected: Boolean
        get() = gateway != null

    override suspend fun observe(): UiObservation =
        currentGateway()?.observe() ?: UiObservation(
            id = "accessibility-unavailable",
            capturedAtElapsedRealtimeMillis = 0,
            completeness = SnapshotCompleteness.UNAVAILABLE,
            inputWindows = emptyList(),
        )

    override suspend fun performSemantic(
        target: SemanticTarget,
        action: RequestedAction,
    ): ActionOutcome =
        currentGateway()?.performSemantic(target, action)
            ?: ActionOutcome.AccessibilityUnavailable

    override fun resolveDestinationPackages(action: RequestedAction): Set<String> =
        currentGateway()?.resolveDestinationPackages(action).orEmpty()

    override fun verifyNavigation(
        action: RequestedAction,
        observation: UiObservation,
    ): VerificationDecision? =
        currentGateway()?.verifyNavigation(action, observation)

    @Synchronized
    private fun currentGateway(): ExecutionGateway? = gateway
}
