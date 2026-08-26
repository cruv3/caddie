package com.caddie.runtime.persistence

import android.content.Context
import com.caddie.agent.core.RecoverySessionStore
import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.StepId
import com.caddie.runtime.persistence.database.RuntimeDatabaseFactory
import com.caddie.runtime.persistence.journal.RoomRecoverySessionStore
import com.caddie.runtime.persistence.recovery.RecoveryCoordinator
import com.caddie.runtime.persistence.recovery.RecoveryProbeRegistry
import com.caddie.runtime.persistence.recovery.StoreRecoveryRepository

/** Creates the Room-backed session store and recovery coordinator used by the app. */
class RuntimePersistence private constructor(
    val store: RecoverySessionStore,
    val actionJournal: ActionAttemptJournal,
    val recoveryCoordinator: RecoveryCoordinator,
    private val closeAction: () -> Unit,
) {
    fun close() = closeAction()

    companion object {
        fun create(
            context: Context,
            probeRegistry: RecoveryProbeRegistry,
        ): RuntimePersistence {
            val database = RuntimeDatabaseFactory.create(context.applicationContext)
            val store = RoomRecoverySessionStore(database)
            return RuntimePersistence(
                store = store,
                actionJournal = store,
                recoveryCoordinator =
                    RecoveryCoordinator(
                        repository = StoreRecoveryRepository(store),
                        probeRegistry = probeRegistry,
                        stepIds = { attemptId ->
                            StepId("recovery-${attemptId.value}")
                        },
                    ),
                closeAction = database::close,
            )
        }
    }
}
