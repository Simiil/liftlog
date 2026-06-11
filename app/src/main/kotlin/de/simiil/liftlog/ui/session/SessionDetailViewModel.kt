package de.simiil.liftlog.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SessionDetailUiState(
    val name: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val exercises: List<DetailExerciseUi> = emptyList(),
    val editingSetId: String? = null,
    val loading: Boolean = true,
)

data class DetailExerciseUi(
    val sessionExerciseId: String,
    val name: String,
    val equipment: Equipment,
    val muscleGroup: MuscleGroup,
    val sets: List<LoggedSet>,
)

@HiltViewModel
class SessionDetailViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val settingsRepository: SettingsRepository,
        private val savedStateHandle: SavedStateHandle,
        private val names: ExerciseNameResolver,
    ) : ViewModel() {
        // Type-safe route fields land in the handle under their field name; reading the key
        // directly works identically in production and in pure-JVM tests (which build a
        // SavedStateHandle(mapOf("sessionId" to ...))), avoiding any toRoute reflection in tests.
        private val sessionId: String = savedStateHandle.get<String>("sessionId")!!

        private val editingSetIdFlow = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SessionDetailUiState> =
            combine(
                sessionRepository.observeSessionDetails(sessionId),
                exerciseRepository.observeAll(),
                settingsRepository.weightUnit,
                editingSetIdFlow,
            ) { details, exercises, unit, editingSetId ->
                if (details == null) {
                    return@combine SessionDetailUiState(loading = true, unit = unit)
                }
                val exerciseMap = exercises.associateBy { it.id }
                val exerciseUis =
                    details.exercises.map { ews ->
                        val exercise = exerciseMap[ews.sessionExercise.exerciseId]
                        DetailExerciseUi(
                            sessionExerciseId = ews.sessionExercise.id,
                            name = exercise?.let { names.displayName(it.id, it.name) }.orEmpty(),
                            equipment = exercise?.equipment ?: Equipment.MACHINE,
                            muscleGroup = exercise?.muscleGroup ?: MuscleGroup.OTHER,
                            sets = ews.sets,
                        )
                    }
                SessionDetailUiState(
                    name = details.session.templateNameSnapshot,
                    startedAt = details.session.startedAt,
                    endedAt = details.session.endedAt,
                    unit = unit,
                    exercises = exerciseUis,
                    editingSetId = editingSetId,
                    loading = false,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SessionDetailUiState(),
            )

        // --- Events ---

        fun onLongPressSet(setId: String) {
            editingSetIdFlow.value = setId
        }

        fun onCollapseEdit() {
            editingSetIdFlow.value = null
        }

        fun onEditSetSave(
            setId: String,
            weightKg: Double,
            reps: Int,
            rpe: Double?,
            note: String?,
        ) {
            viewModelScope.launch {
                sessionRepository.updateSet(setId, weightKg, reps, rpe, note)
                if (editingSetIdFlow.value == setId) editingSetIdFlow.value = null
            }
        }

        fun onDeleteSet(setId: String) {
            viewModelScope.launch {
                sessionRepository.deleteSet(setId)
                if (editingSetIdFlow.value == setId) editingSetIdFlow.value = null
            }
        }
    }
