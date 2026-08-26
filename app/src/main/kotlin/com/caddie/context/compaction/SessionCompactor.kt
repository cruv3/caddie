package com.caddie.context.compaction

import java.security.MessageDigest

/**
 * Summarizes only old, structurally complete turns while retaining every
 * safety-sensitive or uncertain session item verbatim.
 */
class SessionCompactor(
    private val safeToolNames: Set<String>,
    private val maxSummaryChars: Int,
) {
    init {
        require(maxSummaryChars > 0)
    }

    fun compact(original: List<ContextItem>): CompactionResult {
        validate(original)?.let { return CompactionResult.Rejected(original, it) }

        val turns = original
            .withIndex()
            .filter { it.value.turnId != null }
            .groupBy { requireNotNull(it.value.turnId) }
            .map { (id, indexed) -> Turn(id, indexed.minOf { it.index }, indexed.map { it.value }) }
            .sortedBy(Turn::firstIndex)
        val completeTurns = turns.filter(::isSafeCompleteTurn)
        val retainedTurnIds = completeTurns.takeLast(RETAINED_COMPLETE_TURNS).map(Turn::id).toSet()
        val compactedTurns = completeTurns.filterNot { it.id in retainedTurnIds }
        if (compactedTurns.isEmpty()) return CompactionResult.Compacted(original)

        val compactedIds = compactedTurns.flatMap { it.items }.map(ContextItem::id).toSet()
        val previousSummaries = original.filter { it.kind == ContextItemKind.SUMMARY }
        val retained = original.filterNot {
            it.id in compactedIds || it.kind == ContextItemKind.SUMMARY
        }.toMutableList()
        val sourceRefs = (
            previousSummaries.flatMap(ContextItem::sourceRefs) +
                compactedTurns.flatMap(Turn::items).map { ContextSourceRef(it.id, it.sha256) }
            ).distinctBy(ContextSourceRef::id)
        val summary = buildSummary(compactedTurns, sourceRefs)
        val firstRemovedIndex = original.indexOfFirst { it.id in compactedIds || it.kind == ContextItemKind.SUMMARY }
        val insertionIndex = original.take(firstRemovedIndex).count {
            it.id !in compactedIds && it.kind != ContextItemKind.SUMMARY
        }
        retained.add(insertionIndex, summary)

        val protectedIds = protectedIds(original, turns, retainedTurnIds)
        val retainedIds = retained.map(ContextItem::id).toSet()
        if (!retainedIds.containsAll(protectedIds)) {
            return CompactionResult.Rejected(original, "Protected context coverage failed")
        }
        val coveredIds = retainedIds + summary.sourceRefs.map(ContextSourceRef::id)
        if (!coveredIds.containsAll(compactedIds)) {
            return CompactionResult.Rejected(original, "Summary provenance coverage failed")
        }
        return CompactionResult.Compacted(retained.toList())
    }

    private fun protectedIds(
        original: List<ContextItem>,
        turns: List<Turn>,
        retainedTurnIds: Set<String>,
    ): Set<String> {
        val unsafeTurnIds = turns.filterNot(::isSafeCompleteTurn).map(Turn::id).toSet()
        return original.filter {
            it.kind in ALWAYS_PROTECTED ||
                it.turnId in unsafeTurnIds ||
                it.turnId in retainedTurnIds
        }.map(ContextItem::id).toSet()
    }

    private fun isSafeCompleteTurn(turn: Turn): Boolean {
        if (turn.items.none { it.kind == ContextItemKind.ASSISTANT }) return false
        val calls = turn.items.filter { it.kind == ContextItemKind.TOOL_CALL }
        val results = turn.items.filter { it.kind == ContextItemKind.TOOL_RESULT }
        if (calls.any { it.toolName !in safeToolNames || it.toolCallId.isNullOrBlank() }) return false
        if (
            results.any {
                it.toolCallId.isNullOrBlank() ||
                    it.toolName.isNullOrBlank() ||
                    it.outcomeCategory.isNullOrBlank() ||
                    it.verifiedTransition.isNullOrBlank()
            }
        ) {
            return false
        }
        val callIds = calls.map { it.toolCallId }
        val resultIds = results.map { it.toolCallId }
        val toolsByCall = calls.associate { it.toolCallId to it.toolName }
        return callIds.size == callIds.distinct().size &&
            resultIds.size == resultIds.distinct().size &&
            callIds.toSet() == resultIds.toSet() &&
            results.all { toolsByCall[it.toolCallId] == it.toolName }
    }

    private fun buildSummary(
        turns: List<Turn>,
        sources: List<ContextSourceRef>,
    ): ContextItem {
        val header = "Compacted ${turns.size} completed turns."
        val lines = turns.map { turn ->
            val outcomes = turn.items
                .filter { it.kind == ContextItemKind.TOOL_RESULT }
                .joinToString(",") {
                    "${safe(it.toolName)}:${safe(it.outcomeCategory)}:${safe(it.verifiedTransition)}"
                }
            if (outcomes.isEmpty()) "turn=${safe(turn.id)}; completed" else
                "turn=${safe(turn.id)}; tools=$outcomes"
        }
        val content = buildString {
            append(header)
            lines.forEach { line ->
                if (length + 1 + line.length <= maxSummaryChars) append('\n').append(line)
            }
        }.take(maxSummaryChars)
        val digest = sha256(content)
        return ContextItem(
            id = "summary:${digest.take(16)}",
            sha256 = digest,
            kind = ContextItemKind.SUMMARY,
            content = content,
            sourceRefs = sources,
        )
    }

    private fun validate(items: List<ContextItem>): String? {
        if (items.map(ContextItem::id).distinct().size != items.size) return "Duplicate context item ID"
        items.forEach { item ->
            if (item.id.isBlank()) return "Blank context item ID"
            if (!item.sha256.matches(SHA256) || item.sha256 != sha256(item.content)) {
                return "Invalid content hash for ${item.id}"
            }
            if (item.sourceRefs.any { it.id.isBlank() || !it.sha256.matches(SHA256) }) {
                return "Invalid summary provenance for ${item.id}"
            }
        }
        return null
    }

    private fun safe(value: String?): String = value
        .orEmpty()
        .replace(UNSAFE_SUMMARY_CHARS, "_")
        .take(MAX_SAFE_FIELD)

    private data class Turn(
        val id: String,
        val firstIndex: Int,
        val items: List<ContextItem>,
    )

    private companion object {
        const val RETAINED_COMPLETE_TURNS = 4
        const val MAX_SAFE_FIELD = 60
        val SHA256 = Regex("^[0-9a-f]{64}$")
        val UNSAFE_SUMMARY_CHARS = Regex("[^A-Za-z0-9_.:-]")
        val ALWAYS_PROTECTED = setOf(
            ContextItemKind.SYSTEM,
            ContextItemKind.ORIGINAL_TASK,
            ContextItemKind.OVERSIGHT,
            ContextItemKind.PAUSE,
            ContextItemKind.RESUME,
            ContextItemKind.ACTION,
            ContextItemKind.RECOVERY,
        )

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
