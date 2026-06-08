package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.simiil.liftlog.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND isHidden = 0 ORDER BY name COLLATE NOCASE")
    fun observeVisible(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findLiveByName(name: String): ExerciseEntity?

    /** Seeder idempotency: existing PKs are left untouched (hides/tombstones survive). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercises: List<ExerciseEntity>)

    @Insert suspend fun insert(exercise: ExerciseEntity)
    @Update suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercises WHERE deletedAt IS NULL") suspend fun countLive(): Int
}
