# M5 (PR1) — Export / Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lossless, versioned JSON backup — export the whole DB + settings to a SAF file and import it back as a validated full-replace.

**Architecture:** Pure `BackupCodec` (String⇄`BackupSnapshot`, all validation) is the testable seam; `BackupRepositoryImpl` snapshots the DB via a dedicated `BackupDao` and applies an atomic wipe+insert; the Settings ViewModel orchestrates a two-step import (parse → confirm → apply); the SAF `Uri`⇄`String` I/O is isolated behind `DocumentIo`. ViewModels keep depending only on `domain/`.

**Tech Stack:** Kotlin, kotlinx.serialization (already present), Room (room-ktx `@Transaction`), Hilt, Jetpack Compose + `androidx.activity` result contracts, JUnit4 + Turbine + Robolectric-free JVM unit tests + AndroidJUnit4 instrumented tests.

**Spec:** `docs/superpowers/specs/2026-06-09-m5-export-import-design.md`. Read it for the wire format and validation rules.

**Zero new dependencies.**

---

## File structure

| File | Responsibility |
|---|---|
| `domain/repository/BackupRepository.kt` (new) | Interface + `ParseResult`/`ParsedBackup`/`ImportSummary`/`InvalidReason` (Android-free) |
| `data/backup/BackupModels.kt` (new) | `@Serializable` DTOs mirroring data-spec §6 (timestamps as ISO strings) |
| `data/backup/BackupSnapshot.kt` (new) | Entity-list holder; implements `ParsedBackup`; carries `AppInfo` |
| `data/backup/BackupCodec.kt` (new) | PURE `encode`/`decode`/validate + entity⇄DTO + ISO⇄millis |
| `data/dao/BackupDao.kt` (new) | `getAll×7` (incl. tombstones), `getActiveSession`, `@Transaction replaceAll` |
| `data/repository/BackupRepositoryImpl.kt` (new) | Snapshot+encode; decode+live-check; apply+settings write |
| `ui/settings/DocumentIo.kt` (new) | `DocumentIo` interface + `AndroidDocumentIo` (ContentResolver) |
| `data/db/AppDatabase.kt` (modify) | Add `backupDao()`; top-level `const val DB_SCHEMA_VERSION` |
| `di/DatabaseModule.kt` (modify) | Provide `BackupDao` + `AppInfo` |
| `di/RepositoryModule.kt` (modify) | Bind `BackupRepository` + `DocumentIo` |
| `domain/repository/SettingsRepository.kt` (modify) | Add `setWeightUnit` |
| `data/repository/SettingsRepositoryImpl.kt` (modify) | Implement `setWeightUnit` |
| `ui/settings/SettingsViewModel.kt` (modify) | Export/import orchestration + dialog/snackbar state |
| `ui/settings/SettingsScreen.kt` (modify) | Data section, SAF launchers, confirm + error dialogs, snackbar |
| `res/values/strings.xml` (modify) | New strings |
| `testing/FakeSettingsRepository.kt` (modify, src/test) | Add `setWeightUnit` |
| `testing/FakeBackupRepository.kt` (new, src/test) | Programmable fake for VM test |
| `data/backup/BackupCodecTest.kt` (new, src/test) | Golden + validation |
| `app/src/test/resources/backup/golden-backup.json` (new) | Locked wire format |
| `data/repository/SettingsRepositoryTest.kt` (modify, src/test) | `setWeightUnit` round-trip |
| `ui/settings/SettingsViewModelTest.kt` (modify, src/test) | Import/export flows |
| `data/backup/BackupRoundTripTest.kt` (new, src/androidTest) | export→wipe→import lossless + live-session block |

---

## Task 1: `setWeightUnit` on SettingsRepository

Import must restore the persisted weight unit. The kg/lb toggle UI stays deferred to PR2 — this is the persistence side only.

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/testing/FakeSettingsRepository.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/repository/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** — append to `SettingsRepositoryTest`:

```kotlin
    private fun persistingDataStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val state = kotlinx.coroutines.flow.MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun `setWeightUnit persists and re-reads`() = runTest {
        val repo = SettingsRepositoryImpl(persistingDataStore())
        repo.setWeightUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, repo.weightUnit.first())
    }
```

- [ ] **Step 2: Run it — expect FAIL** (compile error: `setWeightUnit` unresolved)

Run: `./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.data.repository.SettingsRepositoryTest"`
Expected: compilation failure.

- [ ] **Step 3: Add to the interface** — `SettingsRepository.kt`, replace the deferred comment + add the method:

```kotlin
interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
    val weightUnit: Flow<WeightUnit>
    suspend fun setThemePreference(preference: ThemePreference)
    /** Persistence for the kg/lb unit. Toggle UI lands in M5 PR2; import restores it now. */
    suspend fun setWeightUnit(unit: WeightUnit)
}
```

- [ ] **Step 4: Implement in `SettingsRepositoryImpl`** — add after `setThemePreference`:

```kotlin
    override suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[KEY_WEIGHT_UNIT] = unit.name
        }
    }
```

- [ ] **Step 5: Update `FakeSettingsRepository`** — add the override:

```kotlin
    override suspend fun setWeightUnit(unit: WeightUnit) {
        weightUnitState.value = unit
    }
```

- [ ] **Step 6: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.data.repository.SettingsRepositoryTest"`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(settings): persist weight unit (setWeightUnit)"
```

---

## Task 2: Domain result types + `BackupRepository` interface

Android-free domain contract. `ParsedBackup` is an opaque handle so the UI can carry a validated backup from parse → apply without seeing data-layer entities.

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/repository/BackupRepository.kt`

- [ ] **Step 1: Create the file**

