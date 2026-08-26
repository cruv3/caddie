package com.caddie.executor.accessibility

internal fun node(
    id: String = "1:0",
    windowId: Int = 1,
    parentObservationNodeId: String? = null,
    packageName: String? = null,
    resourceId: String? = null,
    text: String? = "Send",
    contentDescription: String? = null,
    className: String? = null,
    enabled: Boolean = true,
    visible: Boolean = true,
    actions: Set<UiAction> = emptySet(),
    bounds: UiBounds? = UiBounds(0, 0, 100, 100),
    editable: Boolean = false,
    focused: Boolean = false,
    checkable: Boolean = false,
    checked: Boolean = false,
) = UiNode(
    observationNodeId = id,
    windowId = windowId,
    parentObservationNodeId = parentObservationNodeId,
    packageName = packageName,
    resourceId = resourceId,
    text = text,
    contentDescription = contentDescription,
    className = className,
    enabled = enabled,
    visibleToUser = visible,
    actions = actions,
    bounds = bounds,
    editable = editable,
    focused = focused,
    checkable = checkable,
    checked = checked,
)

internal fun window(
    id: Int = 1,
    layer: Int = 1,
    type: UiWindowType = UiWindowType.APPLICATION,
    barrier: Boolean = true,
    vararg nodes: UiNode,
) = UiWindow(
    id = id,
    type = type,
    layer = layer,
    active = true,
    focused = true,
    interactionBarrier = barrier,
    nodes = nodes.toList(),
)

internal fun snapshot(
    vararg windows: UiWindow,
    completeness: SnapshotCompleteness =
        SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
) = UiObservation(
    id = "snapshot",
    capturedAtElapsedRealtimeMillis = 1L,
    completeness = completeness,
    inputWindows = windows.toList(),
)
