package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.LoggedSet

data class PrefillValues(
    val weightKg: Double?,
    val reps: Int,
)

/** Pre-fill rules for the next set's steppers (03-ux-spec §4.2). Pure. */
object Prefill {
    const val DEFAULT_REPS = 10

    fun forNextSet(
        setsThisEntry: List<LoggedSet>,
        lastPerformance: List<LoggedSet>,
    ): PrefillValues {
        // 1. Previous set of THIS session-exercise entry (keeps duplicate entries independent).
        setsThisEntry.lastOrNull()?.let { return PrefillValues(it.weightKg, it.reps) }
        // 2. Same set-number from the last completed session; clamp to its final set if we've
        //    already exceeded it (defensive — rule 1 covers the in-session continuation case).
        if (lastPerformance.isNotEmpty()) {
            val index = minOf(setsThisEntry.size, lastPerformance.lastIndex)
            val src = lastPerformance[index]
            return PrefillValues(src.weightKg, src.reps)
        }
        // 3. Never performed: empty weight (numpad must be used), reps default 10.
        return PrefillValues(weightKg = null, reps = DEFAULT_REPS)
    }
}
