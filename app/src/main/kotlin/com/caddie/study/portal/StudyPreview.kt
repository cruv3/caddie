package com.caddie.study.portal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/** Builds the read-only click-through flow from the participant-facing study definitions. */
@Serializable
data class StudyPreviewResponse(val steps: List<ParticipantStateResponse>)

fun studyPreviewResponse(): StudyPreviewResponse {
    val previewTaskInstruction =
        "Ermittle die Ankunftszeit mit öffentlichen Verkehrsmitteln von Campus Gummersbach " +
            "nach Campus Deutz und teile Anna die ungefähre Ankunftszeit."

    fun step(
        workflow: String,
        trialIndex: Int? = null,
        taskInstruction: String? = null,
        instrument: InstrumentDto? = null,
        interviewGuide: List<StudyInterviewQuestion>? = null,
        errorVariants: List<Map<String, String>>? = null,
    ) = ParticipantStateResponse(
        session = ParticipantSessionSummary(
            id = 0,
            participant_id = "VORSCHAU",
            workflow_state = workflow,
            workflow_revision = 0,
            current_trial_index = trialIndex,
        ),
        task = taskInstruction?.let { instruction ->
            buildJsonObject {
                put("id", "task_maps_messenger")
                put("instruction", instruction)
                put("briefing", buildJsonObject {
                    put(
                        "situation",
                        "Anna möchte dich am Campus Deutz treffen und hat gefragt, wann du ungefähr da bist. " +
                            "Du startest am Campus Gummersbach und möchtest mit öffentlichen Verkehrsmitteln fahren.",
                    )
                    put("apps", buildJsonArray {
                        add(buildJsonObject {
                            put("name", "Google Maps")
                            put("purpose", "Eine passende Verbindung und die voraussichtliche Ankunftszeit ermitteln.")
                        })
                        add(buildJsonObject {
                            put("name", "Telegram")
                            put("purpose", "Anna die ungefähre Ankunftszeit mitteilen.")
                        })
                    })
                    put(
                        "goal",
                        "Anna weiß anhand einer passenden Verbindung, wann sie ungefähr mit dir am Campus Deutz rechnen kann.",
                    )
                    put("reference_values", buildJsonArray {
                        add(JsonPrimitive("Start: Campus Gummersbach"))
                        add(JsonPrimitive("Ziel: TH Köln Campus Deutz"))
                        add(JsonPrimitive("Verkehrsmittel: öffentlicher Nahverkehr"))
                    })
                    put(
                        "preparation",
                        "Öffne auf dem Studien-Smartphone Telegram und sieh dir Annas neueste Nachricht kurz an. " +
                            "Verändere dabei nichts und lege das Smartphone danach wieder vor dir ab.",
                    )
                })
            }
        },
        instrument = instrument,
        interview_guide = interviewGuide,
        error_variants = errorVariants,
    )

    return StudyPreviewResponse(
        steps = listOf(
            step("consent"),
            step("training"),
            step(
                workflow = "task_card",
                trialIndex = 0,
                taskInstruction = previewTaskInstruction,
            ),
            step(
                workflow = "trial_running",
                trialIndex = 0,
                taskInstruction = previewTaskInstruction,
            ),
            step(
                workflow = "task_questionnaire",
                trialIndex = 0,
                instrument = instrumentDto(TASK, "Fragen zur Aufgabe", TASK_ITEM_IDS),
            ),
            step(
                workflow = "block_questionnaire",
                trialIndex = 1,
                instrument = instrumentDto(BLOCK, "Fragen zum Aufgabenblock", BLOCK_ITEM_IDS),
            ),
            step(
                workflow = "demographics",
                instrument = instrumentDto(END, "Angaben zu deiner Person", END_DEMOGRAPHICS_IDS),
            ),
            step(
                workflow = "preference_ranking",
                instrument = instrumentDto(END, "Abschließende Präferenz", END_RANKING_IDS),
            ),
            step("interview", interviewGuide = STUDY_INTERVIEW_QUESTIONS),
            step(
                "debrief",
                errorVariants = listOf(
                    mapOf(
                        "id" to "preview-error",
                        "description" to "Beispiel einer vorbereiteten Abweichung, die erst jetzt offengelegt wird.",
                    ),
                ),
            ),
            step("completed"),
        ),
    )
}
