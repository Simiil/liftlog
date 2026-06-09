package de.simiil.liftlog.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.PlanRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlansUiState(
    val plans: List<PlanRowUi> = emptyList(),
    val loading: Boolean = true,
)

data class PlanRowUi(
    val id: String,
    val name: String,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val planRepository: PlanRepository,
) : ViewModel() {

    val uiState: StateFlow<PlansUiState> =
        planRepository.observePlans()
            .map { plans ->
                PlansUiState(
                    plans = plans.map { PlanRowUi(it.id, it.name) },
                    loading = false,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun createPlan(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { planRepository.createPlan(name) }
    }

    fun renamePlan(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { planRepository.renamePlan(id, name) }
    }

    fun deletePlan(id: String) {
        viewModelScope.launch { planRepository.softDeletePlan(id) }
    }
}
