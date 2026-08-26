package com.caddie.agent.core

/** Provides a validated identifier for a conversation session. */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }
}

/** Provides a validated identifier for an agent run. */
@JvmInline
value class RunId(val value: String) {
    init {
        require(value.isNotBlank()) { "RunId must not be blank" }
    }
}

/** Provides a validated identifier for one run step. */
@JvmInline
value class StepId(val value: String) {
    init {
        require(value.isNotBlank()) { "StepId must not be blank" }
    }
}

/** Provides a validated identifier for a model tool call. */
@JvmInline
value class ToolCallId(val value: String) {
    init {
        require(value.isNotBlank()) { "ToolCallId must not be blank" }
    }
}

/** Provides a validated identifier for a real-world action attempt. */
@JvmInline
value class AttemptId(val value: String) {
    init {
        require(value.isNotBlank()) { "AttemptId must not be blank" }
    }
}
