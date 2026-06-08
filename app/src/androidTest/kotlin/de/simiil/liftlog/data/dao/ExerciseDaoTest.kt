package de.simiil.liftlog.data.dao

import app.cash.turbine.test
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var dao: ExerciseDao

    private fun ex(id: String, name: String, hidden: Boolean = false, deleted: Long? = null) =
        ExerciseEntity(id, name, MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = hidden, createdAt = 1, updatedAt = 1, deletedAt = deleted)

    @Before fun setUp() { db = newInMemoryDb(); dao = db.exerciseDao() }
    @After fun tearDown() = db.close()

    @Test fun observeVisible_excludesHiddenAndDeleted() = runTest {
        dao.insert(ex("1", "Bench"))
        dao.insert(ex("2", "Hidden", hidden = true))
        dao.insert(ex("3", "Gone", deleted = 99))
        dao.observeVisible().test {
            assertEquals(listOf("Bench"), awaitItem().map { it.name }); cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun findLiveByName_isCaseInsensitive() = runTest {
        dao.insert(ex("1", "Barbell Bench Press"))
        assertNotNull(dao.findLiveByName("barbell bench press"))
    }

    @Test fun insertIgnore_doesNotOverwriteExisting() = runTest {
        dao.insert(ex("1", "Bench", hidden = true))
        dao.insertIgnore(listOf(ex("1", "Bench", hidden = false)))
        assertTrue(dao.findById("1")!!.isHidden) // unchanged
    }

    @Test fun observeAll_includesHidden_excludesDeleted_orderedByName() = runTest {
        dao.insert(ex("1", "Zebra"))
        dao.insert(ex("2", "alpha", hidden = true))
        dao.insert(ex("3", "Gone", deleted = 99))
        dao.observeAll().test {
            assertEquals(listOf("alpha", "Zebra"), awaitItem().map { it.name }); cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun countLive_excludesTombstones() = runTest {
        dao.insert(ex("1", "Bench"))
        dao.insert(ex("2", "Gone", deleted = 99))
        assertEquals(1, dao.countLive())
    }
}
