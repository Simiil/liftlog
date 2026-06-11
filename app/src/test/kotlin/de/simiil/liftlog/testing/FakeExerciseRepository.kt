package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.util.UUID

class FakeExerciseRepository : ExerciseRepository {
    val all: MutableStateFlow<List<Exercise>> = MutableStateFlow(emptyList())
    val visible: MutableStateFlow<List<Exercise>> = MutableStateFlow(emptyList())
    val recentIds: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

    /** Names (lowercase) that should trigger a duplicate-name IllegalArgumentException in createCustom. */
    val duplicateNames: MutableSet<String> = mutableSetOf()

    /** Exercises created via createCustom, in order. */
    val created: MutableList<Exercise> = mutableListOf()

    /** (id, hidden) pairs recorded by setHidden calls. */
    val setHiddenCalls: MutableList<Pair<String, Boolean>> = mutableListOf()

    override fun observeAll(): Flow<List<Exercise>> = all

    override fun observeVisible(): Flow<List<Exercise>> = visible

    override fun observeRecentlyUsedIds(): Flow<List<String>> = recentIds

    override suspend fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
    ): Exercise {
        if (name.isBlank()) throw IllegalArgumentException("Name must not be blank")
        if (duplicateNames.contains(name.lowercase())) {
            throw IllegalArgumentException("An exercise with this name already exists")
        }
        val now = Instant.EPOCH
        val exercise =
            Exercise(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                muscleGroup = muscleGroup,
                equipment = equipment,
                isBuiltIn = false,
                isHidden = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        created += exercise
        visible.value = visible.value + exercise
        all.value = all.value + exercise
        return exercise
    }

    override suspend fun setHidden(
        id: String,
        hidden: Boolean,
    ) {
        setHiddenCalls += id to hidden
    }
}
