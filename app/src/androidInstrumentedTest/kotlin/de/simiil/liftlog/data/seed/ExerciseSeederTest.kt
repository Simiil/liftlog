package de.simiil.liftlog.data.seed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.entity.SeedStateEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ExerciseSeederTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = newInMemoryDb()
    }

    @After fun tearDown() = db.close()

    /** Fixed clocks so updatedAt assertions can't race the wall clock. */
    private fun seederAt(millis: Long) =
        ExerciseSeeder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            dao = db.exerciseDao(),
            seedStateDao = db.seedStateDao(),
            transactor = RoomTransactor(db),
            clock = FixedClock(Instant.fromEpochMilliseconds(millis)),
            json = Json { ignoreUnknownKeys = true },
        )

    @Test fun seed_insertsAllBuiltIns() =
        runTest {
            seederAt(1_000).seed()
            assertEquals(331, db.exerciseDao().countLive())
            db.exerciseDao().observeAll().test {
                val items = awaitItem()
                assertTrue("all rows should be isBuiltIn", items.all { it.isBuiltIn })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun seed_isIdempotent() =
        runTest {
            seederAt(1_000).seed()
            seederAt(2_000).seed()
            assertEquals(331, db.exerciseDao().countLive())
        }

    @Test fun seed_storesAppliedVersion() =
        runTest {
            seederAt(1_000).seed()
            assertEquals(ExerciseSeeder.SEED_VERSION, db.seedStateDao().appliedVersion())
        }

    @Test fun seed_skipsEntirelyWhenVersionCurrent() =
        runTest {
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val row = dao.observeAll().first().first()
            // Tamper a classification field: a converge pass WOULD fix it, so it staying
            // tampered proves the early return (asset not applied).
            dao.update(row.copy(muscleGroup = MuscleGroup.OTHER, updatedAt = 1_500L))
            seederAt(2_000).seed()
            assertEquals(MuscleGroup.OTHER, dao.findById(row.id)!!.muscleGroup)
        }

    @Test fun seed_skipsOnDowngrade() =
        runTest {
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 999))
            seederAt(1_000).seed()
            assertEquals(0, db.exerciseDao().countLive())
        }

    @Test fun seed_convergesChangedClassification_preservingUserState() =
        runTest {
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val original = dao.observeAll().first().first()
            dao.update(
                original.copy(
                    muscleGroup = MuscleGroup.OTHER,
                    equipment = Equipment.OTHER,
                    isHidden = true,
                    updatedAt = 1_500L,
                ),
            )
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0)) // simulate a newer seed file
            seederAt(2_000).seed()
            val after = dao.findById(original.id)
            assertNotNull(after)
            assertEquals("classification restored from seed", original.muscleGroup, after!!.muscleGroup)
            assertEquals("classification restored from seed", original.equipment, after.equipment)
            assertTrue("user isHidden preserved", after.isHidden)
            assertEquals("createdAt preserved", original.createdAt, after.createdAt)
            assertEquals("updatedAt bumped on real change", 2_000L, after.updatedAt)
            assertEquals(ExerciseSeeder.SEED_VERSION, db.seedStateDao().appliedVersion())
        }

    @Test fun seed_noDiff_doesNotBumpUpdatedAt() =
        runTest {
            seederAt(1_000).seed()
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0)) // force a converge pass
            seederAt(2_000).seed()
            val rows = db.exerciseDao().observeAll().first()
            assertTrue("unchanged rows keep their updatedAt", rows.all { it.updatedAt == 1_000L })
        }

    @Test fun seed_neverResurrectsTombstones() =
        runTest {
            // Built-ins aren't deletable via the UI; the seeder still honors tombstones defensively.
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val row = dao.observeAll().first().first()
            dao.update(row.copy(deletedAt = 5_000L))
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0))
            seederAt(2_000).seed()
            assertNull("tombstone must survive re-seed", dao.findById(row.id))
        }
}
