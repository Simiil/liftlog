package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.DayDraft
import de.simiil.liftlog.domain.model.ItemDraft
import de.simiil.liftlog.domain.model.PlanDraft
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlanRepositorySaveDraftTest {

    private val planDao = FakePlanDao()
    private val now = 9000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val planRepo = PlanRepositoryImpl(planDao, FakeTransactor(), clock)

    private fun liveDays(planId: String) =
        planDao.dayTemplates.values.filter { it.planId == planId && it.deletedAt == null }
            .sortedBy { it.position }

    private fun liveExercises(templateId: String) =
        planDao.templateExercises.values.filter { it.templateId == templateId && it.deletedAt == null }
            .sortedBy { it.position }

    // ─── (a) new plan ───────────────────────────────────────────────────────────

    @Test
    fun `savePlanDraft creates a new plan with days, exercises, positions and targets`() = runTest {
        val draft = PlanDraft(
            planId = null,
            name = "  PPL  ",
            days = listOf(
                DayDraft(
                    key = "k-push", templateId = null, name = "Push",
                    items = listOf(
                        ItemDraft(key = "i1", exerciseId = "ex-1", targetSets = 3, targetRepsMin = 8, targetRepsMax = 12),
                        ItemDraft(key = "i2", exerciseId = "ex-2", targetSets = 4, targetRepsMin = 5, targetRepsMax = 5),
                    ),
                ),
                DayDraft(
                    key = "k-pull", templateId = null, name = "Pull",
                    items = listOf(
                        ItemDraft(key = "i3", exerciseId = "ex-3"),
                        ItemDraft(key = "i4", exerciseId = "ex-4", targetSets = 5),
                    ),
                ),
            ),
        )

        val planId = planRepo.savePlanDraft(draft)

        // Plan row: trimmed name, position 0, timestamps from clock, valid UUID.
        val plan = planDao.findPlan(planId)!!
        assertEquals("PPL", plan.name)
        assertEquals(0, plan.position)
        assertEquals(now, plan.createdAt)
        assertEquals(now, plan.updatedAt)
        assertNotNull(UUID.fromString(plan.id))

        // Two days, positions 0..1, in order.
        val days = liveDays(planId)
        assertEquals(2, days.size)
        assertEquals(listOf("Push", "Pull"), days.map { it.name })
        assertEquals(listOf(0, 1), days.map { it.position })

        // Push day: two exercises, positions 0..1, targets stored.
        val push = liveExercises(days[0].id)
        assertEquals(2, push.size)
        assertEquals(listOf("ex-1", "ex-2"), push.map { it.exerciseId })
        assertEquals(listOf(0, 1), push.map { it.position })
        assertEquals(3, push[0].targetSets)
        assertEquals(8, push[0].targetRepsMin)
        assertEquals(12, push[0].targetRepsMax)
        assertEquals(4, push[1].targetSets)

        // Pull day: null targets preserved.
        val pull = liveExercises(days[1].id)
        assertEquals(listOf("ex-3", "ex-4"), pull.map { it.exerciseId })
        assertNull(pull[0].targetSets)
        assertEquals(5, pull[1].targetSets)
        assertNull(pull[1].targetRepsMin)
    }

    // ─── (b) edit existing ───────────────────────────────────────────────────────

    @Test
    fun `savePlanDraft on existing plan reorders, edits, removes - preserving ids and reindexing`() = runTest {
        // Seed: plan with day A (ex te-a1, te-a2) and day B (ex te-b1).
        planDao.plans["plan-1"] = WorkoutPlanEntity(
            id = "plan-1", name = "Old name", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.dayTemplates["day-A"] = PlanDayTemplateEntity(
            id = "day-A", planId = "plan-1", name = "A", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.dayTemplates["day-B"] = PlanDayTemplateEntity(
            id = "day-B", planId = "plan-1", name = "B", position = 1, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.templateExercises["te-a1"] = TemplateExerciseEntity(
            id = "te-a1", templateId = "day-A", exerciseId = "ex-1", position = 0,
            targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.templateExercises["te-a2"] = TemplateExerciseEntity(
            id = "te-a2", templateId = "day-A", exerciseId = "ex-2", position = 1,
            targetSets = null, targetRepsMin = null, targetRepsMax = null, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.templateExercises["te-b1"] = TemplateExerciseEntity(
            id = "te-b1", templateId = "day-B", exerciseId = "ex-9", position = 0,
            targetSets = 2, targetRepsMin = 10, targetRepsMax = 15, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )

        // Edit: rename plan; reorder days (B before A); in day A keep te-a1 (new targets),
        // remove te-a2, add a new exercise ex-7.
        val draft = PlanDraft(
            planId = "plan-1",
            name = "New name",
            days = listOf(
                DayDraft(key = "day-B", templateId = "day-B", name = "B",
                    items = listOf(ItemDraft(key = "te-b1", templateExerciseId = "te-b1", exerciseId = "ex-9",
                        targetSets = 2, targetRepsMin = 10, targetRepsMax = 15))),
                DayDraft(key = "day-A", templateId = "day-A", name = "A renamed",
                    items = listOf(
                        ItemDraft(key = "te-a1", templateExerciseId = "te-a1", exerciseId = "ex-1",
                            targetSets = 5, targetRepsMin = 6, targetRepsMax = 6),
                        ItemDraft(key = "new", templateExerciseId = null, exerciseId = "ex-7", targetSets = 4),
                    )),
            ),
        )

        val planId = planRepo.savePlanDraft(draft)
        assertEquals("plan-1", planId)

        // Plan renamed, same id.
        assertEquals("New name", planDao.findPlan("plan-1")!!.name)
        assertEquals(now, planDao.findPlan("plan-1")!!.updatedAt)

        // Days reordered: B at position 0, A at position 1; ids preserved.
        val days = liveDays("plan-1")
        assertEquals(listOf("day-B", "day-A"), days.map { it.id })
        assertEquals(listOf(0, 1), days.map { it.position })
        assertEquals("A renamed", days[1].name)

        // Day A exercises: te-a1 kept (id preserved, targets updated, reindexed to 0),
        // te-a2 soft-deleted, ex-7 inserted at position 1 with fresh UUID.
        val aExercises = liveExercises("day-A")
        assertEquals(2, aExercises.size)
        assertEquals("te-a1", aExercises[0].id)
        assertEquals(0, aExercises[0].position)
        assertEquals(5, aExercises[0].targetSets)
        assertEquals(6, aExercises[0].targetRepsMin)
        assertEquals("ex-7", aExercises[1].exerciseId)
        assertEquals(1, aExercises[1].position)
        assertEquals(4, aExercises[1].targetSets)
        assertNotNull(UUID.fromString(aExercises[1].id))

        // te-a2 soft-deleted with the clock timestamp.
        val a2 = planDao.templateExercises["te-a2"]!!
        assertEquals(now, a2.deletedAt)
        assertEquals(now, a2.updatedAt)

        // Day B exercise untouched (id preserved, still live, targets unchanged).
        val bExercises = liveExercises("day-B")
        assertEquals(listOf("te-b1"), bExercises.map { it.id })
        assertEquals(2, bExercises[0].targetSets)
    }

    @Test
    fun `savePlanDraft soft-deletes a day removed from the draft and cascades to its exercises`() = runTest {
        planDao.plans["plan-1"] = WorkoutPlanEntity(
            id = "plan-1", name = "P", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.dayTemplates["day-A"] = PlanDayTemplateEntity(
            id = "day-A", planId = "plan-1", name = "A", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.dayTemplates["day-B"] = PlanDayTemplateEntity(
            id = "day-B", planId = "plan-1", name = "B", position = 1, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.templateExercises["te-b1"] = TemplateExerciseEntity(
            id = "te-b1", templateId = "day-B", exerciseId = "ex-9", position = 0,
            targetSets = null, targetRepsMin = null, targetRepsMax = null, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )

        // Draft keeps only day A.
        val draft = PlanDraft(
            planId = "plan-1", name = "P",
            days = listOf(
                DayDraft(key = "day-A", templateId = "day-A", name = "A",
                    items = listOf(ItemDraft(key = "n", exerciseId = "ex-1"))),
            ),
        )

        planRepo.savePlanDraft(draft)

        // Day B soft-deleted, plus its exercise cascaded.
        assertEquals(now, planDao.dayTemplates["day-B"]!!.deletedAt)
        assertEquals(now, planDao.templateExercises["te-b1"]!!.deletedAt)
        assertEquals(listOf("day-A"), liveDays("plan-1").map { it.id })
    }

    // ─── (c) cancel path: no call → DB untouched ─────────────────────────────────

    @Test
    fun `not calling savePlanDraft leaves the database untouched`() = runTest {
        planDao.plans["plan-1"] = WorkoutPlanEntity(
            id = "plan-1", name = "P", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        // Building a draft but never saving it must not mutate anything.
        PlanDraft(planId = "plan-1", name = "Edited but discarded",
            days = listOf(DayDraft(key = "k", templateId = null, name = "Ghost")))

        assertEquals(1, planDao.plans.size)
        assertEquals("P", planDao.plans["plan-1"]!!.name)
        assertEquals(0, planDao.dayTemplates.size)
        assertEquals(0, planDao.templateExercises.size)
    }

    // ─── (d) history isolation ────────────────────────────────────────────────────

    @Test
    fun `savePlanDraft removing a day does not touch snapshotted session_exercises`() = runTest {
        val sessionDao = FakeSessionDao()

        // Plan with one day that has one template-exercise.
        planDao.plans["plan-1"] = WorkoutPlanEntity(
            id = "plan-1", name = "P", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.dayTemplates["day-A"] = PlanDayTemplateEntity(
            id = "day-A", planId = "plan-1", name = "A", position = 0, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        planDao.templateExercises["te-a1"] = TemplateExerciseEntity(
            id = "te-a1", templateId = "day-A", exerciseId = "ex-1", position = 0,
            targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )

        // A past session that snapshotted day-A (separate session_exercises rows).
        sessionDao.sessions["s-1"] = SessionEntity(
            id = "s-1", templateId = "day-A", templateNameSnapshot = "A", startedAt = 1L,
            endedAt = 2L, note = null, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )
        sessionDao.sessionExercises["se-1"] = SessionExerciseEntity(
            id = "se-1", sessionId = "s-1", exerciseId = "ex-1", position = 0,
            targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, createdAt = 1L, updatedAt = 1L, deletedAt = null,
        )

        // Save a draft that removes day-A entirely (empty plan).
        planRepo.savePlanDraft(PlanDraft(planId = "plan-1", name = "P", days = emptyList()))

        // Template side: day + its exercise are soft-deleted.
        assertEquals(now, planDao.dayTemplates["day-A"]!!.deletedAt)
        assertEquals(now, planDao.templateExercises["te-a1"]!!.deletedAt)

        // History side: the snapshotted session_exercise is completely unchanged.
        val se = sessionDao.sessionExercises["se-1"]!!
        assertNull("session_exercise must remain live", se.deletedAt)
        assertEquals(1L, se.updatedAt)
        assertEquals(3, se.targetSets)
        assertEquals(8, se.targetRepsMin)
        assertEquals(12, se.targetRepsMax)
        // And the session itself is untouched.
        assertNull(sessionDao.sessions["s-1"]!!.deletedAt)
    }
}
