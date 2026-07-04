package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.ui.UiTestTags.PLAN_DELETE_CONFIRM
import de.simiil.liftlog.ui.UiTestTags.PLAN_MENU_DELETE
import de.simiil.liftlog.ui.UiTestTags.PLAN_OVERFLOW
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Instrumented path test for the plan delete flow (single-plan Plan tab, issue #30 PR3b).
 *
 * Flow:
 * 1. Switch to the Plan tab via the bottom nav.
 * 2. Open the top-bar overflow ("⋮") → "Delete plan".
 * 3. Confirm the AlertDialog → atomically tombstones the plan and reseeds a fresh default
 *    (the tab never observes zero plans, see [de.simiil.liftlog.domain.plan.DefaultPlanEnsurer]).
 * 4. Assert: the tab's title becomes the localized default-plan name, and the repository holds
 *    exactly one live plan whose id differs from the deleted one.
 *
 * ### Harness
 * Same as [TemplateStartPathTest]: [createAndroidComposeRule] launches MainActivity sharing
 * the test's Hilt singleton DB; seeding happens via the injected repos (the built-in seeder
 * does NOT run under HiltTestApplication, so the exercise is made via
 * [ExerciseRepository.createCustom]). Only one plan is seeded — it's the fallback selection
 * the Plan tab shows automatically (no explicit `selectPlan` needed).
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
    fun planDelete_overflowToConfirm_reseedsDefaultAndTombstonesOld() {
        // 1. Switch to the Plan tab (bottom-nav item addressed by its localized label).
        val planLabel = composeRule.activity.getString(R.string.tab_plans)
        await(hasText(planLabel), atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNodeWithText(planLabel).performClick()

        // 2. Top-bar overflow → "Delete plan".
        awaitTag(PLAN_OVERFLOW, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(PLAN_OVERFLOW).performClick()
        awaitTag(PLAN_MENU_DELETE)
        composeRule.onNodeWithTag(PLAN_MENU_DELETE).performClick()

        // 3. Confirm dialog → Delete.
        awaitTag(PLAN_DELETE_CONFIRM)
        composeRule.onNodeWithTag(PLAN_DELETE_CONFIRM).performClick()

        // 4a. The tab's title becomes the localized default-plan name.
        val defaultPlanName = composeRule.activity.getString(R.string.default_plan_name)
        await(hasText(defaultPlanName), atLeast = 1, timeoutMillis = 10_000)

        // 4b. Room-level: exactly one live plan, with a different id than the deleted one.
        awaitSingleLivePlanDifferentFrom(planId)
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

    /**
     * Polls [PlanRepository.observePlans] every 100 ms until it emits exactly one live plan whose
     * id differs from [oldPlanId] (the atomic delete+reseed landed), or throws after [timeoutMillis].
     *
     * Uses [delay] (not Thread.sleep) because it runs inside [runBlocking].
     */
    private fun awaitSingleLivePlanDifferentFrom(
        oldPlanId: String,
        timeoutMillis: Long = 10_000,
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val live = planRepository.observePlans().first()
            if (live.size == 1 && live.first().id != oldPlanId) {
                assertEquals(1, live.size)
                assertNotEquals(oldPlanId, live.first().id)
                return@runBlocking
            }
            delay(100)
        }
        val last = planRepository.observePlans().first()
        throw AssertionError(
            "Timed out after ${timeoutMillis}ms waiting for exactly one live plan different from " +
                "$oldPlanId; last seen ${last.map { it.id }}",
        )
    }
}
