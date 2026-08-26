package com.caddie.context.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Describes whether a replay may execute, guide only, or remain disabled. */
enum class ReplayClassification {
    ELIGIBLE,
    GUIDANCE_ONLY,
    DISABLED,
}

/** Records the lifecycle decision applied to one replay revision. */
enum class ReplayStatus {
    ACTIVE,
    REJECTED,
    SUPERSEDED,
}

/** Constrains replay use to one known Android environment. */
data class ReplayEnvironment(
    val packageName: String?,
    val appVersion: String?,
    val minSdk: Int?,
    val maxSdk: Int?,
    val buildFingerprint: String?,
    val localeTag: String?,
)

/** Represents one observable condition used to verify replay progress. */
data class ReplayPredicate(
    val kind: String,
    val expectedValue: String,
)

/** Stores immutable provenance for a replay definition. */
data class ReplayProvenance(
    val sourcePath: String,
    val sourceSha256: String,
)

/** Summarizes prior verified replay success without participant identifiers. */
data class ReplaySuccessMetadata(
    val verifiedRuns: Int,
    val lastVerifiedBuild: String?,
)

/** Defines one semantic replay step and rejects coordinate-shaped arguments. */
data class ReplayStep(
    val toolName: String,
    val arguments: Map<String, String>,
    val selectorFingerprint: String?,
    val precondition: ReplayPredicate?,
    val postcondition: ReplayPredicate?,
) {
    init {
        require(arguments.keys.none { it.lowercase() in FORBIDDEN_ARGUMENT_KEYS }) {
            "Replay steps cannot contain coordinate-shaped arguments"
        }
    }

    private companion object {
        val FORBIDDEN_ARGUMENT_KEYS = setOf("x", "y", "bounds", "tap")
    }
}

/** Contains one versioned replay definition before its safety classification. */
data class ReplayTrajectory(
    val trajectoryId: String,
    val revisionId: String,
    val skillId: String,
    val status: ReplayStatus,
    val environment: ReplayEnvironment,
    val startPredicate: ReplayPredicate?,
    val steps: List<ReplayStep>,
    val terminalPredicate: ReplayPredicate?,
    val provenance: ReplayProvenance,
    val success: ReplaySuccessMetadata?,
    val legacyContainsCoordinates: Boolean,
)

/** Applies the conservative eligibility rules to immutable replay definitions. */
object ReplayClassifier {
    fun classify(replay: ReplayTrajectory): ReplayClassification {
        if (replay.status != ReplayStatus.ACTIVE) return ReplayClassification.DISABLED
        if (
            replay.legacyContainsCoordinates ||
            !replay.hasStableIdentity() ||
            !replay.environment.isComplete() ||
            !replay.startPredicate.isComplete() ||
            !replay.terminalPredicate.isComplete() ||
            replay.steps.isEmpty() ||
            replay.steps.any { !it.isComplete() } ||
            !replay.provenance.isComplete() ||
            replay.success?.isComplete() != true
        ) {
            return ReplayClassification.GUIDANCE_ONLY
        }
        return ReplayClassification.ELIGIBLE
    }

    private fun ReplayTrajectory.hasStableIdentity(): Boolean =
        trajectoryId.isNotBlank() && revisionId.isNotBlank() && skillId.isNotBlank()

    private fun ReplayEnvironment.isComplete(): Boolean =
        !packageName.isNullOrBlank() &&
            !appVersion.isNullOrBlank() &&
            minSdk != null &&
            maxSdk != null &&
            minSdk > 0 &&
            maxSdk >= minSdk &&
            !buildFingerprint.isNullOrBlank() &&
            !localeTag.isNullOrBlank()

    private fun ReplayPredicate?.isComplete(): Boolean =
        this != null && kind.isNotBlank() && expectedValue.isNotBlank()

    private fun ReplayStep.isComplete(): Boolean =
        toolName.isNotBlank() &&
            arguments.isNotEmpty() &&
            precondition.isComplete() &&
            postcondition.isComplete()

    private fun ReplayProvenance.isComplete(): Boolean =
        sourcePath.isNotBlank() && sourceSha256.matches(SHA256)

    private fun ReplaySuccessMetadata.isComplete(): Boolean =
        verifiedRuns >= 0 && !lastVerifiedBuild.isNullOrBlank()

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}

/** Represents one retained legacy replay that can never execute by import alone. */
data class ImportedReplay(
    val id: String,
    val skillId: String,
    val sourcePath: String,
    val sourceSha256: String,
    val legacyContainsCoordinates: Boolean,
) {
    val classification: ReplayClassification = ReplayClassification.GUIDANCE_ONLY
}

/** Parses individual legacy replay JSON files without promoting imported data to execution. */
object LegacyReplayImporter {
    fun parse(json: String): List<ImportedReplay> {
        val replay = Json.parseToJsonElement(json).jsonObject
        val imported = listOf(
            ImportedReplay(
                id = replay.getValue("id").jsonPrimitive.content,
                skillId = replay.getValue("skillId").jsonPrimitive.content,
                sourcePath = replay.getValue("sourcePath").jsonPrimitive.content,
                sourceSha256 = replay.getValue("sourceSha256").jsonPrimitive.content,
                legacyContainsCoordinates = replay
                    .getValue("legacyContainsCoordinates")
                    .jsonPrimitive.content.toBooleanStrict(),
            ).also {
                require(it.id.isNotBlank() && it.skillId.isNotBlank())
                require(it.sourcePath.isNotBlank())
                require(it.sourceSha256.matches(SHA256))
            },
        )
        return imported
    }

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}
