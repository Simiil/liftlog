# 01 — Architecture

> **Status:** Draft for review · 2026-06-07
> **Fixed by [HANDOFF.md](../HANDOFF.md) §2:** Kotlin + Jetpack Compose, Room, MVVM + UDF + repository pattern, Coroutines/Flow, Hilt, Material 3. This doc concretizes those decisions.

## 1. Layering

```
┌─────────────────────────────────────────────────────┐
│  ui/          Compose screens + Navigation           │
│               (stateless composables, previewable)   │
└───────────────▲─────────────────────────┬───────────┘
        UiState (StateFlow)          UI events (fn calls)
┌───────────────┴─────────────────────────▼───────────┐
│  ViewModels   one per screen, UDF:                   │
│               state down, events up                  │
└───────────────▲─────────────────────────┬───────────┘
                │   repository INTERFACES (domain/)    │
┌───────────────┴─────────────────────────▼───────────┐
│  domain/      pure Kotlin: models, repo interfaces,  │
│               analytics formulas (no Android deps)   │
└───────────────▲─────────────────────────────────────┘
                │   implementations
┌───────────────┴─────────────────────────────────────┐
│  data/        Room (single source of truth),         │
│               DataStore (settings), repo impls,      │
│               seed data                              │
└──────────────────────────────────────────────────────┘
```

Rules that make this real (enforced in review, lint-able later):

- ViewModels depend **only on domain repository interfaces** — never on DAOs, `AppDatabase`, or `Context`.
- `domain/` has **no Android imports**. Analytics formulas, trend classification, and unit conversion are pure functions → trivially unit-testable ([04-analytics-spec](04-analytics-spec.md)).
- Reads are exposed as `Flow` (Room invalidation drives reactive UI updates, including live chart refresh mid-session). Writes are `suspend` functions.
- Composables are stateless where practical; screen state lives in the ViewModel as a single immutable `UiState` data class.

## 2. Module & package structure

**Single Gradle module (`:app`).** A solo-developer app of this size gains nothing from multi-module builds except configuration overhead; the package boundaries below mirror the layers exactly, so extracting modules later (e.g. `:domain` for build-time isolation) is mechanical. Revisit if build times or team size grow.

```
de.sleisering.liftlog/          (applicationId TBD with app name)
├── data/
│   ├── db/            AppDatabase, type converters
│   ├── entity/        Room entities (suffix: *Entity)
│   ├── dao/           DAOs
│   ├── repository/    repository implementations
│   └── seed/          built-in exercise library (JSON asset + seeder)
├── domain/
│   ├── model/         domain models (Exercise, Session, …)
│   ├── repository/    repository interfaces
│   └── analytics/     pure formula + trend functions
├── export/            backup file format, serializers, import validation
├── ui/
│   ├── theme/         M3 theme, dynamic color, manual toggle
│   ├── navigation/    nav graph, destinations
│   ├── components/    shared: inline numpad, steppers, charts wrapper
│   ├── home/  session/  exercises/  plans/  history/  analytics/  settings/
│   └── …               (one package per screen/feature: Screen + ViewModel + UiState)
└── di/                Hilt modules
```

## 3. MVVM / UDF conventions

- One ViewModel per screen; exposes `val uiState: StateFlow<XyzUiState>` built with `stateIn(viewModelScope, WhileSubscribed(5s), initial)`.
- UI events are plain ViewModel functions (`onLogSet()`, `onWeightChanged(…)`); no event-bus indirection.
- Navigation via Navigation Compose with type-safe routes; the active session is a full-screen destination above the bottom bar ([03-ux-spec](03-ux-spec.md) §2).
- Process-death safety comes from Room, not `SavedStateHandle` heroics: every logged set and the open session row are persisted immediately ([02-data-spec](02-data-spec.md) §4). `SavedStateHandle` carries only trivial transient UI state (e.g. which card is expanded).

## 4. Sync-ready, sync-free (HANDOFF §2 constraint)

The entire insurance policy lives in exactly two places — nothing else in the app knows sync might exist:

