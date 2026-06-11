package de.simiil.liftlog.data.backup

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupCodecTest {
    private val appInfo = AppInfo(name = "LiftLog", versionName = "0.1.0", dbSchemaVersion = 1)
    private val exportedAt = Instant.parse("2026-06-09T12:00:00Z")

    // One row per table; includes a tombstone (exercise ex2) and a hidden exercise.
    private fun fixture() =
        BackupSnapshot(
            exercises =
                listOf(
                    ExerciseEntity(
                        "ex1",
                        "Bench Press",
                        MuscleGroup.CHEST,
                        Equipment.BARBELL,
                        isBuiltIn = true,
                        isHidden = false,
                        createdAt = 1000L,
                        updatedAt = 2000L,
                        deletedAt = null,
                    ),
                    ExerciseEntity(
                        "ex2",
                        "Old Curl",
                        MuscleGroup.BICEPS,
                        Equipment.DUMBBELL,
                        isBuiltIn = false,
                        isHidden = true,
                        createdAt = 1000L,
                        updatedAt = 5000L,
                        deletedAt = 6000L,
                    ),
                ),
            workoutPlans =
                listOf(
                    WorkoutPlanEntity("pl1", "PPL", 0, createdAt = 1000L, updatedAt = 2000L, deletedAt = null),
                ),
            planDayTemplates =
                listOf(
                    PlanDayTemplateEntity("td1", "pl1", "Push", 0, createdAt = 1000L, updatedAt = 2000L, deletedAt = null),
                ),
            templateExercises =
                listOf(
                    TemplateExerciseEntity(
                        "te1",
                        "td1",
                        "ex1",
                        0,
                        targetSets = 3,
                        targetRepsMin = 5,
                        targetRepsMax = 8,
                        createdAt = 1000L,
                        updatedAt = 2000L,
                        deletedAt = null,
                    ),
                ),
            sessions =
                listOf(
                    SessionEntity(
                        "s1",
                        templateId = "td1",
                        templateNameSnapshot = "Push",
                        startedAt = 3000L,
                        endedAt = 4000L,
                        note = null,
                        createdAt = 3000L,
                        updatedAt = 4000L,
                        deletedAt = null,
                    ),
                ),
            sessionExercises =
                listOf(
                    SessionExerciseEntity(
                        "se1",
                        "s1",
                        "ex1",
                        0,
                        targetSets = 3,
                        targetRepsMin = 5,
                        targetRepsMax = 8,
                        createdAt = 3000L,
                        updatedAt = 4000L,
                        deletedAt = null,
                    ),
                ),
            loggedSets =
                listOf(
                    LoggedSetEntity(
                        "ls1",
                        "se1",
                        weightKg = 82.5,
                        reps = 5,
                        position = 1,
                        completedAt = 3500L,
                        rpe = 8.0,
                        note = null,
                        createdAt = 3500L,
                        updatedAt = 3500L,
                        deletedAt = null,
                    ),
                ),
            weightUnit = WeightUnit.LB,
            theme = ThemePreference.DARK,
        )

    private fun golden(): String =
        this::class.java
            .getResourceAsStream("/backup/golden-backup.json")!!
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `encode matches the golden file`() {
        assertEquals(golden().trim(), BackupCodec.encode(fixture(), exportedAt, appInfo).trim())
    }

    @Test
    fun `decode of the golden file reproduces the snapshot`() {
        val result = BackupCodec.decode(golden())
        assertTrue(result is ParseResult.Ready)
        val parsed = (result as ParseResult.Ready).parsed as BackupSnapshot
        assertEquals(fixture(), parsed)
    }

    @Test
    fun `summary counts live rows only`() {
        val result = BackupCodec.decode(golden()) as ParseResult.Ready
        // ex1 live, ex2 tombstoned → 1 live exercise; 1 live session; 1 live set
        assertEquals(1, result.summary.exercises)
        assertEquals(1, result.summary.sessions)
        assertEquals(1, result.summary.sets)
    }

    @Test
    fun `malformed json is rejected`() {
        assertEquals(ParseResult.Invalid(InvalidReason.MALFORMED), BackupCodec.decode("{ not json"))
    }

    @Test
    fun `missing required field is rejected`() {
        val noData =
            """
            {"formatVersion":1,"exportedAt":"2026-06-09T12:00:00Z",
            "app":{"name":"LiftLog","versionName":"0.1.0","dbSchemaVersion":1},
            "settings":{"weightUnit":"KG","theme":"SYSTEM"}}
            """.trimIndent()
        assertEquals(ParseResult.Invalid(InvalidReason.MISSING_FIELDS), BackupCodec.decode(noData))
    }

    @Test
    fun `newer format version is rejected as Newer`() {
        val newer = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(ParseResult.Newer(2), BackupCodec.decode(newer))
    }

    @Test
    fun `unknown entity enum is rejected`() {
        val bad = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"CHEST\"", "\"WINGS\"")
        assertEquals(ParseResult.Invalid(InvalidReason.UNKNOWN_ENUM), BackupCodec.decode(bad))
    }

    @Test
    fun `bad timestamp is rejected`() {
        val bad =
            BackupCodec
                .encode(fixture(), exportedAt, appInfo)
                .replace("\"completedAt\": \"", "\"completedAt\": \"NOPE")
        assertEquals(ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP), BackupCodec.decode(bad))
    }

    @Test
    fun `fk orphan is rejected`() {
        val bad =
            BackupCodec
                .encode(
                    fixture(),
                    exportedAt,
                    appInfo,
                ).replace("\"sessionExerciseId\": \"se1\"", "\"sessionExerciseId\": \"ghost\"")
        assertEquals(ParseResult.Invalid(InvalidReason.FK_ORPHAN), BackupCodec.decode(bad))
    }

    @Test
    fun `unknown settings enum falls back instead of failing`() {
        val odd = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"theme\": \"DARK\"", "\"theme\": \"NEON\"")
        val result = BackupCodec.decode(odd)
        assertTrue(result is ParseResult.Ready) // theme falls back to SYSTEM, still valid
        assertEquals(ThemePreference.SYSTEM, ((result as ParseResult.Ready).parsed as BackupSnapshot).theme)
    }

    @Test
    fun `bad top-level exportedAt is rejected`() {
        val bad =
            BackupCodec
                .encode(fixture(), exportedAt, appInfo)
                .replace("\"exportedAt\": \"2026-06-09T12:00:00Z\"", "\"exportedAt\": \"not-a-date\"")
        assertEquals(ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP), BackupCodec.decode(bad))
    }

    @Test
    fun `ad-hoc session with null templateId round-trips`() {
        val snap =
            BackupSnapshot(
                exercises =
                    listOf(
                        ExerciseEntity(
                            "ex1",
                            "Bench",
                            MuscleGroup.CHEST,
                            Equipment.BARBELL,
                            isBuiltIn = false,
                            isHidden = false,
                            createdAt = 1000L,
                            updatedAt = 1000L,
                            deletedAt = null,
                        ),
                    ),
                workoutPlans = emptyList(),
                planDayTemplates = emptyList(),
                templateExercises = emptyList(),
                sessions =
                    listOf(
                        SessionEntity(
                            "s1",
                            templateId = null,
                            templateNameSnapshot = null,
                            startedAt = 3000L,
                            endedAt = 4000L,
                            note = null,
                            createdAt = 3000L,
                            updatedAt = 4000L,
                            deletedAt = null,
                        ),
                    ),
                sessionExercises = listOf(SessionExerciseEntity("se1", "s1", "ex1", 0, null, null, null, 3000L, 4000L, null)),
                loggedSets =
                    listOf(
                        LoggedSetEntity(
                            "ls1",
                            "se1",
                            60.0,
                            10,
                            1,
                            completedAt = 3500L,
                            rpe = null,
                            note = null,
                            createdAt = 3500L,
                            updatedAt = 3500L,
                            deletedAt = null,
                        ),
                    ),
                weightUnit = WeightUnit.KG,
                theme = ThemePreference.SYSTEM,
            )
        val result = BackupCodec.decode(BackupCodec.encode(snap, exportedAt, appInfo))
        assertTrue(result is ParseResult.Ready)
        assertEquals(snap, (result as ParseResult.Ready).parsed as BackupSnapshot)
    }

    @Test
    fun `unknown weightUnit falls back to KG`() {
        val odd = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"weightUnit\": \"LB\"", "\"weightUnit\": \"STONES\"")
        val result = BackupCodec.decode(odd)
        assertTrue(result is ParseResult.Ready)
        assertEquals(WeightUnit.KG, ((result as ParseResult.Ready).parsed as BackupSnapshot).weightUnit)
    }

    @Test
    fun `out-of-range timestamp is rejected`() {
        val bad =
            BackupCodec
                .encode(fixture(), exportedAt, appInfo)
                .replace("\"completedAt\": \"1970-01-01T00:00:03.500Z\"", "\"completedAt\": \"+999999999-12-31T23:59:59Z\"")
        assertEquals(ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP), BackupCodec.decode(bad))
    }

    @Test
    fun `duplicate primary keys are rejected`() {
        val dup = fixture().copy(exercises = fixture().exercises + fixture().exercises[0]) // ex1 twice
        val json = BackupCodec.encode(dup, exportedAt, appInfo)
        assertEquals(ParseResult.Invalid(InvalidReason.MALFORMED), BackupCodec.decode(json))
    }
}
