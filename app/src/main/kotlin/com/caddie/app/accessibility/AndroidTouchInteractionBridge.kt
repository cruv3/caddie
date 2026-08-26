package com.caddie.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.TouchInteractionController
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.caddie.agent.core.RunId

/** Routes physical taps to agent pause and delegates real gestures back to Android. */
class AndroidTouchInteractionBridge(
    private val service: AccessibilityService,
    private val captureTarget: () -> RunId?,
    private val refreshPausedTouch: (RunId) -> Boolean,
    private val beginPotentialPause: (RunId) -> Boolean,
    private val confirmHeldPause: (RunId) -> Boolean,
    private val cancelProvisionalPause: (RunId) -> Boolean,
    private val delegateImmediately: () -> Boolean = { false },
    private val onUnavailable: () -> Unit = {},
) : AutoCloseable {
    private val density = service.resources.displayMetrics.density
    private val router = NativeTouchGestureRouter<RunId>(
        // Maps and other gesture-heavy surfaces emit more finger jitter than buttons.
        touchSlopPx = maxOf(
            ViewConfiguration.get(service).scaledPagingTouchSlop.toFloat(),
            INTENTIONAL_SWIPE_DP * density,
        ),
        systemEdgeInsetPx = SYSTEM_EDGE_DP * density,
    )
    private var controller: TouchInteractionController? = null
    private var provisionalPauseTarget: RunId? = null

    val isRegistered: Boolean
        get() = controller != null

    private val callback = object : TouchInteractionController.Callback {
        override fun onMotionEvent(event: MotionEvent) {
            handleMotionEvent(event)
        }

        override fun onStateChanged(state: Int) {
            if (state == TouchInteractionController.STATE_CLEAR) {
                router.onCancel()
                cancelHeldPause()
            }
        }
    }

    fun register(): Boolean = runCatching {
        val candidate = service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
        // Keep the candidate before registration so a partial framework failure can be rolled back.
        controller = candidate
        candidate.registerCallback(service.mainExecutor, callback)
        true
    }.onFailure {
        close()
        Log.e(TAG, "precise touch routing unavailable", it)
    }.getOrDefault(false)

    override fun close() {
        val current = controller ?: return
        controller = null
        router.onCancel()
        cancelHeldPause()
        runCatching { current.unregisterCallback(callback) }
            .onFailure { Log.w(TAG, "could not unregister touch routing", it) }
    }

    private fun handleMotionEvent(event: MotionEvent) {
        val metrics = service.resources.displayMetrics
        val decision = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val target = captureTarget()
                val alreadyPaused = target?.let(refreshPausedTouch) == true
                val forcedDelegation = delegateImmediately()
                router.onDown(
                    activeTarget = target,
                    alreadyPaused = alreadyPaused,
                    x = event.x,
                    y = event.y,
                    displayWidth = metrics.widthPixels,
                    displayHeight = metrics.heightPixels,
                    delegateImmediately = forcedDelegation,
                ).also { result ->
                    Log.d(
                        TAG,
                        "down active=${target != null} paused=$alreadyPaused " +
                            "confirmation=$forcedDelegation decision=${result.logName()}",
                    )
                }
            }
            MotionEvent.ACTION_MOVE -> router.onMove(event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN -> router.onAdditionalPointer()
            MotionEvent.ACTION_UP -> router.onUp()
            MotionEvent.ACTION_CANCEL -> {
                router.onCancel()
                cancelHeldPause()
                NativeTouchGestureRouter.Decision.None
            }
            else -> NativeTouchGestureRouter.Decision.None
        }
        applyDecision(decision)
    }

    private fun applyDecision(decision: NativeTouchGestureRouter.Decision<RunId>) {
        when (decision) {
            NativeTouchGestureRouter.Decision.Delegate -> {
                cancelHeldPause()
                delegateInteraction()
            }
            is NativeTouchGestureRouter.Decision.Tap -> {
                if (provisionalPauseTarget == decision.target) {
                    provisionalPauseTarget = null
                    val accepted = confirmHeldPause(decision.target)
                    Log.i(TAG, "physical tap classified; native pause=$accepted")
                } else {
                    Log.w(TAG, "physical tap had no held native pause")
                }
            }
            is NativeTouchGestureRouter.Decision.Hold -> {
                val accepted = beginPotentialPause(decision.target)
                if (accepted) {
                    provisionalPauseTarget = decision.target
                    Log.d(TAG, "physical contact; silent native hold=true")
                } else {
                    router.onCancel()
                    delegateInteraction()
                }
            }
            NativeTouchGestureRouter.Decision.None,
            -> Unit
        }
    }

    private fun cancelHeldPause() {
        val target = provisionalPauseTarget ?: return
        provisionalPauseTarget = null
        val resumed = cancelProvisionalPause(target)
        Log.i(TAG, "physical gesture; provisional native pause cancelled=$resumed")
    }

    private fun delegateInteraction() {
        val current = controller ?: return
        runCatching { current.requestDelegating() }
            .onSuccess { Log.d(TAG, "physical gesture delegated") }
            .onFailure {
                Log.e(TAG, "physical gesture delegation failed", it)
                close()
                onUnavailable()
            }
    }

    private fun NativeTouchGestureRouter.Decision<RunId>.logName(): String =
        when (this) {
            NativeTouchGestureRouter.Decision.Delegate -> "delegate"
            is NativeTouchGestureRouter.Decision.Hold -> "hold"
            is NativeTouchGestureRouter.Decision.Tap -> "tap"
            NativeTouchGestureRouter.Decision.None -> "none"
        }

    private companion object {
        const val TAG = "CaddieTouch"
        const val SYSTEM_EDGE_DP = 32f
        const val INTENTIONAL_SWIPE_DP = 32f
    }
}
