package de.simiil.liftlog.ui.exercises

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceExerciseNameResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ExerciseNameResolver {
        override fun displayName(
            id: String,
            fallbackName: String,
        ): String = BuiltInExerciseNames.resById[id]?.let(context::getString) ?: fallbackName
    }
