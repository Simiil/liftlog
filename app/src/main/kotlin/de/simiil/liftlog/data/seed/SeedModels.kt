package de.simiil.liftlog.data.seed

import kotlinx.serialization.Serializable

@Serializable data class SeedFile(
    val seedVersion: Int,
    val exercises: List<SeedExercise>,
)

@Serializable data class SeedExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
)
