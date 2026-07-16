package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.RecentExercise
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.fakes.FakeExerciseDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExerciseRepositoryTest {
    private val clockMillis = 5000L
    private val clock = FixedClock(Instant.fromEpochMilliseconds(clockMillis))
    private val fixedInstant = Instant.fromEpochMilliseconds(clockMillis)

    private lateinit var dao: FakeExerciseDao
    private lateinit var repo: ExerciseRepositoryImpl

    @BeforeTest
    fun setUp() {
        dao = FakeExerciseDao()
        repo = ExerciseRepositoryImpl(dao, FakeTransactor(), clock)
    }

    @Test
    fun createCustom_trims_name_and_stores_exercise_with_correct_fields() =
        runTest {
            val result = repo.createCustom("  Bench  ", MuscleGroup.CHEST, Equipment.BARBELL)

            assertEquals("Bench", result.name)
            assertFalse(result.isBuiltIn)
            assertFalse(result.isHidden)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)

            // ID is a valid UUID
            val parsed = Uuid.parse(result.id)
            assertNotNull(parsed)
            assertTrue(result.id.isNotBlank())

            // Exactly 1 row stored
            assertEquals(1, dao.rows.size)
            assertEquals("Bench", dao.rows[result.id]!!.name)
        }

    @Test
    fun createCustom_throws_on_blank_name() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                repo.createCustom("   ", MuscleGroup.CHEST, Equipment.BARBELL)
            }
        }

    @Test
    fun createCustom_throws_on_empty_name() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                repo.createCustom("", MuscleGroup.CHEST, Equipment.BARBELL)
            }
        }

    @Test
    fun createCustom_throws_on_case_insensitive_duplicate_of_live_row() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                // Pre-insert a live "Bench" exercise
                repo.createCustom("Bench", MuscleGroup.CHEST, Equipment.BARBELL)
                // Attempt to create "bench" (case-insensitive duplicate) — must throw
                repo.createCustom("bench", MuscleGroup.CHEST, Equipment.BARBELL)
            }
        }

    @Test
    fun createCustom_allows_duplicate_of_soft_deleted_row() =
        runTest {
            // Pre-insert live "Bench" and soft-delete it directly in the fake
            val first = repo.createCustom("Bench", MuscleGroup.CHEST, Equipment.BARBELL)
            val raw = dao.rows[first.id]!!
            dao.rows[first.id] = raw.copy(deletedAt = 1L)

            // Now creating "Bench" again must succeed
            val second = repo.createCustom("Bench", MuscleGroup.CHEST, Equipment.BARBELL)
            assertEquals("Bench", second.name)
            assertEquals(2, dao.rows.size)
        }

    @Test
    fun setHidden_flips_isHidden_and_bumps_updatedAt() =
        runTest {
            val exercise = repo.createCustom("Squat", MuscleGroup.QUADS, Equipment.BARBELL)
            // Override updatedAt in the stored row to a different value to make assertion discriminating
            val raw = dao.rows[exercise.id]!!
            dao.rows[exercise.id] = raw.copy(updatedAt = 1L, isHidden = false)

            repo.setHidden(exercise.id, true)

            val stored = dao.rows[exercise.id]!!
            assertTrue(stored.isHidden)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun setHidden_with_missing_id_is_a_no_op() =
        runTest {
            // Should not throw
            repo.setHidden("non-existent-id", true)
            assertEquals(0, dao.rows.size)
        }

    // ─── observeRecentlyUsedIds ───────────────────────────────────────────────

    @Test
    fun observeRecentlyUsedIds_maps_rows_to_exerciseId_list_preserving_order() =
        runTest {
            // Pre-load fake DAO with three rows in a known order (most-recent first)
            dao.recentlyUsed.value =
                listOf(
                    RecentExercise(exerciseId = "ex-c", lastUsed = 3000L),
                    RecentExercise(exerciseId = "ex-a", lastUsed = 2000L),
                    RecentExercise(exerciseId = "ex-b", lastUsed = 1000L),
                )

            val result = repo.observeRecentlyUsedIds().first()

            assertEquals(listOf("ex-c", "ex-a", "ex-b"), result)
        }

    @Test
    fun observeRecentlyUsedIds_returns_empty_list_when_no_rows_exist() =
        runTest {
            dao.recentlyUsed.value = emptyList()

            val result = repo.observeRecentlyUsedIds().first()

            assertTrue(result.isEmpty())
        }
}
