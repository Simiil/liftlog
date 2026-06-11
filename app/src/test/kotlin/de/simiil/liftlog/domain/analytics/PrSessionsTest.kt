package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import org.junit.Assert.assertEquals
import org.junit.Test

class PrSessionsTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    @Test fun emptyInput_returnsEmptySet() = assertEquals(emptySet<String>(), prSessionIds(emptyMap(), emptyMap(), now))

    @Test fun firstSessionOfAnExercise_isAPr() {
        val result =
            prSessionIds(
                setsByExercise = mapOf("e1" to listOf(DatedSet("s1", now - 5 * day, 100.0, 5))),
                equipmentById = mapOf("e1" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(setOf("s1"), result)
    }

    @Test fun tieDoesNotFlag() {
        val result =
            prSessionIds(
                setsByExercise =
                    mapOf(
                        "e1" to
                            listOf(
                                DatedSet("s1", now - 10 * day, 100.0, 5),
                                DatedSet("s2", now - 5 * day, 100.0, 5), // equal best — not a PR
                            ),
                    ),
                equipmentById = mapOf("e1" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(setOf("s1"), result)
    }

    @Test fun highRepOnlyFirstSession_isNotAnE1rmPr() {
        // Sets >12 reps are Epley-excluded → session e1RM stays 0.0, not > the 0.0 baseline,
        // so even a first-ever session isn't flagged when it has no e1RM-eligible set.
        val result =
            prSessionIds(
                setsByExercise = mapOf("e1" to listOf(DatedSet("s1", now - 5 * day, 40.0, 15))),
                equipmentById = mapOf("e1" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(emptySet<String>(), result)
    }

    @Test fun unionsAcrossExercises() {
        val result =
            prSessionIds(
                setsByExercise =
                    mapOf(
                        "bench" to
                            listOf(
                                DatedSet("s1", now - 20 * day, 100.0, 5), // PR (first ever)
                                DatedSet("s2", now - 10 * day, 100.0, 5), // tie — no flag from bench
                            ),
                        "row" to
                            listOf(
                                DatedSet("s2", now - 10 * day, 60.0, 5), // PR (first for row) — flags s2
                                DatedSet("s3", now - 5 * day, 65.0, 5), // PR (heavier)
                            ),
                    ),
                equipmentById = mapOf("bench" to Equipment.BARBELL, "row" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(setOf("s1", "s2", "s3"), result)
    }

    @Test fun bodyweightExercise_flagsRepsPr() {
        val result =
            prSessionIds(
                setsByExercise =
                    mapOf(
                        "pullup" to
                            listOf(
                                DatedSet("s1", now - 15 * day, 0.0, 8), // PR (first ever)
                                DatedSet("s2", now - 10 * day, 0.0, 10), // PR (more reps)
                                DatedSet("s3", now - 5 * day, 0.0, 9), // below best — no flag
                            ),
                    ),
                equipmentById = mapOf("pullup" to Equipment.BODYWEIGHT),
                nowMillis = now,
            )
        assertEquals(setOf("s1", "s2"), result)
    }

    @Test fun exerciseWithoutKnownEquipment_isSkipped() {
        val result =
            prSessionIds(
                setsByExercise = mapOf("ghost" to listOf(DatedSet("s1", now - 5 * day, 100.0, 5))),
                equipmentById = emptyMap(),
                nowMillis = now,
            )
        assertEquals(emptySet<String>(), result)
    }
}
