package de.simiil.liftlog.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.repository.DaySummary
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the single-plan Plan tab (issue #30 PR3b). Exactly one plan is shown at a time —
 * the tab's current selection, resolved by [PlanRepository.observeSelectedOrFallbackPlanId].
 * [planChoices] lists every live plan, in position order, for the title-bar switcher dropdown
 * (issue #30 PR4) — shown only when there are 2+ choices.
 */
data class PlanTabUiState(
    val loading: Boolean = true,
    /** Null only during load / a transient gap — render a bare scaffold, never an empty-state CTA. */
    val plan: CurrentPlanUi? = null,
    val planChoices: List<PlanChoiceUi> = emptyList(),
)

data class CurrentPlanUi(
    val id: String,
    val name: String,
    val days: List<PlanDayUi>,
)

data class PlanChoiceUi(
    val id: String,
    val name: String,
)

data class PlanDayUi(
    val templateId: String,
    val name: String,
    /** "N exercises" optionally + up to 3 distinct muscle-group labels. */
    val exerciseCount: Int,
    val muscleGroups: List<MuscleGroup>,
)

@HiltViewModel
class PlanViewModel
    @Inject
    constructor(
        private val planRepository: PlanRepository,
        private val exerciseRepository: ExerciseRepository,
        private val sessionRepository: SessionRepository,
        private val defaultPlanEnsurer: DefaultPlanEnsurer,
    ) : ViewModel() {
        val uiState: StateFlow<PlanTabUiState> =
            combine(
                planRepository.observeSelectedOrFallbackPlanId(),
                planRepository.observePlansWithDays(),
                exerciseRepository.observeAll(),
            ) { selectedId, plans, exercises ->
                val groupByExerciseId = exercises.associate { it.id to it.muscleGroup }
                val current = plans.firstOrNull { it.id == selectedId }
                PlanTabUiState(
                    loading = current == null,
                    plan =
                        current?.let { plan ->
                            CurrentPlanUi(
                                id = plan.id,
                                name = plan.name,
                                days = plan.days.map { day -> day.toUi(groupByExerciseId) },
                            )
                        },
                    planChoices = plans.map { PlanChoiceUi(id = it.id, name = it.name) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanTabUiState())

        /**
         * Starts (or resumes) a session for [templateId]. Mirrors [de.simiil.liftlog.ui.home.HomeViewModel.startFromTemplate]:
         * a single active session is allowed at a time, so if one is already live we resume it instead
         * of starting a fresh one from the template.
         */
        fun startDay(
            templateId: String,
            onOpen: (String) -> Unit,
        ) {
            viewModelScope.launch {
                val active = sessionRepository.observeActiveSession().first()
                val id = active?.id ?: sessionRepository.startSessionFromTemplate(templateId).id
                onOpen(id)
            }
        }

        /** Guards [addDay] against re-entrant taps; set/checked synchronously, so two taps in the
         * same UI frame can never both slip through before the first's coroutine even starts. */
        private var addDayInFlight = false

        /** Creates a blank day on the current plan and hands its id to [onCreated] (caller navigates). */
        fun addDay(onCreated: (String) -> Unit) {
            if (addDayInFlight) return
            addDayInFlight = true
            viewModelScope.launch {
                try {
                    val planId = currentPlanId() ?: return@launch
                    val day = planRepository.createDayTemplate(planId, "")
                    onCreated(day.id)
                } finally {
                    addDayInFlight = false
                }
            }
        }

        fun removeDay(templateId: String) {
            viewModelScope.launch { planRepository.softDeleteDayTemplate(templateId) }
        }

        fun reorderDays(orderedIds: List<String>) {
            viewModelScope.launch { planRepository.reorderDayTemplates(orderedIds) }
        }

        fun renamePlan(name: String) {
            viewModelScope.launch {
                val id = currentPlanId() ?: return@launch
                planRepository.renamePlan(id, name.trim())
            }
        }

        /** Persists [id] as the tab's current selection; the shown plan follows via [uiState]. */
        fun selectPlan(id: String) {
            viewModelScope.launch { planRepository.selectPlan(id) }
        }

        /** Creates a new plan named [name] and switches the tab to it. */
        fun createPlan(name: String) {
            viewModelScope.launch {
                val plan = planRepository.createPlan(name.trim())
                planRepository.selectPlan(plan.id)
            }
        }

        /** Atomic delete+reseed: the tab never observes zero plans (issue #30). */
        fun deletePlan() {
            viewModelScope.launch {
                val id = currentPlanId() ?: return@launch
                defaultPlanEnsurer.deletePlan(id)
            }
        }

        private suspend fun currentPlanId(): String? = planRepository.observeSelectedOrFallbackPlanId().first()

        private fun DaySummary.toUi(groupByExerciseId: Map<String, MuscleGroup>): PlanDayUi =
            PlanDayUi(
                templateId = templateId,
                name = name,
                exerciseCount = exerciseCount,
                // up to 3 distinct muscle groups, in first-seen order
                muscleGroups = exerciseIds.mapNotNull { groupByExerciseId[it] }.distinct().take(3),
            )
    }
