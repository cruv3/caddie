package com.caddie.context.retrieval

import com.caddie.context.skill.Skill

/** Identifies whether results used embeddings or the conservative lexical fallback. */
enum class RetrievalMode {
    SEMANTIC,
    DEGRADED_LEXICAL,
}

/** Exposes every component that contributed to one retrieval decision. */
data class RetrievalScore(
    val cosine: Float,
    val triggerBoost: Float,
    val tokenBoost: Float,
    val exactTriggerCount: Int,
    val reasons: List<String>,
) {
    val total: Float = cosine + triggerBoost + tokenBoost
}

/** Describes a small non-skill context hint and its optional persisted vector. */
data class ContextHint(
    val id: String,
    val text: String,
    val triggers: List<String> = emptyList(),
    val embedding: FloatArray? = null,
)

/** Pairs a selected skill with its auditable retrieval score. */
data class RetrievedSkill(
    val skill: Skill,
    val score: RetrievalScore,
)

/** Projects a selected hint without exposing its stored embedding. */
data class RetrievedHint(
    val id: String,
    val text: String,
    val score: RetrievalScore,
)

/** Holds one deterministic, bounded retrieval result. */
data class RetrievalResult(
    val mode: RetrievalMode,
    val skills: List<RetrievedSkill>,
    val hints: List<RetrievedHint>,
)
