package com.caddie.study

import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightDecision
import com.caddie.agent.core.OversightPolicy

/** Applies study-condition rules before allowing a tool call. */
class StudyOversightPolicy(
    private val condition: StudyCondition,
    private val gate: StudyGate,
    private val classifier: StudyActionClassifier,
) : OversightPolicy {
    override suspend fun approve(
        call: ModelDelta.ToolCall,
    ): OversightDecision {
        val assessment = classifier.assess(call)
        val kind = assessment.kind
        return when (condition) {
            StudyCondition.C1_STEPWISE ->
                when (kind) {
                    StudyActionKind.PREPARATORY ->
                        OversightDecision(approved = true)
                    StudyActionKind.PARTICIPANT_MEANINGFUL,
                    StudyActionKind.COMMIT,
                    -> gate.confirmStep(call, assessment.confirmationText)
                }
            StudyCondition.C2_FINAL_CHECKPOINT ->
                if (kind == StudyActionKind.COMMIT) {
                    gate.confirmFinal(listOf(call), assessment.finalSummary)
                } else {
                    OversightDecision(approved = true)
                }
            StudyCondition.C3_VOLUNTARY_INTERVENTION ->
                OversightDecision(approved = true)
        }
    }
}

/** Reports whether supervised study interaction is currently allowed. */
interface StudyGate {
    suspend fun confirmStep(
        call: ModelDelta.ToolCall,
        confirmationText: String? = null,
    ): OversightDecision

    suspend fun confirmFinal(
        calls: List<ModelDelta.ToolCall>,
        summaryLines: List<String> = emptyList(),
    ): OversightDecision
}
