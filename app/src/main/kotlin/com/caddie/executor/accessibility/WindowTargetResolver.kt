package com.caddie.executor.accessibility

/** Describes whether a target is safe and available for interaction. */
sealed interface TargetResolution {
    /** Contains the single actionable node. */
    data class Found(val node: UiNode) : TargetResolution

    /** Reports multiple actionable matches. */
    data class Ambiguous(val count: Int) : TargetResolution

    /** Reports the window currently blocking the target. */
    data class BlockedByWindow(val blockingWindowId: Int) : TargetResolution

    /** Reports a target without usable semantic identity. */
    data object InvalidTarget : TargetResolution

    /** Reports that no node matched the target. */
    data object Missing : TargetResolution

    /** Reports that matching nodes are not visible. */
    data object NotVisible : TargetResolution

    /** Reports that matching nodes are disabled. */
    data object Disabled : TargetResolution

    /** Reports that matching nodes do not support the requested action. */
    data object ActionUnavailable : TargetResolution

    /** Reports that the observation lacks required window information. */
    data object IncompleteSnapshot : TargetResolution
}

/** Resolves a target while enforcing visibility, action, and window-barrier rules. */
object WindowTargetResolver {
    fun resolve(
        target: SemanticTarget,
        requiredAction: UiAction,
        observation: UiObservation,
    ): TargetResolution {
        if (!target.hasSemanticIdentity()) {
            return TargetResolution.InvalidTarget
        }
        if (observation.completeness != SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS) {
            return TargetResolution.IncompleteSnapshot
        }

        val semantic = observation.nodes.filter { NodeResolver.matches(target, it) }
        if (semantic.isEmpty()) {
            return TargetResolution.Missing
        }

        val enabled = semantic.filter(UiNode::enabled)
        if (enabled.isEmpty()) {
            return TargetResolution.Disabled
        }

        val visible = enabled.filter(UiNode::visibleToUser)
        if (visible.isEmpty()) {
            return TargetResolution.NotVisible
        }

        val nodesById = observation.nodes.associateBy(UiNode::observationNodeId)
        val actionable = visible.mapNotNull { candidate ->
            when {
                requiredAction in candidate.actions -> candidate
                requiredAction == UiAction.CLICK || requiredAction == UiAction.LONG_CLICK ->
                    nearestActionableAncestor(candidate, requiredAction, nodesById)
                else -> null
            }
        }.distinctBy(UiNode::observationNodeId)
        if (actionable.isEmpty()) {
            return TargetResolution.ActionUnavailable
        }

        val eligible =
            actionable.filter { candidate ->
                blockingWindow(candidate, requiredAction, observation.windows) == null
            }
        if (eligible.isEmpty()) {
            val blocker =
                actionable
                    .mapNotNull { candidate ->
                        blockingWindow(candidate, requiredAction, observation.windows)
                    }
                    .maxBy(UiWindow::layer)
            return TargetResolution.BlockedByWindow(blocker.id)
        }

        return when (eligible.size) {
            1 -> TargetResolution.Found(eligible.single())
            else -> TargetResolution.Ambiguous(eligible.size)
        }
    }

    private fun nearestActionableAncestor(
        node: UiNode,
        requiredAction: UiAction,
        nodesById: Map<String, UiNode>,
    ): UiNode? {
        val visited = mutableSetOf(node.observationNodeId)
        var parentId = node.parentObservationNodeId
        while (parentId != null && visited.add(parentId)) {
            val parent = nodesById[parentId] ?: return null
            if (parent.windowId != node.windowId || !parent.enabled || !parent.visibleToUser) return null
            if (!contains(parent.bounds, node.bounds)) return null
            if (requiredAction in parent.actions) return parent
            parentId = parent.parentObservationNodeId
        }
        return null
    }

    private fun contains(parent: UiBounds?, child: UiBounds?): Boolean =
        parent != null && child != null &&
            parent.left <= child.left && parent.top <= child.top &&
            parent.right >= child.right && parent.bottom >= child.bottom

    private fun blockingWindow(
        candidate: UiNode,
        requiredAction: UiAction,
        windows: List<UiWindow>,
    ): UiWindow? {
        val candidateWindow = windows.first { it.id == candidate.windowId }

        return windows
            .asSequence()
            .filter { window ->
                window.layer > candidateWindow.layer &&
                    window.interactionBarrier
            }
            .filterNot { window ->
                window.type == UiWindowType.INPUT_METHOD &&
                    (
                        requiredAction == UiAction.SET_TEXT ||
                            requiredAction == UiAction.IME_ENTER
                    ) &&
                    candidate.editable &&
                    candidate.focused
            }
            .maxByOrNull(UiWindow::layer)
    }
}
