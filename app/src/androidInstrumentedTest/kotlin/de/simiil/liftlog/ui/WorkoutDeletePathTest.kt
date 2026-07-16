package de.simiil.liftlog.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.testing.FreshKoinRule
import de.simiil.liftlog.ui.UiTestTags.HOME_RECENT_ROW
import de.simiil.liftlog.ui.UiTestTags.HOME_START_EMPTY
import de.simiil.liftlog.ui.UiTestTags.SESSION_DELETE_CONFIRM
import de.simiil.liftlog.ui.UiTestTags.SESSION_EDIT_DELETE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.cd_edit_workout
import org.jetbrains.compose.resources.getString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Instrumented path test for the workout delete flow (Edit-workout sheet).
 *
 * Flow:
 * 1. Home → tap the seeded finished session under "Recent" → Session Detail.
 * 2. Tap the pencil TopAppBar action (cd_edit_workout) → Edit-workout sheet.
 * 3. Tap the red Delete TextButton → confirm AlertDialog.
 * 4. Tap the confirm button → soft-deletes the session and pops back to Home.
 * 5. Assert: Home shows again (HOME_START_EMPTY) AND the session is tombstoned
 *    (observeSessionDetails emits null).
 *
 * ### Harness
 * Same as [SessionMetaPathTest]: [createAndroidComposeRule] launches MainActivity sharing the
 * test's Koin singleton DB; seeding happens via the injected repos. The built-in seeder does
 * NOT run under KoinTestApplication, so the exercise is made via
 * [ExerciseRepository.createCustom] (same idiom as [TemplateStartPathTest]).
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDeletePathTest : KoinComponent {
    @get:Rule(order = 0)
    val koinRule = FreshKoinRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val sessionRepository: SessionRepository by inject()

    private val exerciseRepository: ExerciseRepository by inject()

    private lateinit var sessionId: String

    @Before
    fun seed() =
        runBlocking {
            // A FINISHED session with one exercise and one logged set, so Home shows it
            // under "Recent" (mirrors TemplateStartPathTest's prior-session seeding).
            val squat = exerciseRepository.createCustom("Test Squat", MuscleGroup.QUADS, Equipment.BARBELL)
            val session = sessionRepository.startEmptySession()
            val se = sessionRepository.addExerciseToSession(session.id, squat.id)
            sessionRepository.logSet(se.id, 60.0, 5)
            sessionRepository.finishSession(session.id)
            sessionId = session.id
        }

    @Test
    fun workoutDelete_detailToEditSheetToConfirm_tombstonesAndPopsHome() {
        // 1. Home shows the finished session under "Recent" → open Session Detail.
        awaitTag(HOME_RECENT_ROW, timeoutMillis = 10_000)
        composeRule.onNodeWithTag(HOME_RECENT_ROW).performClick()

        // 2. The pencil appears once the detail header loads (startedAt/endedAt non-null).
        val editCd = hasContentDescription(runBlocking { getString(Res.string.cd_edit_workout) })
        await(editCd, atLeast = 1, timeoutMillis = 10_000)
        composeRule.onNode(editCd).performClick()

        // 3. Edit-workout sheet → red Delete button.
        awaitTag(SESSION_EDIT_DELETE)
        composeRule.onNodeWithTag(SESSION_EDIT_DELETE).performClick()

        // 4. Confirm dialog → Delete.
        awaitTag(SESSION_DELETE_CONFIRM)
        composeRule.onNodeWithTag(SESSION_DELETE_CONFIRM).performClick()

        // 5a. Back on Home (the deleted session was the only one, so the start card shows
        //     either in HomeContent or the FirstLaunch empty state — both carry the tag),
        //     and the deleted session's "Recent" row is gone.
        awaitTag(HOME_START_EMPTY, timeoutMillis = 10_000)
        awaitGone(HOME_RECENT_ROW)

        // 5b. Room-level tombstone: the soft-deleted session no longer resolves.
        awaitSessionGone(sessionId)
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
     * Polls [SessionRepository.observeSessionDetails] every 100 ms until it emits null
     * (soft-delete tombstone), or throws [AssertionError] after [timeoutMillis] ms.
     *
     * Uses [delay] (not Thread.sleep) because it runs inside [runBlocking].
     */
    private fun awaitSessionGone(
        id: String,
        timeoutMillis: Long = 10_000,
    ) = runBlocking {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (sessionRepository.observeSessionDetails(id).first() == null) return@runBlocking
            delay(100)
        }
        val last = sessionRepository.observeSessionDetails(id).first()?.session
        throw AssertionError(
            "Timed out after ${timeoutMillis}ms waiting for session $id to be soft-deleted; " +
                "last seen ${last?.let { "non-null session (endedAt=${it.endedAt}, deletedAt=${it.deletedAt})" } ?: "null"}",
        )
    }
}
