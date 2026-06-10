package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment

/**
 * Ids of sessions containing ≥1 headline PR (04-analytics-spec §1/§4; see SessionPoint.isPr), across all
 * exercises. Reuses [summarize] per exercise so Home/History chips agree with the Analytics
 * detail screen by construction. Exercises missing from [equipmentById] (e.g. soft-deleted)
 * are skipped — the detail screen has no summary for them either.
 */
fun prSessionIds(
    setsByExercise: Map<String, List<DatedSet>>,
    equipmentById: Map<String, Equipment>,
    nowMillis: Long,
): Set<String> = buildSet {
    for ((exerciseId, sets) in setsByExercise) {
        val equipment = equipmentById[exerciseId] ?: continue
        val summary = summarize(equipment, sets, nowMillis) ?: continue
        summary.sessions.forEach { if (it.isPr) add(it.sessionId) }
    }
}
