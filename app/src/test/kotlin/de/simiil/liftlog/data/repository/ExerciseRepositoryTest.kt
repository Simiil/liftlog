package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.RecentExercise
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.fakes.FakeExerciseDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ExerciseRepositoryTest {

    private val clockMillis = 5000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneOffset.UTC)
    private val fixedInstant = Instant.ofEpochMilli(clockMillis)

    private lateinit var dao: FakeExerciseDao
    private lateinit var repo: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeExerciseDao()
        repo = ExerciseRepositoryImpl(dao, FakeTransactor(), clock)
    }

    @Test
    fun `createCustom trims name and stores exercise with correct fields`() = runTest {
        val result = repo.createCustom("  Bench  ", MuscleGroup.CHEST, Equipment.BARBELL)

        assertEquals("Bench", result.name)
        assertFalse(result.isBuiltIn)
        assertFalse(result.isHidden)
        assertEquals(fixedInstant, result.createdAt)
        assertEquals(fixedInstant, result.updatedAt)
        assertNull(result.deletedAt)

        // ID is a valid UUID
        val parsed = UUID.fromString(result.id)
        assertNotNull(parsed)
        assertTrue(result.id.isNotBlank())

        // Exactly 1 row stored
        assertEquals(1, dao.rows.size)
        assertEquals("Bench", dao.rows[result.id]!!.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createCustom throws on blank name`() = runTest {
        repo.createCustom("   ", MuscleGroup.CHEST, Equipment.BARBELL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createCustom throws on empty name`() = runTest {
        repo.createCustom("", MuscleGroup.CHEST, Equipment.BARBELL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createCustom throws on case-insensitive duplicate of live row`() = runTest {
        // Pre-insert a live "Bench" exercise
        repo.createCustom("Bench", MuscleGroup.CHEST, Equipment.BARBELL)
        // Attempt to create "bench" (case-insensitive duplicate) — must throw
        repo.createCustom("bench", MuscleGroup.CHEST, Equipment.BARBELL)
    }

    @Test
    fun `createCustom allows duplicate of soft-deleted row`() = runTest {
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
    fun `setHidden flips isHidden and bumps updatedAt`() = runTest {
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
    fun `setHidden with missing id is a no-op`() = runTest {
        // Should not throw
        repo.setHidden("non-existent-id", true)
        assertEquals(0, dao.rows.size)
    }

    // ─── observeRecentlyUsedIds ───────────────────────────────────────────────

    @Test
    fun `observeRecentlyUsedIds maps rows to exerciseId list preserving order`() = runTest {
        // Pre-load fake DAO with three rows in a known order (most-recent first)
        dao.recentlyUsed.value = listOf(
            RecentExercise(exerciseId = "ex-c", lastUsed = 3000L),
            RecentExercise(exerciseId = "ex-a", lastUsed = 2000L),
            RecentExercise(exerciseId = "ex-b", lastUsed = 1000L),
        )

        val result = repo.observeRecentlyUsedIds().first()

        assertEquals(listOf("ex-c", "ex-a", "ex-b"), result)
    }

    @Test
    fun `observeRecentlyUsedIds returns empty list when no rows exist`() = runTest {
        dao.recentlyUsed.value = emptyList()

        val result = repo.observeRecentlyUsedIds().first()

        assertTrue(result.isEmpty())
    }
}
