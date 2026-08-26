package com.caddie.context.compaction

/** Identifies the structural role of one immutable model-context item. */
enum class ContextItemKind {
    SYSTEM,
    ORIGINAL_TASK,
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
    OVERSIGHT,
    PAUSE,
    RESUME,
    ACTION,
    RECOVERY,
    SUMMARY,
}

/** Links a compact summary back to one exact immutable source item. */
data class ContextSourceRef(
    val id: String,
    val sha256: String,
)

/** Represents one immutable, structurally typed session-context item. */
data class ContextItem(
    val id: String,
    val sha256: String,
    val kind: ContextItemKind,
    val content: String,
    val turnId: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val outcomeCategory: String? = null,
    val verifiedTransition: String? = null,
    val sourceRefs: List<ContextSourceRef> = emptyList(),
)

/** Returns safe compacted context or the untouched input with a rejection reason. */
sealed interface CompactionResult {
    data class Compacted(val items: List<ContextItem>) : CompactionResult
    data class Rejected(
        val original: List<ContextItem>,
        val reason: String,
    ) : CompactionResult
}
