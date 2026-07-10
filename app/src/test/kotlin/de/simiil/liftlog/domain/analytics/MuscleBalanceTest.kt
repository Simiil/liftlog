package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleBalanceTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun row(
        group: MuscleGroup,
        startedAt: Long,
        sessionId: String = "s$startedAt",
        exerciseId: String = "e-$group",
        weightKg: Double = 100.0,
        reps: Int = 5,
        equipment: Equipment = Equipment.BARBELL,
    ) = SetWithExercise(sessionId, exerciseId, startedAt, weightKg, reps, group, equipment)

    @Test fun mapping_mergesArmsLegsCore_excludesOther() {
        assertEquals(RadarGroup.CHEST, MuscleGroup.CHEST.toRadarGroup())
        assertEquals(RadarGroup.BACK, MuscleGroup.BACK.toRadarGroup())
        assertEquals(RadarGroup.SHOULDERS, MuscleGroup.SHOULDERS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.BICEPS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.TRICEPS.toRadarGroup())
        assertEquals(RadarGroup.ARMS, MuscleGroup.FOREARMS.toRadarGroup())
        assertEquals(RadarGroup.QUADS, MuscleGroup.QUADS.toRadarGroup())
        assertEquals(RadarGroup.HAMS_GLUTES, MuscleGroup.HAMSTRINGS.toRadarGroup())
        assertEquals(RadarGroup.HAMS_GLUTES, MuscleGroup.GLUTES.toRadarGroup())
        assertEquals(RadarGroup.CALVES, MuscleGroup.CALVES.toRadarGroup())
        assertEquals(RadarGroup.CORE, MuscleGroup.ABS.toRadarGroup())
        assertNull(MuscleGroup.OTHER.toRadarGroup())
    }

    @Test fun setsPerWeek_dividesByEffectiveWindow() {
        // 4 sessions × 2 sets over 4 weeks; first-ever set at now-28d inside a 30d range:
        // effective window = 28d = 4 weeks → 8 sets / 4 weeks = 2.0.
        val rows =
            listOf(28, 21, 14, 7).flatMap { d ->
                List(2) { row(MuscleGroup.CHEST, now - d * day, sessionId = "s$d") }
            }
        val b = muscleBalance(rows, rangeDays = 30, nowMillis = now)
        val chest = b.groups.first { it.group == RadarGroup.CHEST }
        assertEquals(2.0, chest.setsPerWeek, 1e-9)
        assertEquals(10.0, b.rimSetsPerWeek, 1e-9) // rim floors at the target
        assertEquals(0.2, chest.fraction, 1e-9)
        assertEquals(1.0, b.targetFraction, 1e-9)
        assertFalse(b.isEmpty)
    }

    @Test fun singleRecentSession_flooredAtOneWeek_rimAboveTarget() {
        // 12 sets yesterday, nothing else ever: window floors at 1 week → 12 sets/week,
        // rim = max(12, 10) = 12, chest hits the rim, target ring sits at 10/12.
        val rows = List(12) { row(MuscleGroup.CHEST, now - day, sessionId = "s1") }
        val b = muscleBalance(rows, 30, now)
        val chest = b.groups.first { it.group == RadarGroup.CHEST }
        assertEquals(12.0, chest.setsPerWeek, 1e-9)
        assertEquals(12.0, b.rimSetsPerWeek, 1e-9)
        assertEquals(1.0, chest.fraction, 1e-9)
        assertEquals(10.0 / 12.0, b.targetFraction, 1e-9)
    }

    @Test fun outOfRangeSets_dontCountTowardDose() {
        val rows =
            listOf(
                row(MuscleGroup.CHEST, now - 40 * day),
                row(MuscleGroup.BACK, now - day),
            )
        val b = muscleBalance(rows, 30, now)
        assertEquals(0.0, b.groups.first { it.group == RadarGroup.CHEST }.setsPerWeek, 1e-9)
        // History predates the window → effective window is the full 30d.
        assertEquals(7.0 / 30.0, b.groups.first { it.group == RadarGroup.BACK }.setsPerWeek, 1e-9)
    }

    @Test fun otherSets_feedFootnoteNotSpokes_aloneMeansEmpty() {
        val b = muscleBalance(List(2) { row(MuscleGroup.OTHER, now - day, sessionId = "s1") }, 30, now)
        assertTrue(b.isEmpty)
        assertEquals(2, b.unclassifiedSets)
        assertTrue(b.groups.all { it.setsPerWeek == 0.0 })
    }

    @Test fun emptyInput_isEmpty() {
        val b = muscleBalance(emptyList(), 30, now)
        assertTrue(b.isEmpty)
        assertEquals(0, b.unclassifiedSets)
        assertEquals(8, b.groups.size)
    }

    @Test fun groupTrend_setWeightedMeanOfExerciseTrends() {
        // ex-rise: volumes 100→110→120 (+20%), ex-fall: 100→95→90 (−10%), 3 sets each
        // → weighted mean (20·3 − 10·3)/6 = +5% → UP.
        val rising =
            listOf(14 to 100.0, 7 to 110.0, 0 to 120.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "r$d", exerciseId = "ex-rise", weightKg = w, reps = 1)
            }
        val falling =
            listOf(14 to 100.0, 7 to 95.0, 0 to 90.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "f$d", exerciseId = "ex-fall", weightKg = w, reps = 1)
            }
        val b = muscleBalance(rising + falling, 90, now)
        assertEquals(TrendDirection.UP, b.groups.first { it.group == RadarGroup.CHEST }.direction)
    }

    @Test fun groupTrend_balancedOpposition_isFlat() {
        // +20% vs −20% with equal set counts → 0% → FLAT.
        val rising =
            listOf(14 to 100.0, 7 to 110.0, 0 to 120.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "r$d", exerciseId = "ex-rise", weightKg = w, reps = 1)
            }
        val falling =
            listOf(14 to 100.0, 7 to 90.0, 0 to 80.0).map { (d, w) ->
                row(MuscleGroup.CHEST, now - d * day, sessionId = "f$d", exerciseId = "ex-fall", weightKg = w, reps = 1)
            }
        val b = muscleBalance(rising + falling, 90, now)
        assertEquals(TrendDirection.FLAT, b.groups.first { it.group == RadarGroup.CHEST }.direction)
    }

    @Test fun groupTrend_tooFewSessions_isNull() {
        val rows =
            listOf(
                row(MuscleGroup.BACK, now - 7 * day, sessionId = "a", exerciseId = "ex-b"),
                row(MuscleGroup.BACK, now - day, sessionId = "b", exerciseId = "ex-b"),
            )
        val b = muscleBalance(rows, 90, now)
        assertNull(b.groups.first { it.group == RadarGroup.BACK }.direction)
    }

    @Test fun groupTrend_staleExercise_isNull_doseStillCounts() {
        // 3 sessions, last one 30d ago: inside a 90d dose window but stale for trend (>21d).
        val rows =
            listOf(44, 37, 30).map { d ->
                row(MuscleGroup.QUADS, now - d * day, sessionId = "q$d", exerciseId = "ex-q", weightKg = 100.0 + d)
            }
        val b = muscleBalance(rows, 90, now)
        val quads = b.groups.first { it.group == RadarGroup.QUADS }
        assertNull(quads.direction)
        assertTrue(quads.setsPerWeek > 0.0)
    }

    @Test fun bodyweightGroup_getsDoseAndRepsTrend() {
        // weightKg = 0 throughout; total reps 8→10→12 (+50%) → dose counted, trend UP.
        val rows =
            listOf(14 to 8, 7 to 10, 0 to 12).map { (d, reps) ->
                row(
                    MuscleGroup.ABS,
                    now - d * day,
                    sessionId = "c$d",
                    exerciseId = "ex-abs",
                    weightKg = 0.0,
                    reps = reps,
                    equipment = Equipment.BODYWEIGHT,
                )
            }
        val b = muscleBalance(rows, 90, now)
        val core = b.groups.first { it.group == RadarGroup.CORE }
        assertTrue(core.setsPerWeek > 0.0)
        assertEquals(TrendDirection.UP, core.direction)
    }
}
