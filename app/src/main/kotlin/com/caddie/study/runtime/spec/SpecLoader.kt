package com.caddie.study.runtime.spec

import com.caddie.study.runtime.model.CorrectionAssertion
import com.caddie.study.runtime.model.CorrectionPolicy
import com.caddie.study.runtime.model.CorrectionStep
import com.caddie.study.runtime.model.CriticalityClass
import com.caddie.study.runtime.model.ErrorVariant
import com.caddie.study.runtime.model.PostCommitPolicy
import com.caddie.study.runtime.model.ParticipantAppUse
import com.caddie.study.runtime.model.ParticipantTaskBriefing
import com.caddie.study.runtime.model.StepType
import com.caddie.study.runtime.model.StudyStep
import com.caddie.study.runtime.model.TriggerContract
import com.caddie.study.runtime.model.TrialSpec
import com.caddie.study.runtime.model.UiStateEvidence
import com.caddie.study.runtime.model.VerificationRule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads bundled study task specs (JSON, converted from the canonical YAML)
 * into immutable [TrialSpec] domain models.
 *
 * Specs live in the `study-specs` asset folder as JSON files. They are part
 * of the study protocol and shipped with the app version — no runtime YAML
 * dependency.
 */
class SpecLoader(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    },
) {
    /** Parse a single spec JSON string. */
    fun parse(jsonText: String): TrialSpec {
        val dto = json.decodeFromString(SpecDto.serializer(), jsonText)
        return dto.toDomain()
    }

    /** Parse a single spec from an [android.content.res.AssetManager] file. */
    fun loadFromAssets(assets: android.content.res.AssetManager, path: String): TrialSpec {
        val text = assets.open(path).bufferedReader().use { it.readText() }
        return parse(text)
    }

    /** Load every `*.json` spec under [dir] in the assets folder. */
    fun loadAllFromAssets(
        assets: android.content.res.AssetManager,
        dir: String = "study-specs",
    ): Map<String, TrialSpec> {
        val names = assets.list(dir).orEmpty().filter { it.endsWith(".json") }
        return names.associate { name ->
            val spec = loadFromAssets(assets, "$dir/$name")
            spec.id to spec
        }
    }
}

// ── DTOs mirroring the JSON (snake_case) contract ──

@Serializable
private data class SpecDto(
    @SerialName("version") val version: String,
    @SerialName("id") val id: String,
    @SerialName("instruction_de") val instructionDe: String,
    @SerialName("criticality") val criticality: String,
    @SerialName("required_packages") val requiredPackages: List<String> = emptyList(),
    @SerialName("seeded_artifacts") val seededArtifacts: List<String> = emptyList(),
    @SerialName("reset_checklist") val resetChecklist: List<String> = emptyList(),
    @SerialName("steps") val steps: List<StepDto>,
    @SerialName("error_steps") val errorSteps: List<String> = emptyList(),
    @SerialName("c2_summary_lines") val c2SummaryLines: List<String> = emptyList(),
    @SerialName("verification") val verification: List<VerificationDto> = emptyList(),
    @SerialName("max_duration_s") val maxDurationS: Int = 300,
    @SerialName("per_gate_timeout_s") val perGateTimeoutS: Int = 30,
    @SerialName("trigger") val trigger: TriggerDto? = null,
    @SerialName("completion_message_de") val completionMessageDe: String = "",
    @SerialName("participant_briefing") val participantBriefing: ParticipantBriefingDto? = null,
)

@Serializable
private data class ParticipantBriefingDto(
    @SerialName("situation_de") val situationDe: String,
    @SerialName("apps") val apps: List<ParticipantAppUseDto>,
    @SerialName("goal_de") val goalDe: String,
    @SerialName("preparation_de") val preparationDe: String,
    @SerialName("reference_values_de") val referenceValuesDe: List<String> = emptyList(),
)

@Serializable
private data class ParticipantAppUseDto(
    @SerialName("name") val name: String,
    @SerialName("purpose_de") val purposeDe: String,
)

@Serializable
private data class StepDto(
    @SerialName("id") val id: String,
    @SerialName("action") val action: String,
    @SerialName("narration") val narration: String,
    @SerialName("step_type") val stepType: String,
    @SerialName("confirmation_text") val confirmationText: String? = null,
    @SerialName("min_narration_ms") val minNarrationMs: Int = 800,
    @SerialName("confirm_in_c1") val confirmInC1: Boolean = true,
    @SerialName("ready_package") val readyPackage: String? = null,
    @SerialName("ready_resource_id") val readyResourceId: String? = null,
    @SerialName("already_satisfied") val alreadySatisfied: UiStateEvidenceDto? = null,
    @SerialName("error_variant") val errorVariant: ErrorVariantDto? = null,
)

@Serializable
private data class UiStateEvidenceDto(
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    @SerialName("text") val text: String? = null,
)

@Serializable
private data class ErrorVariantDto(
    @SerialName("id") val id: String,
    @SerialName("field") val field: String,
    @SerialName("wrong_value") val wrongValue: String,
    @SerialName("correct_value") val correctValue: String,
    @SerialName("description") val description: String,
    @SerialName("summary_wrong_value") val summaryWrongValue: String? = null,
    @SerialName("summary_correct_value") val summaryCorrectValue: String? = null,
    @SerialName("correction") val correction: CorrectionDto? = null,
)

