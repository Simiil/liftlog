package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.dao.RecentExercise
import de.simiil.liftlog.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeExerciseDao : ExerciseDao {
    val rows = linkedMapOf<String, ExerciseEntity>()

    /** Settable backing store for recently-used projection; preload in tests to drive observeRecentlyUsedIds(). */
    val recentlyUsed = MutableStateFlow<List<RecentExercise>>(emptyList())

    override fun observeAll(): Flow<List<ExerciseEntity>> = TODO("not used in repository write tests")

    override fun observeVisible(): Flow<List<ExerciseEntity>> = TODO("not used in repository write tests")

    override suspend fun findById(id: String): ExerciseEntity? = rows[id]?.takeIf { it.deletedAt == null }

    override suspend fun findByIdAny(id: String): ExerciseEntity? = rows[id]

    override suspend fun findLiveByName(name: String): ExerciseEntity? =
        rows.values.firstOrNull { it.deletedAt == null && it.name.equals(name, ignoreCase = true) }

    override suspend fun insertIgnore(exercises: List<ExerciseEntity>) = TODO("not used in repository tests")

    override suspend fun insert(exercise: ExerciseEntity) {
        rows[exercise.id] = exercise
    }

    override suspend fun update(exercise: ExerciseEntity) {
        rows[exercise.id] = exercise
    }

    override suspend fun countLive(): Int = rows.values.count { it.deletedAt == null }

    override fun observeRecentlyUsedExerciseIds(): Flow<List<RecentExercise>> = recentlyUsed
}
