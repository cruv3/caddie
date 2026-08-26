package com.caddie.study

/**
 * Dispatcher for native study confirmations.
 *
 * Acts as the bridge between [NativeStudyGate] (study logic) and
 * the overlay UI (Android presentation layer).
 *
 * The overlay service registers its enqueue function via [setConfirmationHandler].
 * [NativeStudyGate] calls [dispatch] which forwards to the registered handler.
 *
 * Usage:
 * ```
 * // In OverlayService onCreate:
 * StudyGateDispatcher.setConfirmationHandler { pending ->
 *     // show in overlay, make overlay touchable
 * }
 *
 * // In composition:
 * val gate = NativeStudyGate(
 *     onConfirmationRequested = StudyGateDispatcher::dispatch,
 * )
 * ```
 */
object StudyGateDispatcher {

    @Volatile
    private var handler: ((PendingConfirmation) -> Unit)? = null
    private var owner: Any? = null

    /**
     * Register the confirmation handler.
     *
     * Called once during app startup (typically from OverlayService).
     * The handler must show the confirmation in the UI and make
     * the overlay touchable.
     *
     * @param handler Function to call when a new confirmation is pending.
     * @throws IllegalStateException if a handler is already registered.
     */
    @Synchronized
    fun setConfirmationHandler(owner: Any, handler: (PendingConfirmation) -> Unit) {
        this.owner = owner
        this.handler = handler
    }

    /** Removes a service-owned handler without clearing a newer service instance. */
    @Synchronized
    fun clearConfirmationHandler(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        handler = null
    }

    /**
     * Dispatch a pending confirmation to the registered handler.
     *
     * @throws IllegalStateException if no handler is registered.
     */
    fun dispatch(pending: PendingConfirmation) {
        handler?.invoke(pending)
            ?: throw IllegalStateException("No confirmation handler registered")
    }

    /** Check if a handler is registered. */
    fun isConfigured(): Boolean = handler != null
}
