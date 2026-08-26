package com.caddie.context

import com.caddie.agent.core.EventSink
import com.caddie.agent.core.RunId
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunSnapshot
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.reduce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stores run records in memory and reconstructs snapshots for tests or transient use. */
class InMemorySessionStore : SessionStore, EventSink {
    private val mutex = Mutex()
    private val recordsByRun =
        linkedMapOf<RunId, LinkedHashMap<String, RunRecord>>()

    override suspend fun append(event: RunRecord) {
        mutex.withLock {
            recordsByRun
                .getOrPut(event.runId) { linkedMapOf() }
                .putIfAbsent(event.recordId, event)
        }
    }

    override suspend fun emit(record: RunRecord) {
        append(record)
    }

    override suspend fun snapshot(runId: RunId): RunSnapshot =
        reduce(records(runId), recoveredProcess = false)

    suspend fun records(runId: RunId): List<RunRecord> =
        mutex.withLock {
            recordsByRun[runId]?.values?.toList().orEmpty()
        }
}
