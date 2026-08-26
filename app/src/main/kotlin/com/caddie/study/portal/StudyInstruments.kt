package com.caddie.study.portal

/**
 * Immutable questionnaire definitions used by the study portal.
 * Port of Python `caddie.study.portal.instruments`.
 */

const val INSTRUMENT_VERSION = "2026-08-07"

data class InstrumentItem(
    val id: String,
    val prompt: String,
    val type: InstrumentType,
    val options: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val required: Boolean = true,
    val section: String = "",
)

enum class InstrumentType {
    AGREEMENT_SCALE,
    RATING_SCALE,
    SINGLE_CHOICE,
    RANKING,
    TEXT,
    INTEGER,
}

data class Instrument(
    val id: String,
    val label: String,
    val items: List<InstrumentItem>,
)

// ── TASK instrument (post-task questionnaire) ──

val TASK = Instrument(
    id = "task",
    label = "Aufgaben-Rückfrage",
    items = listOf(
        InstrumentItem(
            id = "task_anomaly_detected",
            prompt = "Gab es bei der Durchführung oder beim Ergebnis etwas, das dir besonders aufgefallen ist?",
            type = InstrumentType.SINGLE_CHOICE,
            options = listOf("Ja", "Nein", "Ich bin mir nicht sicher"),
            section = "Dein erster Eindruck",
        ),
        InstrumentItem(
            id = "task_observation",
            prompt = "Was genau ist dir aufgefallen?",
            type = InstrumentType.TEXT,
            required = false,
            section = "Dein erster Eindruck",
        ),
        InstrumentItem(
            id = "task_anomaly_timing",
            prompt = "Wann ist dir das aufgefallen?",
            type = InstrumentType.SINGLE_CHOICE,
            options = listOf(
                "Vor der Ausführung",
                "Während der Ausführung",
                "Erst beim Ergebnis",
                "Nicht zutreffend / kann ich nicht genau sagen",
            ),
            required = false,
            section = "Dein erster Eindruck",
        ),
        InstrumentItem(
            id = "task_anomaly_response",
            prompt = "Wie hast du darauf reagiert?",
            type = InstrumentType.SINGLE_CHOICE,
            options = listOf(
                "Nichts unternommen",
                "Caddie pausiert",
                "Caddie gestoppt",
                "Caddie korrigiert",
                "Versuchsleitung angesprochen",
                "Etwas anderes",
                "Nicht zutreffend",
            ),
            required = false,
            section = "Dein erster Eindruck",
        ),
        InstrumentItem(
            id = "task_result_match",
            prompt = "Wie gut entsprach das Ergebnis dem, was mit der Aufgabe erreicht werden sollte?",
            type = InstrumentType.SINGLE_CHOICE,
            options = listOf(
                "Gar nicht",
                "Eher nicht",
                "Teilweise",
                "Weitgehend",
                "Vollständig",
                "Kann ich nicht beurteilen",
            ),
            section = "Deine Einschätzung",
        ),
        InstrumentItem(
            id = "task_assessment_confidence",
            prompt = "Wie sicher bist du dir bei dieser Einschätzung?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("1 = überhaupt nicht sicher", "7 = sehr sicher"),
            min = 1,
            max = 7,
            step = 1,
            section = "Deine Einschätzung",
        ),
        InstrumentItem(
            id = "task_criticality",
            prompt = "Angenommen, das Ergebnis dieser Aufgabe wäre falsch: Wie schwerwiegend wären die möglichen Folgen?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("1 = überhaupt nicht schwerwiegend", "7 = sehr schwerwiegend"),
            min = 1,
            max = 7,
            step = 1,
            section = "Mögliche Folgen",
        ),
    ),
)

// ── BLOCK instrument (post-block: NASA-TLX + control + TiA) ──

private val AGREE_ANCHORS = listOf("1 = Stimme gar nicht zu", "7 = Stimme voll zu")

private fun agree(id: String, prompt: String) = InstrumentItem(
    id = id,
    prompt = prompt,
    type = InstrumentType.AGREEMENT_SCALE,
    options = AGREE_ANCHORS,
    min = 1,
    max = 7,
    step = 1,
)

private fun agree(id: String, prompt: String, section: String) =
    agree(id, prompt).copy(section = section)

