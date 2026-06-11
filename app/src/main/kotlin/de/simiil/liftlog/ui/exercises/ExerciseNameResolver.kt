package de.simiil.liftlog.ui.exercises

import de.simiil.liftlog.domain.model.Exercise

/** Resolves an exercise's display name: built-ins -> localized resource, customs -> stored name. */
fun interface ExerciseNameResolver {
    fun displayName(id: String, fallbackName: String): String
}

fun ExerciseNameResolver.displayName(exercise: Exercise): String =
    displayName(exercise.id, exercise.name)
