package de.simiil.liftlog.ui.session

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SessionDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-06-08T10:00:00Z")
    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    // ---- builders ----

    private fun exercise(
        id: String,
        name: String,
        equipment: Equipment = Equipment.BARBELL,
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CHEST,
            equipment = equipment,
            isBuiltIn = true,
            isHidden = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun session(
        id: String,
        name: String? = null,
    ): Session =
        Session(
            id = id,
            templateId = null,
            templateNameSnapshot = name,
            startedAt = now,
            endedAt = now,
            note = null,
            rpe = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun sessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String,
    ): SessionExercise =
        SessionExercise(
            id = id,
            sessionId = sessionId,
            exerciseId = exerciseId,
            position = 0,
            targetSets = null,
            targetRepsMin = null,
            targetRepsMax = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun loggedSet(
        id: String,
        seId: String,
        weightKg: Double = 80.0,
        reps: Int = 8,
    ): LoggedSet =
        LoggedSet(
            id = id,
            sessionExerciseId = seId,
            weightKg = weightKg,
            reps = reps,
            position = 0,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun makeVm(
        sessionRepo: FakeSessionRepository,
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ): SessionDetailViewModel =
        SessionDetailViewModel(
            sessionRepository = sessionRepo,
            exerciseRepository = exerciseRepo,
            settingsRepository = settingsRepo,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to "s1")),
            names = names,
        )

    // ---- Tests ----

    @Test
    fun `loading is true when details not yet emitted`() =
        runTest {
            val repo = FakeSessionRepository()
            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                assertTrue("loading should be true when no details", state.loading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `details are mapped to ui state`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1", name = "Push day")
            val ex = exercise("ex-1", "Bench Press", Equipment.BARBELL)
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1", 100.0, 5)
            val set2 = loggedSet("set-2", "se-1", 100.0, 5)

            exerciseRepo.all.value = listOf(ex)
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1, set2))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertFalse("loading should be false", state.loading)
                assertEquals("Push day", state.name)
                assertEquals(1, state.exercises.size)
                val exerciseUi = state.exercises.first()
                assertEquals("Bench Press", exerciseUi.name)
                assertEquals(Equipment.BARBELL, exerciseUi.equipment)
                assertEquals(2, exerciseUi.sets.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `exercise name is empty string when exerciseId not in repository`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "unknown-ex")
            val set1 = loggedSet("set-1", "se-1")

            // exerciseRepo has no exercises
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertFalse(state.loading)
                val exerciseUi = state.exercises.first()
                assertEquals("", exerciseUi.name)
                assertEquals(Equipment.MACHINE, exerciseUi.equipment)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onEditSetSave calls updateSet on repository`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1")

            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            // settle
            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.onEditSetSave("set-1", 90.0, 6)

            // allow coroutine to execute
            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(
                "updateSet should have been called with correct args",
                sessionRepo.updateSetCalls.contains(Triple("set-1", 90.0, 6)),
            )
        }

    @Test
    fun `onDeleteSet calls deleteSet on repository`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1")

            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.onDeleteSet("set-1")

            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue("deleteSet should have been called", sessionRepo.deleteSetCalls.contains("set-1"))
        }

    @Test
    fun `editingSetId reflects long-press then collapse`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1")

            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                // initial state
                awaitItem()

                // long press
                vm.onLongPressSet("set-1")
                val editingState = awaitItem()
                assertEquals("set-1", editingState.editingSetId)

                // collapse
                vm.onCollapseEdit()
                val collapsedState = awaitItem()
                assertNull("editingSetId should be null after collapse", collapsedState.editingSetId)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editingSetId is cleared after onEditSetSave`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1")

            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                awaitItem()

                vm.onLongPressSet("set-1")
                val editingState = awaitItem()
                assertEquals("set-1", editingState.editingSetId)

                vm.onEditSetSave("set-1", 85.0, 5)
                val savedState = awaitItem()
                assertNull("editingSetId should be cleared after save", savedState.editingSetId)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editingSetId is cleared after onDeleteSet`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository()

            val sess = session("s1")
            val se = sessionExercise("se-1", "s1", "ex-1")
            val set1 = loggedSet("set-1", "se-1")

            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = sess,
                    exercises = listOf(SessionExerciseWithSets(se, listOf(set1))),
                ),
            )

            val vm = makeVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                awaitItem()

                vm.onLongPressSet("set-1")
                val editingState = awaitItem()
                assertEquals("set-1", editingState.editingSetId)

                vm.onDeleteSet("set-1")
                val deletedState = awaitItem()
                assertNull("editingSetId should be cleared after delete", deletedState.editingSetId)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
