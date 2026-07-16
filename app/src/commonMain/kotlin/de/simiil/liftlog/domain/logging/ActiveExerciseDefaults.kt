package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails

/**
 * Default active-exercise selection (03-ux-spec §4): first incomplete exercise,
 * else the last one. Shared by the Active Session screen and the session
 * notification so both agree on "current exercise" after process death.
 */
object ActiveExerciseDefaults {
    fun pickDefault(details: SessionWithDetails): String? {
        if (details.exercises.isEmpty()) return null
        val incomplete = details.exercises.firstOrNull { !isComplete(it) }
        return (incomplete ?: details.exercises.last()).sessionExercise.id
    }

    fun isComplete(ews: SessionExerciseWithSets): Boolean {
        val targetSets = ews.sessionExercise.targetSets ?: return false
        return ews.sets.size >= targetSets
    }
}
