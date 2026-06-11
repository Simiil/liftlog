package de.simiil.liftlog.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.MuscleGroup
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

data class PlansUiState(
    val plans: List<PlanCardUi> = emptyList(),
    val loading: Boolean = true,
)

data class PlanCardUi(
    val id: String,
    val name: String,
    val days: List<PlanDayUi>,
)

data class PlanDayUi(
    val templateId: String,
    val name: String,
    /** "N exercises" optionally + up to 3 distinct muscle-group labels. */
    val exerciseCount: Int,
    val muscleGroups: List<MuscleGroup>,
)

@HiltViewModel
class PlansViewModel
    @Inject
    constructor(
        private val planRepository: PlanRepository,
        private val exerciseRepository: ExerciseRepository,
        private val sessionRepository: SessionRepository,
    ) : ViewModel() {
        val uiState: StateFlow<PlansUiState> =
            combine(
                planRepository.observePlansWithDays(),
                exerciseRepository.observeAll(),
            ) { plans, exercises ->
                val groupByExerciseId = exercises.associate { it.id to it.muscleGroup }
                PlansUiState(
                    plans =
                        plans.map { plan ->
                            PlanCardUi(
                                id = plan.id,
                                name = plan.name,
                                days =
                                    plan.days.map { day ->
                                        PlanDayUi(
                                            templateId = day.templateId,
                                            name = day.name,
                                            exerciseCount = day.exerciseCount,
                                            // up to 3 distinct muscle groups, in first-seen order
                                            muscleGroups =
                                                day.exerciseIds
                                                    .mapNotNull { groupByExerciseId[it] }
                                                    .distinct()
                                                    .take(3),
                                        )
                                    },
                            )
                        },
                    loading = false,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

        /**
         * Starts (or resumes) a session for [templateId]. Mirrors [HomeViewModel.startFromTemplate]:
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
    }
