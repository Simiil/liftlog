# Exercise Model Extension (#37) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Exercise model with `force` (push/pull/static), six new equipment types, and an optional secondary-muscle-group list, plus a version-gated converging seeder — so a later ticket can land the big refined seed list as a pure data swap.

**Architecture:** Two new columns on `exercises` (nullable `force` TEXT, `secondaryMuscleGroups` as a JSON string array via `@TypeConverter`) and a new single-row `seed_state` table, all in one Room v2→v3 migration. Backup format bumps to v3 with backward-compatible DTO defaults. The seeder becomes "converge when the compile-time `SEED_VERSION` is newer than the DB-stored applied version"; restore clears the stored version so imports re-converge.

**Tech Stack:** Kotlin, Room, Hilt, kotlinx.serialization, JUnit4 + Turbine, instrumented tests on `emulator-5554`.

**Spec:** `docs/superpowers/specs/2026-07-09-37-exercise-model-extension-design.md` (owner-approved 2026-07-09).

## Global Constraints

- **No new seed exercises in this ticket.** `app/src/main/assets/seed/exercises.v1.json` is not modified.
- **No new dependencies.** Everything uses libraries already in the version catalog.
- New enum values/fields must survive unknown-value input without crashing: `Equipment` unknown → `OTHER`, `MuscleGroup` unknown → `OTHER`, `Force` unknown → `null`, malformed secondary-list cell → empty list.
- New user-visible strings go to **both** `values/strings.xml` and `values-de/strings.xml` (lint gate enforces it).
- Sync-readiness: preserve `createdAt`, bump `updatedAt` only on real change, never resurrect tombstones, never hard-delete.
- Run `./gradlew ktlintFormat` before every commit.
- CI equivalent: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`.
- Instrumented tests fan out to **every** attached device — always scope:
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- Field placement: new data-class fields are **appended after the last existing field with default values** (`force: Force? = null`, `secondaryMuscleGroups: List<MuscleGroup> = emptyList()`), so the many existing positional constructor call sites in tests keep compiling.

---

### Task 1: `Force` enum, `Equipment` vocabulary, labels + strings

**Files:**
- Create: `app/src/main/kotlin/de/simiil/liftlog/domain/model/Force.kt`
- Create: `app/src/test/kotlin/de/simiil/liftlog/domain/model/ForceTest.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/model/Equipment.kt`
- Modify: `app/src/test/kotlin/de/simiil/liftlog/data/db/ConvertersTest.kt` (fallback expectation)
- Modify: `app/src/main/kotlin/de/simiil/liftlog/ui/exercises/ExerciseLabels.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`

**Interfaces:**
- Consumes: nothing new.
- Produces: `enum class Force { PUSH, PULL, STATIC }` with `companion fun fromStorageValue(value: String?): Force?`; `Equipment` gains `KETTLEBELL, MEDICINE_BALL, FOAM_ROLLER, BANDS, EXERCISE_BALL, OTHER` and `Equipment.fromStorageValue` now falls back to `OTHER` (was `MACHINE`). Later tasks rely on these exact names.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/de/simiil/liftlog/domain/model/ForceTest.kt`:

```kotlin
package de.simiil.liftlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForceTest {
    @Test fun fromStorageValue_recognizesAllNames() {
        Force.entries.forEach { assertEquals(it, Force.fromStorageValue(it.name)) }
    }

    @Test fun fromStorageValue_unknownOrNull_isNull() {
        assertNull(Force.fromStorageValue("???"))
        assertNull(Force.fromStorageValue(null))
    }
}
```

In `ConvertersTest.kt`, update the equipment fallback expectation (this is the behavior change from the spec):

```kotlin
    @Test fun unknownStrings_fallBack() {
        assertEquals(MuscleGroup.OTHER, c.toMuscleGroup("???"))
        assertEquals(Equipment.OTHER, c.toEquipment("???"))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.model.ForceTest" --tests "de.simiil.liftlog.data.db.ConvertersTest"`
Expected: FAIL — `ForceTest` does not compile (unresolved reference `Force`). If Gradle aborts on compilation before running `ConvertersTest`, that counts.

- [ ] **Step 3: Implement `Force` and extend `Equipment`**

Create `app/src/main/kotlin/de/simiil/liftlog/domain/model/Force.kt`:

```kotlin
package de.simiil.liftlog.domain.model

/** Force classification (push/pull/static). Nullable everywhere: absent = unclassified. */
enum class Force {
    PUSH,
    PULL,
    STATIC,
    ;

    companion object {
        /** Unknown/absent → `null` — unlike the other enums there is no sensible catch-all member. */
        fun fromStorageValue(value: String?): Force? = entries.firstOrNull { it.name == value }
    }
}
```

Replace the body of `app/src/main/kotlin/de/simiil/liftlog/domain/model/Equipment.kt`:

```kotlin
package de.simiil.liftlog.domain.model

/** Equipment classification (02-data-spec §3). Entry order drives the picker filter chip row. */
enum class Equipment {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    BODYWEIGHT,
    KETTLEBELL,
    MEDICINE_BALL,
    FOAM_ROLLER,
    BANDS,
    EXERCISE_BALL,
    OTHER,
    ;

    companion object {
        /** Unknown/absent persisted values fall back to [OTHER] (corruption/future-version safety). */
        fun fromStorageValue(value: String?): Equipment = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
```

Note: this breaks compilation of `equipmentLabel` (exhaustive `when`) — fixed next step.

- [ ] **Step 4: Add labels and strings**

In `app/src/main/kotlin/de/simiil/liftlog/ui/exercises/ExerciseLabels.kt`, extend the `when` in `equipmentLabel`:

