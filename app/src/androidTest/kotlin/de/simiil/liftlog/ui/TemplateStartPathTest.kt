package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.ui.UiTestTags.ADD_EXERCISE
import de.simiil.liftlog.ui.UiTestTags.HOME_TEMPLATE_CHIP
import de.simiil.liftlog.ui.UiTestTags.LOGGED_SET_ROW
import de.simiil.liftlog.ui.UiTestTags.LOG_SET_BUTTON
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3 exit criterion: "Cold start → template chip → first set = 3 taps" (05-roadmap Task 11).
 *
 * 1. [templateChip_to_loggedSet_3taps] — Home chip tap → LOG SET → a [LOGGED_SET_ROW] appears,
 *    the snapshotted target (`2/3`, `8–12`) is visible.
 * 2. [templateSession_adHocDeviation] — after starting from the template, ADD_EXERCISE → pick a
 *    DIFFERENT exercise → it appears in the templated session (ad-hoc deviation works).
 *
 * ### Harness
 * Same as [CriticalLoggingPathTest]: [createAndroidComposeRule] launches MainActivity sharing the
 * test's Hilt singleton DB; [@Before] seeds via the injected repos; assertions interact with
 * `onNodeWithTag` after [androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForIdle].
 * The built-in seeder does NOT run under HiltTestApplication, so exercises are made via
 * [ExerciseRepository.createCustom]. A prior **completed** session is seeded so Home shows
 * HomeContent (not FirstLaunch), where the template chip grid lives.
 *
 * Compose UI tests run in CI (API 34); locally they also run now that espresso is current.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TemplateStartPathTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var exerciseRepository: ExerciseRepository
    @Inject lateinit var planRepository: PlanRepository

    @Before
    fun seed() = runBlocking {
        hiltRule.inject()

        // "Test Squat" is the template exercise; "Test Lunge" is the distinct ad-hoc add (test 2).
        val squat = exerciseRepository.createCustom("Test Squat", MuscleGroup.QUADS, Equipment.BARBELL)
        exerciseRepository.createCustom("Test Lunge", MuscleGroup.QUADS, Equipment.DUMBBELL)

        // Plan + day template with one template-exercise carrying real targets.
        val plan = planRepository.createPlan("Test Plan")
        val day = planRepository.createDayTemplate(plan.id, "Push Day")
        val te = planRepository.addExerciseToTemplate(day.id, squat.id)
        planRepository.updateTemplateExerciseTargets(te.id, targetSets = 3, targetRepsMin = 8, targetRepsMax = 12)

        // A prior COMPLETED session so Home shows HomeContent (recent non-empty) — the chip grid is
        // hidden by FirstLaunch when both resume and recent are empty.
        val prior = sessionRepository.startEmptySession()
        val pse = sessionRepository.addExerciseToSession(prior.id, squat.id)
        sessionRepository.logSet(pse.id, 60.0, 5)
        sessionRepository.finishSession(prior.id)
    }

    @Test
    fun templateChip_to_loggedSet_3taps() {
        // The chip appears once the templates Flow emits the seeded plan's day templates.
        awaitTag(HOME_TEMPLATE_CHIP, timeoutMillis = 10_000)

        // Tap the chip → snapshots the template into a new session → opens Active Session.
        composeRule.onNodeWithTag(HOME_TEMPLATE_CHIP).performClick()

        // The snapshotted exercise card is active; LOG SET lands the first set.
        awaitTag(LOG_SET_BUTTON)
        composeRule.onNodeWithTag(LOG_SET_BUTTON).performClick()
        awaitNodeCount(hasTestTag(LOGGED_SET_ROW), 1)

        // The template targets surfaced into the session: progress pill "1/3" + rep hint "8–12".
        awaitText("1/3")
        awaitText("8–12")
    }

    @Test
    fun templateSession_adHocDeviation() {
        awaitTag(HOME_TEMPLATE_CHIP, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(HOME_TEMPLATE_CHIP).performClick()

        // The templated session contains "Test Squat" but not "Test Lunge" yet.
        awaitText("Test Squat")
        assertNodeCount(hasText("Test Lunge", substring = true), 0)

        // Add a DIFFERENT exercise via the shared picker.
        awaitTag(ADD_EXERCISE)
        composeRule.onNodeWithTag(ADD_EXERCISE).performClick()
        awaitText("Test Lunge")
        composeRule.onAllNodesWithText("Test Lunge").onFirst().performClick()

        // Back in the session, "Test Lunge" now shows alongside the templated "Test Squat" —
        // the discriminating assertion: it was absent before the add.
        awaitText("Test Lunge")
        assertTrue(
            "Test Squat should still be present in the templated session",
            nodeCount(hasText("Test Squat", substring = true)) >= 1,
        )
    }

    // ── Waiting helpers ──────────────────────────────────────────────────────────
    // The Active Session screen runs a 1s timer, so the test clock never settles enough for the
    // poll-only `composeRule.waitUntil` to observe a fresh tree. waitForIdle()+poll reliably picks
    // up Room-Flow-driven nodes (cards loading in) across both timer and non-timer screens.

    private fun nodeCount(matcher: SemanticsMatcher): Int =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun assertNodeCount(matcher: SemanticsMatcher, expected: Int) {
        composeRule.waitForIdle()
        assertTrue("Expected $expected nodes, found ${nodeCount(matcher)}", nodeCount(matcher) == expected)
    }

    private fun awaitTag(tag: String, timeoutMillis: Long = 5_000) = await(hasTestTag(tag), 1, timeoutMillis)

    private fun awaitText(text: String, timeoutMillis: Long = 5_000) =
        await(hasText(text, substring = true), 1, timeoutMillis)

    private fun awaitNodeCount(matcher: SemanticsMatcher, count: Int, timeoutMillis: Long = 5_000) =
        await(matcher, count, timeoutMillis)

    private fun await(matcher: SemanticsMatcher, atLeast: Int, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (nodeCount(matcher) >= atLeast) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting for >= $atLeast node(s) matching ${matcher.description}; found ${nodeCount(matcher)}")
    }
}
