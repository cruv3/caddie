package com.caddie.runtime.persistence.journal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Reads and appends durable journal records in Room. */
@Dao
internal abstract class JournalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract suspend fun insertIgnoringConflict(row: JournalEntity): Long

    @Query("SELECT * FROM runtime_journal WHERE record_id = :recordId")
    internal abstract suspend fun byId(recordId: String): JournalEntity?

    @Query(
        """
        SELECT * FROM runtime_journal
        WHERE run_id = :runId
        ORDER BY run_sequence ASC
        """,
    )
    abstract suspend fun records(runId: String): List<JournalEntity>

    @Query(
        """
        SELECT * FROM runtime_journal
        ORDER BY run_id ASC, run_sequence ASC
        """,
    )
    internal abstract suspend fun allRecords(): List<JournalEntity>

    @Query(
        """
        SELECT COALESCE(MAX(run_sequence), 0)
        FROM runtime_journal
        WHERE run_id = :runId
        """,
    )
    abstract suspend fun lastSequence(runId: String): Long

    @Transaction
    open suspend fun append(row: JournalEntity): InsertResult {
        if (insertIgnoringConflict(row) != -1L) {
            return InsertResult.INSERTED
        }

        if (byId(row.recordId)?.sameDurablePayloadAs(row) == true) {
            return InsertResult.ALREADY_PRESENT
        }

        throw JournalConflictException(row.recordId)
    }
}

/** Describes whether a journal row was inserted or already existed. */
internal enum class InsertResult {
    INSERTED,
    ALREADY_PRESENT,
}

/** Signals conflicting content for an existing journal record. */
internal class JournalConflictException(recordId: String) :
    IllegalStateException("Conflicting journal record: $recordId")
