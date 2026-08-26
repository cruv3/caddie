package com.caddie.executor.accessibility

/** Rejects actions whose UI observation is older than the latest snapshot. */
internal object FreshActionDispatchPolicy {
    fun <T> performIfEligible(
        target: SemanticTarget,
        action: RequestedAction,
        observation: UiObservation,
        liveNodes: Map<String, T>,
        perform: (T) -> Boolean,
    ): ActionOutcome {
        val resolution =
            WindowTargetResolver.resolve(
                target = target,
                requiredAction = action.requiredUiAction(),
                observation = observation,
            )
        if (resolution !is TargetResolution.Found) {
            return resolution.toActionOutcome()
        }
        val liveNode =
            liveNodes[resolution.node.observationNodeId]
                ?: return ActionOutcome.StaleObservation
        if (action is RequestedAction.SetChecked && !resolution.node.checkable) {
            return ActionOutcome.ActionUnavailable
        }
        if (action.isAlreadySatisfiedBy(resolution.node)) {
            return ActionOutcome.AlreadySatisfied
        }

        return if (perform(liveNode)) {
            ActionOutcome.Accepted
        } else {
            ActionOutcome.ActionRejected
        }
    }

    private fun TargetResolution.toActionOutcome(): ActionOutcome =
        when (this) {
            TargetResolution.InvalidTarget ->
                ActionOutcome.InvalidTarget
            TargetResolution.Missing ->
                ActionOutcome.TargetMissing
            is TargetResolution.Ambiguous ->
                ActionOutcome.TargetAmbiguous
            TargetResolution.NotVisible ->
                ActionOutcome.TargetNotVisible
            TargetResolution.Disabled ->
                ActionOutcome.TargetDisabled
            is TargetResolution.BlockedByWindow ->
                ActionOutcome.BlockedByWindow
            TargetResolution.ActionUnavailable ->
                ActionOutcome.ActionUnavailable
            TargetResolution.IncompleteSnapshot ->
                ActionOutcome.AccessibilityUnavailable
            is TargetResolution.Found ->
                error("A found target must be dispatched")
        }

    private fun RequestedAction.isAlreadySatisfiedBy(node: UiNode): Boolean =
        when (this) {
            is RequestedAction.SetChecked -> node.checked == checked
            is RequestedAction.SetText ->
                !submit && node.text == text
            RequestedAction.Click,
            RequestedAction.LongClick,
            RequestedAction.Back,
            RequestedAction.DismissInputMethod,
            is RequestedAction.OpenApp,
            is RequestedAction.OpenUrl,
            is RequestedAction.Scroll,
            -> false
        }
}
