package com.caddie.executor.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class FreshActionDispatchPolicyTest {
    private val target = SemanticTarget(text = "Send")

    @Test
    fun `eligible fresh snapshot invokes platform action exactly once`() {
        val targetNode = node(actions = setOf(UiAction.CLICK))
        var platformCalls = 0

        val result =
            FreshActionDispatchPolicy.performIfEligible(
                target = target,
                action = RequestedAction.Click,
                observation = snapshot(window(nodes = arrayOf(targetNode))),
                liveNodes = mapOf(targetNode.observationNodeId to "live"),
            ) {
                platformCalls++
                true
            }

        assertEquals(ActionOutcome.Accepted, result)
        assertEquals(1, platformCalls)
    }

    @Test
    fun `every target rejection maps to a typed outcome without platform action`() {
        val cases =
            listOf(
                invalidTargetCase() to ActionOutcome.InvalidTarget,
                missingTargetCase() to ActionOutcome.TargetMissing,
                ambiguousTargetCase() to ActionOutcome.TargetAmbiguous,
                invisibleTargetCase() to ActionOutcome.TargetNotVisible,
                disabledTargetCase() to ActionOutcome.TargetDisabled,
                blockedTargetCase() to ActionOutcome.BlockedByWindow,
                unavailableActionCase() to ActionOutcome.ActionUnavailable,
                incompleteSnapshotCase() to ActionOutcome.AccessibilityUnavailable,
            )
        var platformCalls = 0

        cases.forEach { (testCase, expected) ->
            assertEquals(
                expected,
                FreshActionDispatchPolicy.performIfEligible(
                    target = testCase.target,
                    action = RequestedAction.Click,
                    observation = testCase.observation,
                    liveNodes = testCase.liveNodes,
                ) {
                    platformCalls++
                    true
                },
            )
        }

        assertEquals(0, platformCalls)
    }

    @Test
    fun `missing live node is stale and performs nothing`() {
        val targetNode = node(actions = setOf(UiAction.CLICK))
        var platformCalls = 0

        val result =
            FreshActionDispatchPolicy.performIfEligible(
                target = target,
                action = RequestedAction.Click,
                observation = snapshot(window(nodes = arrayOf(targetNode))),
                liveNodes = emptyMap<String, String>(),
            ) {
                platformCalls++
                true
            }

        assertEquals(ActionOutcome.StaleObservation, result)
        assertEquals(0, platformCalls)
    }

    @Test
    fun `matching checked state is already satisfied`() {
        val targetNode =
            node(
                checked = true,
                checkable = true,
                actions = setOf(UiAction.CLICK),
            )
        var platformCalls = 0

        val result =
            FreshActionDispatchPolicy.performIfEligible(
                target = target,
                action = RequestedAction.SetChecked(true),
                observation = snapshot(window(nodes = arrayOf(targetNode))),
                liveNodes = mapOf(targetNode.observationNodeId to "live"),
            ) {
                platformCalls++
                true
            }

        assertEquals(ActionOutcome.AlreadySatisfied, result)
        assertEquals(0, platformCalls)
    }

    @Test
    fun `set checked rejects a non-checkable target`() {
        val targetNode =
            node(
                checkable = false,
                checked = false,
                actions = setOf(UiAction.CLICK),
            )
        var platformCalls = 0

        val result =
            FreshActionDispatchPolicy.performIfEligible(
                target = target,
                action = RequestedAction.SetChecked(true),
                observation = snapshot(window(nodes = arrayOf(targetNode))),
                liveNodes = mapOf(targetNode.observationNodeId to "live"),
            ) {
                platformCalls++
                true
            }

        assertEquals(ActionOutcome.ActionUnavailable, result)
        assertEquals(0, platformCalls)
    }

    @Test
    fun `matching text is already satisfied only without submit`() {
        val targetNode =
            node(
                text = "hello",
                editable = true,
                actions = setOf(UiAction.SET_TEXT),
            )
        var platformCalls = 0
        val observation = snapshot(window(nodes = arrayOf(targetNode)))
        val liveNodes = mapOf(targetNode.observationNodeId to "live")

        assertEquals(
            ActionOutcome.AlreadySatisfied,
            FreshActionDispatchPolicy.performIfEligible(
                target = SemanticTarget(text = "hello"),
                action = RequestedAction.SetText("hello"),
                observation = observation,
                liveNodes = liveNodes,
            ) {
                platformCalls++
                true
            },
        )
        assertEquals(
            ActionOutcome.Accepted,
            FreshActionDispatchPolicy.performIfEligible(
                target = SemanticTarget(text = "hello"),
                action = RequestedAction.SetText("hello", submit = true),
                observation = observation,
                liveNodes = liveNodes,
            ) {
                platformCalls++
                true
            },
        )
        assertEquals(1, platformCalls)
    }

    @Test
    fun `rejected platform action is typed and attempted once`() {
        val targetNode = node(actions = setOf(UiAction.LONG_CLICK))
        var platformCalls = 0

        val result =
            FreshActionDispatchPolicy.performIfEligible(
                target = target,
                action = RequestedAction.LongClick,
                observation = snapshot(window(nodes = arrayOf(targetNode))),
                liveNodes = mapOf(targetNode.observationNodeId to "live"),
            ) {
                platformCalls++
                false
            }

        assertEquals(ActionOutcome.ActionRejected, result)
        assertEquals(1, platformCalls)
    }

    private fun invalidTargetCase() =
        case(
            target = SemanticTarget(),
            node = node(actions = setOf(UiAction.CLICK)),
        )

    private fun missingTargetCase() =
        case(
            target = SemanticTarget(text = "Missing"),
            node = node(actions = setOf(UiAction.CLICK)),
        )

    private fun ambiguousTargetCase(): DispatchCase {
        val first = node(id = "1:0", actions = setOf(UiAction.CLICK))
        val second = node(id = "1:1", actions = setOf(UiAction.CLICK))
        return DispatchCase(
            target = target,
            observation = snapshot(window(nodes = arrayOf(first, second))),
            liveNodes =
                mapOf(
                    first.observationNodeId to "first",
                    second.observationNodeId to "second",
                ),
        )
    }

    private fun invisibleTargetCase() =
        case(node = node(visible = false, actions = setOf(UiAction.CLICK)))

    private fun disabledTargetCase() =
        case(node = node(enabled = false, actions = setOf(UiAction.CLICK)))

    private fun unavailableActionCase() = case(node = node(actions = emptySet()))

    private fun incompleteSnapshotCase() =
        case(
            node = node(actions = setOf(UiAction.CLICK)),
            completeness = SnapshotCompleteness.ACTIVE_ROOT_ONLY,
        )

    private fun blockedTargetCase(): DispatchCase {
        val targetNode =
            node(
                id = "1:0",
                windowId = 1,
                actions = setOf(UiAction.CLICK),
            )
        return DispatchCase(
            target = target,
            observation =
                snapshot(
                    window(
                        id = 1,
                        layer = 1,
                        nodes = arrayOf(targetNode),
                    ),
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
                    ),
                ),
            liveNodes = mapOf(targetNode.observationNodeId to "live"),
        )
    }

    private fun case(
        target: SemanticTarget = this.target,
        node: UiNode,
        completeness: SnapshotCompleteness =
            SnapshotCompleteness.ALL_INTERACTIVE_WINDOWS,
    ) = DispatchCase(
        target = target,
        observation =
            snapshot(
                window(nodes = arrayOf(node)),
                completeness = completeness,
            ),
        liveNodes = mapOf(node.observationNodeId to "live"),
    )

    private data class DispatchCase(
        val target: SemanticTarget,
        val observation: UiObservation,
        val liveNodes: Map<String, String>,
    )
}
