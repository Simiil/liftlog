package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.testing.FreshKoinRule
import de.simiil.liftlog.ui.UiTestTags.DAY_EDITOR_DONE
import de.simiil.liftlog.ui.UiTestTags.DAY_NAME_FIELD
import de.simiil.liftlog.ui.UiTestTags.PICKER_ADD_SELECTED
import de.simiil.liftlog.ui.UiTestTags.PLAN_ADD_DAY
import de.simiil.liftlog.ui.UiTestTags.PLAN_DAY_START
import de.simiil.liftlog.ui.UiTestTags.TEMPLATE_ADD_EXERCISE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Instrumented path test for the no-Save, persist-on-change plan editing flow (issue #30 PR3b,
 * 2026-06-12 autosave design). Every edit lands in Room immediately — there is no Save button
 * anywhere on this path.
 *
 * Flow: Plan tab → add a day → type its name and add an exercise in the DB-backed day editor →
 * navigate back (no Save) → the day row already shows the typed name and exercise count →
 * play the day → the active session shows the added exercise.
 *
 * ### Harness
 * Same as [TemplateStartPathTest]: [createAndroidComposeRule] launches MainActivity sharing the
 * test's Koin singleton DB; [@Before] seeds via the injected repos (the built-in seeder does NOT
 * run under KoinTestApplication). One plan with zero days is seeded — it's the fallback selection
 * the Plan tab shows automatically.
 */
@RunWith(AndroidJUnit4::class)
class PlanEditPathTest : KoinComponent {
    @get:Rule(order = 0)
    val koinRule = FreshKoinRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val planRepository: PlanRepository by inject()

    private val exerciseRepository: ExerciseRepository by inject()

    private lateinit var planId: String
    private lateinit var exerciseId: String

    @Before
    fun seed() =
        runBlocking {
            val exercise = exerciseRepository.createCustom("Test Row", MuscleGroup.BACK, Equipment.MACHINE)
            exerciseId = exercise.id

            // A single plan with zero days — the Plan tab's fallback selection, empty to start.
            val plan = planRepository.createPlan("Test Plan")
            planId = plan.id
        }

    @Test
    fun addDay_editInPlace_startsSessionWithTheAddedExercise() {
        // 1. Switch to the Plan tab.
        val planLabel = composeRule.activity.getString(R.string.tab_plans)
        await(hasText(planLabel), atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNodeWithText(planLabel).performClick()

        // 2. Add a training day → lands in the DB-backed day editor.
        awaitTag(PLAN_ADD_DAY, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(PLAN_ADD_DAY).performClick()
        awaitTag(DAY_NAME_FIELD, timeoutMillis = 10_000)

        // Repository persistence: exactly one (blank) day now exists on the seeded plan.
        val dayId = awaitSingleDayId(planId)

        // 3. Type a name — no Save, this is the autosave editor.
        composeRule.onNodeWithTag(DAY_NAME_FIELD).performTextInput("Leg Day")

        // 4. Add the seeded exercise via the shared multi-select picker.
        awaitTag(TEMPLATE_ADD_EXERCISE)
        composeRule.onNodeWithTag(TEMPLATE_ADD_EXERCISE).performClick()
        awaitText("Test Row")
        composeRule.onAllNodesWithText("Test Row").onFirst().performClick()
        awaitTag(PICKER_ADD_SELECTED)
        composeRule.onNodeWithTag(PICKER_ADD_SELECTED).performClick()

        // Back in the day editor: the added exercise is visible.
        awaitText("Test Row")

        // Repository persistence: the exercise landed on the day.
        awaitTemplateExerciseCount(dayId, 1)

        // 5. Leave the editor (Done pill; pure navigation, nothing left to save).
        composeRule.onNodeWithTag(DAY_EDITOR_DONE).performClick()

        // Repository persistence: the rename survived leaving the screen (flushed on dispose).
        awaitDayName(dayId, "Leg Day")

        // 6. Back on the Plan tab: the day row already shows the typed name and exercise count.
        awaitText("Leg Day")
        awaitText("1 exercise")

        // 7. Play the day → active session shows the added exercise.
        awaitTag(PLAN_DAY_START)
        composeRule.onNodeWithTag(PLAN_DAY_START).performClick()
        awaitText("Test Row")
    }

    // ── Waiting helpers ──────────────────────────────────────────────────────────
    // The Active Session screen runs a 1s timer, so the test clock never settles enough for a
    // poll-only `composeRule.waitUntil` to observe a fresh tree. waitForIdle()+poll (the
    // TemplateStartPathTest idiom) reliably picks up Room-Flow-driven nodes across both timer
    // and non-timer screens.

    private fun nodeCount(matcher: SemanticsMatcher): Int =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun awaitTag(
        tag: String,
        timeoutMillis: Long = 5_000,
    ) = await(hasTestTag(tag), 1, timeoutMillis)

    private fun awaitText(
        text: String,
        timeoutMillis: Long = 5_000,
    ) = await(hasText(text, substring = true), 1, timeoutMillis)

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
            "Timed out after ${timeoutMillis}ms waiting for >= $atLeast node(s) matching ${matcher.description}; found ${nodeCount(
                matcher,
            )}",
        )
    }

    // ── Repository polling helpers ────────────────────────────────────────────────
    // Use `delay` (not Thread.sleep) — these run inside `runBlocking`.

    private fun awaitSingleDayId(
        planId: String,
        timeoutMillis: Long = 10_000,
    ): String =
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                val days = planRepository.observeDayTemplates(planId).first()
                if (days.size == 1) return@runBlocking days.first().id
                delay(100)
            }
            throw AssertionError("Timed out after ${timeoutMillis}ms waiting for exactly one day on plan $planId")
        }

    private fun awaitTemplateExerciseCount(
        templateId: String,
        count: Int,
        timeoutMillis: Long = 10_000,
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val exercises = planRepository.observeTemplateExercises(templateId).first()
            if (exercises.size == count) {
                assertEquals(count, exercises.size)
                if (count > 0) assertEquals(exerciseId, exercises.first().exerciseId)
                return@runBlocking
            }
            delay(100)
        }
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting for $count template exercise(s) on $templateId")
    }

    private fun awaitDayName(
        templateId: String,
        name: String,
        timeoutMillis: Long = 10_000,
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val day = planRepository.getDayTemplate(templateId)
            if (day?.name == name) {
                assertNotNull(day)
                return@runBlocking
            }
            delay(100)
        }
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting for day $templateId to be named \"$name\"")
    }
}