```kotlin
package de.simiil.liftlog.domain.repository

import java.time.Instant

/** Opaque, validated backup produced by [BackupRepository.parseImport] and consumed by
 *  [BackupRepository.applyImport]. The data layer's BackupSnapshot is the only implementor. */
interface ParsedBackup

/** Counts shown in the import confirmation (live rows only — what the user recognizes). */
data class ImportSummary(
    val exportedAt: Instant,
    val sessions: Int,
    val exercises: Int,
    val sets: Int,
)

/** Why a backup file was rejected. Each maps to a user-facing string at the UI. */
enum class InvalidReason { MALFORMED, MISSING_FIELDS, BAD_TIMESTAMP, FK_ORPHAN, UNKNOWN_ENUM }

/** Outcome of parsing+validating a candidate file. No writes have happened. */
sealed interface ParseResult {
    data class Ready(val parsed: ParsedBackup, val summary: ImportSummary) : ParseResult
    data object BlockedByLiveSession : ParseResult
    data class Newer(val fileVersion: Int) : ParseResult
    data class Invalid(val reason: InvalidReason) : ParseResult
}

/** Versioned JSON backup (02-data-spec §6). */
interface BackupRepository {
    /** Whole DB (incl. tombstones) + settings → JSON text. */
    suspend fun exportToJson(): String

    /** Parse + validate + live-session check. Never writes. */
    suspend fun parseImport(json: String): ParseResult

    /** Full-replace: wipe + insert everything in [parsed], then restore settings. */
    suspend fun applyImport(parsed: ParsedBackup): ImportSummary
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(backup): domain BackupRepository contract + result types"
```

---

## Task 3: DTOs, `BackupSnapshot`, `AppInfo`

Inert data holders. DTO field order and `BackupData` key order are part of the golden file — keep exactly as written.

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/backup/BackupModels.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/backup/BackupSnapshot.kt`

- [ ] **Step 1: Create `BackupModels.kt`**

```kotlin
package de.simiil.liftlog.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val app: AppInfoDto,
    val settings: SettingsDto,
    val data: BackupData,
)

@Serializable
data class AppInfoDto(val name: String, val versionName: String, val dbSchemaVersion: Int)

@Serializable
data class SettingsDto(val weightUnit: String, val theme: String)

@Serializable
data class BackupData(
    val exercises: List<ExerciseDto>,
    val workoutPlans: List<WorkoutPlanDto>,
    val planDayTemplates: List<PlanDayTemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val loggedSets: List<LoggedSetDto>,
)

