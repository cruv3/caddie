package com.caddie.executor.accessibility

/** Detects higher-layer windows that prevent interaction with a target window. */
internal object WindowBarrierPolicy {
    fun isInteractionBarrier(
        type: UiWindowType,
        active: Boolean,
        focused: Boolean,
        nodes: List<UiNode>,
    ): Boolean {
        if (active || focused) {
            return true
        }
        if (type == UiWindowType.INPUT_METHOD) {
            return false
        }
        return nodes.any { node ->
            node.visibleToUser &&
                node.enabled &&
                node.actions.isNotEmpty()
        }
    }
}
