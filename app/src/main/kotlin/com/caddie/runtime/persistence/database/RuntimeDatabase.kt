package com.caddie.runtime.persistence.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.caddie.runtime.persistence.journal.JournalDao
import com.caddie.runtime.persistence.journal.JournalEntity

/** Defines the Room database that stores append-only runtime journal records. */
@Database(
    entities = [JournalEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class RuntimeDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
}