@Serializable
data class ExerciseDto(
    val id: String, val name: String, val muscleGroup: String, val equipment: String,
    val isBuiltIn: Boolean, val isHidden: Boolean,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class WorkoutPlanDto(
    val id: String, val name: String, val position: Int,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class PlanDayTemplateDto(
    val id: String, val planId: String, val name: String, val position: Int,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class TemplateExerciseDto(
    val id: String, val templateId: String, val exerciseId: String, val position: Int,
    val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class SessionDto(
    val id: String, val templateId: String?, val templateNameSnapshot: String?,
    val startedAt: String, val endedAt: String?, val note: String?,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class SessionExerciseDto(
    val id: String, val sessionId: String, val exerciseId: String, val position: Int,
    val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)

@Serializable
data class LoggedSetDto(
    val id: String, val sessionExerciseId: String, val weightKg: Double, val reps: Int,
    val position: Int, val completedAt: String, val rpe: Double?, val note: String?,
    val createdAt: String, val updatedAt: String, val deletedAt: String?,
)
```

- [ ] **Step 2: Create `BackupSnapshot.kt`**

```kotlin
package de.simiil.liftlog.data.backup

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ParsedBackup

/** App identity stamped into the export header (02-data-spec §6). DI-provided. */
data class AppInfo(val name: String, val versionName: String, val dbSchemaVersion: Int)

/** Lossless in-memory image of the whole DB + the two persisted settings.
 *  Holds entities (not domain models) so round-trips are byte-for-byte. */
data class BackupSnapshot(
    val exercises: List<ExerciseEntity>,
    val workoutPlans: List<WorkoutPlanEntity>,
    val planDayTemplates: List<PlanDayTemplateEntity>,
    val templateExercises: List<TemplateExerciseEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val loggedSets: List<LoggedSetEntity>,
    val weightUnit: WeightUnit,
    val theme: ThemePreference,
) : ParsedBackup
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(backup): serializable DTOs + BackupSnapshot holder"
```

---

## Task 4: `BackupCodec` (pure) + golden & validation tests

The testable seam. Write the validation tests first; generate the golden from the implemented encoder, then lock it.

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/backup/BackupCodec.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/backup/BackupCodecTest.kt`
- Create: `app/src/test/resources/backup/golden-backup.json`

- [ ] **Step 1: Write the test (fixture + golden + validation)**

```kotlin
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
    private fun fixture() = BackupSnapshot(
        exercises = listOf(
            ExerciseEntity("ex1", "Bench Press", MuscleGroup.CHEST, Equipment.BARBELL,
                isBuiltIn = true, isHidden = false, createdAt = 1000L, updatedAt = 2000L, deletedAt = null),
            ExerciseEntity("ex2", "Old Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL,
                isBuiltIn = false, isHidden = true, createdAt = 1000L, updatedAt = 5000L, deletedAt = 6000L),
        ),
        workoutPlans = listOf(
            WorkoutPlanEntity("pl1", "PPL", 0, createdAt = 1000L, updatedAt = 2000L, deletedAt = null)),
        planDayTemplates = listOf(
            PlanDayTemplateEntity("td1", "pl1", "Push", 0, createdAt = 1000L, updatedAt = 2000L, deletedAt = null)),
        templateExercises = listOf(
            TemplateExerciseEntity("te1", "td1", "ex1", 0, targetSets = 3, targetRepsMin = 5,
                targetRepsMax = 8, createdAt = 1000L, updatedAt = 2000L, deletedAt = null)),
        sessions = listOf(
            SessionEntity("s1", templateId = "td1", templateNameSnapshot = "Push", startedAt = 3000L,
                endedAt = 4000L, note = null, createdAt = 3000L, updatedAt = 4000L, deletedAt = null)),
        sessionExercises = listOf(
            SessionExerciseEntity("se1", "s1", "ex1", 0, targetSets = 3, targetRepsMin = 5,
                targetRepsMax = 8, createdAt = 3000L, updatedAt = 4000L, deletedAt = null)),
        loggedSets = listOf(
            LoggedSetEntity("ls1", "se1", weightKg = 82.5, reps = 5, position = 1, completedAt = 3500L,
                rpe = 8.0, note = null, createdAt = 3500L, updatedAt = 3500L, deletedAt = null)),
        weightUnit = WeightUnit.LB,
        theme = ThemePreference.DARK,
    )

    private fun golden(): String =
        this::class.java.getResourceAsStream("/backup/golden-backup.json")!!
            .bufferedReader().use { it.readText() }

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
        val noData = """{"formatVersion":1,"exportedAt":"2026-06-09T12:00:00Z",
            "app":{"name":"LiftLog","versionName":"0.1.0","dbSchemaVersion":1},
            "settings":{"weightUnit":"KG","theme":"SYSTEM"}}""".trimIndent()
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
        val bad = BackupCodec.encode(fixture(), exportedAt, appInfo)
            .replace("\"completedAt\": \"", "\"completedAt\": \"NOPE")
        assertEquals(ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP), BackupCodec.decode(bad))
    }

    @Test
    fun `fk orphan is rejected`() {
        val bad = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"sessionExerciseId\": \"se1\"", "\"sessionExerciseId\": \"ghost\"")
        assertEquals(ParseResult.Invalid(InvalidReason.FK_ORPHAN), BackupCodec.decode(bad))
    }

    @Test
    fun `unknown settings enum falls back instead of failing`() {
        val odd = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"theme\": \"DARK\"", "\"theme\": \"NEON\"")
        val result = BackupCodec.decode(odd)
        assertTrue(result is ParseResult.Ready) // theme falls back to SYSTEM, still valid
        assertEquals(ThemePreference.SYSTEM, ((result as ParseResult.Ready).parsed as BackupSnapshot).theme)
    }
}
```

> **Golden-dependent tests:** `encode matches the golden file`, `decode of the golden file…`, and `summary counts live rows only` depend on `golden-backup.json`, created in Step 4. They fail until then — that's expected; the validation tests (`malformed`, `missing field`, `newer`, `unknown enum`, `bad timestamp`, `fk orphan`, `settings enum fallback`) operate on encoder output and pass once `BackupCodec` exists.

- [ ] **Step 2: Run — expect FAIL** (no `BackupCodec`, no golden resource)

Run: `./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest"`
Expected: compile/`FileNotFound` failure.

- [ ] **Step 3: Implement `BackupCodec.kt`**

```kotlin
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
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeParseException

/** Pure JSON codec for the versioned backup (02-data-spec §6). No Android, no DB. */
object BackupCodec {
    const val CURRENT_FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true // forward-compat: importers ignore unknown fields
    }

    private class UnknownEnumException : RuntimeException()

    fun encode(snapshot: BackupSnapshot, exportedAt: Instant, app: AppInfo): String {
        val file = BackupFile(
            formatVersion = CURRENT_FORMAT_VERSION,
            exportedAt = exportedAt.toString(),
            app = AppInfoDto(app.name, app.versionName, app.dbSchemaVersion),
            settings = SettingsDto(snapshot.weightUnit.name, snapshot.theme.name),
            data = BackupData(
                exercises = snapshot.exercises.map { it.toDto() },
                workoutPlans = snapshot.workoutPlans.map { it.toDto() },
                planDayTemplates = snapshot.planDayTemplates.map { it.toDto() },
                templateExercises = snapshot.templateExercises.map { it.toDto() },
                sessions = snapshot.sessions.map { it.toDto() },
                sessionExercises = snapshot.sessionExercises.map { it.toDto() },
                loggedSets = snapshot.loggedSets.map { it.toDto() },
            ),
        )
        return json.encodeToString(file)
    }

    fun decode(text: String): ParseResult {
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (e: MissingFieldException) {
            return ParseResult.Invalid(InvalidReason.MISSING_FIELDS)
        } catch (e: SerializationException) {
            return ParseResult.Invalid(InvalidReason.MALFORMED)
        } catch (e: IllegalArgumentException) {
            return ParseResult.Invalid(InvalidReason.MALFORMED)
        }

        if (file.formatVersion > CURRENT_FORMAT_VERSION) return ParseResult.Newer(file.formatVersion)
        if (!file.fkIntact()) return ParseResult.Invalid(InvalidReason.FK_ORPHAN)

        val snapshot = try {
            file.toSnapshot()
        } catch (e: UnknownEnumException) {
            return ParseResult.Invalid(InvalidReason.UNKNOWN_ENUM)
        } catch (e: DateTimeParseException) {
            return ParseResult.Invalid(InvalidReason.BAD_TIMESTAMP)
        }

        val summary = ImportSummary(
            exportedAt = Instant.parse(file.exportedAt),
            sessions = snapshot.sessions.count { it.deletedAt == null },
            exercises = snapshot.exercises.count { it.deletedAt == null },
            sets = snapshot.loggedSets.count { it.deletedAt == null },
        )
        return ParseResult.Ready(snapshot, summary)
    }

    // --- timestamp helpers ---
    private fun Long.iso(): String = Instant.ofEpochMilli(this).toString()
    private fun String.millis(): Long = Instant.parse(this).toEpochMilli() // throws DateTimeParseException

    // --- strict entity-enum lookups ---
    private fun muscle(name: String): MuscleGroup =
        MuscleGroup.entries.firstOrNull { it.name == name } ?: throw UnknownEnumException()
    private fun equip(name: String): Equipment =
        Equipment.entries.firstOrNull { it.name == name } ?: throw UnknownEnumException()

    // --- FK integrity (tombstones count as present rows) ---
    private fun BackupFile.fkIntact(): Boolean {
        val ex = data.exercises.mapTo(HashSet()) { it.id }
        val pl = data.workoutPlans.mapTo(HashSet()) { it.id }
        val td = data.planDayTemplates.mapTo(HashSet()) { it.id }
        val ss = data.sessions.mapTo(HashSet()) { it.id }
        val se = data.sessionExercises.mapTo(HashSet()) { it.id }
        if (data.planDayTemplates.any { it.planId !in pl }) return false
        if (data.templateExercises.any { it.templateId !in td || it.exerciseId !in ex }) return false
        if (data.sessions.any { it.templateId != null && it.templateId !in td }) return false
        if (data.sessionExercises.any { it.sessionId !in ss || it.exerciseId !in ex }) return false
        if (data.loggedSets.any { it.sessionExerciseId !in se }) return false
        return true
    }

    private fun BackupFile.toSnapshot() = BackupSnapshot(
        exercises = data.exercises.map { it.toEntity() },
        workoutPlans = data.workoutPlans.map { it.toEntity() },
        planDayTemplates = data.planDayTemplates.map { it.toEntity() },
        templateExercises = data.templateExercises.map { it.toEntity() },
        sessions = data.sessions.map { it.toEntity() },
        sessionExercises = data.sessionExercises.map { it.toEntity() },
        loggedSets = data.loggedSets.map { it.toEntity() },
        weightUnit = WeightUnit.fromStorageValue(settings.weightUnit), // lenient
        theme = ThemePreference.fromStorageValue(settings.theme),      // lenient
    )

    // --- entity → DTO ---
    private fun ExerciseEntity.toDto() = ExerciseDto(id, name, muscleGroup.name, equipment.name,
        isBuiltIn, isHidden, createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun WorkoutPlanEntity.toDto() = WorkoutPlanDto(id, name, position,
        createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun PlanDayTemplateEntity.toDto() = PlanDayTemplateDto(id, planId, name, position,
        createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun TemplateExerciseEntity.toDto() = TemplateExerciseDto(id, templateId, exerciseId, position,
        targetSets, targetRepsMin, targetRepsMax, createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun SessionEntity.toDto() = SessionDto(id, templateId, templateNameSnapshot,
        startedAt.iso(), endedAt?.iso(), note, createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun SessionExerciseEntity.toDto() = SessionExerciseDto(id, sessionId, exerciseId, position,
        targetSets, targetRepsMin, targetRepsMax, createdAt.iso(), updatedAt.iso(), deletedAt?.iso())
    private fun LoggedSetEntity.toDto() = LoggedSetDto(id, sessionExerciseId, weightKg, reps, position,
        completedAt.iso(), rpe, note, createdAt.iso(), updatedAt.iso(), deletedAt?.iso())

    // --- DTO → entity (may throw UnknownEnumException / DateTimeParseException) ---
    private fun ExerciseDto.toEntity() = ExerciseEntity(id, name, muscle(muscleGroup), equip(equipment),
        isBuiltIn, isHidden, createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun WorkoutPlanDto.toEntity() = WorkoutPlanEntity(id, name, position,
        createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun PlanDayTemplateDto.toEntity() = PlanDayTemplateEntity(id, planId, name, position,
        createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun TemplateExerciseDto.toEntity() = TemplateExerciseEntity(id, templateId, exerciseId, position,
        targetSets, targetRepsMin, targetRepsMax, createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun SessionDto.toEntity() = SessionEntity(id, templateId, templateNameSnapshot,
        startedAt.millis(), endedAt?.millis(), note, createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun SessionExerciseDto.toEntity() = SessionExerciseEntity(id, sessionId, exerciseId, position,
        targetSets, targetRepsMin, targetRepsMax, createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
    private fun LoggedSetDto.toEntity() = LoggedSetEntity(id, sessionExerciseId, weightKg, reps, position,
        completedAt.millis(), rpe, note, createdAt.millis(), updatedAt.millis(), deletedAt?.millis())
}
```

- [ ] **Step 4: Generate the golden file**

The encoder is deterministic (fixed fixture, `exportedAt`, `appInfo`). Run the encode test once — it fails showing the produced JSON. Save that exact JSON to `app/src/test/resources/backup/golden-backup.json`. Quick way:

```bash
mkdir -p app/src/test/resources/backup
# Temporarily add this throwaway test, run it, copy the printed block, then delete it:
#   @Test fun dump() { println(BackupCodec.encode(fixture(), exportedAt, appInfo)) }
./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest.dump"
```

Paste the printed JSON verbatim into the resource file. Then **hand-verify against data-spec §6**: top-level keys are `formatVersion, exportedAt, app, settings, data`; `data` keys are in the 7-table order above; timestamps are ISO-8601 `...Z`; enums are names; `deletedAt` is `null` for live rows and a string for `ex2`. Remove the throwaway `dump` test.

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest"`
Expected: all green (encode==golden, decode round-trips, every validation case maps correctly).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(backup): pure BackupCodec with golden + validation tests"
```

---

## Task 5: `BackupDao` + DB/DI wiring

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/dao/BackupDao.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/db/AppDatabase.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/DatabaseModule.kt`

- [ ] **Step 1: Create `BackupDao.kt`**

```kotlin
package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.simiil.liftlog.data.backup.BackupSnapshot
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity

/** Backup-only DAO: the sole place that reads rows WITHOUT the `deletedAt IS NULL` filter
 *  (full-fidelity snapshot) and the sole place that hard-deletes (full-replace import). */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises") suspend fun getAllExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM workout_plans") suspend fun getAllWorkoutPlans(): List<WorkoutPlanEntity>
    @Query("SELECT * FROM plan_day_templates") suspend fun getAllPlanDayTemplates(): List<PlanDayTemplateEntity>
    @Query("SELECT * FROM template_exercises") suspend fun getAllTemplateExercises(): List<TemplateExerciseEntity>
    @Query("SELECT * FROM sessions") suspend fun getAllSessions(): List<SessionEntity>
    @Query("SELECT * FROM session_exercises") suspend fun getAllSessionExercises(): List<SessionExerciseEntity>
    @Query("SELECT * FROM logged_sets") suspend fun getAllLoggedSets(): List<LoggedSetEntity>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)
    @Insert suspend fun insertWorkoutPlans(rows: List<WorkoutPlanEntity>)
    @Insert suspend fun insertPlanDayTemplates(rows: List<PlanDayTemplateEntity>)
    @Insert suspend fun insertTemplateExercises(rows: List<TemplateExerciseEntity>)
    @Insert suspend fun insertSessions(rows: List<SessionEntity>)
    @Insert suspend fun insertSessionExercises(rows: List<SessionExerciseEntity>)
    @Insert suspend fun insertLoggedSets(rows: List<LoggedSetEntity>)

    @Query("DELETE FROM exercises") suspend fun deleteAllExercises()
    @Query("DELETE FROM workout_plans") suspend fun deleteAllWorkoutPlans()
    @Query("DELETE FROM plan_day_templates") suspend fun deleteAllPlanDayTemplates()
    @Query("DELETE FROM template_exercises") suspend fun deleteAllTemplateExercises()
    @Query("DELETE FROM sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM session_exercises") suspend fun deleteAllSessionExercises()
    @Query("DELETE FROM logged_sets") suspend fun deleteAllLoggedSets()

    /** Atomic full replace. Delete children→parents, insert parents→children. */
    @Transaction
    suspend fun replaceAll(s: BackupSnapshot) {
        deleteAllLoggedSets(); deleteAllSessionExercises(); deleteAllSessions()
        deleteAllTemplateExercises(); deleteAllPlanDayTemplates(); deleteAllWorkoutPlans()
        deleteAllExercises()
        insertExercises(s.exercises); insertWorkoutPlans(s.workoutPlans)
        insertPlanDayTemplates(s.planDayTemplates); insertTemplateExercises(s.templateExercises)
        insertSessions(s.sessions); insertSessionExercises(s.sessionExercises)
        insertLoggedSets(s.loggedSets)
    }
}
```

- [ ] **Step 2: Wire into `AppDatabase.kt`** — add the top-level const, reference it in the annotation, and expose the DAO:

Add above the class:
```kotlin
/** Room schema version. Stamped into backup headers (02-data-spec §6) as dbSchemaVersion. */
const val DB_SCHEMA_VERSION = 1
```
Change `version = 1` to `version = DB_SCHEMA_VERSION`, add the import `import de.simiil.liftlog.data.dao.BackupDao`, and add to the abstract methods:
```kotlin
    abstract fun backupDao(): BackupDao
```

- [ ] **Step 3: Provide in `DatabaseModule.kt`** — add the DAO provider next to the others and an `AppInfo` provider:

```kotlin
    @Provides fun provideBackupDao(db: AppDatabase): BackupDao = db.backupDao()

    @Provides @Singleton
    fun provideAppInfo(): AppInfo =
        AppInfo(name = "LiftLog", versionName = BuildConfig.VERSION_NAME, dbSchemaVersion = DB_SCHEMA_VERSION)
```
Add imports: `de.simiil.liftlog.data.dao.BackupDao`, `de.simiil.liftlog.data.backup.AppInfo`, `de.simiil.liftlog.data.db.DB_SCHEMA_VERSION`, `de.simiil.liftlog.BuildConfig`.

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room generates `BackupDao_Impl`).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(backup): BackupDao snapshot+replaceAll, AppInfo + DB wiring"
```

---

## Task 6: `BackupRepositoryImpl` + binding

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/repository/BackupRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/RepositoryModule.kt`

- [ ] **Step 1: Create `BackupRepositoryImpl.kt`**

```kotlin
package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.backup.AppInfo
import de.simiil.liftlog.data.backup.BackupCodec
import de.simiil.liftlog.data.backup.BackupSnapshot
import de.simiil.liftlog.data.dao.BackupDao
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val appInfo: AppInfo,
) : BackupRepository {

    override suspend fun exportToJson(): String {
        val snapshot = BackupSnapshot(
            exercises = backupDao.getAllExercises(),
            workoutPlans = backupDao.getAllWorkoutPlans(),
            planDayTemplates = backupDao.getAllPlanDayTemplates(),
            templateExercises = backupDao.getAllTemplateExercises(),
            sessions = backupDao.getAllSessions(),
            sessionExercises = backupDao.getAllSessionExercises(),
            loggedSets = backupDao.getAllLoggedSets(),
            weightUnit = settingsRepository.weightUnit.first(),
            theme = settingsRepository.themePreference.first(),
        )
        return BackupCodec.encode(snapshot, clock.instant(), appInfo)
    }

    override suspend fun parseImport(json: String): ParseResult {
        val result = BackupCodec.decode(json)
        return if (result is ParseResult.Ready && backupDao.getActiveSession() != null) {
            ParseResult.BlockedByLiveSession
        } else {
            result
        }
    }

    override suspend fun applyImport(parsed: ParsedBackup): ImportSummary {
        val snapshot = parsed as BackupSnapshot
        backupDao.replaceAll(snapshot)
        settingsRepository.setWeightUnit(snapshot.weightUnit)
        settingsRepository.setThemePreference(snapshot.theme)
        return ImportSummary(
            exportedAt = clock.instant(),
            sessions = snapshot.sessions.count { it.deletedAt == null },
            exercises = snapshot.exercises.count { it.deletedAt == null },
            sets = snapshot.loggedSets.count { it.deletedAt == null },
        )
    }
}
```

- [ ] **Step 2: Bind in `RepositoryModule.kt`** — add:

```kotlin
    @Binds abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
```
Add imports for `BackupRepositoryImpl` and `de.simiil.liftlog.domain.repository.BackupRepository`.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(backup): BackupRepositoryImpl + Hilt binding"
```

---

## Task 7: Instrumented round-trip + live-session block

The M5 exit-criterion automated test, on real SQLite.

**Files:**
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/data/backup/BackupRoundTripTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package de.simiil.liftlog.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.data.repository.BackupRepositoryImpl
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: AppDatabase

    // tiny in-file SettingsRepository fake (the unit-test fake lives in src/test, not visible here)
    private class FakeSettings(unit: WeightUnit, theme: ThemePreference) : SettingsRepository {
        val unitFlow = MutableStateFlow(unit)
        val themeFlow = MutableStateFlow(theme)
        override val themePreference: Flow<ThemePreference> = themeFlow
        override val weightUnit: Flow<WeightUnit> = unitFlow
        override suspend fun setThemePreference(preference: ThemePreference) { themeFlow.value = preference }
        override suspend fun setWeightUnit(unit: WeightUnit) { unitFlow.value = unit }
    }

    private val appInfo = AppInfo("LiftLog", "0.1.0", 1)
    private val clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() { db = newInMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun seed() = runTest {
        val dao = db.backupDao()
        dao.insertExercises(listOf(
            ExerciseEntity("ex1", "Bench", MuscleGroup.CHEST, Equipment.BARBELL, true, false, 1, 2, null),
            ExerciseEntity("ex2", "Hidden", MuscleGroup.BICEPS, Equipment.DUMBBELL, false, true, 1, 2, null),
            ExerciseEntity("ex3", "Tombstoned", MuscleGroup.BACK, Equipment.CABLE, false, false, 1, 9, 9), // soft-deleted
        ))
        dao.insertSessions(listOf(
            SessionEntity("s1", null, null, startedAt = 100, endedAt = 200, note = null,
                createdAt = 100, updatedAt = 200, deletedAt = null)))
        dao.insertSessionExercises(listOf(
            SessionExerciseEntity("se1", "s1", "ex1", 0, null, null, null, 100, 200, null)))
        dao.insertLoggedSets(listOf(
            LoggedSetEntity("ls1", "se1", 82.5, 5, 1, completedAt = 150, rpe = null, note = null,
                createdAt = 150, updatedAt = 150, deletedAt = null)))
    }

    @Test
    fun `export then wipe then import restores every row and the settings`() = runTest {
        seed()
        val dao = db.backupDao()
        val settings = FakeSettings(WeightUnit.LB, ThemePreference.DARK)
        val repo = BackupRepositoryImpl(dao, settings, clock, appInfo)

        val before = snapshotTables(dao)
        val json = repo.exportToJson()

        // wipe everything and scramble settings
        dao.deleteAllLoggedSets(); dao.deleteAllSessionExercises(); dao.deleteAllSessions()
        dao.deleteAllExercises()
        settings.unitFlow.value = WeightUnit.KG
        settings.themeFlow.value = ThemePreference.SYSTEM
        assertTrue(dao.getAllExercises().isEmpty())

        val parsed = repo.parseImport(json)
        assertTrue(parsed is ParseResult.Ready)
        repo.applyImport((parsed as ParseResult.Ready).parsed)

        assertEquals(before, snapshotTables(dao))            // row-for-row, incl. tombstone + hidden
        assertEquals(WeightUnit.LB, settings.unitFlow.value)  // settings restored
        assertEquals(ThemePreference.DARK, settings.themeFlow.value)
        assertEquals(2, parsed.summary.exercises)             // ex1 + ex2 live; ex3 tombstoned
    }

    @Test
    fun `import is blocked while a session is in progress`() = runTest {
        seed()
        val dao = db.backupDao()
        val settings = FakeSettings(WeightUnit.KG, ThemePreference.SYSTEM)
        val repo = BackupRepositoryImpl(dao, settings, clock, appInfo)
        val json = repo.exportToJson()

        dao.insertSessions(listOf(SessionEntity("live", null, null, startedAt = 300, endedAt = null,
            note = null, createdAt = 300, updatedAt = 300, deletedAt = null)))

        assertEquals(ParseResult.BlockedByLiveSession, repo.parseImport(json))
    }

    private suspend fun snapshotTables(dao: de.simiil.liftlog.data.dao.BackupDao) = listOf(
        dao.getAllExercises(), dao.getAllWorkoutPlans(), dao.getAllPlanDayTemplates(),
        dao.getAllTemplateExercises(), dao.getAllSessions(), dao.getAllSessionExercises(),
        dao.getAllLoggedSets(),
    )
}
```

- [ ] **Step 2: Run on the emulator** (scope to this class — the task fans out to every attached device)

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.backup.BackupRoundTripTest`
Expected: both tests PASS on `emulator-5554`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test(backup): instrumented round-trip + live-session block"
```

---

## Task 8: `DocumentIo` (SAF I/O)

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/ui/settings/DocumentIo.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/RepositoryModule.kt`

- [ ] **Step 1: Create `DocumentIo.kt`**

```kotlin
package de.simiil.liftlog.ui.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads/writes text to a SAF document Uri. Isolated so the ViewModel stays testable and the
 *  repository/domain never sees android.net.Uri. */
interface DocumentIo {
    suspend fun readText(uri: Uri): String
    suspend fun writeText(uri: Uri, text: String)
}

class AndroidDocumentIo @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentIo {
    override suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot open $uri for reading")
    }

    override suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot open $uri for writing")
    }
}
```

- [ ] **Step 2: Bind in `RepositoryModule.kt`** — add:

```kotlin
    @Binds abstract fun bindDocumentIo(impl: AndroidDocumentIo): DocumentIo
```
Add imports for `de.simiil.liftlog.ui.settings.AndroidDocumentIo` and `de.simiil.liftlog.ui.settings.DocumentIo`.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(backup): DocumentIo SAF read/write wrapper"
```

---

## Task 9: Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the strings** (place near the other `settings_*` entries):

```xml
    <string name="settings_data_label">Data</string>
    <string name="settings_export">Export data</string>
    <string name="settings_export_desc">Save a backup file</string>
    <string name="settings_import">Import data</string>
    <string name="settings_import_desc">Restore from a backup file</string>
    <string name="backup_exported">Backup exported</string>
    <string name="backup_export_failed">Couldn\'t export backup</string>
    <string name="backup_imported">Backup imported</string>
    <string name="backup_import_confirm_title">Replace all data?</string>
    <!-- %1$s = backup date, %2$d = sessions, %3$d = exercises -->
    <string name="backup_import_confirm_message">Replace all current data with the backup from %1$s? %2$d sessions · %3$d exercises. This can\'t be undone.</string>
    <string name="backup_import_confirm_button">Replace</string>
    <string name="backup_cancel">Cancel</string>
    <string name="backup_error_title">Couldn\'t import backup</string>
    <string name="backup_error_live_session">Finish or discard your active session before importing.</string>
    <string name="backup_error_newer">This backup was created by a newer version of LiftLog. Update the app, then try again.</string>
    <string name="backup_error_corrupt">This file isn\'t a valid LiftLog backup, or it\'s incomplete.</string>
    <string name="backup_dialog_ok">OK</string>
```

- [ ] **Step 2: Verify lint sees no missing/extra**

Run: `./gradlew :app:lintDebug`
Expected: no new `MissingTranslation`/`UnusedResources` errors. (All are referenced in Tasks 10–11.)

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(backup): strings for export/import UI"
```

---

## Task 10: `SettingsViewModel` orchestration + tests

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsViewModel.kt`
- Create: `app/src/test/kotlin/de/simiil/liftlog/testing/FakeBackupRepository.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Create `FakeBackupRepository.kt`**

```kotlin
package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import java.time.Instant

class FakeBackupRepository : BackupRepository {
    var exportJson: String = "{}"
    var parseResult: ParseResult = ParseResult.Invalid(de.simiil.liftlog.domain.repository.InvalidReason.MALFORMED)
    var appliedWith: ParsedBackup? = null
    val dummyParsed = object : ParsedBackup {}

    override suspend fun exportToJson(): String = exportJson
    override suspend fun parseImport(json: String): ParseResult = parseResult
    override suspend fun applyImport(parsed: ParsedBackup): ImportSummary {
        appliedWith = parsed
        return ImportSummary(Instant.parse("2026-06-09T12:00:00Z"), sessions = 1, exercises = 1, sets = 1)
    }
}
```

- [ ] **Step 2: Rewrite `SettingsViewModel.kt`**

```kotlin
package de.simiil.liftlog.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.BackupRepository
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.domain.repository.ParsedBackup
import de.simiil.liftlog.domain.repository.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage { EXPORTED, EXPORT_FAILED, IMPORTED }

sealed interface SettingsDialog {
    data object LiveSession : SettingsDialog
    data class Newer(val version: Int) : SettingsDialog
    data class Invalid(val reason: InvalidReason) : SettingsDialog
}

data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val pendingImport: ImportSummary? = null,
    val dialog: SettingsDialog? = null,
    val message: SettingsMessage? = null,
)

private data class Transient(
    val pendingParsed: ParsedBackup? = null,
    val pendingImport: ImportSummary? = null,
    val dialog: SettingsDialog? = null,
    val message: SettingsMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syntheticHistorySeeder: SyntheticHistorySeeder,
    private val backupRepository: BackupRepository,
    private val documentIo: DocumentIo,
    private val clock: Clock,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.themePreference, transient) { theme, t ->
            SettingsUiState(theme, t.pendingImport, t.dialog, t.message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onThemeSelected(preference: ThemePreference) {
        viewModelScope.launch { settingsRepository.setThemePreference(preference) }
    }

    fun seedDemoData() {
        viewModelScope.launch { syntheticHistorySeeder.seed() }
    }

    fun defaultExportFileName(): String =
        "liftlog-backup-${LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)}.json"

    fun export(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                documentIo.writeText(uri, backupRepository.exportToJson())
                SettingsMessage.EXPORTED
            } catch (e: Exception) {
                SettingsMessage.EXPORT_FAILED
            }
            transient.update { it.copy(message = message) }
        }
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            val json = try {
                documentIo.readText(uri)
            } catch (e: Exception) {
                handleParseResult(ParseResult.Invalid(InvalidReason.MALFORMED)); return@launch
            }
            handleParseResult(backupRepository.parseImport(json))
        }
    }

    /** Maps a parse result to dialog/confirm state. Public for unit testing (no Uri needed). */
    fun handleParseResult(result: ParseResult) {
        transient.update {
            when (result) {
                is ParseResult.Ready -> it.copy(pendingParsed = result.parsed, pendingImport = result.summary)
                ParseResult.BlockedByLiveSession -> it.copy(dialog = SettingsDialog.LiveSession)
                is ParseResult.Newer -> it.copy(dialog = SettingsDialog.Newer(result.fileVersion))
                is ParseResult.Invalid -> it.copy(dialog = SettingsDialog.Invalid(result.reason))
            }
        }
    }

    fun confirmImport() {
        val parsed = transient.value.pendingParsed ?: return
        viewModelScope.launch {
            backupRepository.applyImport(parsed)
            transient.update {
                it.copy(pendingParsed = null, pendingImport = null, message = SettingsMessage.IMPORTED)
            }
        }
    }

    fun dismissImport() { transient.update { it.copy(pendingParsed = null, pendingImport = null) } }
    fun dismissDialog() { transient.update { it.copy(dialog = null) } }
    fun consumeMessage() { transient.update { it.copy(message = null) } }
}
```

- [ ] **Step 3: Replace `SettingsViewModelTest.kt`** with the extended version

```kotlin
package de.simiil.liftlog.ui.settings

