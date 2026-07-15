package de.simiil.liftlog.domain.model

/** Session detail/history read model — children are already live-filtered + position-sorted by the mapper. */
data class SessionWithDetails(
    val session: Session,
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    val sessionExercise: SessionExercise,
    val sets: List<LoggedSet>,
)
