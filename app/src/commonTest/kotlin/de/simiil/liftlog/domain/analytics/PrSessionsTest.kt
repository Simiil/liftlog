package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun highRepFirstSession_isAVolumePr() {
        // Sets >12 reps are Epley-excluded from e1RM, but volume counts every rep — so a
        // first-ever high-rep session IS a PR under the volume headline (40×15 = 600 kg).
        val result =
            prSessionIds(
                setsByExercise = mapOf("e1" to listOf(DatedSet("s1", now - 5 * day, 40.0, 15))),
                equipmentById = mapOf("e1" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(setOf("s1"), result)
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
                                DatedSet("s3", now - 5 * day, 65.0, 5), // PR (more volume)
                            ),
                    ),
                equipmentById = mapOf("bench" to Equipment.BARBELL, "row" to Equipment.BARBELL),
                nowMillis = now,
            )
        assertEquals(setOf("s1", "s2", "s3"), result)
    }

    @Test fun bodyweightExercise_flagsTotalRepsPr() {
        // Bodyweight headline is total reps (volume is always 0). s2's two-set session beats
        // s1 on total work (10 > 8) even though its best single set (5) is lower; s3 trails.
        val result =
            prSessionIds(
                setsByExercise =
                    mapOf(
                        "pullup" to
                            listOf(
                                DatedSet("s1", now - 15 * day, 0.0, 8), // totalReps 8 — PR (first ever)
                                DatedSet("s2", now - 10 * day, 0.0, 5), // session s2, two sets:
                                DatedSet("s2", now - 10 * day, 0.0, 5), // totalReps 10 — PR (more total work)
                                DatedSet("s3", now - 5 * day, 0.0, 9), // totalReps 9 — below best, no flag
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
