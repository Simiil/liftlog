package de.simiil.liftlog.data.mapper

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class MapperRoundTripTest {
    @Test fun exercise_entity_to_domain_to_entity_isIdentity() {
        val e =
            ExerciseEntity(
                "id1",
                "Bench",
                MuscleGroup.CHEST,
                Equipment.BARBELL,
                isBuiltIn = true,
                isHidden = false,
                createdAt = 1_000,
                updatedAt = 2_000,
                deletedAt = null,
            )
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test fun loggedSet_nullableTimestamps_roundTrip() {
        val s =
            LoggedSetEntity(
                "s1",
                "se1",
                82.5,
                5,
                1,
                completedAt = 3_000,
                createdAt = 3_000,
                updatedAt = 3_000,
                deletedAt = 4_000,
            )
        assertEquals(s, s.toDomain().toEntity())
    }
}
