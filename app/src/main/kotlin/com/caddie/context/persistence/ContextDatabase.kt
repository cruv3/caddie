package com.caddie.context.persistence

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

/** Stores encrypted generated context separately from the append-only runtime journal. */
@Database(
    entities = [
        ContextContentEntity::class,
        ContextVectorEntity::class,
        ContextProjectionEntity::class,
        ContextReplayEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ContextDatabase : RoomDatabase() {
    abstract fun dao(): ContextDao

    companion object {
        const val DATABASE_NAME = "caddie-context.db"

        fun open(context: Context): ContextDatabase =
            Room.databaseBuilder(context, ContextDatabase::class.java, DATABASE_NAME).build()
    }
}

@Entity(
    tableName = "context_content",
    indices = [
        Index(value = ["generation_id", "owner_id", "source_hash"], unique = true),
        Index(value = ["owner_id", "active"]),
    ],
)
data class ContextContentEntity(
    @PrimaryKey @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "envelope_version") val envelopeVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "active") val active: Boolean,
)

@Entity(
    tableName = "context_vector",
    foreignKeys = [
        ForeignKey(
            entity = ContextContentEntity::class,
            parentColumns = ["content_id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["content_id"]),
        Index(
            value = ["generation_id", "owner_id", "source_hash", "model_id", "dimension"],
            unique = true,
        ),
        Index(value = ["owner_id", "model_id", "dimension", "active"]),
    ],
)
data class ContextVectorEntity(
    @PrimaryKey @ColumnInfo(name = "vector_id") val vectorId: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "dimension") val dimension: Int,
    @ColumnInfo(name = "envelope_version") val envelopeVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "active") val active: Boolean,
)

@Entity(
    tableName = "context_projection",
    indices = [
        Index(value = ["generation_id", "owner_id", "source_hash", "frontier"], unique = true),
        Index(value = ["owner_id", "active"]),
    ],
)
data class ContextProjectionEntity(
    @PrimaryKey @ColumnInfo(name = "projection_id") val projectionId: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "frontier") val frontier: String,
    @ColumnInfo(name = "envelope_version") val envelopeVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "active") val active: Boolean,
)

@Entity(
    tableName = "context_replay",
    indices = [
        Index(value = ["generation_id", "owner_id", "trajectory_id", "revision_id"], unique = true),
        Index(value = ["owner_id", "active"]),
    ],
)
data class ContextReplayEntity(
    @PrimaryKey @ColumnInfo(name = "replay_id") val replayId: String,
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "trajectory_id") val trajectoryId: String,
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "classification") val classification: String,
    @ColumnInfo(name = "safety_state") val safetyState: String,
    @ColumnInfo(name = "envelope_version") val envelopeVersion: Int,
    @ColumnInfo(name = "nonce") val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext") val ciphertext: ByteArray,
    @ColumnInfo(name = "active") val active: Boolean,
)