```kotlin
@Composable
fun equipmentLabel(equipment: Equipment): String =
    stringResource(
        when (equipment) {
            Equipment.BARBELL -> R.string.equipment_barbell
            Equipment.DUMBBELL -> R.string.equipment_dumbbell
            Equipment.MACHINE -> R.string.equipment_machine
            Equipment.CABLE -> R.string.equipment_cable
            Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
            Equipment.KETTLEBELL -> R.string.equipment_kettlebell
            Equipment.MEDICINE_BALL -> R.string.equipment_medicine_ball
            Equipment.FOAM_ROLLER -> R.string.equipment_foam_roller
            Equipment.BANDS -> R.string.equipment_bands
            Equipment.EXERCISE_BALL -> R.string.equipment_exercise_ball
            Equipment.OTHER -> R.string.equipment_other
        },
    )
```

In `app/src/main/res/values/strings.xml`, after `<string name="equipment_bodyweight">Bodyweight</string>`:

```xml
    <string name="equipment_kettlebell">Kettlebell</string>
    <string name="equipment_medicine_ball">Medicine ball</string>
    <string name="equipment_foam_roller">Foam roller</string>
    <string name="equipment_bands">Bands</string>
    <string name="equipment_exercise_ball">Exercise ball</string>
    <string name="equipment_other">Other</string>
```

In `app/src/main/res/values-de/strings.xml`, after `<string name="equipment_bodyweight">Körpergewicht</string>`:

```xml
    <string name="equipment_kettlebell">Kettlebell</string>
    <string name="equipment_medicine_ball">Medizinball</string>
    <string name="equipment_foam_roller">Faszienrolle</string>
    <string name="equipment_bands">Bänder</string>
    <string name="equipment_exercise_ball">Gymnastikball</string>
    <string name="equipment_other">Sonstiges</string>
```

