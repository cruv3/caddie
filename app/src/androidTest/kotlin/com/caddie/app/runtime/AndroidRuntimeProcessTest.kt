package com.caddie.app.runtime

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.app.CaddieApplication
import com.caddie.runtime.persistence.RuntimePersistence
import com.caddie.runtime.persistence.recovery.RecoveryProbeRegistry
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRuntimeProcessTest {
    @Test
    fun applicationOwnsOneRuntimeProcess() {
        val application = ApplicationProvider.getApplicationContext<CaddieApplication>()

        assertSame(application.runtimeProcess, application.runtimeProcess)
        assertTrue(application.runtimeProcess.studyExecutionConfigured)
    }

    @Test
    fun persistenceUsesOneStoreForRunsAndActionClaims() {
        val application = ApplicationProvider.getApplicationContext<CaddieApplication>()
        val persistence = RuntimePersistence.create(
            application,
            RecoveryProbeRegistry { null },
        )
        try {
            assertSame(persistence.store, persistence.actionJournal)
        } finally {
            persistence.close()
        }
    }
}
