package de.simiil.liftlog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.SessionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(val sessions: List<HistorySessionUi> = emptyList())

data class HistorySessionUi(
    val sessionId: String,
    val name: String?,
    val startedAt: Instant,
    val setCount: Int,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
    ) { history, counts ->
        HistoryUiState(
            history.filter { it.endedAt != null }
                .map { HistorySessionUi(it.id, it.templateNameSnapshot, it.startedAt, counts[it.id] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
