package de.simiil.liftlog.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.logging.ActiveEntry
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
import de.simiil.liftlog.domain.logging.ActiveExerciseDefaults
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
import de.simiil.liftlog.domain.units.Decimals
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.notification.NotificationPermissionTick
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class CardState { COMPLETED, ACTIVE, UPCOMING }

enum class NumpadTarget { WEIGHT, REPS }

data class ActiveSessionUiState(
    val sessionId: String = "",
    val name: String? = null, // null -> "Quick workout" (resolved in UI)
    val sessionRpe: Double? = null,
    val sessionNote: String? = null,
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
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
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

data class NumpadUi(
    val target: NumpadTarget,
    val text: String,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ActiveSessionViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val settingsRepository: SettingsRepository,
        private val savedStateHandle: SavedStateHandle,
        private val names: ExerciseNameResolver,
        private val tracker: ActiveEntryTracker,
        private val permissionTick: NotificationPermissionTick,
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

        // Debounced note persistence: every keystroke lands here; the repo write happens
        // 500 ms after typing pauses. onFinish flushes synchronously so nothing is lost.
        private val pendingNote = MutableStateFlow<String?>(null)

        // Mirrors of the streams, so event handlers can compute without re-collecting.
        @Volatile
        private var latestDetails: SessionWithDetails? = null
        private var currentUnit: WeightUnit = WeightUnit.KG
        private var pendingReplaceSeId: String?
            get() = savedStateHandle.get<String?>("pendingReplaceSeId")
            set(value) {
                savedStateHandle["pendingReplaceSeId"] = value
            }

        val uiState: StateFlow<ActiveSessionUiState> =
            combine(
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
                    val activeCard =
                        details.exercises.firstOrNull { it.sessionExercise.id == active }
                            ?: return@collect

                    // 3. Auto-advance (dormant in M2: ad-hoc exercises have null targetSets).
                    val targetSets = activeCard.sessionExercise.targetSets
                    if (targetSets != null && activeCard.sets.size >= targetSets) {
                        val next =
                            details.exercises.firstOrNull { ews ->
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
            // Mirror the entry into the shared tracker so the session notification
            // shows (and logs) exactly what the app would (issue #36). entryFlow
            // always tracks the ACTIVE card, so this covers activation, dialing,
            // numpad edits, and post-log re-priming.
            viewModelScope.launch {
                entryFlow.collect { e ->
                    tracker.update(e?.let { ActiveEntry(it.sessionExerciseId, it.weightKg, it.reps) })
                }
            }
            viewModelScope.launch {
                pendingNote.filterNotNull().debounce(500).collect { text ->
                    sessionRepository.updateSessionNote(sessionId, text)
                }
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

            val cards =
                details.exercises.map { ews ->
                    val se = ews.sessionExercise
                    val exercise = exerciseMap[se.exerciseId]
                    val isActive = se.id == effectiveActiveId
                    ExerciseCardUi(
                        sessionExerciseId = se.id,
                        exerciseId = se.exerciseId,
                        name = exercise?.let { names.displayName(it.id, it.name) }.orEmpty(),
                        equipment = exercise?.equipment ?: Equipment.MACHINE,
                        targetSets = se.targetSets,
                        targetRepsMin = se.targetRepsMin,
                        targetRepsMax = se.targetRepsMax,
                        state =
                            when {
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
                sessionRpe = details.session.rpe,
                sessionNote = details.session.note,
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
                val initialText =
                    when (target) {
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
                        val weightKg =
                            if (text.isBlank()) {
                                null
                            } else {
                                Decimals
                                    .parse(text)
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
                val se =
                    if (pending != null) {
                        sessionRepository
                            .replaceExercise(pending, exerciseId)
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

        fun onEditSetSave(
            setId: String,
            weightKg: Double,
            reps: Int,
        ) {
            viewModelScope.launch {
                sessionRepository.updateSet(setId, weightKg, reps)
                if (editingSetIdFlow.value == setId) editingSetIdFlow.value = null
            }
        }

        fun onDeleteSet(setId: String) {
            viewModelScope.launch {
                sessionRepository.deleteSet(setId)
                if (editingSetIdFlow.value == setId) editingSetIdFlow.value = null
            }
        }

        fun onSessionRpeChange(rpe: Double?) {
            viewModelScope.launch { sessionRepository.updateSessionRpe(sessionId, rpe) }
        }

        fun onSessionNoteChange(text: String) {
            pendingNote.value = text
        }

        /** Synchronous note flush for collapse/focus-loss — closes the debounce-window loss gap. */
        fun onNoteFlush() {
            val text = pendingNote.value ?: return
            viewModelScope.launch { sessionRepository.updateSessionNote(sessionId, text) }
        }

        fun onFinish() {
            viewModelScope.launch {
                pendingNote.value?.let { sessionRepository.updateSessionNote(sessionId, it) }
                val count = latestDetails?.exercises?.sumOf { it.sets.size } ?: 0
                sessionRepository.finishSession(sessionId)
                tracker.clear()
                nav.update { it.copy(finished = true, lastFinishedSetCount = count) }
            }
        }

        fun onDiscard() {
            viewModelScope.launch {
                sessionRepository.softDeleteSession(sessionId)
                tracker.clear()
                nav.update { it.copy(discarded = true) }
            }
        }

        // --- Notification permission prompt (issue #36) ---

        /** Called when the system prompt resolves so a grant takes effect mid-session.
         *  Prompt cadence itself is OS-managed (two explicit denials → silent no-ops). */
        fun onNotificationPermissionResult() {
            permissionTick.bump()
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
                val ghost =
                    ghostCache.value[exerciseId]
                        ?: sessionRepository.lastPerformance(exerciseId).also { fetched ->
                            ghostCache.update { it + (exerciseId to fetched) }
                        }
                val prefill = Prefill.forNextSet(setsThisEntry = card.sets, lastPerformance = ghost)
                entryFlow.value = EntryUi(seId, prefill.weightKg, prefill.reps)
            }
        }

        private fun pickDefault(details: SessionWithDetails): String? = ActiveExerciseDefaults.pickDefault(details)

        private fun isComplete(ews: SessionExerciseWithSets): Boolean = ActiveExerciseDefaults.isComplete(ews)

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
