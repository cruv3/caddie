package com.caddie.study.portal

import kotlinx.serialization.Serializable

/** One neutral interview question and optional prompts for the investigator. */
@Serializable
data class StudyInterviewQuestion(
    val id: String,
    val prompt: String,
    val probes: List<String> = emptyList(),
)

/** Neutral questions asked before the participant learns about assigned errors. */
val STUDY_INTERVIEW_QUESTIONS = listOf(
    StudyInterviewQuestion(
        id = "overall_experience",
        prompt = "Wie war es insgesamt für dich, mit Caddie zusammenzuarbeiten?",
        probes = listOf("Was war angenehm?", "Was war schwierig?"),
    ),
    StudyInterviewQuestion(
        id = "condition_comparison",
        prompt = "Welche Unterschiede zwischen den drei Arten der Zusammenarbeit waren für dich entscheidend?",
        probes = listOf("Welche Art hat dir am besten oder am wenigsten gefallen – und warum?"),
    ),
    StudyInterviewQuestion(
        id = "perceived_control",
        prompt = "Wann hattest du das Gefühl, Caddie wirklich unter Kontrolle zu haben?",
        probes = listOf("Wann hattest du dieses Gefühl nicht?", "Was hat dir beim Eingreifen geholfen?"),
    ),
    StudyInterviewQuestion(
        id = "attention_strategy",
        prompt = "Woran hast du entschieden, wann du genauer hinschauen oder eingreifen solltest?",
        probes = listOf("Gab es bestimmte Anzeigen, Aktionen oder Aufgaben, bei denen du aufmerksamer warst?"),
    ),
    StudyInterviewQuestion(
        id = "suspicious_moments",
        prompt = "Gab es Momente, in denen dir am Vorgehen oder am Ergebnis etwas nicht richtig vorkam?",
        probes = listOf("Was ist dir aufgefallen?", "Wie hast du darauf reagiert?"),
    ),
    StudyInterviewQuestion(
        id = "delegation_boundary",
        prompt = "Welche Aufgaben würdest du Caddie selbstständig überlassen – und bei welchen sollte Caddie vorher fragen?",
        probes = listOf("Wovon hängt diese Entscheidung für dich ab?"),
    ),
)

fun validateInterviewAnswers(answers: Map<String, String>) {
    val expectedIds = STUDY_INTERVIEW_QUESTIONS.mapTo(mutableSetOf()) { it.id }
    require(answers.keys == expectedIds) { "interview answers must match the interview guide" }
    require(answers.values.all { it.isNotBlank() }) { "interview answers must not be blank" }
}
