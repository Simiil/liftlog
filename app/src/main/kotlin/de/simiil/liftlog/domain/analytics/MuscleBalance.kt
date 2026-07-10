package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlin.math.max

/**
 * Radar spokes: merged muscle groups (spec 2026-07-10). 12 raw groups would crowd the chart and
 * manufacture fake "undertrained" alarms for isolation-only groups under single-group attribution.
 */
enum class RadarGroup {
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    QUADS,
    HAMS_GLUTES,
    CALVES,
    CORE,
}

/** Chart-facing merge of the 12 storage groups; OTHER → null = excluded from the radar. */
fun MuscleGroup.toRadarGroup(): RadarGroup? =
    when (this) {
        MuscleGroup.CHEST -> RadarGroup.CHEST
        MuscleGroup.BACK -> RadarGroup.BACK
        MuscleGroup.SHOULDERS -> RadarGroup.SHOULDERS
        MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS -> RadarGroup.ARMS
        MuscleGroup.QUADS -> RadarGroup.QUADS
        MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> RadarGroup.HAMS_GLUTES
        MuscleGroup.CALVES -> RadarGroup.CALVES
        MuscleGroup.ABS -> RadarGroup.CORE
        MuscleGroup.OTHER -> null
    }

/** Evidence-based weekly dose landmark; drawn as the radar's dashed target ring. */
const val TARGET_SETS_PER_WEEK = 10.0

/** One logged set joined with its exercise's classification (input row for [muscleBalance]). */
data class SetWithExercise(
    val sessionId: String,
    val exerciseId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
)

data class GroupBalance(
    val group: RadarGroup,
    val setsPerWeek: Double,
    /** Spoke length as a fraction of the rim (0..1). */
    val fraction: Double,
    /** Set-weighted trend over the window; null = no valid trend (hollow vertex). */
    val direction: TrendDirection?,
)

data class MuscleBalance(
    /** All 8 groups, in [RadarGroup] declaration order. */
    val groups: List<GroupBalance>,
    val rimSetsPerWeek: Double,
    /** Radius of the target ring as a fraction of the rim. */
    val targetFraction: Double,
    /** OTHER-classified sets in range (footnote only; not on the chart). */
    val unclassifiedSets: Int,
    /** True when no radar-classified sets exist in range (card shows the empty state). */
    val isEmpty: Boolean,
)

private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 604_800_000.0

private data class ExerciseTrend(
    val group: RadarGroup,
    val weight: Int,
    val percent: Double,
)

/**
 * Reduces set rows to the radar model (spec 2026-07-10 §Metric semantics).
 *
 * Dose = sets/week over the calendar window `[nowMillis − rangeDays, nowMillis]`; the divisor is
 * the effective window (starts at the first-ever set), floored at one week. Trend = per
 * contributing exercise on its headline-metric series ([summarize]'s `primary`: volume for
 * weighted, total reps for bodyweight) via [trend] with `windowDays = rangeDays`, combined as a
 * set-count-weighted mean of percent changes and classified with the shared ±1% thresholds.
 *
 * [rows] is the FULL set history (repository passes everything since 0); range filtering happens
 * here so per-exercise trend series keep their pre-window context ([trend] windows itself off
 * its last point).
 */
fun muscleBalance(
    rows: List<SetWithExercise>,
    rangeDays: Long,
    nowMillis: Long,
): MuscleBalance {
    val cutoff = nowMillis - rangeDays * DAY_MS
    val inRange = rows.filter { it.startedAt >= cutoff }
    val classified = inRange.filter { it.muscleGroup.toRadarGroup() != null }
    val unclassified = inRange.size - classified.size

    if (classified.isEmpty()) {
        return MuscleBalance(
            groups = RadarGroup.entries.map { GroupBalance(it, 0.0, 0.0, null) },
            rimSetsPerWeek = TARGET_SETS_PER_WEEK,
            targetFraction = 1.0,
            unclassifiedSets = unclassified,
            isEmpty = true,
        )
    }

    val firstEver = rows.minOf { it.startedAt }
    val effectiveStart = max(cutoff, firstEver)
    val weeks = max(1.0, (nowMillis - effectiveStart) / WEEK_MS)

    val setsPerWeek: Map<RadarGroup, Double> =
        classified
            .groupingBy { it.muscleGroup.toRadarGroup()!! }
            .eachCount()
            .mapValues { it.value / weeks }
    val rim = max(setsPerWeek.values.max(), TARGET_SETS_PER_WEEK)

    val exerciseTrends: List<ExerciseTrend> =
        classified
            .groupBy { it.exerciseId }
            .mapNotNull { (id, inRangeSets) ->
                val group = inRangeSets.first().muscleGroup.toRadarGroup()!!
                val history = rows.filter { it.exerciseId == id }
                val summary =
                    summarize(
                        equipment = history.first().equipment,
                        sets = history.map { DatedSet(it.sessionId, it.startedAt, it.weightKg, it.reps) },
                        nowMillis = nowMillis,
                    ) ?: return@mapNotNull null
                val ok =
                    trend(
                        summary.sessions.map { TrendPoint(it.timeMillis, it.primary) },
                        nowMillis,
                        windowDays = rangeDays,
                    ) as? TrendResult.Ok ?: return@mapNotNull null
                ExerciseTrend(group, inRangeSets.size, ok.percent)
            }
    val directionByGroup: Map<RadarGroup, TrendDirection> =
        exerciseTrends
            .groupBy { it.group }
            .mapValues { (_, ts) ->
                trendDirection(ts.sumOf { it.percent * it.weight } / ts.sumOf { it.weight })
            }

    return MuscleBalance(
        groups =
            RadarGroup.entries.map { g ->
                val spw = setsPerWeek[g] ?: 0.0
                GroupBalance(g, spw, spw / rim, directionByGroup[g])
            },
        rimSetsPerWeek = rim,
        targetFraction = TARGET_SETS_PER_WEEK / rim,
        unclassifiedSets = unclassified,
        isEmpty = false,
    )
}
