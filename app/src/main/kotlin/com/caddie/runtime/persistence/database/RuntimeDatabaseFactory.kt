package com.caddie.runtime.persistence.database

import android.content.Context
import androidx.room.Room

/** Creates the configured runtime Room database. */
internal object RuntimeDatabaseFactory {
    fun create(context: Context): RuntimeDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            RuntimeDatabase::class.java,
            "caddie-runtime.db",
        ).addMigrations(*RuntimeMigrations.ALL)
            .build()
}
