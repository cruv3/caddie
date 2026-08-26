package com.caddie.executor.accessibility

/** Resolves a semantic target to one unambiguous node in an observation. */
object NodeResolver {
    fun resolve(
        target: SemanticTarget,
        nodes: List<UiNode>,
    ): Resolution {
        if (!target.hasSemanticIdentity()) {
            return Resolution.InvalidTarget
        }

        val matchingNodes =
            nodes.filter { node ->
                node.enabled && matches(target, node)
            }

        return when (matchingNodes.size) {
            0 -> Resolution.Missing
            1 -> Resolution.Found(matchingNodes.single())
            else -> Resolution.Ambiguous(matchingNodes.size)
        }
    }

    internal fun matches(
        target: SemanticTarget,
        node: UiNode,
    ): Boolean =
        matches(target.packageName, node.packageName) &&
            matches(target.resourceId, node.resourceId) &&
            matchesAccessibleText(target.text, node) &&
            matches(
                target.contentDescription,
                node.contentDescription,
            ) &&
            matches(target.className, node.className) &&
            (target.focused == null || target.focused == node.focused)

    private fun matches(
        expected: String?,
        actual: String?,
    ): Boolean = expected == null || expected == actual

    private fun matchesAccessibleText(expected: String?, node: UiNode): Boolean =
        expected == null ||
            expected.equals(node.text, ignoreCase = true) ||
            expected.equals(node.contentDescription, ignoreCase = true)
}
