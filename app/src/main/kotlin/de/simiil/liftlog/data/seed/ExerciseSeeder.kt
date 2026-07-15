package de.simiil.liftlog.data.seed

import android.content.Context
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.dao.SeedStateDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.SeedStateEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Version-gated built-in seeding (02-data-spec §7). When [SEED_VERSION] is newer than the
 * DB-stored applied version, converge live built-in rows to the seed file: insert missing ids,
 * update changed classification (name/muscleGroup/equipment/force/secondaries) — preserving
 * isHidden and createdAt, bumping updatedAt only on real change, never touching tombstones,
 * never removing rows absent from the file. When versions match, returns without opening the
 * asset. Idempotent; converge + version stamp run in one transaction.
 */
class ExerciseSeeder(
    private val context: Context,
    private val dao: ExerciseDao,
    private val seedStateDao: SeedStateDao,
    private val transactor: Transactor,
    private val clock: Clock,
    private val json: Json,
) {
    suspend fun seed() {
        val applied = seedStateDao.appliedVersion()
        if (applied != null && applied >= SEED_VERSION) return // covers app downgrades too
        val text =
            context.assets
                .open(ASSET)
                .bufferedReader()
                .use { it.readText() }
        val exercises = json.decodeFromString<SeedFile>(text).exercises
        val now = clock.now().toEpochMilliseconds()
        transactor.immediate {
            val existingById = dao.findAllAny().associateBy { it.id }
            val toInsert = mutableListOf<ExerciseEntity>()
            for (seed in exercises) {
                val existing = existingById[seed.id]
                when {
                    existing == null -> toInsert += seed.toEntity(now)
                    existing.deletedAt != null -> Unit // tombstone wins; never resurrect
                    else -> {
                        val converged =
                            existing.copy(
                                name = seed.name,
                                muscleGroup = MuscleGroup.fromStorageValue(seed.muscleGroup),
                                equipment = Equipment.fromStorageValue(seed.equipment),
                                force = Force.fromStorageValue(seed.force),
                                secondaryMuscleGroups = seed.secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) },
                            )
                        if (converged != existing) dao.update(converged.copy(updatedAt = now))
                    }
                }
            }
            if (toInsert.isNotEmpty()) dao.insertIgnore(toInsert)
            seedStateDao.upsert(SeedStateEntity(appliedSeedVersion = SEED_VERSION))
        }
    }

    private fun SeedExercise.toEntity(now: Long) =
        ExerciseEntity(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.fromStorageValue(muscleGroup),
            equipment = Equipment.fromStorageValue(equipment),
            isBuiltIn = true,
            isHidden = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            force = Force.fromStorageValue(force),
            secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) },
        )

    companion object {
        /** Bump together with a new `seed/exercises.v<N>.json` asset. SeedAssetTest locks file ↔ constant. */
        const val SEED_VERSION = 2
        private const val ASSET = "seed/exercises.v$SEED_VERSION.json"
    }
}