@Serializable
private data class CorrectionDto(
    @SerialName("after_commit") val afterCommit: String,
    @SerialName("rewind_to_step_id") val rewindToStepId: String? = null,
    @SerialName("rewind_steps") val rewindSteps: List<CorrectionStepDto> = emptyList(),
    @SerialName("steps") val steps: List<CorrectionStepDto> = emptyList(),
    @SerialName("assertions") val assertions: List<CorrectionAssertionDto> = emptyList(),
    @SerialName("irreversible_message_de") val irreversibleMessageDe: String? = null,
)

@Serializable
private data class CorrectionStepDto(
    @SerialName("id") val id: String,
    @SerialName("action") val action: String,
    @SerialName("narration") val narration: String,
    @SerialName("confirmation_text") val confirmationText: String,
    @SerialName("min_narration_ms") val minNarrationMs: Int = 0,
    @SerialName("run_if_target_present") val runIfTargetPresent: Boolean = false,
    @SerialName("ready_package") val readyPackage: String? = null,
)

@Serializable
private data class CorrectionAssertionDto(
    @SerialName("check_type") val checkType: String,
    @SerialName("value") val value: String,
)

@Serializable
private data class VerificationDto(
    @SerialName("id") val id: String,
    @SerialName("assertion") val assertion: String,
    @SerialName("check_type") val checkType: String,
    @SerialName("parameters") val parameters: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    @SerialName("screenshot_evidence") val screenshotEvidence: Boolean = true,
)

@Serializable
private data class TriggerDto(
    @SerialName("reference_phrases") val referencePhrases: List<String>,
    @SerialName("required_concepts") val requiredConcepts: List<List<String>>,
    @SerialName("forbidden_concepts") val forbiddenConcepts: List<String> = emptyList(),
    @SerialName("wake_words") val wakeWords: List<String> = listOf("jarvis", "caddie"),
)

// ── DTO → domain mapping ──

private fun SpecDto.toDomain() = TrialSpec(
    version = version,
    id = id,
    instructionDe = instructionDe,
    criticality = CriticalityClass.fromWire(criticality),
    requiredPackages = requiredPackages,
    seededArtifacts = seededArtifacts,
    resetChecklist = resetChecklist,
    steps = steps.map { it.toDomain() },
    errorSteps = errorSteps,
    c2SummaryLines = c2SummaryLines,
    verification = verification.map { it.toDomain() },
    maxDurationS = maxDurationS,
    perGateTimeoutS = perGateTimeoutS,
    trigger = trigger?.toDomain(),
    completionMessageDe = completionMessageDe,
    participantBriefing = participantBriefing?.let { briefing ->
        ParticipantTaskBriefing(
            situationDe = briefing.situationDe,
            apps = briefing.apps.map { app -> ParticipantAppUse(app.name, app.purposeDe) },
            goalDe = briefing.goalDe,
            preparationDe = briefing.preparationDe,
            referenceValuesDe = briefing.referenceValuesDe,
        )
    },
)

private fun StepDto.toDomain() = StudyStep(
    id = id,
    action = action,
    narration = narration,
    stepType = StepType.fromWire(stepType),
    confirmationText = confirmationText,
    errorVariant = errorVariant?.toDomain(),
    minNarrationMs = minNarrationMs,
    confirmInC1 = confirmInC1,
    readyPackage = readyPackage,
    readyResourceId = readyResourceId,
    alreadySatisfied = alreadySatisfied?.let { evidence ->
        UiStateEvidence(
            packageName = evidence.packageName,
            resourceId = evidence.resourceId,
            text = evidence.text,
        )
    },
)

private fun ErrorVariantDto.toDomain() = ErrorVariant(
    id = id,
    field = field,
    wrongValue = wrongValue,
    correctValue = correctValue,
    description = description,
    summaryWrongValue = summaryWrongValue,
    summaryCorrectValue = summaryCorrectValue,
    correction = correction?.toDomain(),
)

private fun CorrectionDto.toDomain() = CorrectionPolicy(
    afterCommit = PostCommitPolicy.fromWire(afterCommit),
    rewindToStepId = rewindToStepId,
    rewindSteps = rewindSteps.map { it.toDomain() },
    steps = steps.map { it.toDomain() },
    assertions = assertions.map { CorrectionAssertion(it.checkType, it.value) },
    irreversibleMessageDe = irreversibleMessageDe,
)

private fun CorrectionStepDto.toDomain() = CorrectionStep(
    id = id,
    action = action,
    narration = narration,
    confirmationText = confirmationText,
    minNarrationMs = minNarrationMs,
    runIfTargetPresent = runIfTargetPresent,
    readyPackage = readyPackage,
)

private fun VerificationDto.toDomain() = VerificationRule(
    id = id,
    assertion = assertion,
    checkType = checkType,
    parameters = parameters.mapValues { it.value.toDomainValue() },
    screenshotEvidence = screenshotEvidence,
)

private fun JsonElement.toDomainValue(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        content == "true" -> true
        content == "false" -> false
        content.toLongOrNull() != null -> content.toLong()
        content.toDoubleOrNull() != null -> content.toDouble()
        else -> content
    }
    is JsonArray -> map { it.toDomainValue() }
    is JsonObject -> mapValues { it.value.toDomainValue() }
}

private fun TriggerDto.toDomain() = TriggerContract(
    referencePhrases = referencePhrases,
    requiredConcepts = requiredConcepts,
    forbiddenConcepts = forbiddenConcepts,
    wakeWords = wakeWords,
)
