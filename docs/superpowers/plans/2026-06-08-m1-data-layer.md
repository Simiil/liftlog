# M1 — Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The full Room schema, DAOs, domain models, repository conventions, and the built-in exercise seed — fully tested — before any feature UI depends on it (05-roadmap M1).

**Architecture:** Room is the single source of truth (01-architecture §1). Entities live in `data/`, pure domain models + repository interfaces in `domain/`, repository implementations bridge them. Sync-readiness conventions (UUID PKs, `createdAt`/`updatedAt` millis, `deletedAt` soft-delete tombstones, repository-orchestrated cascades) are baked into every syncable entity (02-data-spec §2).

**Tech Stack:** Room (+ KTX), KSP, Hilt, kotlinx.serialization (JSON seed asset), JUnit/Turbine/coroutines-test (local JVM), AndroidJUnit + room-testing on a Gradle-managed device (CI only).

---

## The hard constraint that shapes this plan: no local emulator/KVM

This machine has **no `/dev/kvm`** (CPU exposes `svm` but virtualization is not available), so neither an emulator nor a Gradle-managed device can run locally. **Instrumented (DAO) tests therefore run only in CI** — exactly as `CLAUDE.md` already states.

The whole testing architecture follows from this:

| Test layer | Where it runs | Verifies | Source set |
|---|---|---|---|
| Pure-logic JVM tests | **Local + CI**, fast | enums, mappers, repository orchestration (via fake DAOs), seed-asset shape | `app/src/test` |
| Instrumented Room tests | **CI only** | real-SQLite DAO `@Query` behavior, soft-delete `IS NULL` filtering, `@Relation` graph, seeder idempotency | `app/src/androidTest` |

**Implications for every task below:**
- Push as much behavior as possible behind **interfaces testable in plain JVM**. Repository impls depend on injected **DAO interfaces** and a **`Transactor`** seam, so cascade/active-session/uniqueness/timestamp logic is verified locally with hand-written fake DAOs.
- Instrumented tasks (8, the instrumented half of 12) are verified locally only by **compiling** the `androidTest` source (`./gradlew assembleDebugAndroidTest`); their semantics are confirmed by pushing and watching CI. The implementer must state this in the task's verification.
- The **first CI run with instrumented tests** is the real proof that Gradle-managed devices work on GitHub runners. See the flagged risk in §Decisions.

## Decisions flagged for owner review (milestone gate)

These are reasoned defaults made where the docs leave room; flagged per CLAUDE.md rather than blocking. Reject any at the gate and the change is localized.

