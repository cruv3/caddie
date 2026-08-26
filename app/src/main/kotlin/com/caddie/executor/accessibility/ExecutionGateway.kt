package com.caddie.executor.accessibility

/** Observes Android UI state and executes semantic actions against fresh nodes. */
interface ExecutionGateway {
    suspend fun observe(): UiObservation

    /** Returns every package Android may legitimately choose for this navigation. */
    fun resolveDestinationPackages(action: RequestedAction): Set<String> = emptySet()

    /** Verifies an Android navigation using platform-owned foreground-window evidence. */
    fun verifyNavigation(
        action: RequestedAction,
        observation: UiObservation,
    ): VerificationDecision? = null

    suspend fun performSemantic(
        target: SemanticTarget,
        action: RequestedAction,
    ): ActionOutcome
}

/** Describes a semantic action requested by the agent. */
sealed interface RequestedAction {
    /** Opens one installed Android package or explicit component. */
    data class OpenApp(val packageOrComponent: String) : RequestedAction

    /** Opens one validated HTTPS URL through Android's resolver. */
    data class OpenUrl(val url: String) : RequestedAction

    /** Requests Android's global back action without coordinate input. */
    data object Back : RequestedAction

    /** Dismisses the keyboard without navigating when no keyboard is visible. */
    data object DismissInputMethod : RequestedAction

    /** Requests a normal click. */
    data object Click : RequestedAction

    /** Requests a long click. */
    data object LongClick : RequestedAction

    /** Requests replacement of editable text. */
    data class SetText(
        val text: String,
        val submit: Boolean = false,
    ) : RequestedAction

    /** Requests a specific checked state. */
    data class SetChecked(
        val checked: Boolean,
    ) : RequestedAction

    /** Requests scrolling in the supplied direction. */
    data class Scroll(
        val forward: Boolean,
    ) : RequestedAction
}

/** Describes the result of attempting a semantic action. */
sealed interface ActionOutcome {
    /** Indicates that the platform accepted the action. */
    data object Accepted : ActionOutcome

    /** Indicates that the requested state was already present. */
    data object AlreadySatisfied : ActionOutcome

    /** Indicates that the semantic target was invalid. */
    data object InvalidTarget : ActionOutcome

    /** Indicates that no matching target exists. */
    data object TargetMissing : ActionOutcome

    /** Indicates that multiple targets matched. */
    data object TargetAmbiguous : ActionOutcome

    /** Indicates that the matching target is not visible. */
    data object TargetNotVisible : ActionOutcome

    /** Indicates that the matching target is disabled. */
    data object TargetDisabled : ActionOutcome

    /** Indicates that another window blocks interaction. */
    data object BlockedByWindow : ActionOutcome

    /** Indicates that the target does not support the requested action. */
    data object ActionUnavailable : ActionOutcome

    /** Indicates that the platform rejected the action. */
    data object ActionRejected : ActionOutcome

    /** Indicates that the supplied observation is no longer current. */
    data object StaleObservation : ActionOutcome

    /** Indicates that no usable Accessibility connection exists. */
    data object AccessibilityUnavailable : ActionOutcome
}
