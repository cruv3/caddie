package com.caddie.app.accessibility

/** Classifies one held touch as a pause tap or a gesture that Android should delegate. */
class NativeTouchGestureRouter<T : Any>(
    private val touchSlopPx: Float,
    private val systemEdgeInsetPx: Float,
) {
    init {
        require(touchSlopPx > 0f) { "touch slop must be positive" }
        require(systemEdgeInsetPx >= 0f) { "system edge inset must not be negative" }
    }

    private var target: T? = null
    private var downX = 0f
    private var downY = 0f

    fun onDown(
        activeTarget: T?,
        alreadyPaused: Boolean,
        x: Float,
        y: Float,
        displayWidth: Int,
        displayHeight: Int,
        delegateImmediately: Boolean = false,
    ): Decision<T> {
        clear()
        if (
            activeTarget == null ||
            alreadyPaused ||
            delegateImmediately ||
            isSystemEdge(x, y, displayWidth, displayHeight)
        ) {
            return Decision.Delegate
        }
        target = activeTarget
        downX = x
        downY = y
        return Decision.Hold(activeTarget)
    }

    fun onMove(x: Float, y: Float): Decision<T> {
        if (target == null) return Decision.None
        val dx = x - downX
        val dy = y - downY
        if (dx * dx + dy * dy < touchSlopPx * touchSlopPx) return Decision.None
        clear()
        return Decision.Delegate
    }

    fun onAdditionalPointer(): Decision<T> {
        if (target == null) return Decision.None
        clear()
        return Decision.Delegate
    }

    fun onUp(): Decision<T> {
        val capturedTarget = target ?: return Decision.None
        clear()
        return Decision.Tap(capturedTarget)
    }

    fun onCancel() = clear()

    private fun isSystemEdge(
        x: Float,
        y: Float,
        displayWidth: Int,
        displayHeight: Int,
    ): Boolean =
        x <= systemEdgeInsetPx ||
            x >= displayWidth - systemEdgeInsetPx ||
            y <= systemEdgeInsetPx ||
            y >= displayHeight - systemEdgeInsetPx

    private fun clear() {
        target = null
        downX = 0f
        downY = 0f
    }

    sealed interface Decision<out T : Any> {
        data object None : Decision<Nothing>
        data object Delegate : Decision<Nothing>
        data class Hold<T : Any>(val target: T) : Decision<T>
        data class Tap<T : Any>(val target: T) : Decision<T>
    }
}
