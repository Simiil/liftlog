package de.simiil.liftlog.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity

// NOTE: @Relation cannot filter deletedAt; tombstoned children are loaded then dropped + sorted in the mapper (later task).
data class SessionWithDetailsRelation(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSetsRelation>,
)

data class SessionExerciseWithSetsRelation(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<LoggedSetEntity>,
)

/** Analytics projection (02-data-spec §4): set-level rows; e1RM math stays in pure Kotlin (M4). */
data class SetRow(
    val sessionId: String,
    val exerciseId: String,
    val startedAt: Long,
    val weightKg: Double,
    val reps: Int,
)

/** Picker query projection: exercise with its most-recent logged-set timestamp. */
data class RecentExercise(
    val exerciseId: String,
    val lastUsed: Long,
)

/** Picker query projection: per-session count of live logged sets. */
data class SessionSetCount(
    val sessionId: String,
    val setCount: Int,
)

/** Analytics browser projection: an exercise that has ≥1 logged set, with its most-recent session start. */
data class TrainedExerciseRow(
    val exerciseId: String,
    val lastTrainedAt: Long,
)
