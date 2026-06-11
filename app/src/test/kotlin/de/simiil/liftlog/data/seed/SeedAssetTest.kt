package de.simiil.liftlog.data.seed

import de.simiil.liftlog.domain.model.Equipment
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

    @Test fun seedVersionIsOne() = assertEquals(1, seed.seedVersion)

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
