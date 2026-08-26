package com.caddie.study.store

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that existing study sessions survive native schema upgrades. */
@RunWith(AndroidJUnit4::class)
class StudyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudyRoomDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationFrom1To2PreservesSessionsAndAddsAnEmptyPreparedClaim() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """INSERT INTO sessions (
                    participant_id, mode, source, entered_at, status,
                    workflow_state, workflow_revision, current_trial_index,
                    assignment_json, assignment_hash
                ) VALUES ('P01', 'live', 'live_digital', '2026-08-01T00:00:00Z',
                    'in_progress', 'trial_ready', 4, 0, '{}', 'sha256:test')""".trimIndent(),
            )
            close()
        }

        val database = Room.databaseBuilder(
            context,
            StudyRoomDatabase::class.java,
            TEST_DATABASE,
        ).build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT participant_id, prepared_trial_index FROM sessions",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("P01", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFrom2To3AddsSeparateCourseBonusStorage() {
        helper.createDatabase(TEST_DATABASE, 2).close()

        val database = Room.databaseBuilder(
            context,
            StudyRoomDatabase::class.java,
            TEST_DATABASE,
        ).build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT count(*) FROM course_bonus_records",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "study-migration-test.db"
    }
}
