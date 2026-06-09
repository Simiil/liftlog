package de.simiil.liftlog.domain.analytics

/**
 * Estimated 1-rep-max (Epley) with guardrails (04-analytics-spec §2).
 * reps == 1 → weight; 2..12 → weight·(1 + reps/30); > 12 → null (excluded from e1RM).
 */
fun e1rm(weightKg: Double, reps: Int): Double? = when {
    reps == 1 -> weightKg
    reps in 2..12 -> weightKg * (1 + reps / 30.0)
    else -> null
}
