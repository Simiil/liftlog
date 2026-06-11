package de.simiil.liftlog.domain.analytics

/** One logged set reduced to the fields analytics needs (weight canonical kg). */
data class SetEntry(
    val weightKg: Double,
    val reps: Int,
)

/** Per-exercise, per-session metrics (04-analytics-spec §1). */
data class SessionMetrics(
    val topSetKg: Double,
    val volumeKg: Double,
    val e1rmKg: Double,
    val maxReps: Int,
    val totalReps: Int,
)

/**
 * Training volume = Σ (weight × reps) across [sets], in canonical kg. The single source for
 * the volume formula — the weekly summary and session-detail summary call this rather than
 * re-inlining the multiply-and-sum (04-analytics-spec §1).
 */
fun volumeKg(sets: List<SetEntry>): Double = sets.sumOf { it.weightKg * it.reps }

fun sessionMetrics(sets: List<SetEntry>): SessionMetrics {
    var top = 0.0
    var bestE1rm = 0.0
    var maxReps = 0
    var totalReps = 0
    for (s in sets) {
        top = maxOf(top, s.weightKg)
        totalReps += s.reps
        maxReps = maxOf(maxReps, s.reps)
        e1rm(s.weightKg, s.reps)?.let { bestE1rm = maxOf(bestE1rm, it) }
    }
    return SessionMetrics(top, volumeKg(sets), bestE1rm, maxReps, totalReps)
}
