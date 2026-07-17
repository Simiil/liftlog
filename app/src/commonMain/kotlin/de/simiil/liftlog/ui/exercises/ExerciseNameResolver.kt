package de.simiil.liftlog.ui.exercises

import de.simiil.liftlog.domain.model.Exercise

/** Resolves an exercise's display name: built-ins -> localized resource, customs -> stored name.
 *  Suspend because the common implementation reads Compose Multiplatform string resources
 *  (`org.jetbrains.compose.resources.getString`, which is suspend). */
fun interface ExerciseNameResolver {
    suspend fun displayName(
        id: String,
        fallbackName: String,
    ): String
}

suspend fun ExerciseNameResolver.displayName(exercise: Exercise): String = displayName(exercise.id, exercise.name)
