package de.simiil.liftlog.domain.units

/** Workout-level RPE scale: 6.0–10.0 in 0.5 steps (2026-06-11 spec §2). Pure; no Android deps. */
object Rpe {
    const val MIN = 6.0
    const val MAX = 10.0
    const val STEP = 0.5
    const val DEFAULT = 8.0

    /** First interaction from unset starts at [DEFAULT]. */
    fun increment(value: Double?): Double = value?.let { (it + STEP).coerceAtMost(MAX) } ?: DEFAULT

    fun decrement(value: Double?): Double = value?.let { (it - STEP).coerceAtLeast(MIN) } ?: DEFAULT

    fun isWhole(value: Double): Boolean = value % 1.0 == 0.0
}
