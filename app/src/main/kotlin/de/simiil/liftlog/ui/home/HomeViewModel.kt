package de.simiil.liftlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateChipUi(
    val templateId: String,
    val name: String,
    /** Number of exercises in the day template. */
    val exerciseCount: Int = 0,
    /** Up to 3 distinct muscle groups (first-seen order), for the chip's bottom line. */
    val muscleGroups: List<MuscleGroup> = emptyList(),
)

data class HomeUiState(
    val resume: ResumeCardUi? = null,
    val recent: List<RecentSessionUi> = emptyList(),
    val templates: List<TemplateChipUi> = emptyList(),
    /** True when at least one plan is defined. Drives the first-launch state with [recent]. */
    val hasPlans: Boolean = false,
)

data class ResumeCardUi(
    val sessionId: String,
    val name: String?,
    val exerciseCount: Int,
    val startedAt: Instant,
)

data class RecentSessionUi(
    val sessionId: String,
    val name: String?,
    val startedAt: Instant,
    val setCount: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val resume = sessionRepository.observeActiveSession()
        .flatMapLatest { active ->
            if (active == null) {
                flowOf(null)
            } else {
                sessionRepository.observeSessionDetails(active.id).map { details ->
                    ResumeCardUi(
                        sessionId = active.id,
                        name = active.templateNameSnapshot,
                        exerciseCount = details?.exercises?.size ?: 0,
                        startedAt = active.startedAt,
                    )
                }
            }
        }

    // Chips show the days of the most-used (else first) plan, each with its exercise
    // count + up to 3 distinct muscle groups — mirrors PlansViewModel's day rows.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val templates: Flow<List<TemplateChipUi>> =
        planRepository.observeMostUsedOrFirstPlanId().flatMapLatest { planId ->
            if (planId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    planRepository.observePlansWithDays(),
                    exerciseRepository.observeAll(),
                ) { plans, exercises ->
                    val groupByExerciseId = exercises.associate { it.id to it.muscleGroup }
                    plans.firstOrNull { it.id == planId }
                        ?.days
                        ?.map { day ->
                            TemplateChipUi(
                                templateId = day.templateId,
                                name = day.name,
                                exerciseCount = day.exerciseCount,
                                muscleGroups = day.exerciseIds
                                    .mapNotNull { groupByExerciseId[it] }
                                    .distinct()
                                    .take(3),
                            )
                        }
                        ?: emptyList()
                }
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        resume,
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
        templates,
        planRepository.observePlans(),
    ) { resumeCard, history, counts, chips, plans ->
        HomeUiState(
            resume = resumeCard,
            recent = history
                .filter { it.endedAt != null }
                .take(5)
                .map { session ->
                    RecentSessionUi(
                        sessionId = session.id,
                        name = session.templateNameSnapshot,
                        startedAt = session.startedAt,
                        setCount = counts[session.id] ?: 0,
                    )
                },
            templates = chips,
            hasPlans = plans.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startOrResume(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val existing = uiState.value.resume?.sessionId
            if (existing != null) { onReady(existing); return@launch }
            try { onReady(sessionRepository.startEmptySession().id) }
            catch (e: IllegalStateException) {
                // lost the race: a session became active — resume it
                sessionRepository.observeActiveSession().first()?.id?.let(onReady)
            }
        }
    }

    fun startFromTemplate(templateId: String, onOpenSession: (String) -> Unit) {
        viewModelScope.launch {
            val active = sessionRepository.observeActiveSession().first()
            val id = active?.id ?: sessionRepository.startSessionFromTemplate(templateId).id
            onOpenSession(id)
        }
    }
}
