# 08 — Internationalization Spec (M6)

> **Status:** Draft for review · 2026-06-11
> Defines **M6 — Internationalization**: translating the app into German while
> keeping English as the source language, without touching the data model. Builds
> on the already-externalized string layer ([03-ux-spec](03-ux-spec.md),
> M2–M5). File:line references verified against
> `app/src/main/kotlin/de/simiil/liftlog/...` as of 2026-06-11.

## 1. Goals & decisions

- **Source language English, first translation German.** The framework must scale
  to more locales as pure translation work, with no further code changes.
- **Don't muddle the data model.** Room stays the single source of truth; no
  translation columns, no per-locale rows, no export-format change. Translation is
  a **presentation-layer** concern only.
- **Custom exercises are never translated** — the user typed them in their own
  language; they render verbatim.
- **Language follows the system**, with the Android 13+ per-app override. No
  in-app language picker, no new third-party dependency (constraint #5 — see §4).
- **Search must match translated names** (explicit requirement): a German user
  searching "Bankdrücken" finds Barbell Bench Press (§3.3).

## 2. The translation surface

| Surface | State today | M6 work |
|---|---|---|
| UI strings | ~95% in `res/values/strings.xml` (246 strings + plurals, used via `stringResource`/`pluralStringResource`) | Externalize the stragglers (§5.1), then translate in `values-de/` |
| Muscle groups & equipment | **Already** routed through `R.string.muscle_*` / `R.string.equipment_*` via `ui/exercises/ExerciseLabels.kt` | Translation only — **zero code change** |
| Built-in exercise names | English `String` on the entity, seeded from JSON | The architecture piece — §3 |
| Custom exercise names | User-typed `String` | **Untouched** — rendered verbatim |
| Number / weight / date formatting | Mixed; some hardcoded `.` separators and joins | Locale-correctness pass — §5 |

## 3. Built-in exercise names

### 3.1 Principle: resource lookup by stable ID, DB untouched

`ExerciseEntity.name` (`data/entity/ExerciseEntity.kt`) keeps the **English
canonical name** exactly as today — it is what the seeder writes
(`data/seed/ExerciseSeeder.kt`), what the export file carries
([02-data-spec](02-data-spec.md) §6), and the fallback when no translation
exists. Translation never enters the database.

The lever is that every built-in ships with a **fixed UUID** and an `isBuiltIn`
flag ([02-data-spec](02-data-spec.md) §3, §7). That lets us map a built-in's ID →
a localized string resource **at render time**:

- Add one `exercise_*` string per built-in to `values/strings.xml` (English) and
  `values-de/strings.xml` (German). Naming: a stable slug, e.g.
  `<string name="exercise_barbell_bench_press">Barbell Bench Press</string>`.
- A compile-time map carries the UUID → resource binding (no
  `Resources.getIdentifier()` — it is slow and breaks under R8 resource
  shrinking):

  ```kotlin
  // ui/exercises/BuiltInExerciseNames.kt — the single source of the binding
  object BuiltInExerciseNames {
      val resById: Map<String, Int> = mapOf(
          "7a0737bd-d46f-4dd1-9dad-ed3e4a83869a" to R.string.exercise_barbell_bench_press,
          // … one entry per built-in (331)
      )
  }
  ```

### 3.2 The resolver

A single resolver returns the display name; it is the only place the built-in /
custom decision lives:

```kotlin
// ui/exercises/ExerciseNameResolver.kt
class ExerciseNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun displayName(exercise: Exercise): String =
        if (exercise.isBuiltIn)
            BuiltInExerciseNames.resById[exercise.id]?.let(context::getString) ?: exercise.name
        else exercise.name
}
```

`Context.getString` resolves against the **active configuration locale**, so the
resolver tracks the current language automatically — a system/locale change
recreates the Activity and ViewModels, and the next resolution returns the new
language. A thin `@Composable` wrapper (using `stringResource` over the same
`resById`) serves direct rendering, so composables and ViewModels share one
binding.

**Render sites** (all currently read `exercise.name` straight off the model):
`ui/exercises/ExercisePickerScreen.kt` (PickerExerciseRow),
`ui/session/ExerciseCard.kt`, `ui/session/SessionDetailScreen.kt`,
`ui/analytics/AnalyticsScreen.kt` (browser rows) + `ExerciseDetailScreen.kt`,
`ui/plans/PlanEditorScreen.kt` (template-exercise rows). History/analytics resolve
the exercise live by `exerciseId` (no name is snapshotted on `session_exercises`),
so they re-localize for free.

### 3.3 Search & sort (the explicit requirement)

`ui/exercises/ExercisePickerViewModel.kt` (≈L41–65) currently filters on
`ex.name.contains(q, …)` and sorts by `it.name.lowercase()` — both against the
**stored English name**. M6 reworks this against the **resolved display name**:

- The VM injects `ExerciseNameResolver` and computes `displayName` per exercise.
- **Filter** matches the localized display name **and** the canonical English name
  (matching English too is free and lets bilingual users find either spelling).
- **Sort** uses a locale-aware `java.text.Collator.getInstance(Locale.getDefault())`
  on the display name, so German umlauts order correctly (e.g. "Ü" near "U", not
  after "Z").

The VM emits a small UI item (`Exercise` + resolved `displayName`) so the
composable does not re-resolve per frame.

### 3.4 Drift guard

A JVM unit test keeps the seed asset and the resources in lockstep — CI fails if
they diverge:

1. Every `id` in `assets/seed/exercises.v<N>.json` (N = `ExerciseSeeder.SEED_VERSION`)
   has an entry in `BuiltInExerciseNames.resById`, and vice-versa.
2. Every mapped `@StringRes` resolves (no dangling resource id).
3. Each English `exercise_*` value **equals** the corresponding JSON `name` after
   unescaping `\'` (apostrophes must be XML-escaped for AAPT; `BuiltInExerciseNamesTest`
   unescapes before comparing) — so the canonical DB name and the English display
   string can never silently drift.

This also defends future seed additions ([02-data-spec](02-data-spec.md) §7): a
new exercise without a mapping/translation breaks the build rather than shipping a
half-localized library.

## 4. Language mechanism

The user gets a non-default language by changing it at the OS level — no in-app UI:

- `res/xml/locales_config.xml` lists the supported BCP-47 tags (`en`, `de`).
- `android:localeConfig="@xml/locales_config"` on `<application>` in
  `AndroidManifest.xml` (which already sets `supportsRtl="true"`).
- **Android 13+ (API 33+):** the system exposes a per-app language picker in
  Settings → Apps → LiftLog → Language, populated from `locales_config`.
- **Android 12 (minSdk 31):** the app follows the device language; per-app
  override is unavailable (an OS limitation, not an app one).
- `androidResources.localeFilters = listOf("en", "de")` in `app/build.gradle.kts`
  keeps the APK from bundling partial library translations for other locales.

**Why no in-app picker:** an in-app switcher independent of the device language
needs `AppCompatDelegate.setApplicationLocales` (AppCompat 1.6+) or a hand-rolled
`LocaleManager` + API 31–32 fallback. The app is pure Compose/Material3 with no
AppCompat dependency; adding one fails constraint #5 (justify every dependency)
for marginal benefit. The OS-level mechanism is zero-cost and zero-setup
(constraint #1). An in-app picker remains a clean future add if demanded — it
would only set the locale list, not change any of §3/§5.

## 5. Locale-correct formatting

These bugs are invisible in English (decimal point, ASCII joins) and only surface
once a non-English locale exists, so they belong in M6, not M5.

### 5.1 Hardcoded strings to externalize first

- `ui/analytics/AnalyticsScreen.kt:58` — `Text("Analytics")` → `R.string.analytics_title`.
- `ui/session/ExerciseCard.kt:201` — `"target ${card.targetSets}×"` → a
  parameterized string resource.
- A full `lint` sweep with `HardcodedText` confirms there are no others; CI keeps
  the gate on (§7).

### 5.2 Numbers & weights

- **Decimal separator.** `domain/units/Weights.format` (≈L34–38) renders via
  `BigDecimal(...).stripTrailingZeros().toPlainString()`, which always emits `.`;
  German expects `,`. Centralize display formatting to be locale-aware
  (`NumberFormat`/`DecimalFormat` for `Locale.getDefault()`) while preserving the
  "max 2 decimals, trailing zeros stripped" rule ([02-data-spec](02-data-spec.md) §5).
- **Entry must match display.** `ui/components/InlineNumpad.kt` shows a `.` key
  (L168) and parses with `"%.2f".format(...)` / `.toDouble()` (≈L84). The decimal
  key label and the parser must follow the locale separator, so entry and display
  stay coherent (a German user types and sees `,`). The canonical stored value is
  still kg `Double` — only the string boundary changes.
- **Volume & RPE.** `AnalyticsScreen.kt:101` (`"%.1ft"`) and
  `ui/components/LoggedSetRow.kt:115` (RPE `"%.1f"`) use the default locale's
  number rules but bake in formatting/suffix; route through locale-aware number
  formatting + a translatable unit suffix (the "t" tonnes label).

### 5.3 Composed strings & joins

Route composed display strings through string-resource templates so translators
control them: `ExerciseCard.kt` `× / ·` joins (≈L434–436), `ui/plans/PlansScreen.kt:279`
(`"$count · $groups"`), and the rep-range en-dash in `domain/logging/Targets.kt:7–9`
(`"$min–$max"`). `×` and `–` likely stay as-is but become translator-overridable.

> Already correct, leave alone: `ui/components/TrendBadge.kt:39` and
> `SessionDetailScreen.kt:198` pin `Locale.US` deliberately; date formatting uses
> `Locale.getDefault()` / `FormatStyle` (`SessionDetailScreen.kt:163`,
> `ExerciseDetailScreen.kt`, `SettingsScreen.kt`); elapsed-time `"%d:%02d:%02d"`
> is locale-safe.

## 6. Interactions & non-changes

- **Export / import: unchanged, no `formatVersion` bump.** Built-ins export their
  English canonical `name`; on import they re-localize via stable UUID. Custom
  names round-trip verbatim. Stated explicitly so reviewers don't expect a schema
  touch. The round-trip test (§7) gains a re-localization assertion.
- **RTL:** `supportsRtl="true"` is already set; German is LTR, so no layout work
  now. RTL languages (Arabic/Hebrew) would need a mirroring audit — future, out of
  scope.
- **Accessibility:** content descriptions live in `strings.xml` and translate
  automatically. The M5 audit ([07-accessibility-audit](07-accessibility-audit.md))
  was English-only — add a German TalkBack spot-check (§7).

## 7. Testing

- **Seed ↔ resource sync** (JVM): the §3.4 drift guard.
- **Locale-aware formatting** (JVM): `Weights.format(82.5, KG)` → `"82,5 kg"` under
  `Locale.GERMANY`, `"82.5 kg"` under `Locale.US`; numpad parse accepts the locale
  separator.
- **Localized search** (Compose UI): with the German config, picker search for a
  German term resolves the correct built-in — the §3.3 requirement.
- **Translation completeness**: keep lint `MissingTranslation` and `HardcodedText`
  unsuppressed; CI (`./gradlew lint`) fails on a missing `values-de` key.
- **Export round-trip**: extend the existing M5 test to assert built-in names
  re-localize after import.
- **German TalkBack spot-check**: on-device pass over the logging hot path.

## 8. PR breakdown

Mirrors the M5 three-PR pattern; each PR builds + lints + tests green on its own.

1. **PR1 — Foundations & locale-correctness.** Externalize stragglers (§5.1),
   keep lint gates on, make number/weight formatting + numpad entry locale-aware
   (§5.2), route composed joins through templates (§5.3), add `locales_config.xml`
   + manifest + `localeFilters`. Still English, now correct and translation-ready.
2. **PR2 — Built-in name localization.** `exercise_*` English keys,
   `BuiltInExerciseNames` map, `ExerciseNameResolver`, wire into all render sites
   and into picker search/sort (§3.2–3.3), the drift-guard test (§3.4). Mechanism
   in place; values still English.
3. **PR3 — German translation.** `values-de/strings.xml` for all UI strings,
   muscle/equipment labels, and the 331 exercise names; localized-search UI test;
   German TalkBack spot-check.

## 9. Sequencing & exit criteria

**Sequencing vs M5.** M6 follows M5. M5 ships English-only v1; the locale-correctness
fixes (§5) are invisible in English, so they need not block the M5 release. If the
owner prefers a bilingual launch, the v1 release tag waits for M6 — owner's call,
recorded here so it isn't relitigated.

**Exit criteria.**

- App renders fully in German when the device/per-app language is German; no
  hardcoded UI text remains (lint `HardcodedText` clean).
- All 331 built-in exercises display localized names; custom exercises render
  verbatim; the seed↔resource drift test is green.
- Picker search and sort operate on localized names (UI test green).
- Weights, volume, and RPE format with the locale decimal separator; numpad entry
  accepts it; the formatting test is green.
- `lint` (incl. `MissingTranslation`) + `testDebugUnitTest` + `assembleDebug`
  green in CI; export round-trip re-localizes built-ins.

**Future (out of scope):** additional languages (pure `values-xx/` + exercise
translation work), an in-app language picker, and RTL support.
