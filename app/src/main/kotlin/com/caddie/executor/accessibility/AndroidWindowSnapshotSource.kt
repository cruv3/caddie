package com.caddie.executor.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Converts current Accessibility windows and nodes into an immutable UI observation. */
class AndroidWindowSnapshotSource
    @JvmOverloads
    constructor(
        private val service: AccessibilityService,
        observationId: (() -> String)? = null,
        private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    ) {
    private val observationSequence = AtomicLong()
    private val observationId: () -> String =
        observationId ?: {
            "accessibility-${observationSequence.incrementAndGet()}"
        }

    fun observe(): UiObservation =
        withFreshLiveSnapshot { observation, _ -> observation }

    internal fun <T> withFreshLiveSnapshot(
        block: (UiObservation, Map<String, AccessibilityNodeInfo>) -> T,
    ): T {
        val ownedWindows =
            service.windows
                .orEmpty()
                .toList()
        if (ownedWindows.isEmpty()) {
            return withActiveRootDiagnosticSnapshot(block)
        }

        val ownedNodes = mutableListOf<AccessibilityNodeInfo>()
        val activeRootCandidate by lazy(LazyThreadSafetyMode.NONE) {
            service.rootInActiveWindow
                ?.also { ownedNodes += it }
                ?.let { root ->
                    WindowRootCandidate(root.windowId, root)
                }
        }
        return try {
            val liveNodes = linkedMapOf<String, AccessibilityNodeInfo>()
            val windows =
                ownedWindows
                    .sortedWith(
                        compareByDescending<AccessibilityWindowInfo> { it.layer }
                            .thenBy { it.id },
                    )
                    .map { window ->
                        snapshotWindow(
                            window = window,
                            ownedNodes = ownedNodes,
                            liveNodes = liveNodes,
                            activeRoot = { activeRootCandidate },
                        )
                    }
            block(
                UiObservation(
                    id = observationId(),
                    capturedAtElapsedRealtimeMillis = elapsedRealtime(),
                    completeness = interactiveWindowCompleteness(windows),
                    inputWindows = windows,
                ),
                liveNodes,
            )
        } finally {
            ownedNodes.asReversed().forEach(::releaseNode)
            ownedWindows.asReversed().forEach(::releaseWindow)
        }
    }

    private fun <T> withActiveRootDiagnosticSnapshot(
        block: (UiObservation, Map<String, AccessibilityNodeInfo>) -> T,
    ): T {
        val root = service.rootInActiveWindow
        if (root == null) {
            return block(unavailableObservation(), emptyMap())
        }

        val ownedNodes = mutableListOf(root)
        return try {
            val liveNodes = linkedMapOf<String, AccessibilityNodeInfo>()
            val nodes =
                snapshotNodes(
                    windowId = root.windowId,
                    root = root,
                    ownedNodes = ownedNodes,
                    liveNodes = liveNodes,
                )
            block(
                UiObservation(
                    id = observationId(),
                    capturedAtElapsedRealtimeMillis = elapsedRealtime(),
                    completeness = SnapshotCompleteness.ACTIVE_ROOT_ONLY,
                    inputWindows =
                        listOf(
                            UiWindow(
                                id = root.windowId,
                                type = UiWindowType.APPLICATION,
                                layer = 0,
                                active = true,
                                focused = true,
                                interactionBarrier = false,
                                bounds = root.bounds(),
                                nodes = nodes,
                            ),
                        ),
                ),
                liveNodes,
            )
        } finally {
            ownedNodes.asReversed().forEach(::releaseNode)
        }
    }

    private fun unavailableObservation(): UiObservation =
        UiObservation(
            id = observationId(),
            capturedAtElapsedRealtimeMillis = elapsedRealtime(),
            completeness = SnapshotCompleteness.UNAVAILABLE,
            inputWindows = emptyList(),
        )

    private fun snapshotWindow(
        window: AccessibilityWindowInfo,
        ownedNodes: MutableList<AccessibilityNodeInfo>,
        liveNodes: MutableMap<String, AccessibilityNodeInfo>,
        activeRoot: () -> WindowRootCandidate<AccessibilityNodeInfo>?,
    ): UiWindow {
        val windowRoot = window.root
        if (windowRoot != null) {
            ownedNodes += windowRoot
        }
        val root =
            selectWindowRoot(
                windowId = window.id,
                windowRoot = windowRoot,
                activeRoot =
                    if (windowRoot == null && window.isActive) {
                        activeRoot()
                    } else {
                        null
                    },
            )
        val nodes =
            if (root == null) {
                emptyList()
            } else {
                snapshotNodes(
                    windowId = window.id,
                    root = root,
                    ownedNodes = ownedNodes,
                    liveNodes = liveNodes,
                )
            }
        val type = window.type.toUiWindowType()

        return UiWindow(
            id = window.id,
            type = type,
            layer = window.layer,
            active = window.isActive,
            focused = window.isFocused,
            interactionBarrier =
                WindowBarrierPolicy.isInteractionBarrier(
                    type = type,
                    active = window.isActive,
                    focused = window.isFocused,
                    nodes = nodes,
                ),
            bounds = window.bounds(),
            nodes = nodes,
        )
    }

    private fun snapshotNodes(
        windowId: Int,
        root: AccessibilityNodeInfo,
        ownedNodes: MutableList<AccessibilityNodeInfo>,
        liveNodes: MutableMap<String, AccessibilityNodeInfo>,
    ): List<UiNode> {
        val pending = ArrayDeque<PendingNode>()
        val descriptors = mutableListOf<UiNode>()
        pending += PendingNode(root, parentObservationNodeId = null)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val observationNodeId = "$windowId:${descriptors.size}"
            descriptors +=
                current.node.toDescriptor(
                    observationNodeId = observationNodeId,
                    parentObservationNodeId =
                        current.parentObservationNodeId,
                    windowId = windowId,
                )
            liveNodes[observationNodeId] = current.node

            repeat(current.node.childCount) { childIndex ->
                current.node.getChild(childIndex)?.let { child ->
                    ownedNodes += child
                    pending +=
                        PendingNode(
                            node = child,
                            parentObservationNodeId = observationNodeId,
                        )
                }
            }
        }

        return descriptors
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.toDescriptor(
        observationNodeId: String,
        parentObservationNodeId: String?,
        windowId: Int,
    ): UiNode {
        val rangeInfo = rangeInfo
        return UiNode(
            observationNodeId = observationNodeId,
            windowId = windowId,
            parentObservationNodeId = parentObservationNodeId,
            packageName = packageName?.toString(),
            resourceId = viewIdResourceName,
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            className = className?.toString(),
            enabled = isEnabled,
            visibleToUser = isVisibleToUser,
            clickable = isClickable,
            longClickable = isLongClickable,
            checkable = isCheckable,
            checked = isChecked,
            selected = isSelected,
            editable = isEditable,
            focused = isFocused,
            scrollable = isScrollable,
            password = isPassword,
            bounds = bounds(),
            range =
                rangeInfo?.let {
                    UiRange(
                        type = it.type,
                        min = it.min,
                        max = it.max,
                        current = it.current,
                    )
                },
            actions =
                actionList
                    .mapNotNull { action -> uiActionFor(action.id) }
                    .toSet(),
        )
    }

    private fun AccessibilityNodeInfo.bounds(): UiBounds? {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect.toUiBounds()
    }

    private fun AccessibilityWindowInfo.bounds(): UiBounds? {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect.toUiBounds()
    }

    private fun Rect.toUiBounds(): UiBounds? =
        if (right > left && bottom > top) {
            UiBounds(left, top, right, bottom)
        } else {
            null
        }

    private fun Int.toUiWindowType(): UiWindowType =
        when (this) {
            AccessibilityWindowInfo.TYPE_APPLICATION ->
                UiWindowType.APPLICATION
            AccessibilityWindowInfo.TYPE_INPUT_METHOD ->
                UiWindowType.INPUT_METHOD
            AccessibilityWindowInfo.TYPE_SYSTEM ->
                UiWindowType.SYSTEM
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ->
                UiWindowType.ACCESSIBILITY_OVERLAY
            else -> UiWindowType.UNKNOWN
        }

    private fun uiActionFor(actionId: Int): UiAction? =
        when (actionId) {
            AccessibilityNodeInfo.ACTION_CLICK ->
                UiAction.CLICK
            AccessibilityNodeInfo.ACTION_LONG_CLICK ->
                UiAction.LONG_CLICK
            AccessibilityNodeInfo.ACTION_SET_TEXT ->
                UiAction.SET_TEXT
            AccessibilityNodeInfo
                .AccessibilityAction
                .ACTION_IME_ENTER
                .id -> UiAction.IME_ENTER
            AccessibilityNodeInfo
                .AccessibilityAction
                .ACTION_SCROLL_FORWARD
                .id -> UiAction.SCROLL_FORWARD
            AccessibilityNodeInfo
                .AccessibilityAction
                .ACTION_SCROLL_BACKWARD
                .id -> UiAction.SCROLL_BACKWARD
            AccessibilityNodeInfo
                .AccessibilityAction
                .ACTION_SET_PROGRESS
                .id -> UiAction.SET_PROGRESS
            else -> null
        }

    @Suppress("DEPRECATION")
    private fun releaseNode(node: AccessibilityNodeInfo) {
        // API 33+ no longer pools nodes, but this remains the explicit
        // ownership boundary for older supported platform behavior.
        node.recycle()
    }

    @Suppress("DEPRECATION")
    private fun releaseWindow(window: AccessibilityWindowInfo) {
        // service.windows hands ownership of each returned window to this
        // source. Release exactly once and never touch it after this scope.
        window.recycle()
    }

/** Holds an Accessibility node while its observed parent relationship is resolved. */
private data class PendingNode(
        val node: AccessibilityNodeInfo,
        val parentObservationNodeId: String?,
    )
}

/** Associates a candidate root node with the window that supplied it. */
internal data class WindowRootCandidate<out T>(
    val windowId: Int,
    val root: T,
)

internal fun <T> selectWindowRoot(
    windowId: Int,
    windowRoot: T?,
    activeRoot: WindowRootCandidate<T>?,
): T? =
    windowRoot
        ?: activeRoot
            ?.takeIf { it.windowId == windowId }
            ?.root

internal fun interactiveWindowCompleteness(
    windows: List<UiWindow>,
): SnapshotCompleteness =
    if (
        windows.any { window ->
            window.nodes.isEmpty() &&
                (
                    window.type != UiWindowType.SYSTEM ||
                        window.active ||
                        window.focused ||
                        window.interactionBarrier
                )
        }
    ) {
        SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS
    } else {
        SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS
    }
