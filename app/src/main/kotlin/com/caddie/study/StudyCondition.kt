package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ToolCallTransformer

/** Identifies the active study condition and its oversight behavior. */
enum class StudyCondition(
    val wireValue: String,
) {
    C1_STEPWISE("c1_stepwise"),
    C2_FINAL_CHECKPOINT("c2_final_checkpoint"),
    C3_VOLUNTARY_INTERVENTION("c3_voluntary_intervention"),
}

/** Classifies actions that receive study-specific oversight. */
enum class StudyActionKind {
    PREPARATORY,
    PARTICIPANT_MEANINGFUL,
    COMMIT,
}

/** Combines oversight risk with the frozen participant-facing study text. */
data class StudyActionAssessment(
    val kind: StudyActionKind,
    val confirmationText: String? = null,
    val finalSummary: List<String> = emptyList(),
)

/** Maps a tool call to its study action category. */
fun interface StudyActionClassifier : ToolCallTransformer {
    fun assess(call: ModelDelta.ToolCall): StudyActionAssessment

    override fun transform(call: ModelDelta.ToolCall): ModelDelta.ToolCall = call
}