(Do NOT change the unrelated `?: Equipment.MACHINE` placeholders in `DayEditorViewModel`/`ActiveSessionViewModel`/`SessionDetailViewModel` — those are missing-exercise display defaults, not storage fallbacks.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.domain.model.ForceTest" --tests "de.simiil.liftlog.data.db.ConvertersTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add -A
git commit -m "feat(model): Force enum + full equipment vocabulary (#37)"
```

---

### Task 2: Room converters for `Force?` and `List<MuscleGroup>`

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/db/Converters.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/db/ConvertersTest.kt`

**Interfaces:**
- Consumes: `Force.fromStorageValue`, `MuscleGroup.fromStorageValue` (Task 1 / existing).
- Produces: `fromForce(Force?): String?`, `toForce(String?): Force?`, `fromMuscleGroupList(List<MuscleGroup>): String` (JSON array of names, e.g. `["BACK","BICEPS"]`, empty list → `[]`), `toMuscleGroupList(String): List<MuscleGroup>` (lenient). Task 3's entity fields and migration DEFAULT `'[]'` rely on exactly this encoding.

- [ ] **Step 1: Write the failing tests**

Append to `ConvertersTest.kt` (add imports `de.simiil.liftlog.domain.model.Force` and `org.junit.Assert.assertNull`):

```kotlin
    @Test fun force_roundTrips() {
        Force.entries.forEach { assertEquals(it, c.toForce(c.fromForce(it))) }
        assertNull(c.toForce(c.fromForce(null)))
    }

    @Test fun muscleGroupList_roundTrips() {
        val lists =
            listOf(
                emptyList(),
                listOf(MuscleGroup.BACK),
                listOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
            )
        lists.forEach { assertEquals(it, c.toMuscleGroupList(c.fromMuscleGroupList(it))) }
    }

    @Test fun muscleGroupList_emptyEncodesAsEmptyJsonArray() {
        // The v2→v3 migration backfills existing rows with '[]' — lock the encoding.
        assertEquals("[]", c.fromMuscleGroupList(emptyList()))
    }

    @Test fun muscleGroupList_unknownNameFallsBackToOther() {
        assertEquals(listOf(MuscleGroup.OTHER), c.toMuscleGroupList("""["WINGS"]"""))
    }

    @Test fun muscleGroupList_malformedCellDegradesToEmpty() {
        assertEquals(emptyList<MuscleGroup>(), c.toMuscleGroupList("not json"))
        assertEquals(emptyList<MuscleGroup>(), c.toMuscleGroupList(""))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.db.ConvertersTest"`
Expected: FAIL — unresolved references `toForce` / `fromMuscleGroupList` (compilation error).

- [ ] **Step 3: Implement the converters**

Replace `app/src/main/kotlin/de/simiil/liftlog/data/db/Converters.kt`:

```kotlin
package de.simiil.liftlog.data.db

import androidx.room.TypeConverter
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Enums persist as their [Enum.name]; unknown strings fall back (see enum docs).
 *  Enum lists persist as JSON string arrays (`["BACK","BICEPS"]`); malformed cells degrade to empty. */
class Converters {
    @TypeConverter fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.fromStorageValue(value)

    @TypeConverter fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter fun toEquipment(value: String): Equipment = Equipment.fromStorageValue(value)

    @TypeConverter fun fromForce(value: Force?): String? = value?.name

    @TypeConverter fun toForce(value: String?): Force? = Force.fromStorageValue(value)

    @TypeConverter fun fromMuscleGroupList(value: List<MuscleGroup>): String = Json.encodeToString(value.map { it.name })

    @TypeConverter fun toMuscleGroupList(value: String): List<MuscleGroup> =
        try {
            Json.decodeFromString<List<String>>(value).map { MuscleGroup.fromStorageValue(it) }
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
}
```

(`Json` here is the default companion instance — no per-call construction, and the injected app `Json` is unavailable because Room instantiates `Converters` itself.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.db.ConvertersTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A
git commit -m "feat(db): converters for Force and secondary muscle-group list (#37)"
```

---

### Task 3: Schema v3 — entity/domain fields, `seed_state` table, migration

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/entity/ExerciseEntity.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/domain/model/Exercise.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/mapper/Mappers.kt` (exercise mappers only)
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/entity/SeedStateEntity.kt`
- Create: `app/src/main/kotlin/de/simiil/liftlog/data/dao/SeedStateDao.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/db/AppDatabase.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/db/Migrations.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/di/DatabaseModule.kt`
- Modify: `app/src/androidTest/kotlin/de/simiil/liftlog/di/TestDatabaseModule.kt`
- Create (generated): `app/schemas/de.simiil.liftlog.data.db.AppDatabase/3.json`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/mapper/MapperRoundTripTest.kt`
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/db/MigrationTest.kt`

**Interfaces:**
- Consumes: `Force`, converters (Tasks 1–2).
- Produces: `ExerciseEntity`/`Exercise` gain trailing `force: Force? = null` and `secondaryMuscleGroups: List<MuscleGroup> = emptyList()`; `SeedStateEntity(id: Int = 1, appliedSeedVersion: Int)` in table `seed_state`; `SeedStateDao.appliedVersion(): Int?` and `SeedStateDao.upsert(SeedStateEntity)`; `AppDatabase.seedStateDao()`; `DB_SCHEMA_VERSION = 3`; `MIGRATION_2_3`. Tasks 4–6 rely on all of these names exactly.

- [ ] **Step 1: Write the failing mapper test**

In `MapperRoundTripTest.kt`, add imports `de.simiil.liftlog.domain.model.Force` and add:

```kotlin
    @Test fun exercise_withForceAndSecondaries_roundTrips() {
        val e =
            ExerciseEntity(
                "id2",
                "Push-up",
                MuscleGroup.CHEST,
                Equipment.BODYWEIGHT,
                isBuiltIn = true,
                isHidden = false,
                createdAt = 1_000,
                updatedAt = 2_000,
                deletedAt = null,
                force = Force.PUSH,
                secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            )
        assertEquals(e, e.toDomain().toEntity())
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.mapper.MapperRoundTripTest"`
Expected: FAIL — no `force` parameter on `ExerciseEntity` (compilation error).

- [ ] **Step 3: Add the fields and update the exercise mappers**

`app/src/main/kotlin/de/simiil/liftlog/data/entity/ExerciseEntity.kt`:

```kotlin
package de.simiil.liftlog.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val force: Force? = null,
    @ColumnInfo(defaultValue = "[]")
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
)
```

(`defaultValue = "[]"` must match MIGRATION_2_3's `DEFAULT '[]'` or `MigrationTestHelper` schema validation fails.)

`app/src/main/kotlin/de/simiil/liftlog/domain/model/Exercise.kt`:

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
    val force: Force? = null,
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
)
```

In `Mappers.kt`, replace the two exercise mappers (they are fully positional today and would silently drop the new fields — switch to named args and pass them through):

```kotlin
fun ExerciseEntity.toDomain() =
    Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isBuiltIn = isBuiltIn,
        isHidden = isHidden,
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        deletedAt = deletedAt.toInstantOrNull(),
        force = force,
        secondaryMuscleGroups = secondaryMuscleGroups,
    )

fun Exercise.toEntity() =
    ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isBuiltIn = isBuiltIn,
        isHidden = isHidden,
        createdAt = createdAt.toMillis(),
        updatedAt = updatedAt.toMillis(),
        deletedAt = deletedAt.toMillisOrNull(),
        force = force,
        secondaryMuscleGroups = secondaryMuscleGroups,
    )
```

(`ExerciseRepositoryImpl.createCustom` needs no change: the entity defaults give custom exercises `force = null`, empty secondaries — exactly the spec.)

- [ ] **Step 4: Add `seed_state` entity + DAO**

Create `app/src/main/kotlin/de/simiil/liftlog/data/entity/SeedStateEntity.kt`:

```kotlin
package de.simiil.liftlog.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row bookkeeping for the seeder (02-data-spec §7): which seed version has been applied.
 *  Local derived state — deliberately NOT part of the backup format. */
@Entity(tableName = "seed_state")
data class SeedStateEntity(
    @PrimaryKey val id: Int = 1,
    val appliedSeedVersion: Int,
)
```

Create `app/src/main/kotlin/de/simiil/liftlog/data/dao/SeedStateDao.kt`:

```kotlin
package de.simiil.liftlog.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.simiil.liftlog.data.entity.SeedStateEntity

@Dao
interface SeedStateDao {
    /** Null = never seeded (fresh install, or first launch after the v3 migration). */
    @Query("SELECT appliedSeedVersion FROM seed_state WHERE id = 1")
    suspend fun appliedVersion(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeedStateEntity)
}
```

- [ ] **Step 5: Bump the schema and wire the migration**

In `AppDatabase.kt`: set `const val DB_SCHEMA_VERSION = 3`, add `SeedStateEntity::class` to the `entities` array, add import `de.simiil.liftlog.data.entity.SeedStateEntity`, and add:

```kotlin
    abstract fun seedStateDao(): SeedStateDao
```

(plus import `de.simiil.liftlog.data.dao.SeedStateDao`).

In `Migrations.kt`, append:

```kotlin
/**
 * v2 → v3 (issue #37): exercise classification extension + seeder version gate.
 * - exercises gains nullable `force` and NOT NULL `secondaryMuscleGroups` (JSON array, default `[]`)
 * - new single-row `seed_state` table (created empty → the next seeder run converges and stamps it)
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN force TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN secondaryMuscleGroups TEXT NOT NULL DEFAULT '[]'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `seed_state` (`id` INTEGER NOT NULL, `appliedSeedVersion` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
```

In `DatabaseModule.kt`: change to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` (import `MIGRATION_2_3`), and add:

```kotlin
    @Provides fun provideSeedStateDao(db: AppDatabase): SeedStateDao = db.seedStateDao()
```

(import `de.simiil.liftlog.data.dao.SeedStateDao`).

In `TestDatabaseModule.kt` (it `replaces` DatabaseModule, so it must mirror the new provider):

```kotlin
    @Provides fun provideSeedStateDao(db: AppDatabase): SeedStateDao = db.seedStateDao()
```

(import `de.simiil.liftlog.data.dao.SeedStateDao`).

- [ ] **Step 6: Run unit tests + generate the exported schema**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (mapper round-trip now green; nothing else regresses — the trailing-default placement keeps every existing fixture compiling).

Then verify `app/schemas/de.simiil.liftlog.data.db.AppDatabase/3.json` was generated (KSP runs during the test compile). If not, run `./gradlew :app:kspDebugKotlin`. Check it contains `"force"`, `"secondaryMuscleGroups"` with `"defaultValue": "[]"`, and a `seed_state` table with `id`, `appliedSeedVersion`. If the `CREATE TABLE` statement in `3.json` for `seed_state` differs from the migration SQL above, adjust the migration SQL to match `3.json` exactly.

- [ ] **Step 7: Write the failing migration test**

Append to `MigrationTest.kt`:

```kotlin
    @Test
    fun migrate2To3_addsClassificationColumns_andSeedStateTable() {
        helper.createDatabase("migration-test-v3", 2).apply {
            execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, equipment, isBuiltIn, isHidden, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('ex1', 'Bench', 'CHEST', 'BARBELL', 1, 0, 1000, 1000, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-v3", 3, true, MIGRATION_2_3)

        db.query("SELECT force, secondaryMuscleGroups FROM exercises WHERE id = 'ex1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("existing rows get NULL force", c.isNull(0))
            assertEquals("existing rows get empty secondaries", "[]", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM seed_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("seed_state starts empty so the seeder re-converges", 0, c.getInt(0))
        }
    }
```

- [ ] **Step 8: Run the migration test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.db.MigrationTest`
Expected: PASS (both migration tests). `runMigrationsAndValidate` also validates the whole v3 schema against `3.json` — a mismatch here means the migration SQL needs to match the exported schema (see Step 6).

- [ ] **Step 9: Commit (include the generated schema)**

```bash
./gradlew ktlintFormat
git add -A app/schemas/de.simiil.liftlog.data.db.AppDatabase/3.json
git commit -m "feat(db): schema v3 — exercise force/secondaries + seed_state table (#37)"
```

---

### Task 4: Backup format v3

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/backup/BackupModels.kt` (ExerciseDto)
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/backup/BackupCodec.kt`
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/backup/BackupCodecTest.kt`
- Regenerate: `app/src/test/resources/backup/golden-backup.json`

**Interfaces:**
- Consumes: entity fields from Task 3; `Force.fromStorageValue`, `MuscleGroup.fromStorageValue`.
- Produces: `ExerciseDto` gains trailing `force: String? = null` and `secondaryMuscleGroups: List<String> = emptyList()`; `BackupCodec.CURRENT_FORMAT_VERSION = 3`. Task 7 documents this.

- [ ] **Step 1: Update the test fixture and expectations (failing first)**

In `BackupCodecTest.kt`:

1. Add import `de.simiil.liftlog.domain.model.Force`.
2. Change `appInfo` to `dbSchemaVersion = 3`.
3. In `fixture()`, give `ex1` the new fields (named args at the end of the `ExerciseEntity` call):

```kotlin
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
                        force = Force.PUSH,
                        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
                    ),
```

(`ex2` keeps defaults — the golden file then locks that `force: null` / `[]` are still written to the wire thanks to `encodeDefaults`.)

4. Update the `newer format version` test (current is 3 now):

```kotlin
    @Test
    fun `newer format version is rejected as Newer`() {
        val newer = BackupCodec.encode(fixture(), exportedAt, appInfo).replace("\"formatVersion\": 3", "\"formatVersion\": 4")
        assertEquals(ParseResult.Newer(4), BackupCodec.decode(newer))
    }
```

5. Add the new-field behavior tests:

```kotlin
    @Test
    fun `v2 file without force or secondaries imports with null and empty`() {
        val v2 =
            """
            {"formatVersion":2,"exportedAt":"2026-06-09T12:00:00Z",
             "app":{"name":"LiftLog","versionName":"0.1.0","dbSchemaVersion":2},
             "settings":{"weightUnit":"KG","theme":"SYSTEM"},
             "data":{
               "exercises":[{"id":"ex1","name":"Bench","muscleGroup":"CHEST","equipment":"BARBELL",
                 "isBuiltIn":true,"isHidden":false,"createdAt":"1970-01-01T00:00:01Z","updatedAt":"1970-01-01T00:00:01Z","deletedAt":null}],
               "workoutPlans":[],"planDayTemplates":[],"templateExercises":[],
               "sessions":[],"sessionExercises":[],"loggedSets":[]}}
            """.trimIndent()
        val result = BackupCodec.decode(v2)
        assertTrue(result is ParseResult.Ready)
        val exercise = ((result as ParseResult.Ready).parsed as BackupSnapshot).exercises.single()
        assertNull(exercise.force)
        assertEquals(emptyList<MuscleGroup>(), exercise.secondaryMuscleGroups)
    }

    @Test
    fun `unknown force and secondary muscle fall back leniently`() {
        // Unlike muscleGroup/equipment (identity, strict), the new classification fields are lenient.
        val odd =
            BackupCodec
                .encode(fixture(), exportedAt, appInfo)
                .replace("\"force\": \"PUSH\"", "\"force\": \"YEET\"")
                .replace("\"TRICEPS\"", "\"WINGS\"")
        val result = BackupCodec.decode(odd)
        assertTrue(result is ParseResult.Ready)
        val ex1 = ((result as ParseResult.Ready).parsed as BackupSnapshot).exercises.first { it.id == "ex1" }
        assertNull(ex1.force)
        assertEquals(listOf(MuscleGroup.OTHER, MuscleGroup.SHOULDERS), ex1.secondaryMuscleGroups)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest"`
Expected: FAIL — golden mismatch (`encode matches the golden file`), `Newer(3)` vs expected, and the two new tests fail (fields missing from DTO / version still 2).

- [ ] **Step 3: Implement the format bump**

In `BackupModels.kt`, extend `ExerciseDto` (trailing fields — DTO field order IS the wire layout, appending keeps the diff minimal):

```kotlin
@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
    val force: String? = null, // v3; default = v1/v2-import compat, always on the wire (encodeDefaults)
    val secondaryMuscleGroups: List<String> = emptyList(), // v3; same compat pattern
)
```

In `BackupCodec.kt`:

1. `const val CURRENT_FORMAT_VERSION = 3`
2. Add import `de.simiil.liftlog.domain.model.Force`.
3. Replace `ExerciseEntity.toDto()`:

```kotlin
    private fun ExerciseEntity.toDto() =
        ExerciseDto(
            id = id,
            name = name,
            muscleGroup = muscleGroup.name,
            equipment = equipment.name,
            isBuiltIn = isBuiltIn,
            isHidden = isHidden,
            createdAt = createdAt.iso(),
            updatedAt = updatedAt.iso(),
            deletedAt = deletedAt?.iso(),
            force = force?.name,
            secondaryMuscleGroups = secondaryMuscleGroups.map { it.name },
        )
```

4. Replace `ExerciseDto.toEntity()`:

```kotlin
    private fun ExerciseDto.toEntity() =
        ExerciseEntity(
            id = id,
            name = name,
            muscleGroup = muscle(muscleGroup),
            equipment = equip(equipment),
            isBuiltIn = isBuiltIn,
            isHidden = isHidden,
            createdAt = createdAt.millis(),
            updatedAt = updatedAt.millis(),
            deletedAt = deletedAt?.millis(),
            force = Force.fromStorageValue(force), // lenient: unknown → null
            secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) }, // lenient: unknown → OTHER
        )
```

- [ ] **Step 4: Regenerate the golden file**

Temporarily add to `BackupCodecTest.kt` (import `java.io.File`):

```kotlin
    @Test
    fun `TEMP regenerate golden`() {
        File("src/test/resources/backup/golden-backup.json").writeText(BackupCodec.encode(fixture(), exportedAt, appInfo) + "\n")
    }
```

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest"`
Then **delete the TEMP test**, and inspect `git diff app/src/test/resources/backup/golden-backup.json` — expected changes only: `formatVersion: 3`, `dbSchemaVersion: 3`, and each exercise gaining `"force"` + `"secondaryMuscleGroups"` keys (ex1 populated, ex2 `null`/`[]`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.backup.BackupCodecTest"`
Expected: PASS (all tests, TEMP test removed).

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add -A
git commit -m "feat(backup): format v3 with exercise force + secondary muscles (#37)"
```

---

### Task 5: Version-gated converging seeder

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/seed/SeedModels.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/seed/ExerciseSeeder.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/ExerciseDao.kt` (add `findByIdAny`)
- Test: `app/src/test/kotlin/de/simiil/liftlog/data/seed/SeedAssetTest.kt`
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/seed/ExerciseSeederTest.kt`

**Interfaces:**
- Consumes: `SeedStateDao`, `SeedStateEntity`, `Transactor` (Task 3), `Force`, converters.
- Produces: `ExerciseSeeder.SEED_VERSION: Int` (public companion const, currently `1`); `ExerciseSeeder` constructor `(context, dao, seedStateDao, transactor, clock, json)`; `SeedExercise` gains `force: String? = null`, `secondaryMuscleGroups: List<String> = emptyList()`; `ExerciseDao.findByIdAny(id): ExerciseEntity?`. Task 6 constructs the seeder with this exact signature.

- [ ] **Step 1: Extend the seed schema + asset tests (failing first)**

In `SeedModels.kt`:

```kotlin
package de.simiil.liftlog.data.seed

import kotlinx.serialization.Serializable

@Serializable data class SeedFile(
    val seedVersion: Int,
    val exercises: List<SeedExercise>,
)

@Serializable data class SeedExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val force: String? = null,
    val secondaryMuscleGroups: List<String> = emptyList(),
)
```

In `SeedAssetTest.kt`: delete the `seedVersionIsOne` test, add import `de.simiil.liftlog.domain.model.Force`, and add:

```kotlin
    @Test fun seedVersionMatchesSeederConstant() = assertEquals(ExerciseSeeder.SEED_VERSION, seed.seedVersion)

    @Test fun forceValuesAreRecognizedWhenPresent() {
        seed.exercises.mapNotNull { it.force }.forEach { f ->
            assertTrue("bad force $f", Force.entries.any { it.name == f })
        }
    }

    @Test fun secondaryMusclesAreValidDedupedAndExcludePrimary() {
        seed.exercises.forEach { e ->
            assertEquals("duplicate secondaries in ${e.name}", e.secondaryMuscleGroups.size, e.secondaryMuscleGroups.toSet().size)
            e.secondaryMuscleGroups.forEach { m ->
                assertTrue("bad secondary $m in ${e.name}", MuscleGroup.entries.any { it.name == m })
            }
            assertTrue("primary listed as secondary in ${e.name}", e.muscleGroup !in e.secondaryMuscleGroups)
        }
    }
```

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.seed.SeedAssetTest"`
Expected: FAIL — `ExerciseSeeder.SEED_VERSION` unresolved (compilation error). The two data-quality tests are vacuous against the current asset (all secondaries empty) but lock the contract for the future seed swap.

- [ ] **Step 2: Add `findByIdAny` to `ExerciseDao`**

In `ExerciseDao.kt`, next to `findById`:

```kotlin
    /** Seeder convergence: reads THROUGH tombstones so a soft-deleted id is never re-inserted (PK conflict). */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun findByIdAny(id: String): ExerciseEntity?
```

- [ ] **Step 3: Rewrite the seeder**

Replace `app/src/main/kotlin/de/simiil/liftlog/data/seed/ExerciseSeeder.kt`:

```kotlin
package de.simiil.liftlog.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.simiil.liftlog.data.dao.ExerciseDao
import de.simiil.liftlog.data.dao.SeedStateDao
import de.simiil.liftlog.data.db.Transactor
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.SeedStateEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Version-gated built-in seeding (02-data-spec §7). When [SEED_VERSION] is newer than the
 * DB-stored applied version, converge live built-in rows to the seed file: insert missing ids,
 * update changed classification (name/muscleGroup/equipment/force/secondaries) — preserving
 * isHidden and createdAt, bumping updatedAt only on real change, never touching tombstones,
 * never removing rows absent from the file. When versions match, returns without opening the
 * asset. Idempotent; converge + version stamp run in one transaction.
 */
@Singleton
class ExerciseSeeder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: ExerciseDao,
        private val seedStateDao: SeedStateDao,
        private val transactor: Transactor,
        private val clock: Clock,
        private val json: Json,
    ) {
        suspend fun seed() {
            val applied = seedStateDao.appliedVersion()
            if (applied != null && applied >= SEED_VERSION) return // covers app downgrades too
            val text =
                context.assets
                    .open(ASSET)
                    .bufferedReader()
                    .use { it.readText() }
            val exercises = json.decodeFromString<SeedFile>(text).exercises
            val now = clock.millis()
            transactor.immediate {
                val toInsert = mutableListOf<ExerciseEntity>()
                for (seed in exercises) {
                    val existing = dao.findByIdAny(seed.id)
                    when {
                        existing == null -> toInsert += seed.toEntity(now)
                        existing.deletedAt != null -> Unit // tombstone wins; never resurrect
                        else -> {
                            val converged =
                                existing.copy(
                                    name = seed.name,
                                    muscleGroup = MuscleGroup.fromStorageValue(seed.muscleGroup),
                                    equipment = Equipment.fromStorageValue(seed.equipment),
                                    force = Force.fromStorageValue(seed.force),
                                    secondaryMuscleGroups = seed.secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) },
                                )
                            if (converged != existing) dao.update(converged.copy(updatedAt = now))
                        }
                    }
                }
                if (toInsert.isNotEmpty()) dao.insertIgnore(toInsert)
                seedStateDao.upsert(SeedStateEntity(appliedSeedVersion = SEED_VERSION))
            }
        }

        private fun SeedExercise.toEntity(now: Long) =
            ExerciseEntity(
                id = id,
                name = name,
                muscleGroup = MuscleGroup.fromStorageValue(muscleGroup),
                equipment = Equipment.fromStorageValue(equipment),
                isBuiltIn = true,
                isHidden = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                force = Force.fromStorageValue(force),
                secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.fromStorageValue(it) },
            )

        companion object {
            /** Bump together with a new `seed/exercises.v<N>.json` asset. SeedAssetTest locks file ↔ constant. */
            const val SEED_VERSION = 1
            private const val ASSET = "seed/exercises.v$SEED_VERSION.json"
        }
    }
