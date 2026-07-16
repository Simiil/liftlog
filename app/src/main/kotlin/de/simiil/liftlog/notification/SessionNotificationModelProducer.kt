package de.simiil.liftlog.notification

import de.simiil.liftlog.domain.logging.ActiveEntry
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.logging.ActiveExerciseDefaults
import de.simiil.liftlog.domain.logging.Prefill
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant

/**
 * What the session notification shows and what its LOG SET action would log.
 *
 * Body states derived by the notification builder:
 * - [exerciseName] == null (no exercises) or ([nextWeightKg] == null with [setsDone] == 0,
 *   never-performed): empty state, LOG SET hidden.
 * - [targetSets] != null and [setsDone] < [targetSets]: "Set {setsDone+1} of {targetSets} · next: …".
 * - [targetSets] == null (ad-hoc) or all targets met: "{setsDone} sets · next: …".
 */
data class SessionNotificationModel(
    val sessionId: String,
    val startedAt: Instant,
    val sessionName: String?,
    val exerciseName: String?,
    val sessionExerciseId: String?,
    val setsDone: Int,
    val targetSets: Int?,
    val nextWeightKg: Double?,
    val nextReps: Int,
    val unit: WeightUnit,
) {
    /** LOG SET only when there is something concrete to log. */
    val showLogSet: Boolean
        get() = sessionExerciseId != null && nextWeightKg != null

    fun bodyState(): NotificationBodyState =
        when {
            exerciseName == null || nextWeightKg == null -> NotificationBodyState.EMPTY
            targetSets != null && setsDone < targetSets -> NotificationBodyState.TARGET_PROGRESS
            else -> NotificationBodyState.SET_COUNT
        }
}

enum class NotificationBodyState { EMPTY, TARGET_PROGRESS, SET_COUNT }

/**
 * Maps session state to [SessionNotificationModel]s, mirroring the Active Session
 * screen's rules: current exercise = [ActiveEntryTracker]'s selection while it points
 * at a live, incomplete exercise, else [ActiveExerciseDefaults.pickDefault]; next-set
 * values = the tracker's dialed values, else [Prefill.forNextSet]. Emits null when the
 * session is finished or discarded — the caller must then remove the notification.
 */
class SessionNotificationModelProducer(
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val tracker: ActiveEntryTracker,
    private val names: ExerciseNameResolver,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun models(sessionId: String): Flow<SessionNotificationModel?> {
        // Last-performance snapshot per exercise, fetched once (mirrors the ViewModel's ghostCache).
        val ghostCache = mutableMapOf<String, List<LoggedSet>>()
        return combine(
            sessionRepository.observeSessionDetails(sessionId),
            exerciseRepository.observeAll(),
            settingsRepository.weightUnit,
            tracker.state,
        ) { details, exercises, unit, entry ->
            Inputs(details, exercises.associateBy { it.id }, unit, entry)
        }.mapLatest { (details, exercisesById, unit, entry) ->
            if (details == null) return@mapLatest null
            val session = details.session
            if (session.endedAt != null || session.deletedAt != null) return@mapLatest null

            val card = currentCard(details, entry?.sessionExerciseId)
            if (card == null) {
                return@mapLatest SessionNotificationModel(
                    sessionId = session.id,
                    startedAt = session.startedAt,
                    sessionName = session.templateNameSnapshot,
                    exerciseName = null,
                    sessionExerciseId = null,
                    setsDone = 0,
                    targetSets = null,
                    nextWeightKg = null,
                    nextReps = Prefill.DEFAULT_REPS,
                    unit = unit,
                )
            }

            val se = card.sessionExercise
            val trackerApplies = entry != null && entry.sessionExerciseId == se.id && entry.weightKg != null
            val (nextWeightKg, nextReps) =
                if (trackerApplies) {
                    entry.weightKg to entry.reps
                } else {
                    val ghost =
                        ghostCache.getOrPut(se.exerciseId) {
                            sessionRepository.lastPerformance(se.exerciseId)
                        }
                    val prefill = Prefill.forNextSet(setsThisEntry = card.sets, lastPerformance = ghost)
                    prefill.weightKg to prefill.reps
                }

            SessionNotificationModel(
                sessionId = session.id,
                startedAt = session.startedAt,
                sessionName = session.templateNameSnapshot,
                exerciseName = exercisesById[se.exerciseId]?.let { names.displayName(it.id, it.name) },
                sessionExerciseId = se.id,
                setsDone = card.sets.size,
                targetSets = se.targetSets,
                nextWeightKg = nextWeightKg,
                nextReps = nextReps,
                unit = unit,
            )
        }
    }

    /**
     * The tracker's selection wins while it points at a live, incomplete exercise;
     * a stale selection (removed, or target just met) falls through to the shared
     * default — which is also the in-app auto-advance target.
     */
    private fun currentCard(
        details: SessionWithDetails,
        trackedSeId: String?,
    ): SessionExerciseWithSets? {
        val tracked = details.exercises.firstOrNull { it.sessionExercise.id == trackedSeId }
        if (tracked != null && !ActiveExerciseDefaults.isComplete(tracked)) return tracked
        val defaultId = ActiveExerciseDefaults.pickDefault(details) ?: return null
        return details.exercises.firstOrNull { it.sessionExercise.id == defaultId }
    }

    private data class Inputs(
        val details: SessionWithDetails?,
        val exercisesById: Map<String, Exercise>,
        val unit: WeightUnit,
        val entry: ActiveEntry?,
    )
}
