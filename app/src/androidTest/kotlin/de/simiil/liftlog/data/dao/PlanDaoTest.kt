package de.simiil.liftlog.data.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import de.simiil.liftlog.testing.tombstoneOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Timestamp used for soft-delete calls; differs from row-creation updatedAt=1L so that
 *  an updatedAt==NOW assertion genuinely proves the write occurred. */
private const val NOW = 7000L

@RunWith(AndroidJUnit4::class)
class PlanDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var dao: PlanDao
    private lateinit var exerciseDao: ExerciseDao

    // -- builders --

    private fun plan(id: String, name: String, position: Int, deleted: Long? = null) =
        WorkoutPlanEntity(id, name, position, createdAt = 1L, updatedAt = 1L, deletedAt = deleted)

    private fun dayTemplate(
        id: String, planId: String, name: String, position: Int, deleted: Long? = null
    ) = PlanDayTemplateEntity(id, planId, name, position, createdAt = 1L, updatedAt = 1L, deletedAt = deleted)

    private fun templateExercise(
        id: String, templateId: String, exerciseId: String, position: Int, deleted: Long? = null
    ) = TemplateExerciseEntity(
        id, templateId, exerciseId, position,
        targetSets = null, targetRepsMin = null, targetRepsMax = null,
        createdAt = 1L, updatedAt = 1L, deletedAt = deleted
    )

    private fun exercise(id: String) =
        ExerciseEntity(id, "Exercise $id", MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = false, createdAt = 1L, updatedAt = 1L, deletedAt = null)

    @Before fun setUp() {
        db = newInMemoryDb()
        dao = db.planDao()
        exerciseDao = db.exerciseDao()
    }

    @After fun tearDown() = db.close()

    // -------------------------------------------------------------------------
    // observePlans
    // -------------------------------------------------------------------------

    @Test fun observePlans_excludesTombstones_orderedByPosition() = runTest {
        dao.insertPlan(plan("p2", "Plan B", position = 2))
        dao.insertPlan(plan("p1", "Plan A", position = 1))
        dao.insertPlan(plan("p3", "Deleted", position = 0, deleted = 99L))
        dao.observePlans().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals(listOf("Plan A", "Plan B"), items.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // dayTemplatesForPlan
    // -------------------------------------------------------------------------

    @Test fun dayTemplatesForPlan_excludesTombstones_orderedByPosition() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 2))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 1))
        dao.insertDayTemplate(dayTemplate("d3", "p1", "Day 3 deleted", position = 0, deleted = 99L))

        val results = dao.dayTemplatesForPlan("p1")
        assertEquals(2, results.size)
        assertEquals(listOf("Day 2", "Day 1"), results.map { it.name })
    }

    // -------------------------------------------------------------------------
    // templateExercisesFor
    // -------------------------------------------------------------------------

    @Test fun templateExercisesFor_excludesTombstones_orderedByPosition() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 2))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 1))
        dao.insertTemplateExercise(templateExercise("te3", "d1", "ex1", position = 0, deleted = 99L))

        val results = dao.templateExercisesFor("d1")
        assertEquals(2, results.size)
        // position ascending: te2 (pos 1) before te1 (pos 2)
        assertEquals(listOf("te2", "te1"), results.map { it.id })
    }

    // -------------------------------------------------------------------------
    // softDeleteDayTemplatesForPlan
    // -------------------------------------------------------------------------

    @Test fun softDeleteDayTemplatesForPlan_tombstonesDeletedAtAndUpdatedAt() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertPlan(plan("p2", "Plan 2", position = 2))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))
        // Day template belonging to another plan — must NOT be touched
        dao.insertDayTemplate(dayTemplate("d3", "p2", "Other plan day", position = 1))

        dao.softDeleteDayTemplatesForPlan("p1", NOW)

        // Both templates of p1 are tombstoned with both timestamps set to NOW
        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d1"))
        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d2"))

        // Other plan's template is untouched — deletedAt should still be null
        assertEquals(null, db.tombstoneOf("plan_day_templates", "d3")!!.first)

        // Confirm via DAO filter as well
        assertTrue("live p1 templates should be empty", dao.dayTemplatesForPlan("p1").isEmpty())
        assertEquals(1, dao.dayTemplatesForPlan("p2").size)
        assertEquals("d3", dao.dayTemplatesForPlan("p2")[0].id)
    }

    @Test fun softDeleteDayTemplatesForPlan_doesNotTouchAlreadyTombstoned() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        // Insert one already-tombstoned template; it must remain at its original deletedAt=50L (not rewritten to NOW)
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Already dead", position = 1, deleted = 50L))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Live", position = 2))

        dao.softDeleteDayTemplatesForPlan("p1", NOW)

        // Pre-tombstoned row must NOT have its deletedAt rewritten
        assertEquals(50L, db.tombstoneOf("plan_day_templates", "d1")!!.first)

        // The live row must now be tombstoned
        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d2"))
    }

    // -------------------------------------------------------------------------
    // softDeleteTemplateExercisesForPlan
    // -------------------------------------------------------------------------

    @Test fun softDeleteTemplateExercisesForPlan_tombstonesExercisesAcrossAllDayTemplates() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertPlan(plan("p2", "Plan 2", position = 2))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p2", "Other Day", position = 1))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 1))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 2))
        // Template exercise belonging to another plan's template — must NOT be touched
        dao.insertTemplateExercise(templateExercise("te3", "d2", "ex1", position = 1))

        dao.softDeleteTemplateExercisesForPlan("p1", NOW)

        // p1's template exercises are tombstoned with both timestamps set to NOW
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te1"))
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te2"))

        // p2's template exercise is untouched — deletedAt should still be null
        assertEquals(null, db.tombstoneOf("template_exercises", "te3")!!.first)

        // Confirm via DAO filter as well
        assertTrue("p1 template exercises should be tombstoned", dao.templateExercisesFor("d1").isEmpty())
        assertEquals(1, dao.templateExercisesFor("d2").size)
        assertEquals("te3", dao.templateExercisesFor("d2")[0].id)
    }

    // -------------------------------------------------------------------------
    // softDeletePlan
    // -------------------------------------------------------------------------

    @Test fun softDeletePlan_setsDeletedAtAndUpdatedAt() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertPlan(plan("p2", "Plan 2", position = 2))

        dao.softDeletePlan("p1", NOW)

        // Tombstoned plan must have both timestamps set to NOW
        assertEquals(NOW to NOW, db.tombstoneOf("workout_plans", "p1"))

        // Sibling plan p2 must NOT be touched — deletedAt should still be null
        assertEquals(null, db.tombstoneOf("workout_plans", "p2")!!.first)

        // observePlans excludes deleted plans
        dao.observePlans().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("p2", items[0].id)
            cancelAndIgnoreRemainingEvents()
        }

        // p1 itself must not be retrievable via findPlan (which filters deletedAt IS NULL)
        val found = dao.findPlan("p1")
        assertNull("tombstoned plan should not be returned by findPlan", found)
    }
}
