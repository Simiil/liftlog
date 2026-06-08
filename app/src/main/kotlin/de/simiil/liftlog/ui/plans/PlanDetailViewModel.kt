package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.PlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanDetailUiState(
    val planName: String = "",
    val days: List<DayRowUi> = emptyList(),
    val loading: Boolean = true,
)

data class DayRowUi(
    val id: String,
    val name: String,
)

@HiltViewModel
class PlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
) : ViewModel() {

    // Type-safe route fields land in the handle under their field name; reading the key
    // directly works identically in production and in pure-JVM tests (which build a
    // SavedStateHandle(mapOf("planId" to ...))), avoiding any toRoute reflection in tests.
    private val planId: String = savedStateHandle.get<String>("planId")!!

    val uiState: StateFlow<PlanDetailUiState> = combine(
        planRepository.observePlan(planId),
        planRepository.observeDayTemplates(planId),
    ) { plan, days ->
        PlanDetailUiState(
            planName = plan?.name.orEmpty(),
            days = days.map { DayRowUi(it.id, it.name) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDetailUiState())

    fun createDay(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { planRepository.createDayTemplate(planId, name) }
    }

    fun renameDay(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { planRepository.renameDayTemplate(id, name) }
    }

    fun deleteDay(id: String) {
        viewModelScope.launch { planRepository.softDeleteDayTemplate(id) }
    }
}
