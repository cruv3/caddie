package com.caddie.executor.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds the only Android intents exposed to the native agent tool surface. */
internal object AndroidNavigationIntentFactory {
    fun openApp(
        packageOrComponent: String,
        resolveLauncher: (String) -> Intent? = { null },
    ): Intent =
        (if ('/' in packageOrComponent) {
            Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName.unflattenFromString(packageOrComponent),
            )
        } else {
            resolveLauncher(packageOrComponent)
                ?: Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageOrComponent)
        }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openUrl(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Finds the live Accessibility node that matches an observed semantic node. */
internal object LiveNodeMatcher {
    fun resolve(
        target: SemanticTarget,
        nodes: List<UiNode>,
    ): Resolution = NodeResolver.resolve(target, nodes)
}

/** Executes semantic actions against the currently connected Accessibility service. */
class AndroidAccessibilityGateway(
    private val service: AccessibilityService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val snapshotSource: AndroidWindowSnapshotSource =
        AndroidWindowSnapshotSource(service),
    private val windowTransitions: AccessibilityWindowTransitionGate =
        AccessibilityWindowTransitionGate(),
    private val onSemanticClickDispatch: () -> Unit = {},
) : ExecutionGateway {
    override suspend fun observe(): UiObservation {
        windowTransitions.awaitExpectedWindow()
        return withContext(dispatcher) {
            snapshotSource.observe()
        }
    }

    override fun resolveDestinationPackages(action: RequestedAction): Set<String> =
        when (action) {
            is RequestedAction.OpenApp -> setOf(action.packageOrComponent.substringBefore('/'))
            is RequestedAction.OpenUrl -> resolvePackages(
                AndroidNavigationIntentFactory.openUrl(action.url),
            )
            else -> emptySet()
        }

    override fun verifyNavigation(
        action: RequestedAction,
        observation: UiObservation,
    ): VerificationDecision? =
        verifyObservedNavigation(
            action = action,
            callerPackage = service.packageName,
            observation = observation,
        )

    fun onAccessibilityEvent(eventType: Int) {
        if (
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            windowTransitions.notifyWindowChanged()
        }
    }

    override suspend fun performSemantic(
        target: SemanticTarget,
        action: RequestedAction,
    ): ActionOutcome =
        withContext(dispatcher) {
            if (action is RequestedAction.OpenApp) {
                return@withContext startActivity(
                    AndroidNavigationIntentFactory.openApp(action.packageOrComponent) {
                        service.packageManager.getLaunchIntentForPackage(it)
                    },
                )
            }
            if (action is RequestedAction.OpenUrl) {
                return@withContext startActivity(
                    AndroidNavigationIntentFactory.openUrl(action.url),
                )
            }
            if (action == RequestedAction.Back) {
                return@withContext if (
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                ) {
                    ActionOutcome.Accepted
                } else {
                    ActionOutcome.ActionRejected
                }
            }
            if (action == RequestedAction.DismissInputMethod) {
                return@withContext withFreshLiveSnapshot { observation, _ ->
                    if (!InputMethodBackPolicy.shouldPerformGlobalBack(observation)) {
                        ActionOutcome.Accepted
                    } else if (
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    ) {
                        ActionOutcome.Accepted
                    } else {
                        ActionOutcome.ActionRejected
                    }
                }
            }
            withFreshLiveSnapshot { observation, liveNodes ->
                FreshActionDispatchPolicy.performIfEligible(
                    target = target,
                    action = action,
                    observation = observation,
                    liveNodes = liveNodes,
                ) { liveNode ->
                    performAction(liveNode, action)
                }
            }
        }

    private fun startActivity(intent: Intent): ActionOutcome =
        try {
            windowTransitions.expectNextWindow()
            service.startActivity(intent)
            ActionOutcome.Accepted
        } catch (_: ActivityNotFoundException) {
            windowTransitions.clearExpectation()
            ActionOutcome.ActionUnavailable
        } catch (_: SecurityException) {
            windowTransitions.clearExpectation()
            ActionOutcome.ActionRejected
        }

    @Suppress("DEPRECATION")
    private fun resolvePackages(intent: Intent): Set<String> {
        val resolved = service.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        val candidates = service.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
        return navigationPackageCandidates(resolved, candidates)
    }

    internal fun <T> withFreshLiveSnapshot(
        block: (
            UiObservation,
            Map<String, AccessibilityNodeInfo>,
        ) -> T,
    ): T = snapshotSource.withFreshLiveSnapshot(block)

    private fun performAction(
        node: AccessibilityNodeInfo,
        action: RequestedAction,
    ): Boolean =
        SemanticPlatformDispatchPolicy.perform(action) { operation ->
            performPlatformOperation(node, operation)
        }

    private fun performPlatformOperation(
        node: AccessibilityNodeInfo,
        operation: PlatformOperation,
    ): Boolean =
        when (operation.action) {
            UiAction.CLICK -> {
                onSemanticClickDispatch()
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            UiAction.LONG_CLICK -> {
                onSemanticClickDispatch()
                node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
            UiAction.SET_TEXT ->
                node.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo
                                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            operation.text,
                        )
                    },
                )
            UiAction.IME_ENTER ->
                node.performAction(
                    AccessibilityNodeInfo
                        .AccessibilityAction
                        .ACTION_IME_ENTER
                        .id,
                )
            UiAction.SCROLL_FORWARD ->
                node.performAction(
                    AccessibilityNodeInfo
                        .AccessibilityAction
                        .ACTION_SCROLL_FORWARD
                        .id,
                )
            UiAction.SCROLL_BACKWARD ->
                node.performAction(
                    AccessibilityNodeInfo
                        .AccessibilityAction
                        .ACTION_SCROLL_BACKWARD
                        .id,
                )
            UiAction.SET_PROGRESS -> false
        }

}

