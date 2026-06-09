package de.simiil.liftlog.domain.logging

/** Display helpers for template/session targets (03-ux-spec §4.4). Pure; no Android deps. */
object Targets {
    /** Rep-range hint for the reps stepper: "8–12", "8" (equal/one-sided), or null (no target). */
    fun repRangeHint(min: Int?, max: Int?): String? = when {
        min != null && max != null -> if (min == max) "$min" else "$min–$max"
        min != null -> "$min"
        max != null -> "$max"
        else -> null
    }
}