```

- [ ] **Step 4: Run the asset tests**

Run: `./gradlew testDebugUnitTest --tests "de.simiil.liftlog.data.seed.SeedAssetTest"`
Expected: PASS

- [ ] **Step 5: Update + extend the instrumented seeder tests**

Replace `app/src/androidTest/kotlin/de/simiil/liftlog/data/seed/ExerciseSeederTest.kt`:

```kotlin
package de.simiil.liftlog.data.seed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import de.simiil.liftlog.data.db.AppDatabase
import de.simiil.liftlog.data.db.RoomTransactor
import de.simiil.liftlog.data.entity.SeedStateEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ExerciseSeederTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = newInMemoryDb()
    }

    @After fun tearDown() = db.close()

    /** Fixed clocks so updatedAt assertions can't race the wall clock. */
    private fun seederAt(millis: Long) =
        ExerciseSeeder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            dao = db.exerciseDao(),
            seedStateDao = db.seedStateDao(),
            transactor = RoomTransactor(db),
            clock = Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC),
            json = Json { ignoreUnknownKeys = true },
        )

    @Test fun seed_insertsAllBuiltIns() =
        runTest {
            seederAt(1_000).seed()
            assertEquals(69, db.exerciseDao().countLive())
            db.exerciseDao().observeAll().test {
                val items = awaitItem()
                assertTrue("all rows should be isBuiltIn", items.all { it.isBuiltIn })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun seed_isIdempotent() =
        runTest {
            seederAt(1_000).seed()
            seederAt(2_000).seed()
            assertEquals(69, db.exerciseDao().countLive())
        }

    @Test fun seed_storesAppliedVersion() =
        runTest {
            seederAt(1_000).seed()
            assertEquals(ExerciseSeeder.SEED_VERSION, db.seedStateDao().appliedVersion())
        }

    @Test fun seed_skipsEntirelyWhenVersionCurrent() =
        runTest {
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val row = dao.observeAll().first().first()
            // Tamper a classification field: a converge pass WOULD fix it, so it staying
            // tampered proves the early return (asset not applied).
            dao.update(row.copy(muscleGroup = MuscleGroup.OTHER, updatedAt = 1_500L))
            seederAt(2_000).seed()
            assertEquals(MuscleGroup.OTHER, dao.findById(row.id)!!.muscleGroup)
        }

    @Test fun seed_skipsOnDowngrade() =
        runTest {
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 999))
            seederAt(1_000).seed()
            assertEquals(0, db.exerciseDao().countLive())
        }

    @Test fun seed_convergesChangedClassification_preservingUserState() =
        runTest {
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val original = dao.observeAll().first().first()
            dao.update(
                original.copy(
                    muscleGroup = MuscleGroup.OTHER,
                    equipment = Equipment.OTHER,
                    isHidden = true,
                    updatedAt = 1_500L,
                ),
            )
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0)) // simulate a newer seed file
            seederAt(2_000).seed()
            val after = dao.findById(original.id)
            assertNotNull(after)
            assertEquals("classification restored from seed", original.muscleGroup, after!!.muscleGroup)
            assertEquals("classification restored from seed", original.equipment, after.equipment)
            assertTrue("user isHidden preserved", after.isHidden)
            assertEquals("createdAt preserved", original.createdAt, after.createdAt)
            assertEquals("updatedAt bumped on real change", 2_000L, after.updatedAt)
            assertEquals(ExerciseSeeder.SEED_VERSION, db.seedStateDao().appliedVersion())
        }

    @Test fun seed_noDiff_doesNotBumpUpdatedAt() =
        runTest {
            seederAt(1_000).seed()
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0)) // force a converge pass
            seederAt(2_000).seed()
            val rows = db.exerciseDao().observeAll().first()
            assertTrue("unchanged rows keep their updatedAt", rows.all { it.updatedAt == 1_000L })
        }

    @Test fun seed_neverResurrectsTombstones() =
        runTest {
            // Built-ins aren't deletable via the UI; the seeder still honors tombstones defensively.
            seederAt(1_000).seed()
            val dao = db.exerciseDao()
            val row = dao.observeAll().first().first()
            dao.update(row.copy(deletedAt = 5_000L))
            db.seedStateDao().upsert(SeedStateEntity(appliedSeedVersion = 0))
            seederAt(2_000).seed()
            assertNull("tombstone must survive re-seed", dao.findById(row.id))
        }
}
```

- [ ] **Step 6: Run the instrumented seeder tests**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.seed.ExerciseSeederTest`
Expected: PASS (8 tests)

