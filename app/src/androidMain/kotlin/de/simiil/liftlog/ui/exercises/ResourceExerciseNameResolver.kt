package de.simiil.liftlog.ui.exercises

import android.content.Context

class ResourceExerciseNameResolver(
    private val context: Context,
) : ExerciseNameResolver {
    override fun displayName(
        id: String,
        fallbackName: String,
    ): String = BuiltInExerciseNames.resById[id]?.let(context::getString) ?: fallbackName
}
