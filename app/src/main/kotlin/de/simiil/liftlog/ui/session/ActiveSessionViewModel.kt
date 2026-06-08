package de.simiil.liftlog.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.logging.Prefill
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.domain.units.Weights
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CardState { COMPLETED, ACTIVE, UPCOMING }

enum class NumpadTarget { WEIGHT, REPS }

data class ActiveSessionUiState(
    val sessionId: String = "",
    val name: String? = null, // null -> "Quick workout" (resolved in UI)
    val startedAt: Instant? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val cards: List<ExerciseCardUi> = emptyList(),
    val entry: EntryUi? = null, // present iff there is an ACTIVE card
    val loading: Boolean = true,
    val finished: Boolean = false,
    val discarded: Boolean = false,
    val lastFinishedSetCount: Int = 0,
)

data class ExerciseCardUi(
    val sessionExerciseId: String,
    val exerciseId: String,
    val name: String,
    val equipment: Equipment,
    val targetSets: Int?,
    val state: CardState,
    val sets: List<LoggedSet>, // kg, sorted by position (already sorted by the mapper)
    val ghostSets: List<LoggedSet>, // last-performance snapshot; non-empty only for the ACTIVE card
    val editingSetId: String? = null,
)

data class EntryUi(
    val sessionExerciseId: String,
    val weightKg: Double?, // null -> empty (numpad required); LOG SET disabled
    val reps: Int,
    val numpad: NumpadUi? = null,
)

