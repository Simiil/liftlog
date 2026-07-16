package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.testing.FreshKoinRule
import de.simiil.liftlog.ui.UiTestTags.PLAN_MENU_NEW
import de.simiil.liftlog.ui.UiTestTags.PLAN_NEW_CONFIRM
import de.simiil.liftlog.ui.UiTestTags.PLAN_NEW_FIELD
import de.simiil.liftlog.ui.UiTestTags.PLAN_OVERFLOW
import de.simiil.liftlog.ui.UiTestTags.PLAN_SWITCHER
import de.simiil.liftlog.ui.UiTestTags.PLAN_SWITCHER_ITEM
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.tab_plans
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Instrumented path test for the multi-plan chrome (issue #30 PR4): the title-bar switcher
 * dropdown, hidden with exactly one plan and shown with two or more, and "New plan" in the
 * overflow menu.
 *
 * Flow:
 * 1. With a single seeded plan, the switcher affordance is entirely absent (plain title).
 * 2. Overflow -> "New plan" -> dialog -> type a name -> Create: the tab switches to the new
 *    plan (title shows its name) and the switcher now exists (2 live plans).
 * 3. Open the switcher -> tap the original plan's item: the title switches back.
 *
 * ### Harness
 * Same as [PlanDeletePathTest]: [createAndroidComposeRule] launches MainActivity sharing the
 * test's Koin singleton DB; seeding happens via the injected repo (the built-in seeder does NOT
 * run under KoinTestApplication). One plan is seeded — it's the fallback selection the Plan tab
 * shows automatically.
 */
@RunWith(AndroidJUnit4::class)
class PlanSwitchPathTest : KoinComponent {
    @get:Rule(order = 0)
    val koinRule = FreshKoinRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val planRepository: PlanRepository by inject()

    private lateinit var originalPlanId: String
    private lateinit var originalPlanName: String

    @Before
    fun seed() =
        runBlocking {
            // A single plan — the Plan tab's fallback selection, with no days (unrelated to this test).
            originalPlanName = "Original Plan"
            val plan = planRepository.createPlan(originalPlanName)
            originalPlanId = plan.id
        }

    @Test
    fun singlePlan_hasNoSwitcherAffordance() {
        // Switch to the Plan tab.
        val planLabel = runBlocking { getString(Res.string.tab_plans) }
        await(hasText(planLabel), atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNodeWithText(planLabel).performClick()

        // The plain title is visible; no switcher tag exists anywhere in the tree.
        await(hasText(originalPlanName), atLeast = 1, timeoutMillis = 10_000)
        assertEquals(0, nodeCount(hasTestTag(PLAN_SWITCHER)))
    }

    @Test
    fun createPlan_fromOverflow_switchesToItAndRevealsTheSwitcher() {
        // 1. Switch to the Plan tab.
        val planLabel = runBlocking { getString(Res.string.tab_plans) }
        await(hasText(planLabel), atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNodeWithText(planLabel).performClick()
        await(hasText(originalPlanName), atLeast = 1, timeoutMillis = 10_000)

        // 2. Overflow -> "New plan".
        awaitTag(PLAN_OVERFLOW, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(PLAN_OVERFLOW).performClick()
        awaitTag(PLAN_MENU_NEW)
        composeRule.onNodeWithTag(PLAN_MENU_NEW).performClick()

        // 3. Dialog: confirm is disabled while blank, type a name, then confirm.
        awaitTag(PLAN_NEW_FIELD)
        composeRule.onNodeWithTag(PLAN_NEW_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(PLAN_NEW_FIELD).performTextInput("Second Plan")
        composeRule.onNodeWithTag(PLAN_NEW_CONFIRM).performClick()

        // 4. Title switches to the new plan, and the switcher now exists (2 live plans).
        await(hasText("Second Plan"), atLeast = 1, timeoutMillis = 10_000)
        awaitTag(PLAN_SWITCHER, timeoutMillis = 10_000)

        // Repository state: two live plans; selection follows the new one.
        runBlocking {
            val live = planRepository.observePlans().first()
            assertEquals(2, live.size)
            assertEquals("Second Plan", live.first { it.id != originalPlanId }.name)
        }
    }

    @Test
    fun switcher_selectingAnotherPlan_changesTheTitle() =
        runBlocking {
            // Seed a second plan up front so the switcher is present from the start.
            val secondPlan = planRepository.createPlan("Second Plan")

            // 1. Switch to the Plan tab; the fallback selection is the first plan by position.
            val planLabel = runBlocking { getString(Res.string.tab_plans) }
            await(hasText(planLabel), atLeast = 1, timeoutMillis = 10_000)
            composeRule.onNodeWithText(planLabel).performClick()
            awaitTag(PLAN_SWITCHER, timeoutMillis = 10_000)
            await(hasText(originalPlanName), atLeast = 1, timeoutMillis = 10_000)

            // 2. Open the switcher -> tap the other plan's item.
            composeRule.onNodeWithTag(PLAN_SWITCHER).performClick()
            await(hasTestTag(PLAN_SWITCHER_ITEM), atLeast = 2, timeoutMillis = 10_000)
            composeRule
                .onAllNodesWithTag(PLAN_SWITCHER_ITEM)
                .filterToOne(hasText("Second Plan"))
                .performClick()

            // 3. Title changes to the newly selected plan; repository selection follows.
            await(hasText("Second Plan"), atLeast = 1, timeoutMillis = 10_000)
            val selected = planRepository.observeSelectedOrFallbackPlanId().first()
            assertEquals(secondPlan.id, selected)
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
}