1. **Entity conventions** ([02-data-spec](02-data-spec.md) §2): UUID string PKs, `createdAt`/`updatedAt`, nullable `deletedAt` tombstones, soft-delete-only repositories.
2. **The repository seam**: ViewModels see only `domain/repository` interfaces. A future sync engine is a new data source composed *behind* those interfaces (local-first write-through + background reconciliation) — no ViewModel or UI change required.

Explicitly **not** done in v1: no sync metadata tables, no change journal, no network stack, no conflict-resolution logic, no sync UI. Tombstones are never purged in v1 (volumes are tiny; a retention policy is a future-sync concern).

## 5. Dependencies (all of them, each justified)

| Dependency | Why | Why not alternatives |
|---|---|---|
| Compose BOM + Material 3 | Fixed by handoff | — |
| Navigation Compose | Standard type-safe nav for Compose | Simpler hand-rolled nav loses deep-link/back-stack handling for free |
| Room (+ KTX) | Fixed by handoff; SQLite SSOT | — |
| Hilt (+ hilt-navigation-compose) | Fixed by handoff | — |
| DataStore (Preferences) | Settings (unit, theme): tiny, typed, Flow-based | SharedPreferences is legacy; Room is overkill for 3 keys |
| kotlinx.serialization | Export/import JSON ([02-data-spec](02-data-spec.md) §6) | Kotlin-first, no reflection, compile-time safe; Moshi adds a second codegen stack, Gson is effectively unmaintained |
| **Vico** (compose charts) | Line/column charts with time axes; actively maintained, Apache-2.0, Compose-native | MPAndroidChart is View-based + dormant; hand-rolled Canvas is the documented **fallback** if Vico disappoints — it sits behind our own `ui/components` chart wrapper so swapping it touches one package |
| JUnit, kotlinx-coroutines-test, Turbine | Unit tests: domain formulas, ViewModels, Flow assertions | Standard stack |
| Compose UI Test, Room testing (instrumented) | The critical-path UI test + DAO tests | Standard stack |

No image loading, no logging framework (use `Log`), no analytics/crash SDK (would violate selling point 1), no desugaring (API 31+ has `java.time`).

## 6. Min SDK proposal

**minSdk = 31 (Android 12), targetSdk = latest stable.** Decided with project owner (HANDOFF §9.2).

- Native Material You dynamic color — no fallback-palette codepath to build and test (a static brand palette still exists for users who disable dynamic color, but no API-level branching).
- `java.time`, modern `PendingIntent`/splash-screen behavior without compat shims.
- Cost: excludes roughly the oldest ~15% of active devices (API 26–30, shrinking monthly). Accepted: this is a new app with zero install base; the audience (people actively gym-going with a logging habit) skews toward recent devices.
- Phones only; layouts are width-responsive but tablets are explicitly untuned ([00-product-spec](00-product-spec.md) §4).

## 7. Testing strategy (HANDOFF §7)

| Layer | What | How |
|---|---|---|
| `domain/analytics` | e1RM, volume, trend classification, downsampling — **the highest-value tests in the app** | Plain JUnit against hand-computed fixtures ([04-analytics-spec](04-analytics-spec.md) §5) |
| `domain` misc | Unit conversion, pre-fill selection logic | Plain JUnit |
| `data` | DAO queries (hot-path queries, soft-delete filtering, cascades), seeder idempotency | Instrumented Room tests (in-memory DB) via Gradle-managed devices |
| `export` | Round-trip (export → import → byte-equal domain state), version/corrupt-file rejection | JUnit + golden files |
| ViewModels | State emission per event | JUnit + Turbine + coroutines-test |
| UI | **One deep Compose test of the critical logging path** (start from template → log pre-filled set → adjust weight → log → process-death restore) | Compose UI Test |

Coverage philosophy: exhaustive on pure logic, selective-but-deep on UI — the logging flow gets a real test, screens that are mostly layout get previews, not tests.

## 8. CI proposal (implement at M0, not before)

GitHub Actions, single workflow on PR + push to `main`:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - checkout, setup-java (Temurin 17), gradle/actions/setup-gradle (with caching)
      - ./gradlew lint testDebugUnitTest assembleDebug
  instrumented:        # added at M1 when DAO tests exist
    steps:
      - ./gradlew pixelApi34DebugAndroidTest   # Gradle-managed device
```

Branch protection on `main` once CI exists. No release/signing automation in v1 scope.
