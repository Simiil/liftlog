package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DayEditorUiState(
    val loading: Boolean = true,
    val dayName: String = "", // pending-name overlay merged over DB
    val exercises: List<DayExerciseUi> = emptyList(), // targets overlay merged
    val dayGone: Boolean = false, // template tombstoned -> screen auto-closes
)

data class DayExerciseUi(
    val id: String,
    val exerciseId: String,
    val name: String,
    val equipment: Equipment,
    val muscleGroup: MuscleGroup,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
)

/** Synchronous pending-write overlay for one template-exercise's target values (spec §2). */
private data class TargetsOverlay(
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
)

/**
 * DB-backed, single-day autosave editor (2026-06-12 autosave design, §2/§4). The database is the
 * draft: every mutation reaches [planRepository] directly (debounced for the name field, immediate
 * for everything else). A small in-memory overlay masks the round-trip latency so the name field
 * never loses the cursor and rapid stepper taps never compute from a stale value; the overlay is
 * purely internal — the UI and the repository know nothing about it.
 *
 * Not `@HiltViewModel`: its `debounceMs` default can't be expressed as a Dagger-injectable
 * constructor, so it was previously reached via a narrower `@Inject` secondary constructor.
 * Koin's `viewModel { }` lambda (di/AppModules.kt) can call the primary constructor and use
 * the default directly, so that workaround is gone (#47 PR1.2) — this leaves the Day Editor
 * screen's `hiltViewModel()` call unable to resolve this class until PR1.4 moves that call
 * site to `koinViewModel()`.
 */
class DayEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val names: ExerciseNameResolver,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) : ViewModel() {
    // Type-safe route fields land in the handle under their field name; every day is a real,
    // already-created row by the time this screen opens (mirrors ActiveSessionViewModel's
    // required sessionId), so the id is non-nullable.
    private val templateId: String = savedStateHandle.get<String>(KEY_TEMPLATE_ID)!!

    private val nameOverlay = MutableStateFlow<String?>(null)
    private val targetsOverlay = MutableStateFlow<Map<String, TargetsOverlay>>(emptyMap())

    private var renameJob: Job? = null

    /** One in-flight write per template-exercise row; a newer tap cancels and replaces it. */
    private val targetWriteJobs = mutableMapOf<String, Job>()

    /** True once the day template has emitted non-null at least once (loading vs. gone). */
    private var hasLoadedDay = false

    val uiState: StateFlow<DayEditorUiState> =
        combine(
            planRepository.observeDayTemplate(templateId),
            planRepository.observeTemplateExercises(templateId),
            exerciseRepository.observeAll(),
            nameOverlay,
            targetsOverlay,
        ) { day, templateExercises, exercises, nameOv, targetsOv ->
            if (day == null) {
                if (hasLoadedDay) {
                    DayEditorUiState(loading = false, dayGone = true)
                } else {
                    DayEditorUiState(loading = true)
                }
            } else {
                hasLoadedDay = true
                val exercisesById = exercises.associateBy { it.id }
                DayEditorUiState(
                    loading = false,
                    dayName = nameOv ?: day.name,
                    exercises =
                        templateExercises.map { te ->
                            val exercise = exercisesById[te.exerciseId]
                            val overlay = targetsOv[te.id]
                            DayExerciseUi(
                                id = te.id,
                                exerciseId = te.exerciseId,
                                name = exercise?.let { names.displayName(it.id, it.name) }.orEmpty(),
                                equipment = exercise?.equipment ?: Equipment.OTHER,
                                muscleGroup = exercise?.muscleGroup ?: MuscleGroup.OTHER,
                                targetSets = if (overlay != null) overlay.sets else te.targetSets,
                                targetRepsMin = if (overlay != null) overlay.repsMin else te.targetRepsMin,
                                targetRepsMax = if (overlay != null) overlay.repsMax else te.targetRepsMax,
                            )
                        },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayEditorUiState())

    // ── Name (debounced) ────────────────────────────────────────────────────

    fun setDayName(name: String) {
        nameOverlay.value = name
        renameJob?.cancel()
        renameJob =
            viewModelScope.launch {
                delay(debounceMs)
                persistRename(name)
            }
    }

    /**
     * Cancels any pending debounce timer and writes a pending rename immediately. Called from
     * ON_STOP (the primary trigger — see the screen's DisposableEffect) as well as on dispose;
     * on the dispose path viewModelScope is cancelled moments later, so the write itself runs
     * under [NonCancellable] as belt-and-braces — once started, it always completes.
     */
    fun flushPendingEdits() {
        renameJob?.cancel()
        renameJob = null
        val pending = nameOverlay.value ?: return
        viewModelScope.launch { withContext(NonCancellable) { persistRename(pending) } }
    }

    private suspend fun persistRename(name: String) {
        planRepository.renameDayTemplate(templateId, name)
        // Clear the overlay only if nothing newer has raced in since this write started.
        if (nameOverlay.value == name) nameOverlay.value = null
    }

    // ── Targets (immediate) ─────────────────────────────────────────────────

    fun setTargets(
        id: String,
        sets: Int?,
        repsMin: Int?,
        repsMax: Int?,
    ) {
        targetsOverlay.update { it + (id to TargetsOverlay(sets, repsMin, repsMax)) }
        // Serialize writes per row (the rename pattern with zero debounce): independent
        // fire-and-forget writes could complete out of call order, persisting a stale value
        // after its overlay entry was already cleared. Cancelling the previous write and
        // always writing the CURRENT overlay value guarantees the last tap's value lands last
        // while a write still starts immediately on every tap.
        targetWriteJobs[id]?.cancel()
        targetWriteJobs[id] =
            viewModelScope.launch {
                val pending = targetsOverlay.value[id] ?: return@launch
                planRepository.updateTemplateExerciseTargets(id, pending.sets, pending.repsMin, pending.repsMax)
                targetsOverlay.update { current -> if (current[id] == pending) current - id else current }
            }
    }

    // ── Exercises ────────────────────────────────────────────────────────────

    fun addExercises(ids: List<String>) {
        viewModelScope.launch { planRepository.addExercisesToTemplate(templateId, ids) }
    }

    fun removeExercise(id: String) {
        viewModelScope.launch { planRepository.removeTemplateExercise(id) }
    }

    fun reorderExercises(orderedIds: List<String>) {
        viewModelScope.launch { planRepository.reorderTemplateExercises(orderedIds) }
    }

    private companion object {
        const val KEY_TEMPLATE_ID = "templateId"
        const val DEFAULT_DEBOUNCE_MS = 400L
    }
}
