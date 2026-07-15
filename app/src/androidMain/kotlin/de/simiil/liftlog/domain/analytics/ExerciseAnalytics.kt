package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment

/** One logged set with its session + session start time (SetRow minus the exerciseId grouping key). */
data class DatedSet(
    val sessionId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
)

/** One session's reduced view + PR flags + the chart "primary" value. */
data class SessionPoint(
    val sessionId: String,
    val timeMillis: Long,
    val sets: List<SetEntry>,
    val metrics: SessionMetrics,
    val primary: Double,
    val isPrE1rm: Boolean,
    val isPrTopSet: Boolean,
    val isPrReps: Boolean,
    val isPrVolume: Boolean,
    val isPrTotalReps: Boolean,
    val isPr: Boolean,
)

data class ExerciseSummary(
    val bodyweight: Boolean,
    val sessions: List<SessionPoint>, // chronological
    val trend: TrendResult,
    val currentValue: Double, // last session's primary
    val lastTrainedAt: Long,
)

/**
 * Builds the per-exercise analytics summary from set-level rows (04-analytics-spec §1–§4).
 * [sets] need not be sorted. Returns null when there are no sets.
 */
fun summarize(
    equipment: Equipment,
    sets: List<DatedSet>,
    nowMillis: Long,
): ExerciseSummary? {
    if (sets.isEmpty()) return null
    // Group by session, preserving chronological order by the session's startedAt.
    val grouped =
        sets
            .groupBy { it.sessionId }
            .entries
            .sortedBy { it.value.first().startedAt }

    val bodyweight = equipment == Equipment.BODYWEIGHT && sets.all { it.weightKg == 0.0 }

    var bestE1rm = 0.0
    var bestTop = 0.0
    var bestReps = 0
    var bestVolume = 0.0
    var bestTotalReps = 0
    val sessions =
        grouped.map { (id, rows) ->
            val entries = rows.map { SetEntry(it.weightKg, it.reps) }
            val m = sessionMetrics(entries)
            val prE1rm = m.e1rmKg > bestE1rm
            if (prE1rm) bestE1rm = m.e1rmKg
            val prTop = m.topSetKg > bestTop
            if (prTop) bestTop = m.topSetKg
            val prReps = m.maxReps > bestReps
            if (prReps) bestReps = m.maxReps
            val prVolume = m.volumeKg > bestVolume
            if (prVolume) bestVolume = m.volumeKg
            val prTotalReps = m.totalReps > bestTotalReps
            if (prTotalReps) bestTotalReps = m.totalReps
            SessionPoint(
                sessionId = id,
                timeMillis = rows.first().startedAt,
                sets = entries,
                metrics = m,
                // Headline metric: total work — volume for weighted, total reps for bodyweight
                // (where volume is always 0). e1RM/top-set/max-reps stay available as chart toggles.
                primary = if (bodyweight) m.totalReps.toDouble() else m.volumeKg,
                isPrE1rm = prE1rm,
                isPrTopSet = prTop,
                isPrReps = prReps,
                isPrVolume = prVolume,
                isPrTotalReps = prTotalReps,
                isPr = if (bodyweight) prTotalReps else prVolume,
            )
        }

    val trendResult = trend(sessions.map { TrendPoint(it.timeMillis, it.primary) }, nowMillis)
    val last = sessions.last()
    return ExerciseSummary(
        bodyweight = bodyweight,
        sessions = sessions,
        trend = trendResult,
        currentValue = last.primary,
        lastTrainedAt = last.timeMillis,
    )
}
