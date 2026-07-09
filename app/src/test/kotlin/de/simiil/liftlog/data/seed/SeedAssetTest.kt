package de.simiil.liftlog.data.seed

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeedAssetTest {
    private val seed =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<SeedFile>(File("src/main/assets/seed/exercises.v1.json").readText())

    @Test fun hasExactlyExpectedCount() = assertEquals(69, seed.exercises.size)

    @Test fun seedVersionMatchesSeederConstant() = assertEquals(ExerciseSeeder.SEED_VERSION, seed.seedVersion)

    @Test fun forceValuesAreRecognizedWhenPresent() {
        seed.exercises.mapNotNull { it.force }.forEach { f ->
            assertTrue("bad force $f", Force.entries.any { it.name == f })
        }
    }

    @Test fun secondaryMusclesAreValidDedupedAndExcludePrimary() {
        seed.exercises.forEach { e ->
            assertEquals("duplicate secondaries in ${e.name}", e.secondaryMuscleGroups.size, e.secondaryMuscleGroups.toSet().size)
            e.secondaryMuscleGroups.forEach { m ->
                assertTrue("bad secondary $m in ${e.name}", MuscleGroup.entries.any { it.name == m })
            }
            assertTrue("primary listed as secondary in ${e.name}", e.muscleGroup !in e.secondaryMuscleGroups)
        }
    }

    @Test fun idsAreUniqueValidUuids() {
        val ids = seed.exercises.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { java.util.UUID.fromString(it) } // throws on malformed
    }

    @Test fun namesAreUniqueCaseInsensitive() {
        val names = seed.exercises.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun enumsAreAllRecognized() {
        seed.exercises.forEach {
            assertTrue("bad muscle ${it.muscleGroup}", MuscleGroup.entries.any { e -> e.name == it.muscleGroup })
            assertTrue("bad equipment ${it.equipment}", Equipment.entries.any { e -> e.name == it.equipment })
        }
    }
}
