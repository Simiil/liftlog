package de.simiil.liftlog.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

enum class CreateError { BLANK_NAME, DUPLICATE_NAME }

data class ExercisePickerUiState(
    val query: String = "",
    val muscleFilter: MuscleGroup? = null,
    val equipmentFilter: Equipment? = null,
    /** Shown on top ONLY when query + filters are all empty. Up to 8 entries, ordered by recency. */
    val recent: List<Exercise> = emptyList(),
    /** Filtered + sorted results list. */
    val results: List<Exercise> = emptyList(),
    val createError: CreateError? = null,
)

class ExercisePickerViewModel(
    private val repo: ExerciseRepository,
    private val names: ExerciseNameResolver,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    private val muscleFlow = MutableStateFlow<MuscleGroup?>(null)
    private val equipmentFlow = MutableStateFlow<Equipment?>(null)
    private val createErrorFlow = MutableStateFlow<CreateError?>(null)

    val uiState: StateFlow<ExercisePickerUiState> =
        combine(
            repo.observeVisible(),
            repo.observeRecentlyUsedIds(),
            combine(queryFlow, muscleFlow, equipmentFlow) { q, m, e -> Triple(q, m, e) },
            createErrorFlow,
        ) { visible, recentIds, (q, m, e), err ->
            val active = q.isNotBlank() || m != null || e != null
            val matches =
                visible.mapNotNull { ex ->
                    val display = names.displayName(ex)
                    val ok =
                        (q.isBlank() || display.contains(q, ignoreCase = true) || ex.name.contains(q, ignoreCase = true)) &&
                            (m == null || ex.muscleGroup == m) &&
                            (e == null || ex.equipment == e)
                    if (ok) ex to display else null
                }
            val recencyRank = recentIds.withIndex().associate { (i, id) -> id to i }
            // Collator deliberately created per emission: picks up runtime locale changes.
            val collator = Collator.getInstance()
            val sorted =
                matches
                    .sortedWith(
                        compareBy<Pair<Exercise, String>> { recencyRank[it.first.id] ?: Int.MAX_VALUE }
                            .thenComparator { a, b -> collator.compare(a.second, b.second) },
                    ).map { it.first }
            val recent =
                if (active) {
                    emptyList()
                } else {
                    visible
                        .filter { recencyRank.containsKey(it.id) }
                        .sortedBy { recencyRank[it.id] }
                        .take(8)
                }
            ExercisePickerUiState(q, m, e, recent, sorted, err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState())

    fun onQueryChange(q: String) {
        queryFlow.value = q
        createErrorFlow.value = null
    }

    fun onMuscleFilter(m: MuscleGroup?) {
        muscleFlow.value = m
    }

    fun onEquipmentFilter(e: Equipment?) {
        equipmentFlow.value = e
    }

    fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                createErrorFlow.value = CreateError.BLANK_NAME
                return@launch
            }
            try {
                onCreated(repo.createCustom(name.trim(), muscleGroup, equipment).id)
            } catch (e: IllegalArgumentException) {
                createErrorFlow.value = CreateError.DUPLICATE_NAME
            }
        }
    }
}
