package de.simiil.liftlog.data.seed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class ExerciseSeederTest {
    private lateinit var db: AppDatabase
    private lateinit var seeder: ExerciseSeeder

    @Before fun setUp() {
        db = newInMemoryDb()
        seeder = ExerciseSeeder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            dao = db.exerciseDao(),
            clock = Clock.systemUTC(),
            json = Json { ignoreUnknownKeys = true },
        )
    }

    @After fun tearDown() = db.close()

    @Test fun seed_insertsAllBuiltIns() = runTest {
        seeder.seed()
        assertEquals(69, db.exerciseDao().countLive())
        db.exerciseDao().observeAll().test {
            val items = awaitItem()
            assertTrue("all rows should be isBuiltIn", items.all { it.isBuiltIn })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun seed_isIdempotent() = runTest {
        seeder.seed()
        seeder.seed()
        assertEquals(69, db.exerciseDao().countLive())
    }

    @Test fun seed_doesNotOverwriteUserHidden() = runTest {
        seeder.seed()
        val dao = db.exerciseDao()
        val firstRow = dao.observeAll().first().first()
        val hiddenId = firstRow.id
        dao.update(firstRow.copy(isHidden = true, updatedAt = Clock.systemUTC().millis()))
        seeder.seed()
        val after = dao.findById(hiddenId)
        assertNotNull(after)
        assertTrue("isHidden should still be true after re-seed", after!!.isHidden)
    }
}