1. **Repository scope at M1 = Exercise, Plan, Session (+ existing Settings).** `AnalyticsRepository` and `BackupRepository` interfaces are **not** created yet. Their consumers arrive at M4/M5 and would dictate signatures we'd otherwise guess and rewrite (YAGNI). The seam guarantee still holds — ViewModels already see only `domain/` interfaces, so adding two more later is mechanical. The **DAOs** they need (`AnalyticsDao`, `PrefillDao` building blocks) *are* built and tested now, per the roadmap's "all DAOs per §3–4."
2. **`PrefillDao` ships indexed building-block queries, not the `Flow` wrapper.** 02-data-spec §4 sketches `observeLastPerformance(): Flow`. The reactive composition is genuinely M2 work (its only consumer is the M2 logging pre-fill). M1 ships the two indexed lookups it will compose from, and instrumented-tests them as the hot path. Flagged so the deviation from the sketch is explicit.
3. **`Transactor` seam for transactional cascades.** Cascades are "the repository's job" (02-data-spec §3) and must be atomic. Rather than make cascade logic instrumented-only, repositories depend on a tiny `Transactor` interface (`RoomTransactor` wraps `db.withTransaction`). Fakes run the block inline, so cascade orchestration is unit-tested locally. Justified directly by the no-KVM constraint.
4. **Injected `java.time.Clock`** for `createdAt`/`updatedAt`. Lets JVM tests assert exact timestamps; prod binds `Clock.systemUTC()`.
5. **Enum storage via Room `@TypeConverter`** holding the enum's `name`. Unknown/corrupt values fall back (`MuscleGroup`→`OTHER`, `Equipment`→`MACHINE`) mirroring `ThemePreference.fromStorageValue`. Lossy fallback only fires on corruption/future-version data; the real gate is M5 import validation.
6. **FK columns are all indexed**, including `plan_day_templates.planId` (not in the §3 hot-path index list but required to silence Room's un-indexed-FK warning and good practice).
7. **Built-in library = 69 exercises** (Appendix A counted exactly; "~70" in the spec). The seed test asserts `== 69` so any future change is deliberate.
8. **Built-in UUIDs are frozen on merge.** The 69 fixed UUIDs generated in Task 11 become permanent identifiers shipped to every install and referenced by every export (02-data-spec §7). Never regenerate them after merge.
9. **GMD on CI is the first risk to watch.** We use an `aosp-atd` Gradle-managed device (`pixelApi34DebugAndroidTest`) per 01-architecture §8. If it proves flaky/slow on GitHub runners, the documented fallback is the `reactivecircus/android-emulator-runner` action. Decided not to pre-empt; watch the first instrumented CI run.

## File structure (created/modified across all tasks)

```
gradle/libs.versions.toml                         (M) Room, serialization-json, instrumented test deps
app/build.gradle.kts                              (M) Room/KSP, testInstrumentationRunner, GMD, schemaLocation
app/schemas/de.simiil.liftlog.AppDatabase/1.json  (C) exported Room schema (generated)
.github/workflows/ci.yml                          (M) instrumented job
CLAUDE.md                                         (M) instrumented build command

app/src/main/kotlin/de/simiil/liftlog/
  domain/model/
    MuscleGroup.kt  Equipment.kt                  (C) enums + fromStorageValue
    Exercise.kt WorkoutPlan.kt PlanDayTemplate.kt
    TemplateExercise.kt Session.kt SessionExercise.kt
    LoggedSet.kt SessionWithDetails.kt            (C) pure domain models (Instant timestamps)
  domain/repository/
    ExerciseRepository.kt PlanRepository.kt
    SessionRepository.kt                          (C) interfaces
  data/entity/*Entity.kt                          (C) 7 Room entities
  data/db/
    Converters.kt                                 (C) enum type converters
    AppDatabase.kt                                (C) @Database
    Transactor.kt                                 (C) transaction seam + RoomTransactor
  data/dao/
    ExerciseDao.kt PlanDao.kt SessionDao.kt
    AnalyticsDao.kt PrefillDao.kt                 (C) DAOs
    Relations.kt                                  (C) @Relation POJOs + SetRow
  data/mapper/Mappers.kt                          (C) entity <-> domain
  data/repository/
    ExerciseRepositoryImpl.kt PlanRepositoryImpl.kt
    SessionRepositoryImpl.kt                      (C) impls
  data/seed/
    ExerciseSeeder.kt SeedModels.kt               (C) seeder + @Serializable DTOs
  di/
    DatabaseModule.kt                             (C) db, DAOs, Clock, Transactor, Json, app scope
    RepositoryModule.kt                           (M) bind new repositories
    ApplicationScope.kt                           (C) @Qualifier
  LiftLogApplication.kt                           (M) run seeder on startup

app/src/main/assets/seed/exercises.v1.json        (C) 69 built-ins, fixed UUIDs

app/src/test/kotlin/de/simiil/liftlog/            (JVM, local)
  domain/model/EnumFallbackTest.kt
  data/mapper/MapperRoundTripTest.kt
  data/repository/{Exercise,Plan,Session}RepositoryTest.kt
  data/seed/SeedAssetTest.kt
  testing/fakes/Fake*Dao.kt  FakeTransactor.kt  FixedClock helper

app/src/androidTest/kotlin/de/simiil/liftlog/     (instrumented, CI)
  data/dao/{Exercise,Plan,Session,Analytics,Prefill}DaoTest.kt
  data/seed/ExerciseSeederTest.kt
  testing/DbRule.kt (in-memory AppDatabase helper)
```

---

## Task 1: Build setup — Room, serialization-json, instrumented deps, GMD

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions + libraries to the catalog**

In `[versions]` add (verify each resolves; bump to latest compatible patch if newer exists):
```toml
room = "2.7.2"
androidxTestJunit = "1.2.1"
androidxTestRunner = "1.6.2"
androidxTestCore = "1.6.1"
```
In `[libraries]` add:
```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestJunit" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
```

- [ ] **Step 2: Wire Room/KSP, GMD, schema export, and test deps in `app/build.gradle.kts`**

In `defaultConfig`, add:
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```
Add a `ksp { }` block (top-level in the file, after the `kotlin { }` block) for Room schema export:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
```
Inside `android { }`, add the Gradle-managed device:
```kotlin
testOptions {
    managedDevices {
        localDevices {
            create("pixelApi34") {
                device = "Pixel 6"
                apiLevel = 34
                systemImageSource = "aosp-atd" // headless, CI-friendly
            }
        }
    }
}
```
In `dependencies { }`, add:
```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.kotlinx.serialization.json)

androidTestImplementation(libs.androidx.test.ext.junit)
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.test.core.ktx)
androidTestImplementation(libs.androidx.room.testing)
androidTestImplementation(libs.kotlinx.coroutines.test)
androidTestImplementation(libs.turbine)
```

- [ ] **Step 3: Verify the build still configures and assembles (no usages yet)**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (dependencies resolve; nothing uses Room yet).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "M1: add Room, serialization-json, instrumented test deps, GMD"
```

---

## Task 2: Domain enums

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/model/MuscleGroup.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/model/Equipment.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/domain/model/EnumFallbackTest.kt`

- [ ] **Step 1: Write the failing test**
```kotlin
package de.simiil.liftlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumFallbackTest {
    @Test fun muscleGroup_knownValue_parses() =
        assertEquals(MuscleGroup.CHEST, MuscleGroup.fromStorageValue("CHEST"))

    @Test fun muscleGroup_unknownOrNull_fallsBackToOther() {
        assertEquals(MuscleGroup.OTHER, MuscleGroup.fromStorageValue("KETTLE_TOSS"))
        assertEquals(MuscleGroup.OTHER, MuscleGroup.fromStorageValue(null))
    }

    @Test fun equipment_knownValue_parses() =
        assertEquals(Equipment.BARBELL, Equipment.fromStorageValue("BARBELL"))

    @Test fun equipment_unknownOrNull_fallsBackToMachine() {
        assertEquals(Equipment.MACHINE, Equipment.fromStorageValue("RESISTANCE_BAND"))
        assertEquals(Equipment.MACHINE, Equipment.fromStorageValue(null))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests "*EnumFallbackTest"` → unresolved references.

- [ ] **Step 3: Implement**
```kotlin
// MuscleGroup.kt
package de.simiil.liftlog.domain.model

/** Exercise classification (02-data-spec §3). */
enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS,
    HAMSTRINGS, GLUTES, CALVES, ABS, FOREARMS, OTHER;

    companion object {
        /** Unknown/absent persisted values fall back to [OTHER] (corruption/future-version safety). */
        fun fromStorageValue(value: String?): MuscleGroup =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
```
```kotlin
// Equipment.kt
package de.simiil.liftlog.domain.model

/** Equipment classification (02-data-spec §3). */
enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT;

    companion object {
        /** Unknown/absent → [MACHINE] (least-specific generic bucket; real gate is M5 import validation). */
        fun fromStorageValue(value: String?): Equipment =
            entries.firstOrNull { it.name == value } ?: MACHINE
    }
}
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `M1: domain enums MuscleGroup/Equipment`

---

## Task 3: Pure domain models

**Files:**
- Create: `domain/model/Exercise.kt`, `WorkoutPlan.kt`, `PlanDayTemplate.kt`, `TemplateExercise.kt`, `Session.kt`, `SessionExercise.kt`, `LoggedSet.kt`, `SessionWithDetails.kt`

No Android imports (01-architecture §1). Timestamps are `java.time.Instant` (02-data-spec §2). These are plain data classes with no logic, so they're exercised by the mapper tests in Task 5 rather than a standalone test here.

- [ ] **Step 1: Create `Exercise.kt` (full reference for the field pattern)**
```kotlin
package de.simiil.liftlog.domain.model

import java.time.Instant

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
```

- [ ] **Step 2: Create the remaining models** with exactly these fields (all `createdAt: Instant, updatedAt: Instant, deletedAt: Instant?` appended, matching 02-data-spec §3):

```kotlin
// WorkoutPlan.kt
data class WorkoutPlan(val id: String, val name: String, val position: Int,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)

// PlanDayTemplate.kt
data class PlanDayTemplate(val id: String, val planId: String, val name: String, val position: Int,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)

// TemplateExercise.kt
data class TemplateExercise(val id: String, val templateId: String, val exerciseId: String,
    val position: Int, val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)

// Session.kt
data class Session(val id: String, val templateId: String?, val templateNameSnapshot: String?,
    val startedAt: Instant, val endedAt: Instant?, val note: String?,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)

// SessionExercise.kt
data class SessionExercise(val id: String, val sessionId: String, val exerciseId: String,
    val position: Int, val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)

// LoggedSet.kt
data class LoggedSet(val id: String, val sessionExerciseId: String, val weightKg: Double,
    val reps: Int, val position: Int, val completedAt: Instant, val rpe: Double?, val note: String?,
    val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant?)
```
(Each in its own file, `package de.simiil.liftlog.domain.model`, `import java.time.Instant`.)

- [ ] **Step 3: Create the read aggregates `SessionWithDetails.kt`**
```kotlin
package de.simiil.liftlog.domain.model

/** Session detail/history read model — children are already live-filtered + position-sorted by the mapper. */
data class SessionWithDetails(
    val session: Session,
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    val sessionExercise: SessionExercise,
    val sets: List<LoggedSet>,
)
```

- [ ] **Step 4: Compile** — `./gradlew compileDebugKotlin` → SUCCESS.
- [ ] **Step 5: Commit** — `M1: pure domain models`

---

## Task 4: Room entities + enum converters

**Files:**
- Create: `data/entity/ExerciseEntity.kt`, `WorkoutPlanEntity.kt`, `PlanDayTemplateEntity.kt`, `TemplateExerciseEntity.kt`, `SessionEntity.kt`, `SessionExerciseEntity.kt`, `LoggedSetEntity.kt`
- Create: `data/db/Converters.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/db/ConvertersTest.kt`

- [ ] **Step 1: Write the failing Converters test (JVM, local)**
```kotlin
package de.simiil.liftlog.data.db

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun muscleGroup_roundTrips() {
        MuscleGroup.entries.forEach { assertEquals(it, c.toMuscleGroup(c.fromMuscleGroup(it))) }
    }
    @Test fun equipment_roundTrips() {
        Equipment.entries.forEach { assertEquals(it, c.toEquipment(c.fromEquipment(it))) }
    }
    @Test fun unknownStrings_fallBack() {
        assertEquals(MuscleGroup.OTHER, c.toMuscleGroup("???"))
        assertEquals(Equipment.MACHINE, c.toEquipment("???"))
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement `Converters.kt`**
```kotlin
package de.simiil.liftlog.data.db

import androidx.room.TypeConverter
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup

/** Enums persist as their [Enum.name]; unknown strings fall back (see enum docs). */
class Converters {
    @TypeConverter fun fromMuscleGroup(value: MuscleGroup): String = value.name
    @TypeConverter fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.fromStorageValue(value)
    @TypeConverter fun fromEquipment(value: Equipment): String = value.name
    @TypeConverter fun toEquipment(value: String): Equipment = Equipment.fromStorageValue(value)
}
```

- [ ] **Step 4: Implement the 7 entities** (suffix `*Entity`, package `de.simiil.liftlog.data.entity`). All FKs use the default `onDelete = NO_ACTION` (deletes are soft; cascades are the repository's job — 02-data-spec §3). All FK child columns are indexed.

```kotlin
// ExerciseEntity.kt
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// WorkoutPlanEntity.kt
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey val id: String, val name: String, val position: Int,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// PlanDayTemplateEntity.kt
@Entity(
    tableName = "plan_day_templates",
    foreignKeys = [ForeignKey(WorkoutPlanEntity::class, ["id"], ["planId"])],
    indices = [Index("planId")],
)
data class PlanDayTemplateEntity(
    @PrimaryKey val id: String, val planId: String, val name: String, val position: Int,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// TemplateExerciseEntity.kt
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(PlanDayTemplateEntity::class, ["id"], ["templateId"]),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"]),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey val id: String, val templateId: String, val exerciseId: String, val position: Int,
    val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// SessionEntity.kt
@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(PlanDayTemplateEntity::class, ["id"], ["templateId"])],
    indices = [Index("startedAt"), Index("templateId")],
)
data class SessionEntity(
    @PrimaryKey val id: String, val templateId: String?, val templateNameSnapshot: String?,
    val startedAt: Long, val endedAt: Long?, val note: String?,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// SessionExerciseEntity.kt
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"]),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"]),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String, val sessionId: String, val exerciseId: String, val position: Int,
    val targetSets: Int?, val targetRepsMin: Int?, val targetRepsMax: Int?,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)

// LoggedSetEntity.kt
@Entity(
    tableName = "logged_sets",
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"])],
    indices = [Index("sessionExerciseId")],
)
data class LoggedSetEntity(
    @PrimaryKey val id: String, val sessionExerciseId: String, val weightKg: Double,
    val reps: Int, val position: Int, val completedAt: Long, val rpe: Double?, val note: String?,
    val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
)
```
Imports per file: `androidx.room.Entity`, `androidx.room.PrimaryKey`, `androidx.room.ForeignKey`, `androidx.room.Index`, and the enums from `domain.model`. (`ForeignKey(X::class, ["id"], ["col"])` is positional: entity, parentColumns, childColumns; `onDelete` defaults to `NO_ACTION`.)

- [ ] **Step 5: Run ConvertersTest, expect PASS.** Compile: `./gradlew compileDebugKotlin` → SUCCESS.
- [ ] **Step 6: Commit** — `M1: Room entities + enum type converters`

---

## Task 5: Entity <-> domain mappers

**Files:**
- Create: `data/mapper/Mappers.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/mapper/MapperRoundTripTest.kt`

Pure functions converting `Long` epoch-millis ⇄ `Instant`. Local JVM test.

- [ ] **Step 1: Write the failing round-trip test**
```kotlin
package de.simiil.liftlog.data.mapper

import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class MapperRoundTripTest {
    @Test fun exercise_entity_to_domain_to_entity_isIdentity() {
        val e = ExerciseEntity("id1", "Bench", MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = false, createdAt = 1_000, updatedAt = 2_000, deletedAt = null)
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test fun loggedSet_nullableTimestamps_roundTrip() {
        val s = LoggedSetEntity("s1", "se1", 82.5, 5, 1, completedAt = 3_000,
            rpe = 8.0, note = null, createdAt = 3_000, updatedAt = 3_000, deletedAt = 4_000)
        assertEquals(s, s.toDomain().toEntity())
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement `Mappers.kt`** — one `toDomain()`/`toEntity()` pair per entity. Pattern (apply to all 7):
```kotlin
package de.simiil.liftlog.data.mapper

import de.simiil.liftlog.data.entity.*
import de.simiil.liftlog.domain.model.*
import java.time.Instant

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
private fun Long?.toInstantOrNull(): Instant? = this?.let(Instant::ofEpochMilli)
private fun Instant.toMillis(): Long = toEpochMilli()
private fun Instant?.toMillisOrNull(): Long? = this?.toEpochMilli()

fun ExerciseEntity.toDomain() = Exercise(id, name, muscleGroup, equipment, isBuiltIn, isHidden,
    createdAt.toInstant(), updatedAt.toInstant(), deletedAt.toInstantOrNull())
fun Exercise.toEntity() = ExerciseEntity(id, name, muscleGroup, equipment, isBuiltIn, isHidden,
    createdAt.toMillis(), updatedAt.toMillis(), deletedAt.toMillisOrNull())
// ... repeat for WorkoutPlan, PlanDayTemplate, TemplateExercise, Session, SessionExercise, LoggedSet
```
Implement all 7 pairs (each is a direct field copy with the timestamp helpers above; `weightKg`/`reps`/`position`/`rpe`/`note`/targets copy verbatim).

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `M1: entity<->domain mappers`

---

## Task 6: DAOs + @Relation POJOs

**Files:**
- Create: `data/dao/ExerciseDao.kt`, `PlanDao.kt`, `SessionDao.kt`, `AnalyticsDao.kt`, `PrefillDao.kt`, `Relations.kt`

No tests in this task — DAO behavior is verified by the instrumented suite (Task 8). This task ends at **compile** (`compileDebugKotlin`), which runs Room's annotation processor and fails on any malformed `@Query`/relation — a real correctness gate even without a device.

- [ ] **Step 1: `Relations.kt`** (@Relation graph for session detail + the analytics projection row)
```kotlin
package de.simiil.liftlog.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity

// NOTE: @Relation cannot filter deletedAt; tombstoned children are loaded then dropped + sorted in the mapper (Task 9).
data class SessionWithDetailsRelation(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSetsRelation>,
)

data class SessionExerciseWithSetsRelation(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<LoggedSetEntity>,
)

/** Analytics projection (02-data-spec §4): set-level rows; e1RM math stays in pure Kotlin (M4). */
data class SetRow(val sessionId: String, val startedAt: Long, val weightKg: Double, val reps: Int)
```

- [ ] **Step 2: `ExerciseDao.kt`**
```kotlin
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND isHidden = 0 ORDER BY name COLLATE NOCASE")
    fun observeVisible(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findLiveByName(name: String): ExerciseEntity?

    /** Seeder idempotency: existing PKs are left untouched (hides/tombstones survive). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercises: List<ExerciseEntity>)

    @Insert suspend fun insert(exercise: ExerciseEntity)
    @Update suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT COUNT(*) FROM exercises WHERE deletedAt IS NULL") suspend fun countLive(): Int
}
```

- [ ] **Step 3: `PlanDao.kt`** (CRUD + cascade helpers; timestamps supplied by the repository)
```kotlin
@Dao
interface PlanDao {
    @Query("SELECT * FROM workout_plans WHERE deletedAt IS NULL ORDER BY position")
    fun observePlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE id = :id AND deletedAt IS NULL")
    suspend fun findPlan(id: String): WorkoutPlanEntity?

    @Insert suspend fun insertPlan(plan: WorkoutPlanEntity)
    @Update suspend fun updatePlan(plan: WorkoutPlanEntity)
    @Insert suspend fun insertDayTemplate(template: PlanDayTemplateEntity)
    @Insert suspend fun insertTemplateExercise(templateExercise: TemplateExerciseEntity)

    @Query("SELECT * FROM plan_day_templates WHERE planId = :planId AND deletedAt IS NULL ORDER BY position")
    suspend fun dayTemplatesForPlan(planId: String): List<PlanDayTemplateEntity>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId AND deletedAt IS NULL ORDER BY position")
    suspend fun templateExercisesFor(templateId: String): List<TemplateExerciseEntity>

    @Query("UPDATE workout_plans SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeletePlan(id: String, now: Long)

    @Query("UPDATE plan_day_templates SET deletedAt = :now, updatedAt = :now WHERE planId = :planId AND deletedAt IS NULL")
    suspend fun softDeleteDayTemplatesForPlan(planId: String, now: Long)

    @Query(
        """UPDATE template_exercises SET deletedAt = :now, updatedAt = :now
           WHERE deletedAt IS NULL
             AND templateId IN (SELECT id FROM plan_day_templates WHERE planId = :planId)"""
    )
    suspend fun softDeleteTemplateExercisesForPlan(planId: String, now: Long)
}
```

- [ ] **Step 4: `SessionDao.kt`** (active session, history, detail relation, cascade helpers)
```kotlin
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT id FROM sessions WHERE endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun activeSessionId(): String?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL")
    fun observeSessionWithDetails(id: String): Flow<SessionWithDetailsRelation?>

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSession(id: String): SessionEntity?

    @Insert suspend fun insertSession(session: SessionEntity)
    @Update suspend fun updateSession(session: SessionEntity)
    @Insert suspend fun insertSessionExercise(sessionExercise: SessionExerciseEntity)
    @Insert suspend fun insertLoggedSet(loggedSet: LoggedSetEntity)

    @Query("UPDATE sessions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSession(id: String, now: Long)

    @Query("UPDATE session_exercises SET deletedAt = :now, updatedAt = :now WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun softDeleteSessionExercisesFor(sessionId: String, now: Long)

    @Query(
        """UPDATE logged_sets SET deletedAt = :now, updatedAt = :now
           WHERE deletedAt IS NULL
             AND sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :sessionId)"""
    )
    suspend fun softDeleteLoggedSetsForSession(sessionId: String, now: Long)
}
```

- [ ] **Step 5: `AnalyticsDao.kt`** (verbatim from 02-data-spec §4)
```kotlin
@Dao
interface AnalyticsDao {
    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS startedAt, ls.weightKg AS weightKg, ls.reps AS reps
           FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId AND se.deletedAt IS NULL
           JOIN sessions s          ON s.id = se.sessionId          AND s.deletedAt IS NULL
           WHERE se.exerciseId = :exerciseId AND ls.deletedAt IS NULL
             AND s.startedAt >= :fromMillis AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt"""
    )
    fun observeSetsForExercise(exerciseId: String, fromMillis: Long): Flow<List<SetRow>>
}
```

- [ ] **Step 6: `PrefillDao.kt`** (indexed building blocks; the `Flow` wrapper is M2 — flagged decision #2)
```kotlin
@Dao
interface PrefillDao {
    /** Most recent COMPLETED, live session containing this exercise (03-ux-spec §4 pre-fill source). */
    @Query(
        """SELECT s.id FROM sessions s
           JOIN session_exercises se ON se.sessionId = s.id AND se.deletedAt IS NULL
           WHERE se.exerciseId = :exerciseId AND s.deletedAt IS NULL AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt DESC LIMIT 1"""
    )
    suspend fun lastCompletedSessionIdFor(exerciseId: String): String?

    @Query(
        """SELECT ls.* FROM logged_sets ls
           JOIN session_exercises se ON se.id = ls.sessionExerciseId
           WHERE se.sessionId = :sessionId AND se.exerciseId = :exerciseId
             AND ls.deletedAt IS NULL AND se.deletedAt IS NULL
           ORDER BY ls.position"""
    )
    suspend fun setsForExerciseInSession(sessionId: String, exerciseId: String): List<LoggedSetEntity>
}
```

Imports for each DAO: `androidx.room.*` (`Dao`, `Query`, `Insert`, `Update`, `Transaction`, `OnConflictStrategy`), `kotlinx.coroutines.flow.Flow`, the entities, and the relation POJOs.

- [ ] **Step 7: Compile (runs Room processor)** — `./gradlew compileDebugKotlin` → SUCCESS (no Room query/relation errors).
- [ ] **Step 8: Commit** — `M1: DAOs + @Relation POJOs`

---

## Task 7: AppDatabase + Hilt DatabaseModule

**Files:**
- Create: `data/db/AppDatabase.kt`, `data/db/Transactor.kt`, `di/ApplicationScope.kt`, `di/DatabaseModule.kt`

- [ ] **Step 1: `AppDatabase.kt`**
```kotlin
package de.simiil.liftlog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.simiil.liftlog.data.dao.*
import de.simiil.liftlog.data.entity.*

@Database(
    entities = [
        ExerciseEntity::class, WorkoutPlanEntity::class, PlanDayTemplateEntity::class,
        TemplateExerciseEntity::class, SessionEntity::class, SessionExerciseEntity::class,
        LoggedSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDao(): PlanDao
    abstract fun sessionDao(): SessionDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun prefillDao(): PrefillDao
}
```

- [ ] **Step 2: `Transactor.kt`** (testability seam — flagged decision #3)
```kotlin
package de.simiil.liftlog.data.db

import androidx.room.withTransaction

/** Atomic multi-DAO unit of work. Fakes run [block] inline so cascade logic is JVM-testable. */
interface Transactor {
    suspend fun <R> immediate(block: suspend () -> R): R
}

class RoomTransactor(private val db: AppDatabase) : Transactor {
    override suspend fun <R> immediate(block: suspend () -> R): R = db.withTransaction { block() }
}
```

- [ ] **Step 3: `ApplicationScope.kt`**
```kotlin
package de.simiil.liftlog.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

- [ ] **Step 4: `DatabaseModule.kt`** (db, DAOs, Clock, Transactor, Json, app scope)
```kotlin
package de.simiil.liftlog.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.simiil.liftlog.data.dao.*
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.db.Transactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "liftlog.db").build()

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun providePlanDao(db: AppDatabase): PlanDao = db.planDao()
    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
    @Provides fun provideAnalyticsDao(db: AppDatabase): AnalyticsDao = db.analyticsDao()
    @Provides fun providePrefillDao(db: AppDatabase): PrefillDao = db.prefillDao()

    @Provides @Singleton fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
    @Provides @Singleton fun provideClock(): Clock = Clock.systemUTC()
    @Provides @Singleton fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

- [ ] **Step 5: Build (generates Room impl + exports schema)** — `./gradlew assembleDebug` → SUCCESS; confirm `app/schemas/de.simiil.liftlog.AppDatabase/1.json` was generated.
- [ ] **Step 6: Commit** (include the generated schema) — `M1: AppDatabase, Transactor, DatabaseModule (schema v1)`

---

## Task 8: Instrumented DAO tests (CI-verified)

**Files:**
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/testing/DbRule.kt`
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/data/dao/ExerciseDaoTest.kt`, `PlanDaoTest.kt`, `SessionDaoTest.kt`, `AnalyticsDaoTest.kt`, `PrefillDaoTest.kt`

> **Verification note for the implementer:** there is no local emulator. Verify these by **compiling** (`./gradlew assembleDebugAndroidTest` → SUCCESS) and reasoning through each assertion. They are *executed* in CI (Task 13). Mark the task DONE_WITH_CONCERNS noting "instrumented tests not run locally; rely on CI."

- [ ] **Step 1: In-memory DB helper `DbRule.kt`**
```kotlin
package de.simiil.liftlog.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.simiil.liftlog.data.db.AppDatabase

fun newInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

- [ ] **Step 2: `ExerciseDaoTest.kt`** — assert: `observeAll`/`observeVisible` exclude `deletedAt != null` and (for visible) `isHidden = 1`; `findLiveByName` is case-insensitive; `insertIgnore` skips an existing PK without overwriting a row whose `isHidden` was flipped; `countLive` excludes tombstones; ordering is `name COLLATE NOCASE`. Use `runTest`; collect Flows with Turbine `.first()` or `.test {}`.

```kotlin
package de.simiil.liftlog.data.dao

import app.cash.turbine.test
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var dao: ExerciseDao

    private fun ex(id: String, name: String, hidden: Boolean = false, deleted: Long? = null) =
        ExerciseEntity(id, name, MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = hidden, createdAt = 1, updatedAt = 1, deletedAt = deleted)

    @Before fun setUp() { db = newInMemoryDb(); dao = db.exerciseDao() }
    @After fun tearDown() = db.close()

    @Test fun observeVisible_excludesHiddenAndDeleted() = runTest {
        dao.insert(ex("1", "Bench"))
        dao.insert(ex("2", "Hidden", hidden = true))
        dao.insert(ex("3", "Gone", deleted = 99))
        dao.observeVisible().test {
            assertEquals(listOf("Bench"), awaitItem().map { it.name }); cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun findLiveByName_isCaseInsensitive() = runTest {
        dao.insert(ex("1", "Barbell Bench Press"))
        assertNotNull(dao.findLiveByName("barbell bench press"))
    }

    @Test fun insertIgnore_doesNotOverwriteExisting() = runTest {
        dao.insert(ex("1", "Bench", hidden = true))
        dao.insertIgnore(listOf(ex("1", "Bench", hidden = false)))
        assertTrue(dao.findById("1")!!.isHidden) // unchanged
    }
}
```

- [ ] **Step 3: `SessionDaoTest.kt`** — assert: `observeActiveSession` returns the single `endedAt IS NULL` live row and `null` once ended; `activeSessionId` matches; `observeHistory` is `startedAt DESC` and excludes tombstones; `observeSessionWithDetails` loads the full graph (session → exercises → sets); the three cascade `UPDATE`s tombstone exactly the right rows. Seed a small graph with `insertSession`/`insertSessionExercise`/`insertLoggedSet`. (FK rows must exist before children; satisfy `exerciseId`/`templateId` with inserted parents or `templateId = null`.)

- [ ] **Step 4: `PlanDaoTest.kt`** — assert: `observePlans` is `position`-ordered, excludes tombstones; the cascade `UPDATE`s soft-delete day templates and their template-exercises for a plan; child reads exclude tombstones.

- [ ] **Step 5: `AnalyticsDaoTest.kt`** — assert `observeSetsForExercise` returns only sets from **live, completed** (`endedAt IS NOT NULL`) sessions at/after `fromMillis`, excludes soft-deleted sets/exercises/sessions, and orders by `startedAt`.

- [ ] **Step 6: `PrefillDaoTest.kt`** — assert `lastCompletedSessionIdFor` picks the most recent completed session containing the exercise (ignoring in-progress and tombstoned), and `setsForExerciseInSession` returns that session's sets for that exercise in `position` order.

- [ ] **Step 7: Verify by compiling** — `./gradlew assembleDebugAndroidTest` → SUCCESS.
- [ ] **Step 8: Commit** — `M1: instrumented DAO tests (CI-run)`

---

## Task 9: Repository interfaces + implementations

**Files:**
- Create: `domain/repository/ExerciseRepository.kt`, `PlanRepository.kt`, `SessionRepository.kt`
- Create: `data/repository/ExerciseRepositoryImpl.kt`, `PlanRepositoryImpl.kt`, `SessionRepositoryImpl.kt`
- Create: in `data/mapper/Mappers.kt` add `SessionWithDetailsRelation.toDomain()` (live-filter + sort)
- Modify: `di/RepositoryModule.kt`

Scope per flagged decision #1: only these three (Analytics/Backup deferred). Methods are the minimum to satisfy M1 exit criteria + the seeder; logging/editing methods grow at M2/M3.

- [ ] **Step 1: Interfaces (domain/, pure Kotlin)**
```kotlin
// ExerciseRepository.kt
interface ExerciseRepository {
    fun observeAll(): Flow<List<Exercise>>
    fun observeVisible(): Flow<List<Exercise>>
    /** Creates a custom (non-built-in) exercise. Throws IllegalArgumentException on blank or duplicate (case-insensitive live) name. */
    suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Exercise
    suspend fun setHidden(id: String, hidden: Boolean)
}

// PlanRepository.kt
interface PlanRepository {
    fun observePlans(): Flow<List<WorkoutPlan>>
    suspend fun createPlan(name: String): WorkoutPlan
    /** Soft-deletes the plan and cascades to its day templates and their template-exercises (atomic). */
    suspend fun softDeletePlan(id: String)
}

// SessionRepository.kt
interface SessionRepository {
    fun observeActiveSession(): Flow<Session?>
    fun observeHistory(): Flow<List<Session>>
    fun observeSessionDetails(id: String): Flow<SessionWithDetails?>
    /** Starts an empty ad-hoc session. Throws IllegalStateException if one is already in progress (single live endedAt IS NULL). */
    suspend fun startEmptySession(): Session
    suspend fun finishSession(id: String)
    /** Soft-deletes the session and cascades to session_exercises and logged_sets (atomic). */
    suspend fun softDeleteSession(id: String)
}
```

- [ ] **Step 2: Add the relation mapper to `Mappers.kt`** (live-filter tombstones + sort by position)
```kotlin
fun SessionWithDetailsRelation.toDomain() = SessionWithDetails(
    session = session.toDomain(),
    exercises = exercises
        .filter { it.sessionExercise.deletedAt == null }
        .sortedBy { it.sessionExercise.position }
        .map { se ->
            SessionExerciseWithSets(
                sessionExercise = se.sessionExercise.toDomain(),
                sets = se.sets.filter { it.deletedAt == null }.sortedBy { it.position }.map { it.toDomain() },
            )
        },
)
```

- [ ] **Step 3: `ExerciseRepositoryImpl.kt`**
```kotlin
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeAll() = dao.observeAll().map { it.map(ExerciseEntity::toDomain) }
    override fun observeVisible() = dao.observeVisible().map { it.map(ExerciseEntity::toDomain) }

    override suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Exercise {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Exercise name must not be blank" }
        return transactor.immediate {
            require(dao.findLiveByName(trimmed) == null) { "An exercise named \"$trimmed\" already exists" }
            val now = clock.millis()
            val entity = ExerciseEntity(
                id = UUID.randomUUID().toString(), name = trimmed,
                muscleGroup = muscleGroup, equipment = equipment,
                isBuiltIn = false, isHidden = false,
                createdAt = now, updatedAt = now, deletedAt = null,
            )
            dao.insert(entity)
            entity.toDomain()
        }
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val current = dao.findById(id) ?: return
        dao.update(current.copy(isHidden = hidden, updatedAt = clock.millis()))
    }
}
```

- [ ] **Step 4: `PlanRepositoryImpl.kt`**
```kotlin
@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val dao: PlanDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : PlanRepository {
    override fun observePlans() = dao.observePlans().map { it.map(WorkoutPlanEntity::toDomain) }

    override suspend fun createPlan(name: String): WorkoutPlan {
        val now = clock.millis()
        val entity = WorkoutPlanEntity(UUID.randomUUID().toString(), name.trim(), position = 0,
            createdAt = now, updatedAt = now, deletedAt = null)
        dao.insertPlan(entity)
        return entity.toDomain()
    }

    override suspend fun softDeletePlan(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteTemplateExercisesForPlan(id, now)
        dao.softDeleteDayTemplatesForPlan(id, now)
        dao.softDeletePlan(id, now)
    }
}
```

- [ ] **Step 5: `SessionRepositoryImpl.kt`**
```kotlin
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val transactor: Transactor,
    private val clock: Clock,
) : SessionRepository {
    override fun observeActiveSession() = dao.observeActiveSession().map { it?.toDomain() }
    override fun observeHistory() = dao.observeHistory().map { it.map(SessionEntity::toDomain) }
    override fun observeSessionDetails(id: String) = dao.observeSessionWithDetails(id).map { it?.toDomain() }

    override suspend fun startEmptySession(): Session = transactor.immediate {
        check(dao.activeSessionId() == null) { "A session is already in progress" }
        val now = clock.millis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(), templateId = null, templateNameSnapshot = null,
            startedAt = now, endedAt = null, note = null,
            createdAt = now, updatedAt = now, deletedAt = null,
        )
        dao.insertSession(session)
        session.toDomain()
    }

    override suspend fun finishSession(id: String) {
        val current = dao.findSession(id) ?: return
        val now = clock.millis()
        dao.updateSession(current.copy(endedAt = now, updatedAt = now))
    }

    override suspend fun softDeleteSession(id: String) = transactor.immediate {
        val now = clock.millis()
        dao.softDeleteLoggedSetsForSession(id, now)
        dao.softDeleteSessionExercisesFor(id, now)
        dao.softDeleteSession(id, now)
    }
}
```

- [ ] **Step 6: Bind in `RepositoryModule.kt`** — add three `@Binds`:
```kotlin
@Binds abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository
@Binds abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository
@Binds abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
```

- [ ] **Step 7: Build** — `./gradlew assembleDebug` → SUCCESS (Hilt graph resolves).
- [ ] **Step 8: Commit** — `M1: repository interfaces + impls (Exercise, Plan, Session)`

---

## Task 10: Repository unit tests (JVM, fake DAOs)

**Files:**
- Create: `app/src/test/kotlin/de/simiil/liftlog/testing/fakes/FakeExerciseDao.kt`, `FakePlanDao.kt`, `FakeSessionDao.kt`, `FakeTransactor.kt`
- Create: `app/src/test/kotlin/de/simiil/liftlog/data/repository/ExerciseRepositoryTest.kt`, `PlanRepositoryTest.kt`, `SessionRepositoryTest.kt`

These run **locally** and are the primary verification of cascade/active-session/uniqueness/timestamp conventions.

- [ ] **Step 1: `FakeTransactor`** (runs the block inline)
```kotlin
package de.simiil.liftlog.testing.fakes

import de.simiil.liftlog.data.db.Transactor

class FakeTransactor : Transactor {
    override suspend fun <R> immediate(block: suspend () -> R): R = block()
}
```

- [ ] **Step 2: Fake DAOs** — in-memory `MutableMap<String, *Entity>` (and child maps) implementing the DAO interface. Each cascade/soft-delete method mutates the maps exactly as the SQL would; `findLiveByName` filters `deletedAt == null` + case-insensitive; Flow methods back onto a `MutableStateFlow` updated on writes (only the methods the repository calls need real behavior; others may `TODO()`). Use a fixed clock value where the DAO doesn't set timestamps.

- [ ] **Step 3: `ExerciseRepositoryTest.kt`** — with `Clock.fixed(Instant.ofEpochMilli(5000), ZoneOffset.UTC)`:
  - `createCustom` trims the name, sets `isBuiltIn=false`, `isHidden=false`, `createdAt==updatedAt==5000`, a non-blank UUID `id`.
  - `createCustom` throws `IllegalArgumentException` on a blank name and on a case-insensitive duplicate of a live row.
  - a duplicate of a **soft-deleted** row is allowed (tombstones don't block).
  - `setHidden` flips `isHidden` and bumps `updatedAt`; no-ops for an unknown id.

- [ ] **Step 4: `PlanRepositoryTest.kt`** — `softDeletePlan` tombstones the plan, its day templates, and their template-exercises, all with the same `now`; live siblings under another plan are untouched.

- [ ] **Step 5: `SessionRepositoryTest.kt`**
  - `startEmptySession` inserts a session with `endedAt == null`; a second call throws `IllegalStateException`; after `finishSession`, a new `startEmptySession` succeeds.
  - `finishSession` sets `endedAt` and bumps `updatedAt`.
  - `softDeleteSession` tombstones the session, its session-exercises, and their logged-sets with the same `now`.

- [ ] **Step 6: Run** — `./gradlew testDebugUnitTest` → all green.
- [ ] **Step 7: Commit** — `M1: repository unit tests (fake DAOs)`

---

## Task 11: Built-in exercise seed asset + seeder

**Files:**
- Create: `app/src/main/assets/seed/exercises.v1.json`
- Create: `data/seed/SeedModels.kt`, `data/seed/ExerciseSeeder.kt`
- Modify: `LiftLogApplication.kt`

- [ ] **Step 1: Generate 69 fixed UUIDs and author the asset.** Generate v4 UUIDs (e.g. `for i in $(seq 69); do uuidgen | tr 'A-Z' 'a-z'; done`) and map them 1:1 onto Appendix A (02-data-spec). **These IDs are permanent once merged (flagged decision #8).** Format:
```json
{
  "seedVersion": 1,
  "exercises": [
    { "id": "<uuid>", "name": "Barbell Bench Press", "muscleGroup": "CHEST", "equipment": "BARBELL" }
  ]
}
```
All 69 rows from Appendix A, equipment codes mapped: BB→BARBELL, DB→DUMBBELL, M→MACHINE, C→CABLE, BW→BODYWEIGHT. (Kettlebell Swing → muscleGroup `OTHER`, equipment `DUMBBELL`.) Names verbatim from the table.

- [ ] **Step 2: `SeedModels.kt`**
```kotlin
package de.simiil.liftlog.data.seed

import kotlinx.serialization.Serializable

@Serializable data class SeedFile(val seedVersion: Int, val exercises: List<SeedExercise>)
@Serializable data class SeedExercise(val id: String, val name: String, val muscleGroup: String, val equipment: String)
```

- [ ] **Step 3: `ExerciseSeeder.kt`**
```kotlin
package de.simiil.liftlog.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds built-in exercises on every startup. Idempotent: insert-if-id-absent (02-data-spec §7). */
@Singleton
class ExerciseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExerciseDao,
    private val clock: Clock,
    private val json: Json,
) {
    suspend fun seed() {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val now = clock.millis()
        val entities = json.decodeFromString<SeedFile>(text).exercises.map { e ->
            ExerciseEntity(
                id = e.id, name = e.name,
                muscleGroup = MuscleGroup.fromStorageValue(e.muscleGroup),
                equipment = Equipment.fromStorageValue(e.equipment),
                isBuiltIn = true, isHidden = false,
                createdAt = now, updatedAt = now, deletedAt = null,
            )
        }
        dao.insertIgnore(entities)
    }
    private companion object { const val ASSET = "seed/exercises.v1.json" }
}
```

- [ ] **Step 4: Run the seeder on startup in `LiftLogApplication.kt`**
```kotlin
@HiltAndroidApp
class LiftLogApplication : Application() {
    @Inject lateinit var seeder: ExerciseSeeder
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seed() }
    }
}
```
(Add imports: `javax.inject.Inject`, `de.simiil.liftlog.di.ApplicationScope`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.launch`, `de.simiil.liftlog.data.seed.ExerciseSeeder`.)

- [ ] **Step 5: Build** — `./gradlew assembleDebug` → SUCCESS.
- [ ] **Step 6: Commit** — `M1: built-in exercise seed asset + seeder`

---

## Task 12: Seeder tests (JVM shape + instrumented idempotency)

**Files:**
- Create: `app/src/test/kotlin/de/simiil/liftlog/data/seed/SeedAssetTest.kt` (JVM, local)
- Create: `app/src/androidTest/kotlin/de/simiil/liftlog/data/seed/ExerciseSeederTest.kt` (CI)

- [ ] **Step 1: JVM asset-shape test (local — high value, catches typos)** reads the real file from the module path and validates structure:
```kotlin
package de.simiil.liftlog.data.seed

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SeedAssetTest {
    private val seed = Json { ignoreUnknownKeys = true }
        .decodeFromString<SeedFile>(File("src/main/assets/seed/exercises.v1.json").readText())

    @Test fun hasExactlyExpectedCount() = assertEquals(69, seed.exercises.size)
    @Test fun seedVersionIsOne() = assertEquals(1, seed.seedVersion)

    @Test fun idsAreUniqueValidUuids() {
        val ids = seed.exercises.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { java.util.UUID.fromString(it) } // throws on malformed
    }

    @Test fun namesAreUniqueCaseInsensitive() {
        val names = seed.exercises.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun enumsAreAllRecognized() {
        seed.exercises.forEach {
            assertTrue("bad muscle ${it.muscleGroup}", MuscleGroup.entries.any { e -> e.name == it.muscleGroup })
            assertTrue("bad equipment ${it.equipment}", Equipment.entries.any { e -> e.name == it.equipment })
        }
    }
}
```

- [ ] **Step 2: Run JVM test, expect PASS** (proves the asset Task 11 authored is well-formed) — `./gradlew testDebugUnitTest --tests "*SeedAssetTest"`.

- [ ] **Step 3: Instrumented idempotency test (CI)** — using `InstrumentationRegistry.getInstrumentation().targetContext` (real app assets), `Clock.systemUTC()`, `Json { ignoreUnknownKeys = true }`, in-memory db:
  - `seed()` → `dao.countLive() == 69`, all rows `isBuiltIn == true`.
  - `seed()` twice → still `69` (no duplicates).
  - flip one row's `isHidden = true`, `seed()` again → that row is **still hidden** (insert-if-absent never overwrites).

- [ ] **Step 4: Verify by compiling** — `./gradlew assembleDebugAndroidTest` → SUCCESS. (Executed in CI.)
- [ ] **Step 5: Commit** — `M1: seeder tests (asset shape + idempotency)`

---

## Task 13: Instrumented CI job + docs

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add the `instrumented` job to `ci.yml`** (runs the GMD DAO + seeder tests; KVM enabled via the standard udev trick for GitHub runners)
```yaml
  instrumented:
    name: Instrumented tests (Room DAOs)
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - uses: gradle/actions/setup-gradle@v4
      - name: Accept Android SDK licenses
        run: yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
      - name: Run DAO + seeder tests on a Gradle-managed device
        run: ./gradlew pixelApi34DebugAndroidTest
```
(Keep the existing `build` job unchanged.)

- [ ] **Step 2: Update `CLAUDE.md` "Build & test"** — add:
```
- `./gradlew pixelApi34DebugAndroidTest` — instrumented Room/DAO tests on a
  Gradle-managed device. Requires KVM; this dev machine has none, so these run
  only in CI. Locally, verify with `./gradlew assembleDebugAndroidTest` (compile only).
```

- [ ] **Step 3: Push and watch CI** — this is the first run of the instrumented job and the real test of GMD on GitHub runners (flagged risk #9). Confirm both jobs are green.
- [ ] **Step 4: Commit** — `M1: CI instrumented job (Gradle-managed device) + docs`

---

## Final verification (milestone exit criteria — 05-roadmap M1)

- [ ] `./gradlew lint testDebugUnitTest assembleDebug assembleDebugAndroidTest` → BUILD SUCCESSFUL locally (all JVM tests green; instrumented sources compile).
- [ ] CI green on **both** jobs; in particular DAO tests pass: hot-path queries, soft-delete `IS NULL` filtering, cascade correctness, `@Relation` graph, seeder idempotency.
- [ ] Seeder test proves a fresh install contains exactly 69 built-in exercises.
- [ ] Conventions verified by repository unit tests: UUID PKs, `createdAt`/`updatedAt` set on writes, soft-delete cascades, single-active-session enforcement.
- [ ] Final full-implementation code review, then `superpowers:finishing-a-development-branch`.

## Revision log

_(Append review-driven deviations here during execution, as in the M0 plan.)_
