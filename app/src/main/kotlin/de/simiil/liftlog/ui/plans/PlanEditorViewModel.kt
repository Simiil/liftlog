package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.model.DayDraft
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.ItemDraft
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.PlanDraft
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Which sub-screen of the single editor is showing. */
enum class PlanEditorMode { PLAN, DAY }

data class PlanEditorUiState(
    val mode: PlanEditorMode = PlanEditorMode.PLAN,
    val isNewPlan: Boolean = true,
    val planName: String = "",
    val days: List<EditorDayUi> = emptyList(),
    val editingDay: EditorDayUi? = null,
    val canSave: Boolean = false,
    val canDone: Boolean = false,
)

data class EditorDayUi(
    val key: String,
    val name: String,
    val isNewDay: Boolean,
    val exercises: List<EditorItemUi>,
)

data class EditorItemUi(
    val key: String,
    val exerciseId: String,
    val name: String,
    val equipment: Equipment,
    val muscleGroup: MuscleGroup,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
)

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val names: ExerciseNameResolver,
) : ViewModel() {

    // Type-safe route fields land in the handle under their field name; reading the key directly
    // works identically in production and in pure-JVM tests (which build a SavedStateHandle).
    private val planId: String? = savedStateHandle.get<String>(KEY_PLAN_ID)

    private val json = Json { ignoreUnknownKeys = true }

    /** The single in-memory draft being edited; the source of truth for both modes. */
    private val draft = MutableStateFlow(PlanDraft())

    /** Reorder/UI key of the day currently being edited (DAY mode), or null (PLAN mode). */
    private val editingDayKey = MutableStateFlow<String?>(savedStateHandle.get<String>(KEY_EDITING_DAY))

    init {
        val saved = savedStateHandle.get<String>(KEY_DRAFT)
        when {
            // Restore an in-flight draft across process death.
            saved != null -> draft.value = runCatching { json.decodeFromString<PlanDraft>(saved) }
                .getOrDefault(PlanDraft(planId = planId))
            // Load an existing plan into a fresh draft.
            planId != null -> viewModelScope.launch { draft.value = loadDraft(planId) }
            // New plan: empty draft (planId stays null).
            else -> draft.value = PlanDraft()
        }
    }

    val uiState: StateFlow<PlanEditorUiState> = combine(
        draft,
        editingDayKey,
        exerciseRepository.observeAll(),
    ) { currentDraft, dayKey, exercises ->
        val byId = exercises.associateBy { it.id }
        val days = currentDraft.days.map { day -> day.toUi(byId) }
        val editing = dayKey?.let { key -> days.firstOrNull { it.key == key } }
        PlanEditorUiState(
            mode = if (editing != null) PlanEditorMode.DAY else PlanEditorMode.PLAN,
            isNewPlan = currentDraft.planId == null,
            planName = currentDraft.name,
            days = days,
            editingDay = editing,
            canSave = currentDraft.name.isNotBlank() && currentDraft.days.any { it.isValid() },
            canDone = editing != null && editing.name.isNotBlank() && editing.exercises.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanEditorUiState())

    // ── Plan-scoped mutations ─────────────────────────────────────────────────

    fun setPlanName(name: String) = mutate { it.copy(name = name) }

    fun addDay() {
        val key = UUID.randomUUID().toString()
        mutate { it.copy(days = it.days + DayDraft(key = key)) }
        editDay(key)
    }

    fun editDay(key: String) {
        editingDayKey.value = key
        savedStateHandle[KEY_EDITING_DAY] = key
    }

    fun removeDay(key: String) = mutate { it.copy(days = it.days.filterNot { day -> day.key == key }) }

    fun reorderDays(orderedKeys: List<String>) = mutate { current ->
        val byKey = current.days.associateBy { it.key }
        current.copy(days = orderedKeys.mapNotNull { byKey[it] })
    }

    fun closeDayEditor() {
        editingDayKey.value = null
        savedStateHandle[KEY_EDITING_DAY] = null
    }

    // ── Day-scoped mutations (operate on the day at editingDayKey) ─────────────

    fun setDayName(name: String) = mutateDay { it.copy(name = name) }

    /** Appends items for [exerciseIds], deduping ids already present in the current day. */
    fun addExercises(exerciseIds: List<String>) = mutateDay { day ->
        val present = day.items.map { it.exerciseId }.toSet()
        val additions = exerciseIds
            .filterNot { it in present }
            .distinct()
            .map { ItemDraft(key = UUID.randomUUID().toString(), exerciseId = it) }
        day.copy(items = day.items + additions)
    }

    fun removeItem(key: String) = mutateDay { day ->
        day.copy(items = day.items.filterNot { it.key == key })
    }

    fun reorderItems(orderedKeys: List<String>) = mutateDay { day ->
        val byKey = day.items.associateBy { it.key }
        day.copy(items = orderedKeys.mapNotNull { byKey[it] })
    }

    fun setTargets(key: String, sets: Int?, repsMin: Int?, repsMax: Int?) = mutateDay { day ->
        day.copy(
            items = day.items.map { item ->
                if (item.key == key) {
                    item.copy(targetSets = sets, targetRepsMin = repsMin, targetRepsMax = repsMax)
                } else {
                    item
                }
            },
        )
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    fun save(onSaved: (String) -> Unit) {
        viewModelScope.launch {
            onSaved(planRepository.savePlanDraft(currentDraftDroppingInvalidDays()))
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** Loads an existing plan + its days + their exercises into a draft (ids preserved as keys). */
    private suspend fun loadDraft(id: String): PlanDraft {
        val plan = planRepository.observePlan(id).first()
        val days = planRepository.observeDayTemplates(id).first()
        return PlanDraft(
            planId = id,
            name = plan?.name.orEmpty(),
            days = days.map { day ->
                val items = planRepository.observeTemplateExercises(day.id).first()
                DayDraft(
                    key = day.id,
                    templateId = day.id,
                    name = day.name,
                    items = items.map { te ->
                        ItemDraft(
                            key = te.id,
                            templateExerciseId = te.id,
                            exerciseId = te.exerciseId,
                            targetSets = te.targetSets,
                            targetRepsMin = te.targetRepsMin,
                            targetRepsMax = te.targetRepsMax,
                        )
                    },
                )
            },
        )
    }

    /** Drops empty/invalid days (blank name OR no items) so Save persists only meaningful days. */
    private fun currentDraftDroppingInvalidDays(): PlanDraft {
        val d = draft.value
        return d.copy(days = d.days.filter { it.isValid() })
    }

    /** Applies [transform] to the draft and mirrors a serialized copy into the SavedStateHandle. */
    private inline fun mutate(transform: (PlanDraft) -> PlanDraft) {
        val next = transform(draft.value)
        draft.value = next
        savedStateHandle[KEY_DRAFT] = json.encodeToString(next)
    }

    /** Applies [transform] to the day currently being edited (no-op if none). */
    private inline fun mutateDay(transform: (DayDraft) -> DayDraft) {
        val key = editingDayKey.value ?: return
        mutate { current ->
            current.copy(days = current.days.map { if (it.key == key) transform(it) else it })
        }
    }

    private fun DayDraft.isValid(): Boolean = name.isNotBlank() && items.isNotEmpty()

    private fun DayDraft.toUi(byId: Map<String, de.simiil.liftlog.domain.model.Exercise>): EditorDayUi =
        EditorDayUi(
            key = key,
            name = name,
            isNewDay = templateId == null,
            exercises = items.map { item ->
                val ex = byId[item.exerciseId]
                EditorItemUi(
                    key = item.key,
                    exerciseId = item.exerciseId,
                    name = ex?.let { names.displayName(it.id, it.name) }.orEmpty(),
                    equipment = ex?.equipment ?: Equipment.MACHINE,
                    muscleGroup = ex?.muscleGroup ?: MuscleGroup.OTHER,
                    targetSets = item.targetSets,
                    targetRepsMin = item.targetRepsMin,
                    targetRepsMax = item.targetRepsMax,
                )
            },
        )

    private companion object {
        const val KEY_PLAN_ID = "planId"
        const val KEY_DRAFT = "draft"
        const val KEY_EDITING_DAY = "editingDayKey"
    }
}
