package de.simiil.liftlog.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import de.simiil.liftlog.MainActivity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.testing.FreshKoinRule
import de.simiil.liftlog.ui.UiTestTags.LOG_SET_BUTTON
import de.simiil.liftlog.ui.navigation.SESSION_DEEP_LINK_BASE
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Pins the session notification's tap-to-open path (issue #36): a cold start with the
 * internal `liftlog://session/{id}` deep link must land directly on the Active Session
 * screen (not Home), with the synthetic back stack Navigation builds underneath.
 */
@RunWith(AndroidJUnit4::class)
class SessionDeepLinkTest : KoinComponent {
    @get:Rule(order = 0)
    val koinRule = FreshKoinRule()

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    // The Active Session screen requests POST_NOTIFICATIONS contextually; pre-grant so
    // the system dialog never obscures the tree on API 33+.
    @get:Rule(order = 2)
    val grantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val sessionRepository: SessionRepository by inject()

    private val exerciseRepository: ExerciseRepository by inject()

    @Test
    fun deepLink_coldStart_landsOnActiveSession() {
        val sessionId =
            runBlocking {
                val exercise = exerciseRepository.createCustom("Test Bench", MuscleGroup.CHEST, Equipment.BARBELL)
                val session = sessionRepository.startEmptySession()
                sessionRepository.addExerciseToSession(session.id, exercise.id)
                session.id
            }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData("$SESSION_DEEP_LINK_BASE/$sessionId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            // LOG SET exists only on the Active Session screen — landing there is the assertion.
            awaitTag(LOG_SET_BUTTON)
        }
    }

    /** waitForIdle + re-check: poll-based waitUntil reads a stale tree next to the 1 s session timer. */
    private fun awaitTag(
        tag: String,
        timeoutMillis: Long = 5_000,
    ) {
        val matcher: SemanticsMatcher = hasTestTag(tag)
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting for a node with tag $tag")
    }
}
