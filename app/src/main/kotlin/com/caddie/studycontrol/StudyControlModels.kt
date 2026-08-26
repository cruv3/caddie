package com.caddie.studycontrol

import org.json.JSONArray
import org.json.JSONObject

enum class StudyMode(val wireValue: String, val label: String) {
    Normal("normal", "Normal"),
    Test("test", "Test - Study"),
    Live("live", "Live Study");

    companion object {
        fun fromWire(value: String?): StudyMode =
            entries.firstOrNull { it.wireValue == value } ?: Normal
    }
}

enum class TrialState {
    Idle, Resetting, Armed, Running, Completed, Failed, Aborted, Unknown;

    companion object {
        fun fromWire(value: String?): TrialState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Unknown
    }
}

data class TrialStatus(
    val state: TrialState = TrialState.Idle,
    val participant: String? = null,
    val trialIndex: Int? = null,
    val taskId: String? = null,
    val condition: String? = null,
    val injectError: Boolean = false,
    val reason: String? = null,
)

data class ControlSnapshot(
    val mode: StudyMode,
    val activeParticipant: String?,
    val mcpConnected: Boolean,
    val phoneReady: Boolean,
    val trial: TrialStatus,
)

data class ModePresentation(
    val taskEditable: Boolean,
    val conditionEditable: Boolean,
    val errorVariantEditable: Boolean,
    val showInstruction: Boolean,
)

fun presentationFor(mode: StudyMode): ModePresentation = when (mode) {
    StudyMode.Normal -> ModePresentation(false, false, false, false)
    StudyMode.Test -> ModePresentation(true, true, true, true)
    StudyMode.Live -> ModePresentation(false, false, false, true)
}

data class TrialAssignment(
    val trialIndex: Int,
    val taskId: String,
    val condition: String,
    val injectError: Boolean,
    val instruction: String? = null,
)

data class ParticipantProgress(
    val participant: String,
    val completedTrials: List<Int>,
    val nextTrialIndex: Int?,
    val nextAssignment: TrialAssignment?,
    val assignments: List<TrialAssignment>,
)

data class SessionSummary(
    val sessionId: String,
    val scope: String,
    val participant: String,
    val taskId: String?,
    val condition: String,
    val outcome: String,
    val durationMs: Double,
)

data class TimelineEvent(
    val eventType: String,
    val elapsedMs: Double,
    val timestamp: String,
    val details: String,
)

data class SessionDetail(
    val summary: SessionSummary,
    val timeline: List<TimelineEvent>,
    val rawEvents: String,
    val exportJson: String,
)

fun parseControlSnapshot(json: String): ControlSnapshot {
    val root = JSONObject(json)
    val trial = root.optJSONObject("trial") ?: JSONObject()
    return ControlSnapshot(
        mode = StudyMode.fromWire(root.nullableString("mode")),
        activeParticipant = root.nullableString("active_participant"),
        mcpConnected = root.optBoolean("mcp_connected"),
        phoneReady = root.optBoolean("phone_ready"),
        trial = TrialStatus(
            state = TrialState.fromWire(trial.nullableString("state") ?: "idle"),
            participant = trial.nullableString("participant"),
            trialIndex = trial.nullableInt("trial_index"),
            taskId = trial.nullableString("task_id"),
            condition = trial.nullableString("condition"),
            injectError = trial.optBoolean("inject_error"),
            reason = trial.nullableString("reason"),
        ),
    )
}

fun parseParticipantProgress(json: String): ParticipantProgress {
    val root = JSONObject(json)
    return ParticipantProgress(
        participant = root.optString("participant"),
        completedTrials = root.optJSONArray("completed_trials").ints(),
        nextTrialIndex = root.nullableInt("next_trial_index"),
        nextAssignment = root.optJSONObject("next_assignment")?.toAssignment(),
        assignments = root.optJSONArray("assignments").objects { it.toAssignment() },
    )
}

fun parseSessionSummaries(json: String): List<SessionSummary> =
    JSONObject(json).optJSONArray("sessions").objects { it.toSessionSummary() }

fun parseSessionDetail(json: String): SessionDetail {
    val root = JSONObject(json)
    val summary = root.optJSONObject("summary")?.toSessionSummary()
        ?: error("Study log response has no summary")
    return SessionDetail(
        summary = summary,
        timeline = root.optJSONArray("timeline").objects {
            TimelineEvent(
                eventType = it.optString("event_type", "unknown"),
                elapsedMs = it.optDouble("elapsed_ms", 0.0),
                timestamp = it.optString("timestamp"),
                details = (it.optJSONObject("details") ?: JSONObject()).toString(2),
            )
        },
        rawEvents = root.optString("raw_events"),
        exportJson = root.toString(2),
    )
}

private fun JSONObject.toAssignment() = TrialAssignment(
    trialIndex = optInt("trial_index"),
    taskId = optString("task_id"),
    condition = optString("condition"),
    injectError = optBoolean("inject_error"),
    instruction = nullableString("instruction"),
)

private fun JSONObject.toSessionSummary() = SessionSummary(
    sessionId = optString("session_id"),
    scope = optString("scope"),
    participant = optString("participant"),
    taskId = nullableString("task_id"),
    condition = optString("condition"),
    outcome = optString("outcome"),
    durationMs = optDouble("duration_ms", 0.0),
)

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONArray?.ints(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { optInt(it) }

private fun <T> JSONArray?.objects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList()
    else (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }
