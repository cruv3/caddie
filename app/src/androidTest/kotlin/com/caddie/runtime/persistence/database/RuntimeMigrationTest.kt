package com.caddie.runtime.persistence.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RuntimeDatabase::class.java,
        )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun schema_v1_opens_and_validates_without_destructive_fallback() {
        helper.createDatabase(TEST_DATABASE, 1).close()

        val database =
            Room.databaseBuilder(
                context,
                RuntimeDatabase::class.java,
                TEST_DATABASE,
            ).addMigrations(*RuntimeMigrations.ALL)
                .build()
        try {
            database.openHelper.writableDatabase
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "runtime-migration-test.db"
    }
}
