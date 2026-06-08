package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

class FakeExerciseDao : ExerciseDao {
    val rows = linkedMapOf<String, ExerciseEntity>()

    override fun observeAll(): Flow<List<ExerciseEntity>> = TODO("not used in repository write tests")
    override fun observeVisible(): Flow<List<ExerciseEntity>> = TODO("not used in repository write tests")

    override suspend fun findById(id: String): ExerciseEntity? =
        rows[id]?.takeIf { it.deletedAt == null }

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
}