- [ ] **Step 7: Commit**

```bash
./gradlew ktlintFormat
git add -A
git commit -m "feat(seed): version-gated converging seeder with seed_state (#37)"
```

---

### Task 6: Restore resets seeding

**Files:**
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/dao/BackupDao.kt`
- Modify: `app/src/main/kotlin/de/simiil/liftlog/data/repository/BackupRepositoryImpl.kt`
- Test: `app/src/androidTest/kotlin/de/simiil/liftlog/data/backup/BackupRoundTripTest.kt`

**Interfaces:**
- Consumes: `ExerciseSeeder` (Task 5 signature), `SeedStateDao`.
- Produces: `BackupDao.deleteSeedState()`; `BackupRepositoryImpl` constructor gains a trailing `private val seeder: ExerciseSeeder` parameter (Hilt wires it automatically — `ExerciseSeeder` is `@Singleton @Inject`).

- [ ] **Step 1: Write the failing test**

In `BackupRoundTripTest.kt`:

1. Add imports:

```kotlin
import de.simiil.liftlog.data.seed.ExerciseSeeder
import kotlinx.serialization.json.Json
```

2. Add a seeder helper next to `defaultPlanEnsurer()`:

```kotlin
    private fun exerciseSeeder() =
        ExerciseSeeder(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            dao = db.exerciseDao(),
            seedStateDao = db.seedStateDao(),
            transactor = RoomTransactor(db),
            clock = clock,
            json = Json { ignoreUnknownKeys = true },
        )
