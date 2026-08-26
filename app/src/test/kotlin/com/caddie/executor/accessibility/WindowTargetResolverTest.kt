package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowTargetResolverTest {
    @Test
    fun `visible label resolves its nearest clickable parent`() {
        val row = node(
            id = "1:row",
            resourceId = "com.caddie.studytelegram:id/chat_anna",
            text = null,
            actions = setOf(UiAction.CLICK),
        )
        val label = node(
            id = "1:label",
            parentObservationNodeId = row.observationNodeId,
            text = "Anna",
        )

        assertEquals(
            TargetResolution.Found(row),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Anna"),
                UiAction.CLICK,
                snapshot(window(nodes = arrayOf(row, label))),
            ),
        )
    }

    @Test
    fun `label never promotes to an ancestor outside its bounds`() {
        val unrelated = node(
            id = "1:row",
            text = null,
            bounds = UiBounds(0, 0, 100, 100),
            actions = setOf(UiAction.CLICK),
        )
        val label = node(
            id = "1:label",
            parentObservationNodeId = unrelated.observationNodeId,
            text = "Anna",
            bounds = UiBounds(200, 200, 300, 300),
        )

        assertEquals(
            TargetResolution.ActionUnavailable,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Anna"),
                UiAction.CLICK,
                snapshot(window(nodes = arrayOf(unrelated, label))),
            ),
        )
    }


    @Test
    fun `dialog barrier blocks matching application node below`() {
        val app =
            window(
                id = 1,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            windowId = 1,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val dialog =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.SYSTEM,
                nodes =
                    arrayOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Allow",
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )

        assertEquals(
            TargetResolution.BlockedByWindow(blockingWindowId = 2),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, dialog),
            ),
        )
    }

    @Test
    fun `invisible exact match is rejected`() {
        val hidden =
            node(
                visible = false,
                actions = setOf(UiAction.CLICK),
            )

        assertEquals(
            TargetResolution.NotVisible,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(window(nodes = arrayOf(hidden))),
            ),
        )
    }

    @Test
    fun `ime permits set text on focused editable application node`() {
        val field =
            node(
                editable = true,
                focused = true,
                actions = setOf(UiAction.SET_TEXT),
            )
        val app = window(id = 1, layer = 1, nodes = arrayOf(field))
        val ime =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.INPUT_METHOD,
                nodes =
                    arrayOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Keyboard",
                        ),
                    ),
            )

        assertTrue(
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.SET_TEXT,
                snapshot(app, ime),
            ) is TargetResolution.Found,
        )
    }

    @Test
    fun `ime permits enter on focused editable application node`() {
        val field =
            node(
                editable = true,
                focused = true,
                actions = setOf(UiAction.IME_ENTER),
            )
        val app = window(id = 1, layer = 1, nodes = arrayOf(field))
        val ime =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.INPUT_METHOD,
                nodes =
                    arrayOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Keyboard",
                        ),
                    ),
            )

        assertEquals(
            TargetResolution.Found(field),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.IME_ENTER,
                snapshot(app, ime),
            ),
        )
    }

    @Test
    fun `two eligible visible actionable matches are ambiguous`() {
        val app =
            window(
                nodes =
                    arrayOf(
                        node(id = "1:0", actions = setOf(UiAction.CLICK)),
                        node(id = "1:1", actions = setOf(UiAction.CLICK)),
                    ),
            )

        assertEquals(
            TargetResolution.Ambiguous(count = 2),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app),
            ),
        )
    }

    @Test
    fun `one eligible and one blocked match resolves the eligible node`() {
        val lowerMatch =
            node(
                id = "1:0",
                windowId = 1,
                actions = setOf(UiAction.CLICK),
            )
        val eligibleMatch =
            node(
                id = "2:0",
                windowId = 2,
                actions = setOf(UiAction.CLICK),
            )
        val app =
            window(
                id = 1,
                layer = 1,
                nodes = arrayOf(lowerMatch),
            )
        val dialog =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.SYSTEM,
                nodes = arrayOf(eligibleMatch),
            )

        assertEquals(
            TargetResolution.Found(eligibleMatch),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, dialog),
            ),
        )
    }

    @Test
    fun `equal-layer barrier does not block target`() {
        val target =
            node(
                id = "1:0",
                windowId = 1,
                actions = setOf(UiAction.CLICK),
            )
        val targetWindow =
            window(
                id = 1,
                layer = 2,
                nodes = arrayOf(target),
            )
        val equalLayerBarrier =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.SYSTEM,
                nodes =
                    arrayOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Dialog",
                        ),
                    ),
            )

        assertEquals(
            TargetResolution.Found(target),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(targetWindow, equalLayerBarrier),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate window identities are rejected before barrier resolution`() {
        val app =
            window(
                id = 4,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            id = "4:0",
                            windowId = 4,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val duplicateIdentityBarrier =
            window(
                id = 4,
                layer = 3,
                type = UiWindowType.SYSTEM,
                nodes =
                    arrayOf(
                        node(
                            id = "4:1",
                            windowId = 4,
                            text = "Allow",
                        ),
                    ),
            )

        snapshot(app, duplicateIdentityBarrier)
    }

    @Test
    fun `inactive non-interactive status overlay does not block`() {
        val app =
            window(
                id = 1,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            windowId = 1,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val statusOverlay =
            UiWindow(
                id = 2,
                type = UiWindowType.ACCESSIBILITY_OVERLAY,
                layer = 2,
                active = false,
                focused = false,
                interactionBarrier = false,
                nodes =
                    listOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Status",
                        ),
                    ),
            )

        assertTrue(
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, statusOverlay),
            ) is TargetResolution.Found,
        )
    }

    @Test
    fun `interactive Caddie accessibility overlay blocks application below`() {
        val app =
            window(
                id = 1,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            windowId = 1,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val oversightOverlay =
            window(
                id = 7,
                layer = 7,
                type = UiWindowType.ACCESSIBILITY_OVERLAY,
                nodes =
                    arrayOf(
                        node(
                            id = "7:0",
                            windowId = 7,
                            text = "Confirm",
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )

        assertEquals(
            TargetResolution.BlockedByWindow(blockingWindowId = 7),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, oversightOverlay),
            ),
        )
    }

    @Test
    fun `target inside highest barrier window remains eligible`() {
        val app =
            window(
                id = 1,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            windowId = 1,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val dialog =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.SYSTEM,
                nodes =
                    arrayOf(
                        node(
                            id = "2:0",
                            windowId = 2,
                            text = "Allow",
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )

        assertEquals(
            TargetResolution.Found(dialog.nodes.single()),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Allow"),
                UiAction.CLICK,
                snapshot(app, dialog),
            ),
        )
    }

    @Test
    fun `highest blocking window is reported`() {
        val app =
            window(
                id = 1,
                layer = 1,
                nodes =
                    arrayOf(
                        node(
                            windowId = 1,
                            actions = setOf(UiAction.CLICK),
                        ),
                    ),
            )
        val lowerBarrier =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.SYSTEM,
                nodes = arrayOf(node(id = "2:0", windowId = 2, text = "Dialog")),
            )
        val higherBarrier =
            window(
                id = 3,
                layer = 3,
                type = UiWindowType.ACCESSIBILITY_OVERLAY,
                nodes = arrayOf(node(id = "3:0", windowId = 3, text = "Confirm")),
            )

        assertEquals(
            TargetResolution.BlockedByWindow(blockingWindowId = 3),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, lowerBarrier, higherBarrier),
            ),
        )
    }

    @Test
    fun `active-root-only observation is incomplete`() {
        assertEquals(
            TargetResolution.IncompleteSnapshot,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(
                    window(
                        nodes =
                            arrayOf(
                                node(actions = setOf(UiAction.CLICK)),
                            ),
                    ),
                    completeness = SnapshotCompleteness.ACTIVE_ROOT_ONLY,
                ),
            ),
        )
    }

    @Test
    fun `partial interactive windows observation prevents resolution`() {
        assertEquals(
            TargetResolution.IncompleteSnapshot,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(
                    window(
                        nodes =
                            arrayOf(
                                node(actions = setOf(UiAction.CLICK)),
                            ),
                    ),
                    completeness =
                        SnapshotCompleteness.PARTIAL_INTERACTIVE_WINDOWS,
                ),
            ),
        )
    }

    @Test
    fun `semantic identity is required`() {
        assertEquals(
            TargetResolution.InvalidTarget,
            WindowTargetResolver.resolve(
                SemanticTarget(),
                UiAction.CLICK,
                snapshot(window()),
            ),
        )
    }

    @Test
    fun `missing semantic match is rejected`() {
        assertEquals(
            TargetResolution.Missing,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Missing"),
                UiAction.CLICK,
                snapshot(window(nodes = arrayOf(node()))),
            ),
        )
    }

    @Test
    fun `disabled exact match is rejected`() {
        assertEquals(
            TargetResolution.Disabled,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(
                    window(
                        nodes =
                            arrayOf(
                                node(
                                    enabled = false,
                                    actions = setOf(UiAction.CLICK),
                                ),
                            ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `missing required semantic action is rejected`() {
        assertEquals(
            TargetResolution.ActionUnavailable,
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(
                    window(
                        nodes =
                            arrayOf(
                                node(actions = setOf(UiAction.LONG_CLICK)),
                            ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `ime does not permit click through to focused editable node`() {
        val field =
            node(
                editable = true,
                focused = true,
                actions = setOf(UiAction.CLICK),
            )
        val app = window(id = 1, layer = 1, nodes = arrayOf(field))
        val ime =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.INPUT_METHOD,
                nodes = arrayOf(node(id = "2:0", windowId = 2, text = "Keyboard")),
            )

        assertEquals(
            TargetResolution.BlockedByWindow(blockingWindowId = 2),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.CLICK,
                snapshot(app, ime),
            ),
        )
    }

    @Test
    fun `ime exception requires a focused editable candidate`() {
        val unfocusedField =
            node(
                editable = true,
                focused = false,
                actions = setOf(UiAction.SET_TEXT),
            )
        val app = window(id = 1, layer = 1, nodes = arrayOf(unfocusedField))
        val ime =
            window(
                id = 2,
                layer = 2,
                type = UiWindowType.INPUT_METHOD,
                nodes = arrayOf(node(id = "2:0", windowId = 2, text = "Keyboard")),
            )

        assertEquals(
            TargetResolution.BlockedByWindow(blockingWindowId = 2),
            WindowTargetResolver.resolve(
                SemanticTarget(text = "Send"),
                UiAction.SET_TEXT,
                snapshot(app, ime),
            ),
        )
    }
}