data class NumpadUi(val target: NumpadTarget, val text: String)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Type-safe route fields land in the handle under their field name; reading the key
    // directly works identically in production and in pure-JVM tests (which build a
    // SavedStateHandle(mapOf("sessionId" to ...))), avoiding any toRoute reflection in tests.
    private val sessionId: String = savedStateHandle.get<String>("sessionId")!!

    // --- Internal mutable UI state (transient; activeSeId persisted per architecture §3) ---

    private val activeSeId = MutableStateFlow<String?>(savedStateHandle["activeSeId"])
    private val entryFlow = MutableStateFlow<EntryUi?>(null)
    private val ghostCache = MutableStateFlow<Map<String, List<LoggedSet>>>(emptyMap())
    private val nav = MutableStateFlow(NavSignals())
    private val editingSetIdFlow = MutableStateFlow<String?>(null)

    // Mirrors of the streams, so event handlers can compute without re-collecting.
    @Volatile
    private var latestDetails: SessionWithDetails? = null
    private var currentUnit: WeightUnit = WeightUnit.KG
    private var pendingReplaceSeId: String?
        get() = savedStateHandle.get<String?>("pendingReplaceSeId")
        set(value) { savedStateHandle["pendingReplaceSeId"] = value }

    val uiState: StateFlow<ActiveSessionUiState> = combine(
        sessionRepository.observeSessionDetails(sessionId),
        exerciseRepository.observeAll(),
        settingsRepository.weightUnit,
        activeSeId,
        combine(entryFlow, ghostCache, nav, editingSetIdFlow) { e, g, n, ed -> Extras(e, g, n, ed) },
    ) { details, exercises, unit, activeId, extras ->
        buildState(details, exercises, unit, activeId, extras)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ActiveSessionUiState(sessionId = sessionId),
    )

    init {
        // Reactive bookkeeping: keep mirrors current, pick/repair the active card, seed the
        // entry, and (dormant in M2) auto-advance when a card hits its target. Side effects
        // live here, NOT in the pure combine transform.
        viewModelScope.launch {
            sessionRepository.observeSessionDetails(sessionId).collect { details ->
                latestDetails = details
                if (details == null) return@collect

                val liveIds = details.exercises.mapTo(mutableSetOf()) { it.sessionExercise.id }

                // 1. Ensure the active id refers to a live card.
                val current = activeSeId.value
                if (current == null || current !in liveIds) {
                    setActiveSeId(pickDefault(details))
                }

                val active = activeSeId.value ?: return@collect
                val activeCard = details.exercises.firstOrNull { it.sessionExercise.id == active }
                    ?: return@collect

                // 3. Auto-advance (dormant in M2: ad-hoc exercises have null targetSets).
                val targetSets = activeCard.sessionExercise.targetSets
                if (targetSets != null && activeCard.sets.size >= targetSets) {
                    val next = details.exercises.firstOrNull { ews ->
                        ews.sessionExercise.id != active && !isComplete(ews)
                    }
                    if (next != null) {
                        setActiveSeId(next.sessionExercise.id)
                        seedEntry(next.sessionExercise.id)
                        return@collect
                    }
                }

                // 2. Seed the entry if none is in progress for the active card.
                val entry = entryFlow.value
                if (entry == null || entry.sessionExerciseId != active) {
                    seedEntry(active)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.weightUnit.collect { currentUnit = it }
        }
    }

    // --- Pure transform ---

    private fun buildState(
        details: SessionWithDetails?,
        exercises: List<Exercise>,
        unit: WeightUnit,
        activeId: String?,
        extras: Extras,
    ): ActiveSessionUiState {
        if (details == null) {
            return ActiveSessionUiState(
                sessionId = sessionId,
                unit = unit,
                loading = true,
                finished = extras.nav.finished,
                discarded = extras.nav.discarded,
                lastFinishedSetCount = extras.nav.lastFinishedSetCount,
            )
        }

        val exerciseMap = exercises.associateBy { it.id }
        val liveIds = details.exercises.mapTo(mutableSetOf()) { it.sessionExercise.id }
        val effectiveActiveId = activeId?.takeIf { it in liveIds } ?: pickDefault(details)

        val cards = details.exercises.map { ews ->
            val se = ews.sessionExercise
            val exercise = exerciseMap[se.exerciseId]
            val isActive = se.id == effectiveActiveId
            ExerciseCardUi(
                sessionExerciseId = se.id,
                exerciseId = se.exerciseId,
                name = exercise?.name.orEmpty(),
                equipment = exercise?.equipment ?: Equipment.MACHINE,
                targetSets = se.targetSets,
                state = when {
                    isActive -> CardState.ACTIVE
                    ews.sets.isNotEmpty() -> CardState.COMPLETED
                    else -> CardState.UPCOMING
                },
                sets = ews.sets,
                ghostSets = if (isActive) extras.ghost[se.exerciseId].orEmpty() else emptyList(),
                editingSetId = if (isActive) extras.editingSetId else null,
            )
        }

        val entry = extras.entry?.takeIf { it.sessionExerciseId == effectiveActiveId }

        return ActiveSessionUiState(
            sessionId = sessionId,
            name = details.session.templateNameSnapshot,
            startedAt = details.session.startedAt,
            unit = unit,
            cards = cards,
            entry = entry,
            loading = false,
            finished = extras.nav.finished,
            discarded = extras.nav.discarded,
            lastFinishedSetCount = extras.nav.lastFinishedSetCount,
        )
    }

    // --- Events ---

    fun onActivateCard(seId: String) {
        setActiveSeId(seId)
        seedEntry(seId)
    }

    fun onWeightIncrement() = adjustWeight(increment = true)

    fun onWeightDecrement() = adjustWeight(increment = false)

    private fun adjustWeight(increment: Boolean) {
        entryFlow.update { e ->
            e ?: return@update null
            val current = e.weightKg ?: 0.0
            val deltaKg = Weights.displayToKg(Weights.stepIncrementDisplay(currentUnit), currentUnit)
            val next = (current + if (increment) deltaKg else -deltaKg).coerceAtLeast(0.0)
            e.copy(weightKg = next)
        }
    }

    fun onRepsIncrement() {
        entryFlow.update { e -> e?.copy(reps = (e.reps + 1).coerceAtLeast(1)) }
    }

    fun onRepsDecrement() {
        entryFlow.update { e -> e?.copy(reps = (e.reps - 1).coerceAtLeast(1)) }
    }

    fun onOpenNumpad(target: NumpadTarget) {
        entryFlow.update { e ->
            e ?: return@update null
            val initialText = when (target) {
                NumpadTarget.WEIGHT -> e.weightKg?.let { Weights.format(it, currentUnit) }.orEmpty()
                NumpadTarget.REPS -> e.reps.toString()
            }
            e.copy(numpad = NumpadUi(target, initialText))
        }
    }

    fun onNumpadConfirm(text: String) {
        entryFlow.update { e ->
            val current = e ?: return@update null
            val numpad = current.numpad ?: return@update current
            when (numpad.target) {
                NumpadTarget.WEIGHT -> {
                    val weightKg = if (text.isBlank()) {
                        null
                    } else {
                        text.toDoubleOrNull()
                            ?.let { Weights.displayToKg(it, currentUnit) }
                            ?: current.weightKg
                    }
                    current.copy(weightKg = weightKg, numpad = null)
                }
                NumpadTarget.REPS -> {
                    val reps = text.toIntOrNull()?.coerceAtLeast(1) ?: current.reps
                    current.copy(reps = reps, numpad = null)
                }
            }
        }
    }

    fun onNumpadDismiss() {
        entryFlow.update { e -> e?.copy(numpad = null) }
    }

    fun onLogSet() {
        val e = entryFlow.value ?: return
        val weightKg = e.weightKg ?: return // LOG SET disabled until weight is set (rule 3)
        viewModelScope.launch {
            sessionRepository.logSet(e.sessionExerciseId, weightKg, e.reps)
            // Rule 1 re-prime: next set defaults to what we just logged (and close any numpad).
            entryFlow.value = e.copy(weightKg = weightKg, reps = e.reps, numpad = null)
        }
    }

    fun onPickedExercise(exerciseId: String) {
        viewModelScope.launch {
            val pending = pendingReplaceSeId
            val se = if (pending != null) {
                sessionRepository.replaceExercise(pending, exerciseId)
                    .also { pendingReplaceSeId = null }
            } else {
                sessionRepository.addExerciseToSession(sessionId, exerciseId)
            }
            onActivateCard(se.id)
        }
    }

    fun onRequestRemoveExercise(seId: String) {
        viewModelScope.launch {
            sessionRepository.removeExercise(seId)
            if (activeSeId.value == seId) setActiveSeId(null) // collector re-picks
        }
    }

    fun onRequestReplaceExercise(seId: String) {
        pendingReplaceSeId = seId
    }

    fun onLongPressSet(setId: String) {
        editingSetIdFlow.value = setId
    }

    fun onCollapseEdit() {
        editingSetIdFlow.value = null
    }

    fun onEditSetSave(setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?) {
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

    fun onFinish() {
        viewModelScope.launch {
            val count = latestDetails?.exercises?.sumOf { it.sets.size } ?: 0
            sessionRepository.finishSession(sessionId)
            nav.update { it.copy(finished = true, lastFinishedSetCount = count) }
        }
    }

    fun onDiscard() {
        viewModelScope.launch {
            sessionRepository.softDeleteSession(sessionId)
            nav.update { it.copy(discarded = true) }
        }
    }

    // --- Helpers ---

    private fun setActiveSeId(value: String?) {
        activeSeId.value = value
        savedStateHandle["activeSeId"] = value
    }

    private fun seedEntry(seId: String) {
        viewModelScope.launch {
            val details = latestDetails ?: return@launch
            val card = details.exercises.firstOrNull { it.sessionExercise.id == seId } ?: return@launch
            val exerciseId = card.sessionExercise.exerciseId
            val ghost = ghostCache.value[exerciseId]
                ?: sessionRepository.lastPerformance(exerciseId).also { fetched ->
                    ghostCache.update { it + (exerciseId to fetched) }
                }
            val prefill = Prefill.forNextSet(setsThisEntry = card.sets, lastPerformance = ghost)
            entryFlow.value = EntryUi(seId, prefill.weightKg, prefill.reps)
        }
    }

    private fun pickDefault(details: SessionWithDetails): String? {
        if (details.exercises.isEmpty()) return null
        val incomplete = details.exercises.firstOrNull { !isComplete(it) }
        return (incomplete ?: details.exercises.last()).sessionExercise.id
    }

    private fun isComplete(ews: SessionExerciseWithSets): Boolean {
        val targetSets = ews.sessionExercise.targetSets ?: return false
        return ews.sets.size >= targetSets
    }

    private data class NavSignals(
        val finished: Boolean = false,
        val discarded: Boolean = false,
        val lastFinishedSetCount: Int = 0,
    )

    private data class Extras(
        val entry: EntryUi?,
        val ghost: Map<String, List<LoggedSet>>,
        val nav: NavSignals,
        val editingSetId: String?,
    )
}
