package com.caddie.runtime.persistence

import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.RunState
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.reduce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps full active-run records in memory while journaling minimized recovery state. */
class ProcessSessionStore(
    private val durableStore: SessionStore,
) : SessionStore {
    private val mutex = Mutex()
    private val activeRecords = linkedMapOf<RunId, LinkedHashMap<String, RunRecord>>()

    override suspend fun append(event: RunRecord) {
        mutex.withLock {
            val records = activeRecords.getOrPut(event.runId) { linkedMapOf() }
            val existing = records[event.recordId]
            if (existing != null) {
                require(existing == event) {
                    "Conflicting active-process record: ${event.recordId}"
                }
                return
            }
            durableStore.append(event)
            records[event.recordId] = event
        }
    }

    override suspend fun snapshot(runId: RunId): RunSnapshot {
        val current = mutex.withLock { activeRecords[runId]?.values?.toList() }
        if (!current.isNullOrEmpty()) {
            return reduce(current, recoveredProcess = false)
        }

        val recovered = durableStore.snapshot(runId)
        val recoveredState = when (recovered.state) {
            RunState.CREATED,
            RunState.RUNNING,
            -> RunState.PAUSED_RECOVERABLE
            else -> recovered.state
        }
        return recovered.copy(state = recoveredState, messages = emptyList())
    }
}
