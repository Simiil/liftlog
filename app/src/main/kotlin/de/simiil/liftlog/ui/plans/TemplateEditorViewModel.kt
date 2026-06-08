package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateEditorUiState(
    val dayName: String = "",
    val exercises: List<EditorExerciseUi> = emptyList(),
    val loading: Boolean = true,
)

data class EditorExerciseUi(
    val id: String,           // template_exercise id (stable key for reorder)
    val exerciseId: String,
    val name: String,
    val equipment: Equipment,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
)

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    // Type-safe route fields land in the handle under their field name; reading the key
    // directly works identically in production and in pure-JVM tests, avoiding toRoute
    // reflection in tests.
    private val templateId: String = savedStateHandle.get<String>("templateId")!!

    private val dayName = MutableStateFlow("")

    init {
        viewModelScope.launch {
            dayName.value = planRepository.getDayTemplate(templateId)?.name.orEmpty()
        }
    }

    val uiState: StateFlow<TemplateEditorUiState> = combine(
        planRepository.observeTemplateExercises(templateId),
        exerciseRepository.observeAll(),
        dayName,
    ) { tes, exercises, name ->
        val byId = exercises.associateBy { it.id }
        TemplateEditorUiState(
            dayName = name,
            exercises = tes.map { te ->
                val ex = byId[te.exerciseId]
                EditorExerciseUi(
                    id = te.id,
                    exerciseId = te.exerciseId,
                    name = ex?.name.orEmpty(),
                    equipment = ex?.equipment ?: Equipment.MACHINE,
                    targetSets = te.targetSets,
                    targetRepsMin = te.targetRepsMin,
                    targetRepsMax = te.targetRepsMax,
                )
            },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateEditorUiState())

    fun addExercise(exerciseId: String) {
        viewModelScope.launch { planRepository.addExerciseToTemplate(templateId, exerciseId) }
    }

    fun removeExercise(id: String) {
        viewModelScope.launch { planRepository.removeTemplateExercise(id) }
    }

    fun setTargets(id: String, sets: Int?, repsMin: Int?, repsMax: Int?) {
        viewModelScope.launch { planRepository.updateTemplateExerciseTargets(id, sets, repsMin, repsMax) }
    }

    fun persistOrder(orderedIds: List<String>) {
        viewModelScope.launch { planRepository.reorderTemplateExercises(orderedIds) }
    }
}