val BLOCK = Instrument(
    id = "block",
    label = "Block-Rückfrage",
    items = listOf(
        InstrumentItem(
            id = "condition_observation",
            prompt = "Wie hast du die Zusammenarbeit mit Caddie in diesem Abschnitt erlebt?",
            type = InstrumentType.TEXT,
            required = false,
            section = "Dein erster Eindruck",
        ),
        // NASA-TLX subscales
        InstrumentItem(
            id = "nasa_mental",
            prompt = "Wie geistig anspruchsvoll waren die Aufgaben in diesem Abschnitt?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = sehr gering", "100 = sehr hoch"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        InstrumentItem(
            id = "nasa_physical",
            prompt = "Wie körperlich anstrengend waren die Aufgaben in diesem Abschnitt?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = sehr gering", "100 = sehr hoch"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        InstrumentItem(
            id = "nasa_temporal",
            prompt = "Wie stark war der Zeitdruck während der Aufgaben?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = sehr gering", "100 = sehr hoch"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        InstrumentItem(
            id = "nasa_performance",
            prompt = "Wie erfolgreich warst du beim Erreichen der Aufgabenziele?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = perfekt", "100 = völliger Misserfolg"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        InstrumentItem(
            id = "nasa_effort",
            prompt = "Wie sehr musstest du dich anstrengen, um diese Leistung zu erreichen?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = sehr gering", "100 = sehr hoch"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        InstrumentItem(
            id = "nasa_frustration",
            prompt = "Wie unsicher, entmutigt, gereizt oder gestresst hast du dich gefühlt?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("0 = sehr gering", "100 = sehr hoch"),
            min = 0,
            max = 100,
            step = 5,
            section = "Beanspruchung während der Aufgaben",
        ),
        // Perceived control
        agree("control_perceived", "Ich hatte Kontrolle darüber, was Caddie tat.", "Kontrolle und Eingriff"),
        agree("control_intervene", "Ich konnte Caddie rechtzeitig stoppen oder korrigieren.", "Kontrolle und Eingriff"),
        agree(
            "control_confirmations",
            "Wie häufig Caddie meine Bestätigung verlangte, war für diese Aufgaben angemessen.",
            "Kontrolle und Eingriff",
        ),
        agree(
            "control_attention_moments",
            "Ich wusste, in welchen Momenten ich besonders aufmerksam sein musste.",
            "Kontrolle und Eingriff",
        ),
        // TIA — Transparency & Understanding
        agree("tia_understand_1", "Ich konnte nachvollziehen, warum Caddie eine bestimmte Aktion ausgeführt hat.", "Verständlichkeit und Vorhersagbarkeit"),
        agree("tia_understand_2", "Ich konnte vorhersagen, welche Aktion Caddie als Nächstes ausführen würde.", "Verständlichkeit und Vorhersagbarkeit"),
        agree("tia_understand_3", "Caddies Verhalten war auf Basis dessen, was ich sah, verständlich.", "Verständlichkeit und Vorhersagbarkeit"),
        agree("tia_understand_4", "Die angezeigten Aktionen waren vorhersehbar.", "Verständlichkeit und Vorhersagbarkeit"),
        agree("tia_trust_1", "Ich vertraute darauf, dass Caddie die richtigen Entscheidungen trifft.", "Vertrauen"),
        agree("tia_trust_2", "Ich fühlte mich wohl dabei, Caddie Entscheidungen überlassen zu können.", "Vertrauen"),
    ),
)

// ── END instrument (demographics + preference ranking) ──

private val OVERSIGHT_PREFERENCE_OPTIONS = listOf(
    "Wichtige Schritte einzeln bestätigen",
    "Einmal vor der endgültigen Ausführung bestätigen",
    "Selbstständige Ausführung mit freiwilligem Eingriff",
    "Keine klare Präferenz",
)

val END = Instrument(
    id = "end",
    label = "Abschluss-Fragen",
    items = listOf(
        InstrumentItem(
            id = "age",
            prompt = "Wie alt bist du?",
            type = InstrumentType.INTEGER,
            section = "Zu deiner Person",
        ),
        InstrumentItem(
            id = "gender",
            prompt = "Welchem Geschlecht ordnest du dich zu?",
            type = InstrumentType.SINGLE_CHOICE,
            options = listOf("Weiblich", "Männlich", "Divers", "Keine Angabe"),
            section = "Zu deiner Person",
        ),
        InstrumentItem(
            id = "android_experience",
            prompt = "Wie viel Erfahrung hast du mit Android-Smartphones?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("1 = gar keine Erfahrung", "7 = sehr viel Erfahrung"),
            min = 1,
            max = 7,
            step = 1,
            section = "Deine Vorerfahrung",
        ),
        InstrumentItem(
            id = "ai_assistant_experience",
            prompt = "Wie viel Erfahrung hast du mit KI-, Sprach- oder Smartphone-Assistenten?",
            type = InstrumentType.RATING_SCALE,
            options = listOf("1 = gar keine Erfahrung", "7 = sehr viel Erfahrung"),
            min = 1,
            max = 7,
            step = 1,
            section = "Deine Vorerfahrung",
        ),
        InstrumentItem(
            id = "condition_ranking",
            prompt = "Ordne die drei Arten der Zusammenarbeit nach deiner persönlichen Präferenz (1 = bevorzugt, 3 = weniger bevorzugt).",
            type = InstrumentType.RANKING,
            options = listOf(
                "Bedingung 1 (schrittweise Aufsicht)",
                "Bedingung 2 (finaler Checkpoint)",
                "Bedingung 3 (freiwilliger Eingriff)",
            ),
            section = "Deine Präferenz",
        ),
        InstrumentItem(
            id = "oversight_low_consequence",
            prompt = "Welche Art der Aufsicht würdest du bei Aufgaben mit geringen möglichen Folgen bevorzugen?",
            type = InstrumentType.SINGLE_CHOICE,
            options = OVERSIGHT_PREFERENCE_OPTIONS,
            section = "Deine Präferenz",
        ),
        InstrumentItem(
            id = "oversight_high_consequence",
            prompt = "Welche Art der Aufsicht würdest du bei Aufgaben mit schwerwiegenden möglichen Folgen bevorzugen?",
            type = InstrumentType.SINGLE_CHOICE,
            options = OVERSIGHT_PREFERENCE_OPTIONS,
            section = "Deine Präferenz",
        ),
    ),
)

/** Item IDs for each instrument. */
val TASK_ITEM_IDS = TASK.items.mapTo(mutableSetOf()) { it.id }
val BLOCK_ITEM_IDS = BLOCK.items.mapTo(mutableSetOf()) { it.id }
val END_DEMOGRAPHICS_IDS = setOf("age", "gender", "android_experience", "ai_assistant_experience")
val END_RANKING_IDS = setOf("condition_ranking", "oversight_low_consequence", "oversight_high_consequence")

/** Validate that all required item IDs are present with correct answer types. */
fun validateInstrumentAnswers(
    instrument: Instrument,
    requiredItemIds: Set<String>,
    answers: Map<String, Any>,
) {
    val includedItems = instrument.items.filter { it.id in requiredItemIds }
    val requiredIds = includedItems.filter { it.required }.mapTo(mutableSetOf()) { it.id }
    requiredIds += conditionalRequiredIds(instrument, answers)
    if (!answers.keys.all { it in requiredItemIds } || !answers.keys.containsAll(requiredIds)) {
        throw IllegalArgumentException(
            "answers must contain every required item ID and no unknown IDs, got ${answers.keys}"
        )
    }
    validateInstrumentAnswerValues(instrument, answers)
}

/** Validates a corrected response, including explicitly marked missing values. */
fun validateInstrumentCorrection(
    instrument: Instrument,
    requiredItemIds: Set<String>,
    answers: Map<String, Any>,
    missing: Map<String, Any>,
) {
    require(answers.keys.intersect(missing.keys).isEmpty()) {
        "an item cannot contain both an answer and a missing marker"
    }
    val requiredIds = instrument.items
        .filter { it.id in requiredItemIds && it.required }
        .mapTo(mutableSetOf()) { it.id }
    requiredIds += conditionalRequiredIds(instrument, answers)
    require((answers.keys + missing.keys).containsAll(requiredIds)) {
        "answers and missing markers must cover every required item ID"
    }
    require((answers.keys + missing.keys).all { it in requiredItemIds }) {
        "answers and missing markers contain an unknown item ID"
    }
    require(missing.values.all { it is String && it.isNotBlank() }) {
        "missing markers must contain a non-empty reason"
    }
    validateInstrumentAnswerValues(instrument, answers)
}

private fun conditionalRequiredIds(
    instrument: Instrument,
    answers: Map<String, Any>,
): Set<String> =
    if (
        instrument.id == TASK.id &&
        answers["task_anomaly_detected"] in setOf("Ja", "Ich bin mir nicht sicher")
    ) {
        setOf("task_anomaly_timing", "task_anomaly_response")
    } else {
        emptySet()
    }

private fun validateInstrumentAnswerValues(
    instrument: Instrument,
    answers: Map<String, Any>,
) {
    val itemMap = instrument.items.associateBy { it.id }
    for ((itemId, answer) in answers) {
        val item = itemMap[itemId]
            ?: throw IllegalArgumentException("unknown item: $itemId")
        when (item.type) {
            InstrumentType.AGREEMENT_SCALE,
            InstrumentType.RATING_SCALE,
            -> validateScaleAnswer(item, answer)
            InstrumentType.INTEGER -> require(answer is Int) { "$itemId must be Int" }
            InstrumentType.SINGLE_CHOICE -> {
                require(answer is String) { "$itemId must be String" }
                require(answer in item.options) { "$itemId is not an allowed option" }
            }
            InstrumentType.RANKING -> {
                require(answer is Map<*, *>) { "$itemId must be an object" }
                require(answer.keys == item.options.toSet()) { "$itemId has invalid anchors" }
                val ranks = answer.values
                require(ranks.all { it is Int }) { "$itemId ranks must be integers" }
                require(ranks.toSet() == (1..item.options.size).toSet()) {
                    "$itemId must use every rank exactly once"
                }
            }
            InstrumentType.TEXT -> require(answer is String) { "$itemId must be String" }
        }
    }
}

private fun validateScaleAnswer(item: InstrumentItem, answer: Any) {
    require(answer is Int) { "${item.id} must be Int" }
    item.min?.let { require(answer >= it) { "${item.id} is below its minimum" } }
    item.max?.let { require(answer <= it) { "${item.id} is above its maximum" } }
    item.step?.let { step ->
        val origin = item.min ?: 0
        require((answer - origin) % step == 0) {
            "${item.id} does not match its step size"
        }
    }
}
