package de.simiil.liftlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.SessionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val resume: ResumeCardUi? = null,
    val recent: List<RecentSessionUi> = emptyList(),
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

    val uiState: StateFlow<HomeUiState> = combine(
        resume,
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
    ) { resumeCard, history, counts ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startOrResume(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val existing = uiState.value.resume?.sessionId
            onReady(existing ?: sessionRepository.startEmptySession().id)
        }
    }
}
