package com.caddie.runtime.persistence.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caddie.runtime.persistence.journal.InsertResult
import com.caddie.runtime.persistence.journal.JournalConflictException
import com.caddie.runtime.persistence.journal.JournalEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeDatabaseTest {
    private lateinit var database: RuntimeDatabase

    @Before
    fun openDatabase() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                RuntimeDatabase::class.java,
            ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun appendIsOrderedAndALogicalDuplicateIsIdempotent() = runTest {
        val record2 = journalRow(recordId = "record-2", sequence = 2)
        val record1 = journalRow(recordId = "record-1", sequence = 1)
        val record1Retry =
            record1.copy(
                runSequence = 3,
                createdAtEpochMs = record1.createdAtEpochMs + 1,
            )

        assertEquals(InsertResult.INSERTED, database.journalDao().append(record2))
        assertEquals(InsertResult.INSERTED, database.journalDao().append(record1))
        assertEquals(InsertResult.ALREADY_PRESENT, database.journalDao().append(record1Retry))
        assertEquals(
            listOf(record1, record2),
            database.journalDao().records("run-1"),
        )
    }

    @Test
    fun conflictingDuplicateIsRejected() = runTest {
        database.journalDao().append(journalRow(recordId = "record-1", sequence = 1))

        try {
            database.journalDao().append(
                journalRow(
                    recordId = "record-1",
                    sequence = 1,
                    recordType = "RUN_FINISHED",
                ),
            )
            fail("Expected JournalConflictException")
        } catch (conflict: JournalConflictException) {
            assertTrue(conflict.message.orEmpty().contains("record-1"))
        }
    }

    private fun journalRow(
        recordId: String,
        sequence: Long,
        recordType: String = "RUN_STARTED",
    ) = JournalEntity(
        recordId = recordId,
        runId = "run-1",
        runSequence = sequence,
        createdAtEpochMs = 1_700_000_000_000,
        recordType = recordType,
    )
}
