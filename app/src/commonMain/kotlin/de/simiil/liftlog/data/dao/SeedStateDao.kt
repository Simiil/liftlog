package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.simiil.liftlog.data.entity.SeedStateEntity

@Dao
interface SeedStateDao {
    /** Null = never seeded (fresh install, or first launch after the v3 migration). */
    @Query("SELECT appliedSeedVersion FROM seed_state WHERE id = 1")
    suspend fun appliedVersion(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeedStateEntity)
}
