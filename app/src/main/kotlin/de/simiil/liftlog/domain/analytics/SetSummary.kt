package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import java.util.Locale

/**
 * Compact, accurate set-summary line — the single source for the analytics recent-sessions
 * list and the active-session exercise card (issue #28). Never drops or re-pairs sets:
 * consecutive sets at the same display weight collapse into one run, runs keep session order.
 *
 * - uniform:    "15 kg × 10·10·10·10"
 * - mixed:      "55 kg × 10, 60 kg × 9·5, 55 kg × 10"
 * - bodyweight: "12·10·8" (all sets at 0 kg)
 *
 * Runs group by the *formatted* display weight, not raw kg: distinct stored weights can round
 * to the same display value after unit conversion (e.g. 27.2155422 kg and 27.216 kg both show
 * as "60" in lb), and two adjacent runs with identical labels would look broken.
 */
fun formatSetSummary(
    sets: List<SetEntry>,
    unit: WeightUnit,
    locale: Locale = Locale.getDefault(),
): String {
    if (sets.isEmpty()) return ""
    if (sets.all { it.weightKg == 0.0 }) return sets.joinToString("·") { it.reps.toString() }
    val label = Weights.label(unit)
    val runs = mutableListOf<Pair<String, MutableList<Int>>>()
    for (set in sets) {
        val weight = Weights.format(set.weightKg, unit, locale)
        val last = runs.lastOrNull()
        if (last != null && last.first == weight) {
            last.second.add(set.reps)
        } else {
            runs.add(weight to mutableListOf(set.reps))
        }
    }
    return runs.joinToString(", ") { (weight, reps) -> "$weight $label × ${reps.joinToString("·")}" }
}
