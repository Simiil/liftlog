package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.dao.PrefillDao
import de.simiil.liftlog.data.entity.LoggedSetEntity

class FakePrefillDao : PrefillDao {
    var lastCompletedSessionId: String? = null
    val setsBySessionAndExercise = mutableMapOf<Pair<String, String>, List<LoggedSetEntity>>()

    override suspend fun lastCompletedSessionIdFor(exerciseId: String): String? = lastCompletedSessionId

    override suspend fun setsForExerciseInSession(
        sessionId: String,
        exerciseId: String,
    ): List<LoggedSetEntity> = setsBySessionAndExercise[sessionId to exerciseId] ?: emptyList()
}
