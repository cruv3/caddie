package com.caddie.study.runtime.audit

/** Durable fact describing one controlled error applied to an effective tool call. */
data class ControlledErrorEvent(
    val studyRunId: String,
    val trialAttemptId: String,
    val trialIndex: Int,
    val taskId: String,
    val stepId: String,
    val errorVariantId: String,
    val field: String,
    val correctValue: String,
    val wrongValue: String,
)

/** Persists controlled-error facts before participant oversight or dispatch. */
fun interface ControlledErrorRecorder {
    suspend fun record(event: ControlledErrorEvent)
}

/** Connects the process-owned agent to the currently running portal database. */
class ControlledErrorRecorderSlot : ControlledErrorRecorder {
    private data class Binding(val owner: Any, val recorder: ControlledErrorRecorder)

    @Volatile private var binding: Binding? = null

    @Synchronized
    fun connect(owner: Any, recorder: ControlledErrorRecorder) {
        check(binding == null || binding?.owner === owner) { "study event recorder already connected" }
        binding = Binding(owner, recorder)
    }

    @Synchronized
    fun disconnect(owner: Any) {
        if (binding?.owner === owner) binding = null
    }

    override suspend fun record(event: ControlledErrorEvent) {
        val active = binding ?: error("study event recorder unavailable")
        active.recorder.record(event)
    }
}
