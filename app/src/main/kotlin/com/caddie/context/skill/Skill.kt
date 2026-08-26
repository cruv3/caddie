package com.caddie.context.skill

import kotlinx.serialization.Serializable

/** Stores one normalized skill document together with its source provenance. */
@Serializable
data class Skill(
    val id: String,
    val title: String,
    val description: String,
    val triggers: List<String>,
    val body: String,
    val sourcePath: String,
    val sourceSha256: String,
    val replayId: String?,
)
