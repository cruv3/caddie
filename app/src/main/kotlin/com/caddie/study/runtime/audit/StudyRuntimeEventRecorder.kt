package com.caddie.study.runtime.audit

/** One durable executor event associated with a live or isolated test session. */
data class StudyRuntimeEvent(
    val studyRunId: String,
    val participantId: String,
    val trialIndex: Int,
    val taskId: String,
    val eventType: String,
    val details: Map<String, Any?> = emptyMap(),
)

fun interface StudyRuntimeEventRecorder {
    suspend fun record(event: StudyRuntimeEvent)
}

/** Connects the process-owned executor to the active portal database. */
class StudyRuntimeEventRecorderSlot : StudyRuntimeEventRecorder {
    private data class Binding(val owner: Any, val recorder: StudyRuntimeEventRecorder)

    @Volatile private var binding: Binding? = null

    @Synchronized
    fun connect(owner: Any, recorder: StudyRuntimeEventRecorder) {
        check(binding == null || binding?.owner === owner) { "study runtime recorder already connected" }
        binding = Binding(owner, recorder)
    }

    @Synchronized
    fun disconnect(owner: Any) {
        if (binding?.owner === owner) binding = null
    }

    override suspend fun record(event: StudyRuntimeEvent) {
        binding?.recorder?.record(event)
    }
}