import app.cash.turbine.test
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.ImportSummary
import de.simiil.liftlog.domain.repository.InvalidReason
import de.simiil.liftlog.domain.repository.ParseResult
import de.simiil.liftlog.testing.FakeBackupRepository
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC)
    private fun noOpSeeder() = SyntheticHistorySeeder(FakeSessionDao(), Clock.systemUTC())

    private fun vm(backup: FakeBackupRepository = FakeBackupRepository()) = SettingsViewModel(
        settingsRepository = FakeSettingsRepository(),
        syntheticHistorySeeder = noOpSeeder(),
        backupRepository = backup,
        documentIo = object : DocumentIo {
            override suspend fun readText(uri: android.net.Uri) = error("unused")
            override suspend fun writeText(uri: android.net.Uri, text: String) = error("unused")
        },
        clock = clock,
    )

    @Test
    fun `selecting a theme persists it and updates ui state`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            assertEquals(ThemePreference.SYSTEM, awaitItem().theme)
            viewModel.onThemeSelected(ThemePreference.DARK)
            assertEquals(ThemePreference.DARK, awaitItem().theme)
        }
    }

    @Test
    fun `default export file name uses the clock date`() {
        assertEquals("liftlog-backup-2026-06-09.json", vm().defaultExportFileName())
    }

    @Test
    fun `Ready parse result shows the confirm summary`() {
        val viewModel = vm()
        val summary = ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 12, 5, 40)
        viewModel.handleParseResult(ParseResult.Ready(object : de.simiil.liftlog.domain.repository.ParsedBackup {}, summary))
        assertEquals(summary, viewModel.uiState.value.pendingImport)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `blocked parse result shows the live-session dialog`() {
        val viewModel = vm()
        viewModel.handleParseResult(ParseResult.BlockedByLiveSession)
        assertEquals(SettingsDialog.LiveSession, viewModel.uiState.value.dialog)
    }

    @Test
    fun `newer and invalid parse results show their dialogs`() {
        val viewModel = vm()
        viewModel.handleParseResult(ParseResult.Newer(2))
        assertEquals(SettingsDialog.Newer(2), viewModel.uiState.value.dialog)
        viewModel.dismissDialog()
        viewModel.handleParseResult(ParseResult.Invalid(InvalidReason.FK_ORPHAN))
        assertEquals(SettingsDialog.Invalid(InvalidReason.FK_ORPHAN), viewModel.uiState.value.dialog)
    }

    @Test
    fun `confirming an import applies it and emits the imported message`() = runTest {
        val backup = FakeBackupRepository()
        val viewModel = vm(backup)
        val summary = ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 1, 1, 1)
        viewModel.handleParseResult(ParseResult.Ready(backup.dummyParsed, summary))
        viewModel.confirmImport()
        assertEquals(backup.dummyParsed, backup.appliedWith)
        assertEquals(SettingsMessage.IMPORTED, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.pendingImport)
    }

    @Test
    fun `dismissing the confirm cancels without applying`() {
        val backup = FakeBackupRepository()
        val viewModel = vm(backup)
        viewModel.handleParseResult(ParseResult.Ready(backup.dummyParsed,
            ImportSummary(Instant.parse("2026-06-01T00:00:00Z"), 1, 1, 1)))
        viewModel.dismissImport()
        assertNull(viewModel.uiState.value.pendingImport)
        viewModel.confirmImport()
        assertNull(backup.appliedWith) // nothing applied
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "de.simiil.liftlog.ui.settings.SettingsViewModelTest"`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(settings): export/import orchestration in SettingsViewModel + tests"
```

---

## Task 11: Settings screen UI

Adds the Data section, SAF launchers, snackbar, and the confirm + error dialogs. Full replacement of `SettingsScreen.kt`.

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Replace `SettingsScreen.kt`**

```kotlin
package de.simiil.liftlog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.InvalidReason
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::prepareImport) }

    val exported = stringResource(R.string.backup_exported)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val imported = stringResource(R.string.backup_imported)
    LaunchedEffect(uiState.message) {
        val text = when (uiState.message) {
            SettingsMessage.EXPORTED -> exported
            SettingsMessage.EXPORT_FAILED -> exportFailed
            SettingsMessage.IMPORTED -> imported
            null -> null
        }
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.selectableGroup()) {
                SectionHeader(R.string.settings_theme_label)
                ThemeOptionRow(R.string.theme_system, ThemePreference.SYSTEM, uiState.theme, viewModel::onThemeSelected)
                ThemeOptionRow(R.string.theme_light, ThemePreference.LIGHT, uiState.theme, viewModel::onThemeSelected)
                ThemeOptionRow(R.string.theme_dark, ThemePreference.DARK, uiState.theme, viewModel::onThemeSelected)
            }

            SectionHeader(R.string.settings_data_label)
            ActionRow(
                icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                titleRes = R.string.settings_export,
                descRes = R.string.settings_export_desc,
                onClick = { exportLauncher.launch(viewModel.defaultExportFileName()) },
            )
            ActionRow(
                icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                titleRes = R.string.settings_import,
                descRes = R.string.settings_import_desc,
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )

            if (de.simiil.liftlog.BuildConfig.DEBUG) {
                TextButton(
                    onClick = viewModel::seedDemoData,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_seed_demo)) }
            }
        }
    }

    uiState.pendingImport?.let { summary ->
        val date = remember(summary) { DateFormat.getDateInstance().format(Date(summary.exportedAt.toEpochMilli())) }
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = {
                Text(stringResource(R.string.backup_import_confirm_message, date, summary.sessions, summary.exercises))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(stringResource(R.string.backup_import_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.backup_cancel)) }
            },
        )
    }

    uiState.dialog?.let { dialog ->
        val body = when (dialog) {
            SettingsDialog.LiveSession -> stringResource(R.string.backup_error_live_session)
            is SettingsDialog.Newer -> stringResource(R.string.backup_error_newer)
            is SettingsDialog.Invalid -> when (dialog.reason) {
                InvalidReason.MALFORMED, InvalidReason.MISSING_FIELDS, InvalidReason.BAD_TIMESTAMP,
                InvalidReason.FK_ORPHAN, InvalidReason.UNKNOWN_ENUM -> stringResource(R.string.backup_error_corrupt)
            }
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.backup_error_title)) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.backup_dialog_ok)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(@StringRes labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    @StringRes titleRes: Int,
    @StringRes descRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp) // comfortable tap target (a11y §7 floor is 48dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    @StringRes labelRes: Int,
    option: ThemePreference,
    currentSelection: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = option == currentSelection,
                onClick = { onSelect(option) },
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == currentSelection, onClick = null)
        Text(text = stringResource(labelRes), modifier = Modifier.padding(start = 12.dp))
    }
}
```

> **Icon note:** `Icons.Outlined.FileDownload`/`FileUpload` are in `androidx.compose.material:material-icons-extended` if the core set lacks them. If the build can't resolve them, substitute core icons already used in this codebase (e.g. `Icons.Outlined.Share` / `Icons.AutoMirrored.Outlined.ArrowBack` siblings) — confirm against what `material-icons-core` provides; do NOT add the extended-icons dependency just for these (constraint #5). A safe core fallback is `Icons.Default.KeyboardArrowDown` (export) / `Icons.Default.KeyboardArrowUp` (import) or omit the icon.

- [ ] **Step 2: Compile + lint**

Run: `./gradlew :app:compileDebugKotlin :app:lintDebug`
Expected: BUILD SUCCESSFUL; no unresolved icons; no lint errors.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(settings): Data section with export/import, confirm + error dialogs"
```

---

## Task 12: Full verification + device check

**Files:** none (verification only).

- [ ] **Step 1: Run exactly what CI runs**

Run: `./gradlew lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL; lint 0 errors; all unit tests green.

- [ ] **Step 2: Run the instrumented suite (scoped)**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.backup.BackupRoundTripTest`
Expected: PASS on `emulator-5554`.

- [ ] **Step 3: Manual device check** (`./gradlew installDebug`, launch, open Settings via the gear)

Verify on `emulator-5554`:
1. Seed demo data (debug button). Tap **Export data** → SAF create-document with default name `liftlog-backup-2026-06-09.json` → save → "Backup exported" snackbar.
2. Tap **Import data** → pick the file → **confirm dialog** shows the date + counts → Replace → "Backup imported" snackbar; History/Analytics still show the data.
3. Start a session (Home), leave it live, return to Settings → **Import data** → pick file → **error dialog** "Finish or discard your active session…". Discard the session, retry → confirm dialog appears.
4. Import a hand-corrupted file (edit a `}` out) → **error dialog** "…isn't a valid LiftLog backup".
5. Check **dark mode** (`adb shell cmd uimode night yes`) renders the rows/dialogs correctly.

- [ ] **Step 4: Final commit (if any fixes)**

```bash
git add -A && git commit -m "chore(backup): verification fixes"
```

---

## Self-review notes

- **Spec coverage:** DTOs/format → Task 3; pure codec + ISO↔millis + `ignoreUnknownKeys` → Task 4; validation table (malformed/missing/newer/bad-timestamp/FK-orphan/unknown-enum; lenient settings enums) → Task 4 tests; `BackupDao` snapshot incl. tombstones + atomic replace → Task 5; live-session block + settings restore → Tasks 6–7; two-step parse→confirm→apply → Tasks 6/10/11; SAF export/import + confirm + error dialogs → Tasks 8/11; golden-file + round-trip (exit criterion) → Tasks 4/7; `setWeightUnit` → Task 1. Zero new deps preserved (icon note in Task 11).
- **Type consistency:** `ParseResult`/`ParsedBackup`/`ImportSummary`/`InvalidReason` defined in Task 2 and used identically in Tasks 4/6/10/11; `BackupSnapshot` (Task 3) implements `ParsedBackup` and is the only cast target (Task 6 `applyImport`); `AppInfo` defined Task 3, provided Task 5, consumed Tasks 4/6/7; `DB_SCHEMA_VERSION` defined Task 5, used in the `@Database` annotation and `AppInfo`.
- **Known follow-ups (out of scope):** "pop to Home" after import is intentionally replaced by a Settings snackbar + reactive refresh (spec §UX resolution); kg/lb toggle UI, licenses/about, a11y audit, R8/signing/icon/app-name remain M5 PR2/PR3.
