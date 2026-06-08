package de.simiil.liftlog.data.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
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
    private lateinit var sessionDao: SessionDao

    // -- builders --

    private fun plan(id: String, name: String, position: Int, deleted: Long? = null) =
        WorkoutPlanEntity(id, name, position, createdAt = 1L, updatedAt = 1L, deletedAt = deleted)

    private fun dayTemplate(
        id: String, planId: String, name: String, position: Int, deleted: Long? = null
    ) = PlanDayTemplateEntity(id, planId, name, position, createdAt = 1L, updatedAt = 1L, deletedAt = deleted)

    private fun templateExercise(
        id: String, templateId: String, exerciseId: String, position: Int, deleted: Long? = null,
        targetSets: Int? = null, targetRepsMin: Int? = null, targetRepsMax: Int? = null,
    ) = TemplateExerciseEntity(
        id, templateId, exerciseId, position,
        targetSets = targetSets, targetRepsMin = targetRepsMin, targetRepsMax = targetRepsMax,
        createdAt = 1L, updatedAt = 1L, deletedAt = deleted
    )

    private fun exercise(id: String) =
        ExerciseEntity(id, "Exercise $id", MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = false, createdAt = 1L, updatedAt = 1L, deletedAt = null)

    // -- session builders (for observeMostRecentlyUsedPlanId) --

    private fun session(
        id: String,
        templateId: String? = null,
        startedAt: Long = 1000L,
        deleted: Long? = null,
    ) = SessionEntity(
        id = id,
        templateId = templateId,
        templateNameSnapshot = templateId?.let { "Snapshot $it" },
        startedAt = startedAt,
        endedAt = null,
        note = null,
        createdAt = startedAt,
        updatedAt = startedAt,
        deletedAt = deleted,
    )

    @Before fun setUp() {
        db = newInMemoryDb()
        dao = db.planDao()
        exerciseDao = db.exerciseDao()
        sessionDao = db.sessionDao()
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

    // -------------------------------------------------------------------------
    // observePlan
    // -------------------------------------------------------------------------

    @Test fun observePlan_emitsLivePlan_thenNullAfterSoftDelete() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))

        dao.observePlan("p1").test {
            val first = awaitItem()
            assertNotNull(first)
            assertEquals("p1", first!!.id)

            dao.softDeletePlan("p1", NOW)
            val afterDelete = awaitItem()
            assertNull("observePlan should emit null after soft-delete", afterDelete)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // observeDayTemplatesForPlan
    // -------------------------------------------------------------------------

    @Test fun observeDayTemplatesForPlan_orderedByPosition_excludesTombstones() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        // Insert out of order (2, 0, 1) so ORDER BY is load-bearing
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))
        dao.insertDayTemplate(dayTemplate("d0", "p1", "Day 0", position = 0))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("dX", "p1", "Dead day", position = 3, deleted = 99L))

        dao.observeDayTemplatesForPlan("p1").test {
            val items = awaitItem()
            assertEquals(3, items.size)
            assertEquals(listOf("d0", "d1", "d2"), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // observeTemplateExercisesFor
    // -------------------------------------------------------------------------

    @Test fun observeTemplateExercisesFor_orderedByPosition_excludesTombstones() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        // Insert out of order (2, 0, 1)
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 2))
        dao.insertTemplateExercise(templateExercise("te0", "d1", "ex1", position = 0))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 1))
        dao.insertTemplateExercise(templateExercise("teX", "d1", "ex1", position = 3, deleted = 99L))

        dao.observeTemplateExercisesFor("d1").test {
            val items = awaitItem()
            assertEquals(3, items.size)
            assertEquals(listOf("te0", "te1", "te2"), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // maxPlanPosition / maxDayTemplatePosition / maxTemplateExercisePosition
    // -------------------------------------------------------------------------

    @Test fun maxPlanPosition_nullForEmptyTable_maxLivePositionOtherwise() = runTest {
        assertNull("no plans → maxPlanPosition should be null", dao.maxPlanPosition())

        dao.insertPlan(plan("p0", "Plan 0", position = 0))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertPlan(plan("p2", "Plan 2", position = 2))
        // Soft-delete the max-position plan — max of live should become 1
        dao.softDeletePlan("p2", NOW)

        assertEquals(1, dao.maxPlanPosition())
    }

    @Test fun maxDayTemplatePosition_nullForEmptyTemplate_maxLivePositionOtherwise() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        assertNull("no days → maxDayTemplatePosition should be null", dao.maxDayTemplatePosition("p1"))

        dao.insertDayTemplate(dayTemplate("d0", "p1", "Day 0", position = 0))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))
        // Soft-delete the position-2 day — max of live should become 1
        dao.softDeleteDayTemplate("d2", NOW)

        assertEquals(1, dao.maxDayTemplatePosition("p1"))
    }

    @Test fun maxTemplateExercisePosition_nullForEmptyTemplate_maxLivePositionOtherwise() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        assertNull("no exercises → maxTemplateExercisePosition should be null",
            dao.maxTemplateExercisePosition("d1"))

        dao.insertTemplateExercise(templateExercise("te0", "d1", "ex1", position = 0))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 1))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 2))
        // Soft-delete the position-2 one — max of live should become 1
        dao.softDeleteTemplateExercise("te2", NOW)

        assertEquals(1, dao.maxTemplateExercisePosition("d1"))
    }

    // -------------------------------------------------------------------------
    // updateTemplateExercisePosition
    // -------------------------------------------------------------------------

    @Test fun updateTemplateExercisePosition_writesPositionAndUpdatedAt_leavesOtherColumnsIntact() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0,
            targetSets = 3, targetRepsMin = 8, targetRepsMax = 12))

        dao.updateTemplateExercisePosition("te1", 5, NOW)

        val updated = dao.findTemplateExercise("te1")
        assertNotNull(updated)
        assertEquals(5, updated!!.position)
        assertEquals(NOW, updated.updatedAt)
        // Other columns must be intact
        assertEquals("ex1", updated.exerciseId)
        assertEquals(3, updated.targetSets)
        assertEquals(8, updated.targetRepsMin)
        assertEquals(12, updated.targetRepsMax)
        assertEquals(1L, updated.createdAt)
        assertNull(updated.deletedAt)
    }

    // -------------------------------------------------------------------------
    // softDeleteDayTemplate + softDeleteTemplateExercisesForTemplate (cascade)
    // -------------------------------------------------------------------------

    @Test fun softDeleteDayTemplate_tombstonesDayOnly_siblingsUntouched() = runTest {
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))

        dao.softDeleteDayTemplate("d1", NOW)

        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d1"))
        // Sibling day must NOT be touched
        assertEquals(null, db.tombstoneOf("plan_day_templates", "d2")!!.first)
    }

    @Test fun softDeleteTemplateExercisesForTemplate_tombstonesDayExercises_leavesOtherDayUntouched() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 1))
        // Sibling day's exercise — must NOT be touched
        dao.insertTemplateExercise(templateExercise("te3", "d2", "ex1", position = 0))

        dao.softDeleteTemplateExercisesForTemplate("d1", NOW)

        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te1"))
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te2"))
        // Sibling day's exercise is untouched
        assertEquals(null, db.tombstoneOf("template_exercises", "te3")!!.first)
    }

    @Test fun softDeleteDayTemplate_andItsExercises_combinedCascade_siblingsUntouched() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d2", "p1", "Day 2", position = 2))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 1))
        // Sibling day d2 exercise — must NOT be touched
        dao.insertTemplateExercise(templateExercise("te3", "d2", "ex1", position = 0))

        // Simulate repo-level softDeleteDayTemplate: cascade exercises then day
        dao.softDeleteTemplateExercisesForTemplate("d1", NOW)
        dao.softDeleteDayTemplate("d1", NOW)

        // d1 and its exercises are tombstoned
        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d1"))
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te1"))
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te2"))
        // d2 and its exercise are untouched
        assertEquals(null, db.tombstoneOf("plan_day_templates", "d2")!!.first)
        assertEquals(null, db.tombstoneOf("template_exercises", "te3")!!.first)
    }

    // -------------------------------------------------------------------------
    // softDeleteTemplateExercise (single-row)
    // -------------------------------------------------------------------------

    @Test fun softDeleteTemplateExercise_tombstonesExactlyOneRow() = runTest {
        exerciseDao.insert(exercise("ex1"))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Day 1", position = 1))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0))
        dao.insertTemplateExercise(templateExercise("te2", "d1", "ex1", position = 1))

        dao.softDeleteTemplateExercise("te1", NOW)

        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te1"))
        // Sibling row must NOT be touched
        assertEquals(null, db.tombstoneOf("template_exercises", "te2")!!.first)
        // Verify via DAO filter
        val live = dao.templateExercisesFor("d1")
        assertEquals(1, live.size)
        assertEquals("te2", live[0].id)
    }

    // -------------------------------------------------------------------------
    // observeMostRecentlyUsedPlanId
    // -------------------------------------------------------------------------

    @Test fun observeMostRecentlyUsedPlanId_returnsLatestTemplatePlanId() = runTest {
        // Plan A and Plan B each with a day template
        dao.insertPlan(plan("pA", "Plan A", position = 0))
        dao.insertPlan(plan("pB", "Plan B", position = 1))
        dao.insertDayTemplate(dayTemplate("dA", "pA", "Day A", position = 0))
        dao.insertDayTemplate(dayTemplate("dB", "pB", "Day B", position = 0))

        // Session from plan A's template (startedAt=1000), session from plan B's template (startedAt=2000)
        sessionDao.insertSession(session("s1", templateId = "dA", startedAt = 1000L))
        sessionDao.insertSession(session("s2", templateId = "dB", startedAt = 2000L))

        dao.observeMostRecentlyUsedPlanId().test {
            val result = awaitItem()
            assertEquals(listOf("pB"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeMostRecentlyUsedPlanId_returnsEmptyWhenNoTemplateSession() = runTest {
        dao.insertPlan(plan("pA", "Plan A", position = 0))
        // Session with no templateId (ad-hoc)
        sessionDao.insertSession(session("s1", templateId = null, startedAt = 1000L))

        dao.observeMostRecentlyUsedPlanId().test {
            val result = awaitItem()
            assertEquals(emptyList<String>(), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeMostRecentlyUsedPlanId_ignoresDeletedSessions() = runTest {
        dao.insertPlan(plan("pA", "Plan A", position = 0))
        dao.insertPlan(plan("pB", "Plan B", position = 1))
        dao.insertDayTemplate(dayTemplate("dA", "pA", "Day A", position = 0))
        dao.insertDayTemplate(dayTemplate("dB", "pB", "Day B", position = 0))

        // Plan B's session is more recent but deleted — result should be plan A
        sessionDao.insertSession(session("s1", templateId = "dA", startedAt = 1000L))
        sessionDao.insertSession(session("s2", templateId = "dB", startedAt = 2000L, deleted = 99L))

        dao.observeMostRecentlyUsedPlanId().test {
            val result = awaitItem()
            assertEquals(listOf("pA"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeMostRecentlyUsedPlanId_skipsSoftDeletedPlan() = runTest {
        dao.insertPlan(plan("pA", "Plan A", position = 0))
        dao.insertPlan(plan("pB", "Plan B", position = 1))
        dao.insertDayTemplate(dayTemplate("dA", "pA", "Day A", position = 0))
        dao.insertDayTemplate(dayTemplate("dB", "pB", "Day B", position = 0))

        // Plan A used earlier, Plan B used more recently → normally B wins.
        sessionDao.insertSession(session("s1", templateId = "dA", startedAt = 1000L))
        sessionDao.insertSession(session("s2", templateId = "dB", startedAt = 2000L))

        // Soft-delete plan B via the real cascade (templates first, then the plan).
        dao.softDeleteDayTemplatesForPlan("pB", NOW)
        dao.softDeletePlan("pB", NOW)

        // B's plan is tombstoned, so its (more recent) session is skipped and live plan A wins.
        dao.observeMostRecentlyUsedPlanId().test {
            val result = awaitItem()
            assertEquals(listOf("pA"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // observeFirstPlanId
    // -------------------------------------------------------------------------

    @Test fun observeFirstPlanId_returnsLowestPositionLivePlan() = runTest {
        dao.insertPlan(plan("p2", "Plan 2", position = 2))
        dao.insertPlan(plan("p0", "Plan 0", position = 0))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))

        dao.observeFirstPlanId().test {
            val result = awaitItem()
            assertEquals(listOf("p0"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeFirstPlanId_returnsEmptyWhenNoPlans() = runTest {
        dao.observeFirstPlanId().test {
            val result = awaitItem()
            assertEquals(emptyList<String>(), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeFirstPlanId_excludesTombstonedPlans() = runTest {
        dao.insertPlan(plan("p0", "Plan 0", position = 0, deleted = 99L))
        dao.insertPlan(plan("p1", "Plan 1", position = 1))

        dao.observeFirstPlanId().test {
            val result = awaitItem()
            assertEquals(listOf("p1"), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Snapshot isolation: editing/deleting a template must never touch session_exercises
    // -------------------------------------------------------------------------

    @Test fun snapshotIsolation_mutatingTemplateDoesNotAffectSessionExercises() = runTest {
        exerciseDao.insert(exercise("ex1"))

        // Set up plan / day / template exercise
        dao.insertPlan(plan("p1", "Push Plan", position = 0))
        dao.insertDayTemplate(dayTemplate("d1", "p1", "Push Day", position = 0))
        dao.insertTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0,
            targetSets = 3, targetRepsMin = 8, targetRepsMax = 12))

        // Simulate a snapshot: insert a session + a session_exercise that copied the template row
        val snapshotSession = session("sess-snap", templateId = "d1", startedAt = 5000L)
        sessionDao.insertSession(snapshotSession)
        val seId = "se-snap-1"
        sessionDao.insertSessionExercise(
            SessionExerciseEntity(
                id = seId,
                sessionId = "sess-snap",
                exerciseId = "ex1",
                position = 0,
                targetSets = 3,
                targetRepsMin = 8,
                targetRepsMax = 12,
                createdAt = 5000L,
                updatedAt = 5000L,
                deletedAt = null,
            )
        )

        // Mutate the template: change targets, then soft-delete the day and its exercises
        dao.updateTemplateExercise(templateExercise("te1", "d1", "ex1", position = 0,
            targetSets = 5, targetRepsMin = 6, targetRepsMax = 10))
        dao.softDeleteTemplateExercisesForTemplate("d1", NOW)
        dao.softDeleteDayTemplate("d1", NOW)

        // The template exercise and day are tombstoned
        assertEquals(NOW to NOW, db.tombstoneOf("template_exercises", "te1"))
        assertEquals(NOW to NOW, db.tombstoneOf("plan_day_templates", "d1"))

        // The session_exercise snapshot must be byte-for-byte unchanged
        assertNull("session_exercise must still be live",
            db.tombstoneOf("session_exercises", seId)!!.first)

        // Verify targets are still the original snapshot values
        val seRow = sessionDao.findSessionExercise(seId)
        assertNotNull(seRow)
        assertEquals("ex1", seRow!!.exerciseId)
        assertEquals(0, seRow.position)
        assertEquals(3, seRow.targetSets)
        assertEquals(8, seRow.targetRepsMin)
        assertEquals(12, seRow.targetRepsMax)
        assertEquals(5000L, seRow.updatedAt)   // unchanged — not bumped by the template mutation
        assertNull(seRow.deletedAt)
    }
}
