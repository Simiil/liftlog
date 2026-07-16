package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.testing.FreshKoinRule
import de.simiil.liftlog.ui.UiTestTags.HOME_START_EMPTY
import de.simiil.liftlog.ui.UiTestTags.RPE_INCREMENT
import de.simiil.liftlog.ui.UiTestTags.SESSION_META_NOTE
import de.simiil.liftlog.ui.UiTestTags.SESSION_META_ROW
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

/**
 * Instrumented path test for the Session Meta Row (workout-level RPE + note).
 *
 * Flow:
 * 1. Home → start empty workout.
 * 2. Scroll to / ensure the meta row is visible (last item of the LazyColumn).
 * 3. Tap the collapsed row to expand it.
 * 4. Tap RPE_INCREMENT twice — null → 8.0 (DEFAULT) → 8.5.
 * 5. Type "felt strong" into the note field.
 * 6. Tap "Done" to collapse (triggers onNoteFlush on the VM side).
 * 7. Poll Room via observeActiveSession until rpe == 8.5 AND note == "felt strong".
 *
 * ### Timer tree-staleness
 * The Active Session screen drives a 1 s timer that keeps the composition busy.
 * poll-based `composeRule.waitUntil` reads a stale tree on active-session pages.
 * We use the same waitForIdle+poll pattern as TemplateStartPathTest (per-file copy).
 */
@RunWith(AndroidJUnit4::class)
class SessionMetaPathTest : KoinComponent {
    @get:Rule(order = 0)
    val koinRule = FreshKoinRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    // The Active Session screen requests POST_NOTIFICATIONS contextually (#36); pre-grant
    // so the system dialog never obscures the tree on API 33+.
    @get:Rule(order = 2)
    val grantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val sessionRepository: SessionRepository by inject()

    @Test
    fun sessionMeta_rpeAndNote_persistAfterCollapse() {
        // 1. Start an empty session from Home.
        awaitTag(HOME_START_EMPTY, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(HOME_START_EMPTY).performClick()

        // 2. Bring the meta row into view (last item; on an empty session it may already be
        //    visible, but scroll just in case).
        awaitTag(SESSION_META_ROW, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(SESSION_META_ROW).performScrollTo()

        // 3. Tap the collapsed row to expand.
        composeRule.onNodeWithTag(SESSION_META_ROW).performClick()
        composeRule.waitForIdle()

        // 4. Tap RPE_INCREMENT twice: null → 8.0 → 8.5.
        //    Between the two taps we need two separate signals:
        //    (a) awaitSession DB wait — confirms Room has committed the first write;
        //    (b) await(cd_rpe_clear) UI wait — confirms the composition has caught up and the
        //        stepper now holds a value (Clear only appears when value != null), so the
        //        second tap sees the correct state rather than stale null.
        awaitTag(RPE_INCREMENT)
        composeRule.onNodeWithTag(RPE_INCREMENT).performClick()
        // (a) DB write confirmed: rpe == 8.0
        awaitSession(description = "rpe == 8.0") { it?.rpe != null && abs(it.rpe!! - 8.0) < 0.001 }
        // (b) Composition catch-up confirmed: Clear button is visible
        await(
            hasContentDescription(composeRule.activity.getString(R.string.cd_rpe_clear)),
            atLeast = 1,
            timeoutMillis = 5_000,
        )
        composeRule.onNodeWithTag(RPE_INCREMENT).performClick()
        composeRule.waitForIdle()

        // 5. Type the note text.
        awaitTag(SESSION_META_NOTE)
        composeRule.onNodeWithTag(SESSION_META_NOTE).performTextInput("felt strong")
        composeRule.waitForIdle()

        // 6. Tap "Done" to collapse and flush the note.
        val doneLabel = composeRule.activity.getString(R.string.common_done)
        composeRule.onNodeWithText(doneLabel).performClick()
        composeRule.waitForIdle()

        // 7. Poll Room until rpe == 8.5 AND note == "felt strong".
        //    onNoteFlush() dispatches a coroutine; give it a generous window.
        val session =
            awaitSession(
                timeoutMillis = 10_000,
                description = "rpe == 8.5 && note == \"felt strong\"",
            ) { it?.rpe != null && abs(it.rpe!! - 8.5) < 0.001 && it.note == "felt strong" }

        assertEquals("RPE should be 8.5 after two increments", 8.5, session.rpe!!, 0.001)
        assertEquals("Note should be 'felt strong' after flush", "felt strong", session.note)
    }

    // ── Wait helpers ─────────────────────────────────────────────────────────────
    // The Active Session screen has a running 1 s timer so composeRule.waitUntil reads
    // a stale tree. Use waitForIdle() + manual re-check (same pattern as TemplateStartPathTest).

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
     * Polls [SessionRepository.observeActiveSession] every 100 ms until [predicate] is satisfied,
     * or throws [AssertionError] after [timeoutMillis] ms. Returns the matching non-null session.
     *
     * Uses [delay] (not Thread.sleep) because it runs inside [runBlocking].
     */
    private fun awaitSession(
        timeoutMillis: Long = 10_000,
        description: String,
        predicate: (Session?) -> Boolean,
    ): Session =
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                val session = sessionRepository.observeActiveSession().first()
                if (predicate(session)) return@runBlocking requireNotNull(session)
                delay(100)
            }
            val last = sessionRepository.observeActiveSession().first()
            throw AssertionError(
                "Timed out after ${timeoutMillis}ms waiting for $description; " +
                    "last seen rpe=${last?.rpe}, note=${last?.note}",
            )
        }
}
