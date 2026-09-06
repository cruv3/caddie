package com.caddie.context.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class ProjectionMetadata(val frontier: String, val sourceHash: String)
data class ReplaySafetyMetadata(val classification: String, val safetyState: String)

/** Atomically stages, validates, and activates one complete context generation. */
@Dao
abstract class ContextDao {
    @Query("DELETE FROM context_content WHERE owner_id = :owner")
    protected abstract suspend fun deleteOwnerContents(owner: String)

    @Query("DELETE FROM context_projection WHERE owner_id = :owner")
    protected abstract suspend fun deleteOwnerProjections(owner: String)

    @Query("DELETE FROM context_replay WHERE owner_id = :owner")
    protected abstract suspend fun deleteOwnerReplays(owner: String)

    @Transaction
    open suspend fun deleteOwner(owner: String) {
        require(owner.isNotBlank())
        deleteOwnerProjections(owner)
        deleteOwnerReplays(owner)
        deleteOwnerContents(owner) // Foreign-key cascade removes associated vectors.
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertContents(rows: List<ContextContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertVectors(rows: List<ContextVectorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProjections(rows: List<ContextProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertReplays(rows: List<ContextReplayEntity>)

    @Query("UPDATE context_content SET active = 0 WHERE owner_id IN (:owners)")
    protected abstract suspend fun deactivateContents(owners: List<String>)

    @Query("UPDATE context_vector SET active = 0 WHERE owner_id IN (:owners)")
    protected abstract suspend fun deactivateVectors(owners: List<String>)

    @Query("UPDATE context_projection SET active = 0 WHERE owner_id IN (:owners)")
    protected abstract suspend fun deactivateProjections(owners: List<String>)

    @Query("UPDATE context_replay SET active = 0 WHERE owner_id IN (:owners)")
    protected abstract suspend fun deactivateReplays(owners: List<String>)

    @Query("UPDATE context_content SET active = 1 WHERE generation_id = :generation AND owner_id IN (:owners)")
    protected abstract suspend fun activateContents(generation: String, owners: List<String>)

    @Query("UPDATE context_vector SET active = 1 WHERE generation_id = :generation AND owner_id IN (:owners)")
    protected abstract suspend fun activateVectors(generation: String, owners: List<String>)

    @Query("UPDATE context_projection SET active = 1 WHERE generation_id = :generation AND owner_id IN (:owners)")
    protected abstract suspend fun activateProjections(generation: String, owners: List<String>)

    @Query("UPDATE context_replay SET active = 1 WHERE generation_id = :generation AND owner_id IN (:owners)")
    protected abstract suspend fun activateReplays(generation: String, owners: List<String>)

    @Query("DELETE FROM context_content WHERE generation_id != :generation AND owner_id IN (:owners)")
    protected abstract suspend fun deleteOldContents(generation: String, owners: List<String>)

    @Query("DELETE FROM context_projection WHERE generation_id != :generation AND owner_id IN (:owners)")
    protected abstract suspend fun deleteOldProjections(generation: String, owners: List<String>)

    @Query("DELETE FROM context_replay WHERE generation_id != :generation AND owner_id IN (:owners)")
    protected abstract suspend fun deleteOldReplays(generation: String, owners: List<String>)

    @Query("SELECT * FROM context_content WHERE owner_id = :owner AND active = 1 ORDER BY content_id")
    abstract suspend fun activeContentRows(owner: String): List<ContextContentEntity>

    @Query("SELECT COUNT(*) FROM context_vector WHERE owner_id = :owner AND model_id = :model AND dimension = :dimension AND active = 1")
    abstract suspend fun activeVectorCount(owner: String, model: String, dimension: Int): Int

    @Query("SELECT frontier, source_hash AS sourceHash FROM context_projection WHERE owner_id = :owner AND active = 1 ORDER BY projection_id")
    abstract suspend fun activeProjectionMetadata(owner: String): List<ProjectionMetadata>

    @Query("SELECT classification, safety_state AS safetyState FROM context_replay WHERE owner_id = :owner AND active = 1 ORDER BY replay_id")
    abstract suspend fun activeReplaySafety(owner: String): List<ReplaySafetyMetadata>

    @Query(
        """
        SELECT generation_id FROM context_content
        UNION SELECT generation_id FROM context_vector
        UNION SELECT generation_id FROM context_projection
        UNION SELECT generation_id FROM context_replay
        """,
    )
    abstract suspend fun allGenerationIds(): List<String>

    @Transaction
    open suspend fun activateCorpus(
        generation: String,
        owners: List<String>,
        contents: List<ContextContentEntity>,
        vectors: List<ContextVectorEntity>,
        projections: List<ContextProjectionEntity>,
        replays: List<ContextReplayEntity>,
        afterStaging: suspend () -> Unit = {},
    ) {
        insertContents(contents)
        insertVectors(vectors)
        insertProjections(projections)
        insertReplays(replays)
        afterStaging()
        require(contents.all { it.generationId == generation } &&
            vectors.all { it.generationId == generation && it.dimension > 0 } &&
            projections.all { it.generationId == generation } &&
            replays.all { it.generationId == generation })
        require(owners.isNotEmpty())
        deactivateContents(owners)
        deactivateVectors(owners)
        deactivateProjections(owners)
        deactivateReplays(owners)
        activateContents(generation, owners)
        activateVectors(generation, owners)
        activateProjections(generation, owners)
        activateReplays(generation, owners)
        deleteOldProjections(generation, owners)
        deleteOldReplays(generation, owners)
        deleteOldContents(generation, owners)
    }
}
