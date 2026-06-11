package de.simiil.liftlog.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds built-in exercises on every startup. Idempotent: insert-if-id-absent (02-data-spec §7). */
@Singleton
class ExerciseSeeder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: ExerciseDao,
        private val clock: Clock,
        private val json: Json,
    ) {
        suspend fun seed() {
            val text =
                context.assets
                    .open(ASSET)
                    .bufferedReader()
                    .use { it.readText() }
            val now = clock.millis()
            val entities =
                json.decodeFromString<SeedFile>(text).exercises.map { e ->
                    ExerciseEntity(
                        id = e.id,
                        name = e.name,
                        muscleGroup = MuscleGroup.fromStorageValue(e.muscleGroup),
                        equipment = Equipment.fromStorageValue(e.equipment),
                        isBuiltIn = true,
                        isHidden = false,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                }
            dao.insertIgnore(entities)
        }

        private companion object {
            const val ASSET = "seed/exercises.v1.json"
        }
    }
