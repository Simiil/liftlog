package de.simiil.liftlog.data.mapper

import de.simiil.liftlog.data.dao.SessionWithDetailsRelation
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.model.TemplateExercise
import de.simiil.liftlog.domain.model.WorkoutPlan
import kotlin.time.Instant

private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

private fun Long?.toInstantOrNull(): Instant? = this?.let(Instant::fromEpochMilliseconds)

private fun Instant.toMillis(): Long = toEpochMilliseconds()

private fun Instant?.toMillisOrNull(): Long? = this?.toEpochMilliseconds()

fun ExerciseEntity.toDomain() =
    Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isBuiltIn = isBuiltIn,
        isHidden = isHidden,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        deletedAt = deletedAt.toInstantOrNull(),
        force = force,
        secondaryMuscleGroups = secondaryMuscleGroups,
    )

fun Exercise.toEntity() =
    ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isBuiltIn = isBuiltIn,
        isHidden = isHidden,
        createdAt = createdAt.toMillis(),
        updatedAt = updatedAt.toMillis(),
        deletedAt = deletedAt.toMillisOrNull(),
        force = force,
        secondaryMuscleGroups = secondaryMuscleGroups,
    )

fun WorkoutPlanEntity.toDomain() =
    WorkoutPlan(
        id,
        name,
        position,
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun WorkoutPlan.toEntity() =
    WorkoutPlanEntity(
        id,
        name,
        position,
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun PlanDayTemplateEntity.toDomain() =
    PlanDayTemplate(
        id,
        planId,
        name,
        position,
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun PlanDayTemplate.toEntity() =
    PlanDayTemplateEntity(
        id,
        planId,
        name,
        position,
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun TemplateExerciseEntity.toDomain() =
    TemplateExercise(
        id,
        templateId,
        exerciseId,
        position,
        targetSets,
        targetRepsMin,
        targetRepsMax,
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun TemplateExercise.toEntity() =
    TemplateExerciseEntity(
        id,
        templateId,
        exerciseId,
        position,
        targetSets,
        targetRepsMin,
        targetRepsMax,
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun SessionEntity.toDomain() =
    Session(
        id,
        templateId,
        templateNameSnapshot,
        startedAt.toInstant(),
        endedAt.toInstantOrNull(),
        note,
        rpe,
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun Session.toEntity() =
    SessionEntity(
        id,
        templateId,
        templateNameSnapshot,
        startedAt.toMillis(),
        endedAt.toMillisOrNull(),
        note,
        rpe,
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun SessionExerciseEntity.toDomain() =
    SessionExercise(
        id,
        sessionId,
        exerciseId,
        position,
        targetSets,
        targetRepsMin,
        targetRepsMax,
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun SessionExercise.toEntity() =
    SessionExerciseEntity(
        id,
        sessionId,
        exerciseId,
        position,
        targetSets,
        targetRepsMin,
        targetRepsMax,
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun LoggedSetEntity.toDomain() =
    LoggedSet(
        id,
        sessionExerciseId,
        weightKg,
        reps,
        position,
        completedAt.toInstant(),
        createdAt.toInstant(),
        updatedAt.toInstant(),
        deletedAt.toInstantOrNull(),
    )

fun LoggedSet.toEntity() =
    LoggedSetEntity(
        id,
        sessionExerciseId,
        weightKg,
        reps,
        position,
        completedAt.toMillis(),
        createdAt.toMillis(),
        updatedAt.toMillis(),
        deletedAt.toMillisOrNull(),
    )

fun SessionWithDetailsRelation.toDomain() =
    SessionWithDetails(
        session = session.toDomain(),
        exercises =
            exercises
                .filter { it.sessionExercise.deletedAt == null }
                .sortedBy { it.sessionExercise.position }
                .map { se ->
                    SessionExerciseWithSets(
                        sessionExercise = se.sessionExercise.toDomain(),
                        sets =
                            se.sets
                                .filter { it.deletedAt == null }
                                .sortedBy { it.position }
                                .map { it.toDomain() },
                    )
                },
    )