```

3. Every existing `BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer())` call in this file gains a final `exerciseSeeder()` argument (search the file for `BackupRepositoryImpl(` — this will not compile until Step 3, which is expected for the moment; the new test comes first):

```kotlin
            val repo = BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer(), exerciseSeeder())
```

4. In the existing test `export then wipe then import restores every row and the settings`, the final table comparison is `assertEquals(before, snapshotTables(dao))` (line ~198). The reseed legitimately adds 69 built-in rows to `exercises` after import, so replace that single line with:

```kotlin
            // applyImport now reseeds built-ins after restore (issue #37): the exercises table
            // gains seeded rows, so compare the backup's own rows by id; other tables stay exact.
            val fixtureIds = setOf("ex1", "ex2", "ex3")
            assertEquals(before[0], dao.getAllExercises().filter { it.id in fixtureIds }) // row-for-row, incl. tombstone + hidden
            assertEquals(before.drop(1), snapshotTables(dao).drop(1))
```

5. Add the new test:

```kotlin
    @Test
    fun `import re-converges built-ins after restore`() =
        runTest {
            seed() // fixture rows only — no built-ins in this backup
            val dao = db.backupDao()
            val settings = FakeSettings(WeightUnit.KG, ThemePreference.SYSTEM)
            val repo = BackupRepositoryImpl(dao, settings, clock, appInfo, defaultPlanEnsurer(), exerciseSeeder())

            val json = repo.exportToJson()
            val parsed = repo.parseImport(json)
            assertTrue(parsed is ParseResult.Ready)
            repo.applyImport((parsed as ParseResult.Ready).parsed)

            // replaceAll cleared seed_state → applyImport reseeded: 69 built-ins + ex1/ex2 live fixtures
            assertEquals(69 + 2, db.exerciseDao().countLive())
            assertEquals(ExerciseSeeder.SEED_VERSION, db.seedStateDao().appliedVersion())
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.backup.BackupRoundTripTest`
Expected: FAIL — compilation error (`BackupRepositoryImpl` has no 6th parameter; `deleteSeedState` missing).

- [ ] **Step 3: Implement**

In `BackupDao.kt`, add with the other deletes:

```kotlin
    /** Restore clears the applied seed version so the post-import reseed re-converges built-ins. */
    @Query("DELETE FROM seed_state")
    suspend fun deleteSeedState()
```

and call it inside `replaceAll` after `deleteAllExercises()`:

```kotlin
        deleteAllExercises()
        deleteSeedState()
```

In `BackupRepositoryImpl.kt`: add import `de.simiil.liftlog.data.seed.ExerciseSeeder`, add the constructor parameter after `defaultPlanEnsurer`:

```kotlin
        private val defaultPlanEnsurer: DefaultPlanEnsurer,
        private val seeder: ExerciseSeeder,
```

and in `applyImport`, immediately after `backupDao.replaceAll(snapshot)`:

```kotlin
                backupDao.replaceAll(snapshot)
                // replaceAll cleared seed_state; re-converge so a restore from an old backup
                // can't leave stale or missing built-in rows behind (02-data-spec §7).
                seeder.seed()
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.backup.BackupRoundTripTest`
Expected: PASS — all four tests: the adjusted round-trip test (fixture rows restored row-for-row by id, other tables exact), the new reseed test, and the two untouched tests (`import is blocked…` never calls `applyImport`; `…zero plans…` only asserts on plans, so the reseed doesn't affect it).

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add -A
git commit -m "feat(backup): restore resets seed version and re-converges built-ins (#37)"
```

---

### Task 7: Update the data spec

**Files:**
- Modify: `docs/02-data-spec.md`

**Interfaces:** documentation only.

- [ ] **Step 1: §3 `exercises` table + new `seed_state` section**

In the `### exercises` table, replace the `equipment` row and add two rows after it:

```markdown
| equipment | TEXT | Enum: `BARBELL DUMBBELL MACHINE CABLE BODYWEIGHT KETTLEBELL MEDICINE_BALL FOAM_ROLLER BANDS EXERCISE_BALL OTHER` |
| force | TEXT? | Enum: `PUSH PULL STATIC`; `NULL` = unclassified (custom exercises, stretches) |
| secondaryMuscleGroups | TEXT | JSON string array of `muscleGroup` enum names (e.g. `["BACK","BICEPS"]`), `[]` = none; excludes the primary, no duplicates |
```

After the `### exercises` table (before `### workout_plans`), add:

```markdown
### `seed_state`
| Column | Type | Notes |
|---|---|---|
| id | INTEGER PK | Always `1` — single-row table |
| appliedSeedVersion | INTEGER | Seed version last converged into `exercises` (§7). Local derived state: **not** exported/imported; cleared by import so restores re-converge |
```

- [ ] **Step 2: §6 format v3**

In the `Rules:` list of §6, update the format-versioning bullet and append one:

```markdown
- **Format versioning**: `formatVersion` bumps only on breaking changes. Current version: **3** (since 2026-07-09, issue #37; adds exercise `force` + `secondaryMuscleGroups`). The importer accepts `formatVersion <= current`; a `Newer(version)` check refuses files from a newer app with a clear message ("backup was created by a newer app version").
- **v1/v2 → v3 import compat**: `ExerciseDto.force` (string enum name or `null`) and `ExerciseDto.secondaryMuscleGroups` (string array) default to `null`/`[]` when absent, so older files import cleanly. Unlike `muscleGroup`/`equipment` (identity fields, strict `UNKNOWN_ENUM` rejection), the two new classification fields import **leniently**: unknown `force` → `null`, unknown secondary names → `OTHER`.
```

Also update the example JSON's exercise object and header to show v3 (`"formatVersion": 3`, `"dbSchemaVersion": 3`, and `"force": "PUSH", "secondaryMuscleGroups": ["TRICEPS","SHOULDERS"]` on the sample exercise).

- [ ] **Step 3: §7 seeding rewrite**

Replace the body of `## 7. Built-in exercise seeding` with:

```markdown
- `assets/seed/exercises.v<N>.json` asset (N = `ExerciseSeeder.SEED_VERSION`, locked by SeedAssetTest): built-in exercises, each with a **hardcoded fixed UUID**, name, muscleGroup, equipment, optional force, optional secondaryMuscleGroups.
- **Version-gated convergence** (since v3 / issue #37): the seeder reads the single `seed_state` row and returns immediately — without opening the asset — when `appliedSeedVersion >= SEED_VERSION`. Otherwise (fresh install, first launch after the v3 migration, or a seed bump) it converges in one transaction: inserts missing ids; updates changed classification fields (`name, muscleGroup, equipment, force, secondaryMuscleGroups`) on live built-in rows, preserving `isHidden`/`createdAt` and bumping `updatedAt` only on real change; then stamps `appliedSeedVersion = SEED_VERSION`. A crash mid-seed re-runs the idempotent converge next launch.
- Convergence never removes or resurrects: rows absent from the seed file are left untouched (user history may hang off them), tombstoned rows stay tombstoned. App downgrades (stored version > constant) skip seeding.
- Import clears `seed_state` inside the restore transaction and re-runs the seeder right after, so restoring an old backup cannot leave stale or missing built-ins.
- Built-in UUIDs are stable across all installs → exports from any device reference the same built-in IDs, keeping future sync/merge sane.
```

- [ ] **Step 4: Commit**

```bash
git add docs/02-data-spec.md
git commit -m "docs: data spec v3 — exercise classification, seed_state, backup v3 (#37)"
```

---

### Task 8: Full verification sweep

**Files:** none (verification only).

- [ ] **Step 1: CI-equivalent build**

Run: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, no lint errors (the DE strings task keeps the i18n lint gate green).

- [ ] **Step 2: Full instrumented data-layer suite**

Run each (scoped — connected tests fan out to every attached device):

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.db.MigrationTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.seed.ExerciseSeederTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.backup.BackupRoundTripTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.simiil.liftlog.data.dao.ExerciseDaoTest
```

Expected: all PASS.

- [ ] **Step 3: On-device smoke check (upgrade path)**

The critical real-world path is **upgrade with existing data** (v2 DB → v3 migration → seeder converge with no seed change):

```bash
./gradlew installDebug
adb -s emulator-5554 shell am start -n de.simiil.liftlog/.MainActivity
```

Verify: app launches, exercise picker opens, equipment filter row shows the six new chips (Kettlebell … Other), existing exercises/history intact. (If the emulator still has a pre-branch install, this exercises MIGRATION_2_3 on real data; otherwise it's a fresh-install seed check — both are useful, note which one ran.)

- [ ] **Step 4: Done — hand off**

Use superpowers:finishing-a-development-branch (PR against `main`, title `feat: exercise model extension — force, equipment, secondary muscles (#37)`, body references issue #37 and the spec/plan docs).
