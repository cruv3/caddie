package com.caddie.executor.accessibility

import kotlinx.coroutines.CancellationException

/** Contains the platform action and arguments derived from a semantic request. */
internal data class PlatformOperation(
    val action: UiAction,
    val text: String? = null,
)

/** Maps supported semantic requests to Accessibility platform operations. */
internal object SemanticPlatformDispatchPolicy {
    fun perform(
        requested: RequestedAction,
        dispatch: (PlatformOperation) -> Boolean,
    ): Boolean =
        when (requested) {
            is RequestedAction.OpenApp,
            is RequestedAction.OpenUrl,
            RequestedAction.Back,
            RequestedAction.DismissInputMethod,
            -> error("Device navigation is dispatched by the gateway")
            RequestedAction.Click ->
                dispatch(PlatformOperation(UiAction.CLICK))
            RequestedAction.LongClick ->
                dispatch(PlatformOperation(UiAction.LONG_CLICK))
            is RequestedAction.SetText ->
                performSetText(requested, dispatch)
            is RequestedAction.SetChecked ->
                dispatch(PlatformOperation(UiAction.CLICK))
            is RequestedAction.Scroll ->
                dispatch(
                    PlatformOperation(
                        if (requested.forward) {
                            UiAction.SCROLL_FORWARD
                        } else {
                            UiAction.SCROLL_BACKWARD
                        },
                    ),
                )
        }

    private fun performSetText(
        requested: RequestedAction.SetText,
        dispatch: (PlatformOperation) -> Boolean,
    ): Boolean {
        val setTextAccepted =
            dispatch(
                PlatformOperation(
                    action = UiAction.SET_TEXT,
                    text = requested.text,
                ),
            )
        if (setTextAccepted && requested.submit) {
            try {
                dispatch(PlatformOperation(UiAction.IME_ENTER))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Set-text was already accepted and may have changed the UI.
                // A failed follow-up Enter cannot make the action safe to retry.
            }
        }

        return setTextAccepted
    }
}
