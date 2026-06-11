package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.ui.UiTestTags.HOME_START_EMPTY
import de.simiil.liftlog.ui.UiTestTags.RPE_INCREMENT
import de.simiil.liftlog.ui.UiTestTags.SESSION_META_NOTE
import de.simiil.liftlog.ui.UiTestTags.SESSION_META_ROW
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

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
 * We use the `await` helper from [TemplateStartPathTest] (waitForIdle + re-check loop).
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SessionMetaPathTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

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
        awaitTag(RPE_INCREMENT)
        composeRule.onNodeWithTag(RPE_INCREMENT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RPE_INCREMENT).performClick()
        composeRule.waitForIdle()

        // 5. Type the note text.
        awaitTag(SESSION_META_NOTE)
        composeRule.onNodeWithTag(SESSION_META_NOTE).performTextInput("felt strong")
        composeRule.waitForIdle()

        // 6. Tap "Done" to collapse and flush the note.
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        // 7. Poll Room until rpe == 8.5 AND note == "felt strong".
        //    onNoteFlush() dispatches a coroutine; give it a generous window.
        val session =
            awaitSessionMeta(
                expectedRpe = 8.5,
                expectedNote = "felt strong",
                timeoutMillis = 10_000,
            )

        assertNotNull("Active session should exist after meta update", session)
        assertEquals("RPE should be 8.5 after two increments", 8.5, session!!.rpe!!, 0.001)
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
     * Polls [SessionRepository.observeActiveSession] until both [expectedRpe] and
     * [expectedNote] are set, or until [timeoutMillis] elapses.
     *
     * Returns the matched session (or null if timed out — the caller asserts non-null).
     */
    private fun awaitSessionMeta(
        expectedRpe: Double,
        expectedNote: String,
        timeoutMillis: Long,
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val session = sessionRepository.observeActiveSession().first()
            if (session?.rpe != null &&
                Math.abs(session.rpe!! - expectedRpe) < 0.001 &&
                session.note == expectedNote
            ) {
                return@runBlocking session
            }
            Thread.sleep(100)
        }
        // Return whatever is currently in DB so the caller can log the actual values.
        sessionRepository.observeActiveSession().first()
    }
}
