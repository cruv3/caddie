package com.caddie.executor.accessibility

/** Stores the screen-space rectangle occupied by a UI element. */
data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left) { "UiBounds right must be greater than left" }
        require(bottom > top) { "UiBounds bottom must be greater than top" }
    }
}

/** Identifies the Android category of an observed window. */
enum class UiWindowType {
    APPLICATION,
    INPUT_METHOD,
    SYSTEM,
    ACCESSIBILITY_OVERLAY,
    UNKNOWN,
}

/** Indicates whether an observation contains all expected window content. */
enum class SnapshotCompleteness {
    ALL_INTERACTIVE_WINDOWS,
    PARTIAL_INTERACTIVE_WINDOWS,
    ACTIVE_ROOT_ONLY,
    UNAVAILABLE,
}

/** Lists semantic operations exposed by an observed UI node. */
enum class UiAction {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    IME_ENTER,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    SET_PROGRESS,
}

/** Describes the minimum, maximum, and current value of a ranged control. */
data class UiRange(
    val type: Int,
    val min: Float,
    val max: Float,
    val current: Float,
)

/** Contains the immutable semantic properties of one observed UI node. */
data class UiNode(
    val observationNodeId: String,
    val windowId: Int,
    val parentObservationNodeId: String? = null,
    val packageName: String? = null,
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val enabled: Boolean = false,
    val visibleToUser: Boolean = false,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val editable: Boolean = false,
    val focused: Boolean = false,
    val scrollable: Boolean = false,
    val password: Boolean = false,
    val bounds: UiBounds? = null,
    val range: UiRange? = null,
    val actions: Set<UiAction> = emptySet(),
)

/** Contains the metadata and flattened node list of one observed window. */
class UiWindow(
    val id: Int,
    val type: UiWindowType,
    val layer: Int,
    val active: Boolean,
    val focused: Boolean,
    val interactionBarrier: Boolean,
    val bounds: UiBounds? = null,
    nodes: List<UiNode>,
) {
    val nodes: List<UiNode> =
        nodes.map { node ->
            node.copy(actions = node.actions.toSet())
        }

    init {
        require(this.nodes.all { it.windowId == id }) {
            "UiWindow nodes must belong to the same window"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is UiWindow &&
            id == other.id &&
            type == other.type &&
            layer == other.layer &&
            active == other.active &&
            focused == other.focused &&
            interactionBarrier == other.interactionBarrier &&
            bounds == other.bounds &&
            nodes == other.nodes

    override fun hashCode(): Int =
        listOf(
            id,
            type,
            layer,
            active,
            focused,
            interactionBarrier,
            bounds,
            nodes,
        ).hashCode()
}

/** Represents one immutable snapshot of all relevant Android UI windows. */
class UiObservation(
    val id: String,
    val capturedAtElapsedRealtimeMillis: Long,
    val completeness: SnapshotCompleteness,
    inputWindows: List<UiWindow>,
) {
    val windows: List<UiWindow> =
        inputWindows.sortedWith(
            compareByDescending<UiWindow>(UiWindow::layer)
                .thenBy(UiWindow::id),
        )
    val nodes: List<UiNode> = this.windows.flatMap(UiWindow::nodes)
    val accessibilityAvailable: Boolean
        get() = completeness != SnapshotCompleteness.UNAVAILABLE

    init {
        require(windows.map(UiWindow::id).distinct().size == windows.size) {
            "UiObservation window IDs must be unique"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is UiObservation &&
            id == other.id &&
            capturedAtElapsedRealtimeMillis ==
                other.capturedAtElapsedRealtimeMillis &&
            completeness == other.completeness &&
            windows == other.windows

    override fun hashCode(): Int =
        listOf(
            id,
            capturedAtElapsedRealtimeMillis,
            completeness,
            windows,
        ).hashCode()
}

/** Describes node state that must match before an action may execute. */
data class ExpectedNodeState(
    val checked: Boolean? = null,
    val selected: Boolean? = null,
    val text: String? = null,
)

/** Identifies a UI target using semantic properties from an observation. */
data class SemanticTarget(
    val packageName: String? = null,
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val focused: Boolean? = null,
    val expectedState: ExpectedNodeState? = null,
) {
    fun hasSemanticIdentity(): Boolean =
        listOf(
            packageName,
            resourceId,
            text,
            contentDescription,
            className,
        ).any { !it.isNullOrBlank() }
}

/** Describes the result of resolving a semantic target. */
sealed interface Resolution {
    /** Contains the single resolved node. */
    data class Found(val node: UiNode) : Resolution

    /** Reports that multiple nodes matched the target. */
    data class Ambiguous(val count: Int) : Resolution

    /** Reports that no node matched the target. */
    data object Missing : Resolution

    /** Reports that the supplied target was structurally invalid. */
    data object InvalidTarget : Resolution
}
