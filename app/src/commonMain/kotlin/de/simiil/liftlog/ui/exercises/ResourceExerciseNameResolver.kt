package de.simiil.liftlog.ui.exercises

import org.jetbrains.compose.resources.getString

/**
 * Resolves built-in exercise names from Compose Multiplatform string resources, following the
 * app's current display locale. Custom exercises (absent from [BuiltInExerciseNames]) fall back
 * to their stored name.
 */
class ResourceExerciseNameResolver : ExerciseNameResolver {
    override suspend fun displayName(
        id: String,
        fallbackName: String,
    ): String = BuiltInExerciseNames.resById[id]?.let { getString(it) } ?: fallbackName
}
