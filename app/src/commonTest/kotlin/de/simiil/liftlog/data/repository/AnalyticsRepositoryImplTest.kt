package de.simiil.liftlog.data.repository

import app.cash.turbine.test
import de.simiil.liftlog.data.dao.AnalyticsDao
import de.simiil.liftlog.data.dao.SetRow
import de.simiil.liftlog.data.dao.TrainedExerciseRow
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.testing.FixedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AnalyticsRepositoryImplTest {
    private val day = 86_400_000L

    // Fixed "now" = Thursday 2026-06-04 12:00 UTC (ISO week starts Monday 2026-06-01).
    private val now = Instant.parse("2026-06-04T12:00:00Z")
    private val clock = FixedClock(now)
    private val nowMs = now.toEpochMilliseconds()

    private fun ex(
        id: String,
        eq: Equipment = Equipment.BARBELL,
    ) = Exercise(
        id = id,
        name = "Ex $id",
        muscleGroup = MuscleGroup.CHEST,
        equipment = eq,
        isBuiltIn = true,
        isHidden = false,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        deletedAt = null,
    )

    private class FakeAnalyticsDao(
        val allSets: List<SetRow>,
        val trained: List<TrainedExerciseRow>,
        val perExercise: Map<String, List<SetRow>>,
    ) : AnalyticsDao {
        override fun observeAllSetsSince(fromMillis: Long): Flow<List<SetRow>> = flowOf(allSets.filter { it.startedAt >= fromMillis })

        override fun observeTrainedExercises(): Flow<List<TrainedExerciseRow>> = flowOf(trained)

        override fun observeSetsForExercise(
            exerciseId: String,
            fromMillis: Long,
        ): Flow<List<SetRow>> = flowOf(perExercise[exerciseId].orEmpty())
    }

    private class FakeExerciseRepository(
        private val list: List<Exercise>,
    ) : ExerciseRepository {
        override fun observeAll() = flowOf(list)

        override fun observeVisible() = flowOf(list)

        override suspend fun createCustom(
            name: String,
            muscleGroup: MuscleGroup,
            equipment: Equipment,
        ) = error("unused")

        override suspend fun setHidden(
            id: String,
            hidden: Boolean,
        ) {}

        override fun observeRecentlyUsedIds() = flowOf(emptyList<String>())
    }

    @Test fun weekSummary_splitsThisVsPreviousWeek() =
        runTest {
            // this week (after Mon 2026-06-01): one session, 2 sets, volume 100·5+100·5=1000
            // last week (Mon 2026-05-25..): one session, volume 50·10=500
            val thisWeek = nowMs - 1 * day
            val lastWeek = nowMs - 8 * day
            val dao =
                FakeAnalyticsDao(
                    allSets =
                        listOf(
                            SetRow("a", "e1", thisWeek, 100.0, 5),
                            SetRow("a", "e1", thisWeek, 100.0, 5),
                            SetRow("b", "e1", lastWeek, 50.0, 10),
                        ),
                    trained = emptyList(),
                    perExercise = emptyMap(),
                )
            val repo = AnalyticsRepositoryImpl(dao, FakeExerciseRepository(emptyList()), clock)
            repo.observeWeekSummary().test {
                val w = awaitItem()
                assertEquals(1, w.sessions)
                assertEquals(2, w.sets)
                assertEquals(1000.0, w.volumeKg, 0.001)
                assertEquals(500.0, w.prevVolumeKg, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun trainedExercises_joinsNameAndSortsByRecentDesc() =
        runTest {
            val dao =
                FakeAnalyticsDao(
                    allSets = emptyList(),
                    trained = listOf(TrainedExerciseRow("e1", 1000L), TrainedExerciseRow("e2", 2000L)),
                    perExercise = emptyMap(),
                )
            val repo = AnalyticsRepositoryImpl(dao, FakeExerciseRepository(listOf(ex("e1"), ex("e2"))), clock)
            repo.observeTrainedExercises().test {
                val list = awaitItem()
                assertEquals(listOf("e2", "e1"), list.map { it.id }) // most-recent first
                assertEquals("Ex e2", list.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun exerciseSummary_assemblesFromRows() =
        runTest {
            val dao =
                FakeAnalyticsDao(
                    allSets = emptyList(),
                    trained = emptyList(),
                    perExercise = mapOf("e1" to listOf(SetRow("s1", "e1", nowMs - 5 * day, 100.0, 5))),
                )
            val repo = AnalyticsRepositoryImpl(dao, FakeExerciseRepository(listOf(ex("e1"))), clock)
            repo.observeExerciseSummary("e1").test {
                val s = awaitItem()!!
                assertEquals(1, s.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun prSessionIds_unionsAcrossExercises() =
        runTest {
            val dao =
                FakeAnalyticsDao(
                    allSets =
                        listOf(
                            SetRow("s1", "e1", nowMs - 20 * day, 100.0, 5), // e1 first session — PR
                            SetRow("s2", "e1", nowMs - 10 * day, 100.0, 5), // tie — no flag from e1
                            SetRow("s2", "e2", nowMs - 10 * day, 60.0, 5), // e2 first session — flags s2
                            SetRow("s3", "ghost", nowMs - 5 * day, 200.0, 5), // exercise not in observeAll() — ignored
                        ),
                    trained = emptyList(),
                    perExercise = emptyMap(),
                )
            val repo = AnalyticsRepositoryImpl(dao, FakeExerciseRepository(listOf(ex("e1"), ex("e2"))), clock)
            repo.observePrSessionIds().test {
                assertEquals(setOf("s1", "s2"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
