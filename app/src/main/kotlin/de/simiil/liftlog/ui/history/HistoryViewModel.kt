package de.simiil.liftlog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<HistorySessionUi> = emptyList(),
)

data class HistorySessionUi(
    val sessionId: String,
    val name: String?,
    val startedAt: Instant,
    val setCount: Int,
    val isPr: Boolean = false,
)

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val analyticsRepository: AnalyticsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<HistoryUiState> =
            combine(
                sessionRepository.observeHistory(),
                sessionRepository.observeSetCountsBySession(),
                analyticsRepository.observePrSessionIds(),
            ) { history, counts, prIds ->
                HistoryUiState(
                    history
                        .filter { it.endedAt != null }
                        .map {
                            HistorySessionUi(
                                sessionId = it.id,
                                name = it.templateNameSnapshot,
                                startedAt = it.startedAt,
                                setCount = counts[it.id] ?: 0,
                                isPr = it.id in prIds,
                            )
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
    }