internal fun navigationPackageCandidates(
    resolvedPackage: String?,
    candidatePackages: List<String>,
): Set<String> {
    val concrete = resolvedPackage?.takeIf { it.isNotBlank() && it != "android" }
    return if (concrete != null) {
        setOf(concrete)
    } else {
        candidatePackages.filter(String::isNotBlank).toSet()
    }
}

internal fun verifyObservedNavigation(
    action: RequestedAction,
    callerPackage: String,
    observation: UiObservation,
): VerificationDecision? {
    val applicationWindows = observation.windows
        .asSequence()
        .filter { it.type == UiWindowType.APPLICATION }
        .toList()
    val markedForeground = applicationWindows.filter { it.active || it.focused }
    val foregroundWindows = markedForeground.ifEmpty {
        applicationWindows.firstOrNull(::hasVisiblePackage)?.let(::listOf).orEmpty()
    }
    val activePackages = foregroundWindows.flatMap { window ->
        window.nodes
            .asSequence()
            .filter { it.enabled && it.visibleToUser }
            .mapNotNull(UiNode::packageName)
            .toList()
    }.toSet()
    val satisfied = when (action) {
        is RequestedAction.OpenApp ->
            action.packageOrComponent.substringBefore('/') in activePackages
        is RequestedAction.OpenUrl -> activePackages.any { it != callerPackage }
        else -> return null
    }
    return if (satisfied) VerificationDecision.Satisfied else VerificationDecision.Contradicted
}

private fun hasVisiblePackage(window: UiWindow): Boolean =
    window.nodes.any { node ->
        node.enabled && node.visibleToUser && !node.packageName.isNullOrBlank()
    }

/** Prevents a keyboard-dismiss step from accidentally navigating the app. */
internal object InputMethodBackPolicy {
    fun shouldPerformGlobalBack(observation: UiObservation): Boolean =
        observation.windows.any { it.type == UiWindowType.INPUT_METHOD }
}
