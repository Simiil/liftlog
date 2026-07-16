package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.mapper.toDomain
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeAll() = dao.observeAll().map { it.map(ExerciseEntity::toDomain) }

    override fun observeVisible() = dao.observeVisible().map { it.map(ExerciseEntity::toDomain) }

    override suspend fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
    ): Exercise {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Exercise name must not be blank" }
        return transactor.immediate {
            require(dao.findLiveByName(trimmed) == null) { "An exercise named \"$trimmed\" already exists" }
            val now = clock.now().toEpochMilliseconds()
            val entity =
                ExerciseEntity(
                    id = Uuid.random().toString(),
                    name = trimmed,
                    muscleGroup = muscleGroup,
                    equipment = equipment,
                    isBuiltIn = false,
                    isHidden = false,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            dao.insert(entity)
            entity.toDomain()
        }
    }

    override suspend fun setHidden(
        id: String,
        hidden: Boolean,
    ) {
        val current = dao.findById(id) ?: return
        dao.update(current.copy(isHidden = hidden, updatedAt = clock.now().toEpochMilliseconds()))
    }

    override fun observeRecentlyUsedIds(): Flow<List<String>> =
        dao.observeRecentlyUsedExerciseIds().map { rows -> rows.map { it.exerciseId } }
}
