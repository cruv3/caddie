package com.caddie.executor.accessibility

/** Validates a semantic target and delegates the resulting platform action. */
class SemanticActionExecutor(
    private val gateway: ExecutionGateway,
) : ActionPerformer {
    override suspend fun execute(
        target: SemanticTarget,
        action: RequestedAction,
    ): ActionOutcome = gateway.performSemantic(target, action)
}

internal fun RequestedAction.requiredUiAction(): UiAction =
    when (this) {
        is RequestedAction.OpenApp,
        is RequestedAction.OpenUrl,
        RequestedAction.Back,
        RequestedAction.DismissInputMethod,
        -> error("Device navigation does not target a semantic node")
        RequestedAction.Click -> UiAction.CLICK
        RequestedAction.LongClick -> UiAction.LONG_CLICK
        is RequestedAction.SetText -> UiAction.SET_TEXT
        is RequestedAction.SetChecked -> UiAction.CLICK
        is RequestedAction.Scroll ->
            if (forward) {
                UiAction.SCROLL_FORWARD
            } else {
                UiAction.SCROLL_BACKWARD
            }
    }
