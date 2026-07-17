package de.simiil.liftlog.ui.exercises

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class ExercisePickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Built-in "1" is localized to "Bankdrücken"; everything else falls back to its stored name.
    private val resolver =
        ExerciseNameResolver { id, fallback ->
            if (id == "1") "Bankdrücken" else fallback
        }

    // JVM unit test: context = null (see AndroidLocaleFormatters KDoc — DateUtils/DateFormat
    // paths aren't exercised here; nameComparator() below is pure java.text.Collator).
    private val formatters = AndroidLocaleFormatters(context = null)

    // ---- helpers ----

    private fun makeExercise(
        id: String,
        name: String,
        muscle: MuscleGroup = MuscleGroup.CHEST,
        equipment: Equipment = Equipment.BARBELL,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = muscle,
        equipment = equipment,
        isBuiltIn = true,
        isHidden = false,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        deletedAt = null,
    )

    // ---- Tests ----

    @Test
    fun `query filters results by case-insensitive substring`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            repo.visible.value =
                listOf(
                    makeExercise("1", "Bench Press"),
                    makeExercise("2", "Squat"),
                    makeExercise("3", "bench row"),
                )

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem() // initial (empty or settled)

                vm.onQueryChange("bench")
                val state = awaitItem()

                val names = state.results.map { it.name }
                assertTrue("Bench Press should match", names.contains("Bench Press"))
                assertTrue("bench row should match", names.contains("bench row"))
                assertTrue("Squat should NOT match", !names.contains("Squat"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `muscle filter narrows results to matching muscle group`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            repo.visible.value =
                listOf(
                    makeExercise("1", "Bench Press", muscle = MuscleGroup.CHEST),
                    makeExercise("2", "Squat", muscle = MuscleGroup.QUADS),
                    makeExercise("3", "Incline Press", muscle = MuscleGroup.CHEST),
                )

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem()

                vm.onMuscleFilter(MuscleGroup.CHEST)
                val state = awaitItem()

                val names = state.results.map { it.name }
                assertTrue("Bench Press should match", names.contains("Bench Press"))
                assertTrue("Incline Press should match", names.contains("Incline Press"))
                assertTrue("Squat should NOT match", !names.contains("Squat"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `equipment filter narrows results to matching equipment`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            repo.visible.value =
                listOf(
                    makeExercise("1", "Barbell Curl", equipment = Equipment.BARBELL),
                    makeExercise("2", "Dumbbell Curl", equipment = Equipment.DUMBBELL),
                    makeExercise("3", "Cable Curl", equipment = Equipment.CABLE),
                )

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem()

                vm.onEquipmentFilter(Equipment.DUMBBELL)
                val state = awaitItem()

                val names = state.results.map { it.name }
                assertEquals(1, names.size)
                assertTrue("Dumbbell Curl should match", names.contains("Dumbbell Curl"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent surfaces recently-used exercises first when no query or filter active`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val ex1 = makeExercise("ex-1", "Bench Press")
            val ex2 = makeExercise("ex-2", "Squat")
            val ex3 = makeExercise("ex-3", "Deadlift")
            repo.visible.value = listOf(ex1, ex2, ex3)
            // recentIds order = most recently used first
            repo.recentIds.value = listOf("ex-3", "ex-1")

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                val state = awaitItem()

                // recent should contain ex-3 then ex-1 (recency order)
                val recentIds = state.recent.map { it.id }
                assertEquals(listOf("ex-3", "ex-1"), recentIds)
                // ex-2 not in recent (not in recentIds)
                assertTrue("ex-2 should NOT be in recent", !recentIds.contains("ex-2"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent is empty when a query is active`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val ex1 = makeExercise("ex-1", "Bench Press")
            repo.visible.value = listOf(ex1)
            repo.recentIds.value = listOf("ex-1")

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem() // settled with recent non-empty

                vm.onQueryChange("bench")
                val state = awaitItem()

                assertTrue("recent should be empty when query is active", state.recent.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent is empty when a muscle filter is active`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val ex1 = makeExercise("ex-1", "Bench Press", muscle = MuscleGroup.CHEST)
            repo.visible.value = listOf(ex1)
            repo.recentIds.value = listOf("ex-1")

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem()

                vm.onMuscleFilter(MuscleGroup.CHEST)
                val state = awaitItem()

                assertTrue("recent should be empty when filter is active", state.recent.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createCustom happy path invokes onCreated with new exercise id`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            var createdId: String? = null
            vm.createCustom("Push-Up", MuscleGroup.CHEST, Equipment.BODYWEIGHT) { id ->
                createdId = id
            }

            // Give coroutine a chance to run (UnconfinedTestDispatcher)
            assertNotNull("onCreated should have been called", createdId)
            assertEquals(1, repo.created.size)
            assertEquals("Push-Up", repo.created.first().name)

            vm.uiState.test {
                val state = awaitItem()
                assertNull("no error on success", state.createError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createCustom blank name sets createError to BLANK_NAME and does not call onCreated`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            var onCreatedCalled = false
            vm.createCustom("   ", MuscleGroup.CHEST, Equipment.BARBELL) {
                onCreatedCalled = true
            }

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(CreateError.BLANK_NAME, state.createError)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("onCreated must NOT be called on blank name", !onCreatedCalled)
            assertEquals(0, repo.created.size)
        }

    @Test
    fun `createCustom duplicate name sets createError to DUPLICATE_NAME and does not call onCreated`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            repo.duplicateNames.add("bench press")

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            var onCreatedCalled = false
            vm.createCustom("Bench Press", MuscleGroup.CHEST, Equipment.BARBELL) {
                onCreatedCalled = true
            }

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(CreateError.DUPLICATE_NAME, state.createError)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("onCreated must NOT be called on duplicate name", !onCreatedCalled)
            assertEquals(0, repo.created.size)
        }

    @Test
    fun `createError is cleared when onQueryChange is called`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem() // initial state (no error)

                // Trigger a blank-name error while subscribed
                vm.createCustom("", MuscleGroup.CHEST, Equipment.BARBELL) {}
                val errorState = awaitItem()
                assertNotNull("error should be set", errorState.createError)

                // onQueryChange sets queryFlow then clears createErrorFlow, which triggers
                // two combine re-emissions. expectMostRecentItem() picks up the final settled
                // state after both upstream changes have been processed.
                vm.onQueryChange("something")
                val clearedState = expectMostRecentItem()
                assertNull("error should be cleared after query change", clearedState.createError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `results sorted by recency then name when no query active`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            val exA = makeExercise("ex-a", "Squat")
            val exB = makeExercise("ex-b", "Bench Press")
            val exC = makeExercise("ex-c", "Deadlift")
            repo.visible.value = listOf(exA, exB, exC)
            // Only ex-c is recently used
            repo.recentIds.value = listOf("ex-c")

            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                val state = awaitItem()
                // ex-c should be first (recency rank 0), then alphabetical: Bench Press, Squat
                val ids = state.results.map { it.id }
                assertEquals("ex-c", ids[0])
                assertEquals("ex-b", ids[1]) // Bench Press before Squat alphabetically
                assertEquals("ex-a", ids[2])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sorts by localized display name, not stored name`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Stored "Zzz Press" resolves to "Aaa Press": must sort first despite its stored name.
            val invertingResolver =
                ExerciseNameResolver { id, fallback ->
                    if (id == "z") "Aaa Press" else fallback
                }
            val repo = FakeExerciseRepository()
            repo.visible.value =
                listOf(
                    makeExercise("m", "Mmm Press"),
                    makeExercise("z", "Zzz Press"),
                )
            val vm = ExercisePickerViewModel(repo, invertingResolver, formatters)
            vm.uiState.test {
                val state = awaitItem()
                // "z" resolves to "Aaa Press" which sorts before "Mmm Press"
                val ids = state.results.map { it.id }
                assertEquals(listOf("z", "m"), ids)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `query matches the localized display name`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = FakeExerciseRepository()
            repo.visible.value =
                listOf(
                    makeExercise("1", "Barbell Bench Press"), // localized -> "Bankdrücken"
                    makeExercise("2", "Squat"),
                )
            val vm = ExercisePickerViewModel(repo, resolver, formatters)

            vm.uiState.test {
                awaitItem()
                vm.onQueryChange("Bankdr")
                val state = awaitItem()
                val ids = state.results.map { it.id }
                assertTrue("localized name should match", ids.contains("1"))
                assertTrue("Squat should not match", !ids.contains("2"))
                cancelAndIgnoreRemainingEvents()
            }
        }
}
