package com.caddie.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.caddie.agent.core.RunId
import com.caddie.app.accessibility.AgentActivityTracker
import com.caddie.app.accessibility.AndroidTouchInteractionBridge
import com.caddie.app.accessibility.PassiveTouchInterventionArbiter
import com.caddie.app.accessibility.PreciseTouchRoutingPolicy
import com.caddie.app.lockscreen.LockScreenSafetyPolicy
import com.caddie.app.runtime.NativeTouchInterventionController
import com.caddie.executor.accessibility.AndroidAccessibilityGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Accessibility service for the Caddie native agent runtime.
 *
 * This service allows the agent to observe screen content and perform
 * actions (tap, swipe, type, gesture) on behalf of the user.
 *
 * Static [instance] is used by [com.caddie.app.ui.evaluateSetup] to check
 * whether the service is connected.
 */
class CompanionAccessibilityService : AccessibilityService() {
    private var executionGateway: AndroidAccessibilityGateway? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val interventionController by lazy {
        NativeTouchInterventionController(
            pauseNativeRun = { runId -> androidRuntimeProcess().pauseForIntervention(runId) },
            resumeNativeRun = { runId ->
                androidRuntimeProcess().resumeAfterIntervention(runId).also { resumed ->
                    Log.i(TAG, "native intervention resume=$resumed")
                }
            },
            schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
            holdNativeRun = { runId ->
                androidRuntimeProcess().holdForPotentialIntervention(runId)
            },
            confirmHeldPause = { runId ->
                androidRuntimeProcess().confirmIntervention(runId)
            },
        )
    }
    private val touchArbiterDelegate = lazy {
        PassiveTouchInterventionArbiter(
            captureTarget = { androidRuntimeProcess().activeNativeRunId() },
            observeTouch = { interventionController.onHumanActivity(it) },
            confirmTouch = ::handleParticipantTouch,
            schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
        )
    }
    private val touchArbiter by touchArbiterDelegate
    private var preciseTouchBridge: AndroidTouchInteractionBridge? = null
    private val serviceScope = MainScope()
    private var touchRoutingJob: Job? = null
    private var nativeRunActive = false
    private var latestLockSignal = LockScreenSafetyPolicy.Signal.STARTED
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private var lockScreenReceiverRegistered = false
    private val lockScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val signal = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> LockScreenSafetyPolicy.Signal.SCREEN_OFF
                Intent.ACTION_SCREEN_ON -> LockScreenSafetyPolicy.Signal.SCREEN_ON
                Intent.ACTION_USER_PRESENT -> LockScreenSafetyPolicy.Signal.USER_PRESENT
                else -> return
            }
            updatePreciseTouchRouting(signal)
        }
    }

    companion object {
        private const val TAG = "CompanionA11y"
        private val connection = AccessibilityConnectionState()
        @Volatile
        var instance: CompanionAccessibilityService? = null
            private set

        /** Whether the accessibility service is currently connected. */
        fun isConnected(): Boolean = connection.connected.value

        /** Emits whenever Android connects or disconnects the service. */
        val connectionChanges: StateFlow<Boolean> = connection.connected

        /** Reports a possible display tap that may still prove to be a swipe. */
        fun reportPossibleParticipantTouch(source: String) {
            instance?.handlePossibleParticipantTouch(source)
        }

        /** Disqualifies the pending tap when Caddie's own overlay detects a drag. */
        fun reportParticipantSwipe() {
            instance?.handleParticipantSwipe()
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected")
        instance = this
        touchRoutingJob?.cancel()
        touchRoutingJob = null
        nativeRunActive = false
        disablePreciseTouchRouting()
        val gateway = AndroidAccessibilityGateway(
            service = this,
            onSemanticClickDispatch = AgentActivityTracker::markSemanticClick,
        )
        executionGateway = gateway
        androidRuntimeProcess().connectAccessibility(
            connection = this,
            gateway = gateway,
        )
        registerLockScreenReceiver()
        touchRoutingJob = serviceScope.launch {
            androidRuntimeProcess().activeNativeRunChanges().collect { runId ->
                nativeRunActive = runId != null
                updatePreciseTouchRouting(latestLockSignal)
            }
        }
        connection.update(true)
    }

    private fun createPreciseTouchBridge(): AndroidTouchInteractionBridge =
        AndroidTouchInteractionBridge(
            service = this,
            captureTarget = { androidRuntimeProcess().activeNativeRunId() },
            refreshPausedTouch = interventionController::onHumanActivity,
            beginPotentialPause = interventionController::beginPotentialTouch,
            confirmHeldPause = interventionController::confirmPotentialTouch,
            cancelProvisionalPause = interventionController::cancelHumanTouch,
            delegateImmediately = {
                (application as CaddieApplication).confirmationGate.hasPendingConfirmation
            },
            onUnavailable = ::disablePreciseTouchRouting,
        )

    private fun registerLockScreenReceiver() {
        if (lockScreenReceiverRegistered) return
        registerReceiver(
            lockScreenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        lockScreenReceiverRegistered = true
    }

    private fun updatePreciseTouchRouting(signal: LockScreenSafetyPolicy.Signal) {
        latestLockSignal = signal
        val allowed = PreciseTouchRoutingPolicy.allows(
            nativeRunActive = nativeRunActive,
            signal = signal,
            keyguardLocked = keyguardManager.isKeyguardLocked,
        )
        if (!allowed) {
            disablePreciseTouchRouting()
            Log.i(TAG, "precise touch routing disabled while idle or locked")
            return
        }
        if (preciseTouchBridge == null) {
            if (!setTouchExplorationRequested(true)) return
            val bridge = createPreciseTouchBridge()
            if (bridge.register()) {
                preciseTouchBridge = bridge
                Log.i(TAG, "precise touch routing registered=true")
            } else {
                bridge.close()
                setTouchExplorationRequested(false)
                Log.w(TAG, "precise touch routing registration failed; normal input restored")
            }
        }
    }

    private fun disablePreciseTouchRouting() {
        val bridge = preciseTouchBridge
        preciseTouchBridge = null
        bridge?.close()
        setTouchExplorationRequested(false)
    }

    private fun setTouchExplorationRequested(enabled: Boolean): Boolean {
        val current = serviceInfo ?: run {
            Log.w(TAG, "touch exploration update skipped without service info")
            return false
        }
        val requestedFlag = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        val updatedFlags = if (enabled) {
            current.flags or requestedFlag
        } else {
            current.flags and requestedFlag.inv()
        }
        if (updatedFlags == current.flags) return true
        return runCatching {
            current.flags = updatedFlags
            serviceInfo = current
        }.onFailure {
            Log.e(TAG, "touch exploration update failed", it)
        }.isSuccess
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            executionGateway?.onAccessibilityEvent(it.eventType)
            when (it.eventType) {
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START ->
                    handlePossibleParticipantTouch("accessibility:${it.eventType}")
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                -> {
                    val agentClick = AgentActivityTracker.consumeExpectedAgentClick()
                    if (preciseTouchBridge?.isRegistered != true && !agentClick) {
                        touchArbiter.onConfirmedTouch("accessibility:${it.eventType}")
                    }
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    if (
                        preciseTouchBridge?.isRegistered != true &&
                        touchArbiter.onScrollObserved()
                    ) {
                        Log.i(TAG, "participant gesture classified as scroll; native pause skipped")
                    }
                }
            }
        }
    }

    private fun handlePossibleParticipantTouch(source: String) {
        if (preciseTouchBridge?.isRegistered == true) return
        if (touchArbiter.onTouchCandidate(source)) {
            Log.d(TAG, "participant touch candidate source=$source")
        }
    }

    private fun handleParticipantSwipe() {
        if (touchArbiter.onScrollObserved()) {
            Log.i(TAG, "participant overlay gesture classified as swipe; native pause skipped")
        }
    }

    private fun handleParticipantTouch(runId: RunId, source: String): Boolean {
        val accepted = interventionController.onHumanTouch(runId)
        Log.i(TAG, "participant touch source=$source, native pause=$accepted")
        return accepted
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy")
        if (instance === this) {
            instance = null
            connection.update(false)
        }
        executionGateway = null
        touchRoutingJob?.cancel()
        touchRoutingJob = null
        nativeRunActive = false
        disablePreciseTouchRouting()
        if (lockScreenReceiverRegistered) {
            unregisterReceiver(lockScreenReceiver)
            lockScreenReceiverRegistered = false
        }
        if (touchArbiterDelegate.isInitialized()) touchArbiter.close()
        interventionController.close()
        androidRuntimeProcess().disconnectAccessibility(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}

/** Observable connection state shared by the service and setup UI. */
internal class AccessibilityConnectionState {
    private val mutableConnected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    fun update(connected: Boolean) {
        mutableConnected.value = connected
    }
}
