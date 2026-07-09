package de.simiil.liftlog.ui

import androidx.compose.ui.test.ComposeTimeoutException
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
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.ui.UiTestTags.ADD_EXERCISE
import de.simiil.liftlog.ui.UiTestTags.HOME_RESUME_CARD
import de.simiil.liftlog.ui.UiTestTags.HOME_START_EMPTY
import de.simiil.liftlog.ui.UiTestTags.LOGGED_SET_ROW
import de.simiil.liftlog.ui.UiTestTags.LOG_SET_BUTTON
import de.simiil.liftlog.ui.UiTestTags.WEIGHT_INCREMENT
import de.simiil.liftlog.ui.UiTestTags.WEIGHT_VALUE
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The headline M2 exit criterion (05-roadmap): a single deep test that walks the whole
 * critical logging path and proves it is both *fast* (pre-filled, ~1 tap to log) and
 * *kill-proof* (survives an activity teardown without losing logged sets).
 *
 * Flow: Home → start empty session → pick exercise → log a pre-filled set → adjust the
 * weight → log a second set → tear the activity down → resume and verify both sets.
 *
 * ### "Process-death" proxy
 * A true process death wipes the in-memory Room database the Hilt test harness installs
 * (`TestDatabaseModule`), so we cannot use it here. Instead we drive
 * [androidx.test.core.app.ActivityScenario.recreate], which destroys and rebuilds the
 * Activity (and re-runs nav/state restoration) while the *singleton* in-memory database —
 * the single source of truth — survives. That is precisely the assertion we care about:
 * the logged sets must be rehydrated **from the repository/Room layer**, not from
 * transient Compose `rememberSaveable` / `SavedStateHandle` state. If logging only lived
 * in memory, the rebuilt Activity would show an empty card and this test would fail.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CriticalLoggingPathTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    // The Active Session screen requests POST_NOTIFICATIONS contextually (#36); pre-grant
    // so the system dialog never obscures the tree on API 33+.
    @get:Rule(order = 2)
    val grantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    private lateinit var exerciseId: String

    @Before
    fun seedPriorSession() =
        runBlocking {
            hiltRule.inject()
            // 1. A custom exercise, so the picker has something to pick (the built-in seeder
            //    does NOT run under HiltTestApplication — this is the only visible exercise).
            val ex = exerciseRepository.createCustom("Test Bench", MuscleGroup.CHEST, Equipment.BARBELL)
            exerciseId = ex.id
            // 2. A PRIOR COMPLETED session with this exercise at a known weight/reps. Once it is
            //    finished (endedAt set), `lastPerformance` can find it and the active card will
            //    pre-fill the next set from its first set: 30 kg × 10 (hard constraint 2).
            val session = sessionRepository.startEmptySession()
            val se = sessionRepository.addExerciseToSession(session.id, exerciseId)
            sessionRepository.logSet(se.id, 30.0, 10)
            sessionRepository.logSet(se.id, 30.0, 10)
            sessionRepository.logSet(se.id, 30.0, 8)
            sessionRepository.finishSession(session.id)
        }

    @Test
    fun startLogAdjustLog_thenSurviveRecreate() {
        // ── Start a fresh empty session from Home ───────────────────────────────────
        waitUntilExists(hasTestTag(HOME_START_EMPTY))
        composeRule.onNodeWithTag(HOME_START_EMPTY).performClick()

        // ── Add the exercise via the picker ─────────────────────────────────────────
        waitUntilExists(hasTestTag(ADD_EXERCISE))
        composeRule.onNodeWithTag(ADD_EXERCISE).performClick()

        // "Test Bench" can appear twice (Recent + Results); either row selects the same id.
        waitUntilExists(hasText("Test Bench"))
        composeRule.onAllNodesWithText("Test Bench").onFirst().performClick()

        // ── Pre-fill from the last session's first set: 30 kg × 10 (hard constraint 2) ─
        // Asserting "30" appears BEFORE any manual entry is what proves pre-fill works.
        waitUntilExists(hasTestTag(WEIGHT_VALUE) and hasText("30", substring = true))

        // ── LOG SET (1 tap) → first logged row, "30 kg × 10" ────────────────────────
        composeRule.onNodeWithTag(LOG_SET_BUTTON).performClick()
        waitUntilNodeCount(hasTestTag(LOGGED_SET_ROW), 1)

        // ── Adjust +2.5 kg → 32.5 (KG step is 2.5; 30 + 2.5 = 32.5) ─────────────────
        composeRule.onNodeWithTag(WEIGHT_INCREMENT).performClick()
        waitUntilExists(hasTestTag(WEIGHT_VALUE) and hasText("32.5", substring = true))

        // ── LOG SET → second logged row, "32.5 kg × 10" ────────────────────────────
        composeRule.onNodeWithTag(LOG_SET_BUTTON).performClick()
        waitUntilNodeCount(hasTestTag(LOGGED_SET_ROW), 2)

        // ── Tear the Activity down (process-death proxy; see class KDoc) ─────────────
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // ── Resume: if restoration landed on Home rather than the active session,
        //    tap the Resume card. Normally the nav back stack restores us straight
        //    into the active session and this branch is skipped. ───────────────────
        if (!waitForExistsOrTimeout(hasTestTag(LOGGED_SET_ROW))) {
            waitUntilExists(hasTestTag(HOME_RESUME_CARD))
            composeRule.onNodeWithTag(HOME_RESUME_CARD).performClick()
        }

        // ── Both logged sets are still present (rehydrated from Room) ────────────────
        waitUntilNodeCount(hasTestTag(LOGGED_SET_ROW), 2)

        // Each set survived with its own weight. Scoping to LOGGED_SET_ROW excludes the
        // ghost "last: 30 kg × 10·10·8" line (which has no row tag) from matching "30 kg".
        composeRule
            .onAllNodes(hasTestTag(LOGGED_SET_ROW) and hasText("30 kg", substring = true))
            .assertCountEquals(1)
        composeRule
            .onAllNodes(hasTestTag(LOGGED_SET_ROW) and hasText("32.5 kg", substring = true))
            .assertCountEquals(1)

        // Exact rendered row text (LoggedSetRow collapsed: "{weight} {unit} × {reps}",
        // where × is U+00D7). The ghost line reads "last: 30 kg × 10·10·8", so neither
        // exact string matches it.
        composeRule.onAllNodesWithText("30 kg × 10").assertCountEquals(1)
        composeRule.onAllNodesWithText("32.5 kg × 10").assertCountEquals(1)
    }

    // ── Wait helpers ────────────────────────────────────────────────────────────────
    // Built on the stable `waitUntil(timeoutMillis, condition)` overload rather than the
    // experimental matcher-based ones, polling the merged semantics tree directly.
    // `atLeastOneRootRequired = false` keeps the poll safe during the brief window after
    // recreate() when no compose root may yet be attached.

    private fun matchingNodeCount(matcher: SemanticsMatcher): Int =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun waitUntilExists(
        matcher: SemanticsMatcher,
        timeoutMillis: Long = 5_000,
    ) {
        composeRule.waitUntil(timeoutMillis) { matchingNodeCount(matcher) > 0 }
    }

    private fun waitUntilNodeCount(
        matcher: SemanticsMatcher,
        count: Int,
        timeoutMillis: Long = 5_000,
    ) {
        composeRule.waitUntil(timeoutMillis) { matchingNodeCount(matcher) == count }
    }

    /** Non-throwing variant for the defensive resume-card fallback. */
    private fun waitForExistsOrTimeout(
        matcher: SemanticsMatcher,
        timeoutMillis: Long = 3_000,
    ): Boolean =
        try {
            waitUntilExists(matcher, timeoutMillis)
            true
        } catch (_: ComposeTimeoutException) {
            false
        }
}
