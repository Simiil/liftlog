package de.simiil.liftlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateChipUi(val templateId: String, val name: String)

data class HomeUiState(
    val resume: ResumeCardUi? = null,
    val recent: List<RecentSessionUi> = emptyList(),
    val templates: List<TemplateChipUi> = emptyList(),
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
    private val planRepository: PlanRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val templates: Flow<List<TemplateChipUi>> =
        planRepository.observeMostUsedOrFirstPlanId().flatMapLatest { planId ->
            if (planId == null) flowOf(emptyList())
            else planRepository.observeDayTemplates(planId)
                .map { days -> days.map { TemplateChipUi(it.id, it.name) } }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        resume,
        sessionRepository.observeHistory(),
        sessionRepository.observeSetCountsBySession(),
        templates,
    ) { resumeCard, history, counts, chips ->
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
            templates = chips,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startOrResume(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val existing = uiState.value.resume?.sessionId
            if (existing != null) { onReady(existing); return@launch }
            try { onReady(sessionRepository.startEmptySession().id) }
            catch (e: IllegalStateException) {
                // lost the race: a session became active — resume it
                sessionRepository.observeActiveSession().first()?.id?.let(onReady)
            }
        }
    }

    fun startFromTemplate(templateId: String, onOpenSession: (String) -> Unit) {
        viewModelScope.launch {
            val active = sessionRepository.observeActiveSession().first()
            val id = active?.id ?: sessionRepository.startSessionFromTemplate(templateId).id
            onOpenSession(id)
        }
    }
}
