package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.ui.UiTestTags.PLANS_CREATE
import de.simiil.liftlog.ui.UiTestTags.PLAN_DELETE_CONFIRM
import de.simiil.liftlog.ui.UiTestTags.PLAN_EDITOR_DELETE
import de.simiil.liftlog.ui.UiTestTags.PLAN_ROW
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Instrumented path test for the plan delete flow (plan editor, PLAN mode).
 *
 * Flow:
 * 1. Switch to the Plans tab via the bottom nav.
 * 2. Tap the seeded plan row → plan editor (existing plan → delete button present).
 * 3. Scroll to the red "Delete plan" button (last LazyColumn item) → tap.
 * 4. Confirm the AlertDialog → soft-deletes the plan and pops back to the Plans list.
 * 5. Assert: Plans list shows again (PLANS_CREATE), no PLAN_ROW remains, and
 *    Room-level observePlans() is empty (soft-delete tombstone).
 *
 * ### Harness
 * Same as [TemplateStartPathTest]: [createAndroidComposeRule] launches MainActivity sharing
 * the test's Hilt singleton DB; seeding happens via the injected repos (the built-in seeder
 * does NOT run under HiltTestApplication, so the exercise is made via
 * [ExerciseRepository.createCustom]).
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class PlanDeletePathTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var planRepository: PlanRepository

    @Inject lateinit var exerciseRepository: ExerciseRepository

    private lateinit var planId: String

    @Before
    fun seed() =
        runBlocking {
            hiltRule.inject()

            // One plan with one day template carrying one exercise (TemplateStartPathTest idiom).
            val squat = exerciseRepository.createCustom("Test Squat", MuscleGroup.QUADS, Equipment.BARBELL)
            val plan = planRepository.createPlan("Test Plan")
            val day = planRepository.createDayTemplate(plan.id, "Push Day")
            planRepository.addExerciseToTemplate(day.id, squat.id)
            planId = plan.id
        }

    @Test
    fun planDelete_editorToConfirm_tombstonesAndPopsToPlansList() {
        // 1. Switch to the Plans tab (bottom-nav item addressed by its localized label).
        val plansLabel = composeRule.activity.getString(R.string.tab_plans)
        await(hasText(plansLabel), atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNodeWithText(plansLabel).performClick()

        // 2. The seeded plan's row appears once the plans Flow emits → open the editor.
        awaitTag(PLAN_ROW, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(PLAN_ROW).performClick()

        // 3. "Delete plan" is the LAST LazyColumn item — scroll it into view, then tap.
        // Assumes the delete row is composed because the single-day seed fits the viewport; performScrollTo guards partial visibility.
        awaitTag(PLAN_EDITOR_DELETE, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(PLAN_EDITOR_DELETE).performScrollTo().performClick()

        // 4. Confirm dialog → Delete.
        awaitTag(PLAN_DELETE_CONFIRM)
        composeRule.onNodeWithTag(PLAN_DELETE_CONFIRM).performClick()

        // 5a. Back on the Plans list: create action visible, the deleted plan's row gone.
        awaitTag(PLANS_CREATE, timeoutMillis = 10_000)
        awaitGone(PLAN_ROW)

        // 5b. Room-level tombstone: no live plans remain.
        awaitNoPlans()
    }

    // ── Wait helpers ─────────────────────────────────────────────────────────────
    // Per-file copies (project convention): poll-based composeRule.waitUntil reads a stale
    // tree on screens with running timers, so waitForIdle()+poll is used everywhere.

    private fun nodeCount(matcher: SemanticsMatcher): Int =
        composeRule
            .onAllNodes(matcher)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .size

    private fun awaitTag(
        tag: String,
        timeoutMillis: Long = 5_000,
    ) = await(hasTestTag(tag), 1, timeoutMillis)

    private fun await(
        matcher: SemanticsMatcher,
        atLeast: Int,
        timeoutMillis: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (nodeCount(matcher) >= atLeast) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out after ${timeoutMillis}ms waiting for >= $atLeast node(s) matching " +
                "${matcher.description}; found ${nodeCount(matcher)}",
        )
    }

    /** Same loop as [await], but waits until ZERO nodes match [tag]. */
    private fun awaitGone(
        tag: String,
        timeoutMillis: Long = 5_000,
    ) {
        val matcher = hasTestTag(tag)
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (nodeCount(matcher) == 0) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out after ${timeoutMillis}ms waiting for 0 nodes matching " +
                "${matcher.description}; found ${nodeCount(matcher)}",
        )
    }

    /**
     * Polls [PlanRepository.observePlans] every 100 ms until it emits an empty list
     * (soft-delete tombstone), or throws [AssertionError] after [timeoutMillis] ms.
     *
     * Uses [delay] (not Thread.sleep) because it runs inside [runBlocking].
     */
    private fun awaitNoPlans(timeoutMillis: Long = 10_000) =
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                if (planRepository.observePlans().first().isEmpty()) return@runBlocking
                delay(100)
            }
            val last = planRepository.observePlans().first()
            throw AssertionError(
                "Timed out after ${timeoutMillis}ms waiting for observePlans() to be empty; " +
                    "last seen ${last.size} plan(s)",
            )
        }
}
