package com.caddie.app.runtime

import com.caddie.agent.core.OversightPolicy
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.RunId
import com.caddie.agent.core.StepId
import com.caddie.agent.core.StepOutcome
import com.caddie.agent.core.ToolCallTransformer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Supplies prompt and oversight behavior for one isolated native run. */
data class NativeRunProfile(
    val oversight: OversightPolicy,
    val requestFactory: RequestFactory,
    val transformer: ToolCallTransformer = ToolCallTransformer.Identity,
)

private typealias NativeStep = suspend (RunId, StepId) -> StepOutcome

/** Bundles the process-owned native runner with its idempotent shutdown action. */
class NativeRuntimeHost(
    val runner: NativeAgentRunner,
    private val normalStepForTask: (suspend (String) -> NativeStep)? = null,
    private val profileStep: ((NativeRunProfile) -> NativeStep)? = null,
    private val beforeFirstRun: suspend () -> Unit = {},
    private val afterNormalRun: (String, NativeTaskResult) -> Unit = { _, _ -> },
    private val clearNormalContextAction: () -> Unit = {},
    private val closeAction: suspend () -> Unit = {},
) {
    private val closed = AtomicBoolean()
    private val initialization = Mutex()
    private var initialized = false

    /** Runs one-time recovery before the participant submits the first task. */
    suspend fun warmUp(): Boolean = initialize()

    suspend fun run(task: String): NativeTaskResult {
        if (!initialize()) return NativeTaskResult.RuntimeClosed
        val selectedStep = normalStepForTask?.invoke(task)
        val result = if (selectedStep == null) runner.run(task) else runner.run(task, selectedStep)
        afterNormalRun(task, result)
        return result
    }

    suspend fun run(task: String, profile: NativeRunProfile): NativeTaskResult {
        if (!initialize()) return NativeTaskResult.RuntimeClosed
        val selectedStep = checkNotNull(profileStep) {
            "native runtime does not support run profiles"
        }.invoke(profile)
        return runner.run(task, selectedStep)
    }

    /** Executes a fixed study plan without invoking the model client. */
    suspend fun runDeterministic(
        task: String,
        completionText: String,
        execute: suspend (RunId) -> Boolean,
    ): NativeTaskResult {
        if (!initialize()) return NativeTaskResult.RuntimeClosed
        return runner.runDeterministic(task, execute, completionText)
    }

    private suspend fun initialize(): Boolean {
        if (closed.get()) return false
        initialization.withLock {
            if (!initialized) {
                beforeFirstRun()
                initialized = true
            }
        }
        return !closed.get()
    }

    suspend fun close() {
        if (closed.compareAndSet(false, true)) closeAction()
    }

    /** Clears process-local context shared only by completed normal runs. */
    fun clearNormalContext() = clearNormalContextAction()
}
