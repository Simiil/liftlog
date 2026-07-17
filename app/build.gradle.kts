import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.room.gradle.plugin)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "LiftLogKit"
            // isStatic is the CMP-recommended default: the framework is embedded directly
            // into the Xcode app target (M8) rather than distributed as a dynamic .framework.
            isStatic = true
            binaryOption("bundleId", "de.simiil.liftlog.kit")
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        // Production repositories + SyntheticHistorySeeder mint RFC-4122 ids via kotlin.uuid.Uuid
        // (java.util.UUID is JVM-only). Four commonMain files need it, so opt in module-wide here
        // rather than annotating each (PR4 Task 5 reviewer guidance).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform artifacts (the `compose.*` DSL accessors are
            // ERROR-deprecated in CMP 1.11.1; these are the coordinates they map to).
            // The koin BOM platform is applied via the top-level dependencies block
            // because platform() was removed from the KMP source-set DSL in Kotlin 2.3 (KT-58759).
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.material.icons.extended)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.components.resources)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            // JetBrains multiplatform fork of navigation-compose (same androidx.navigation.*
            // packages). On the Android target its release variant depends directly on the real
            // androidx.navigation:navigation-compose, so ui/navigation/** and MainActivity see
            // the identical classes they did before this swap; the fork only supplies its own
            // implementation for non-Android targets (iOS here).
            implementation(libs.jetbrains.navigation.compose)
            // Data layer (Room DB, DAOs, entities, repositories) is common as of PR4 Task 5.
            implementation(libs.androidx.room.runtime)
            // datastore-preferences-core carries the KMP DataStore<Preferences> API used by the
            // settings/plan repositories; the android-only `preferencesDataStoreFile` helper (used
            // only by the Android platformModule) stays in androidMain via datastore-preferences.
            implementation(libs.androidx.datastore.preferences.core)
            // BundledSQLiteDriver: Room reads/writes via SQLite compiled from source rather than the
            // OS's framework SQLite, so behavior is identical across platforms. The DB builder that
            // installs the driver lives in each platform's platformModule.
            implementation(libs.androidx.sqlite.bundled)
            // Drag-to-reorder for the plan/day-editor lists (ui/plans/**, common as of PR5 Task 4).
            // reorderable 3.x is a Compose Multiplatform library (publishes iosArm64/iosSimulatorArm64
            // variants), so those screens compile for iOS too.
            implementation(libs.reorderable)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // @Preview functions live in androidMain (ui/**/*Previews.kt) and import
            // androidx.compose.ui.tooling.preview.Preview. The composable screens/components they
            // preview are in commonMain (PR5 Task 4); previews stay Android-only because the CMP
            // @Preview annotation is platform-split (this artifact resolves to androidx's
            // ui-tooling-preview on Android). On the Android target this CMP artifact maps to
            // androidx.compose.ui:ui-tooling-preview, so the imports are unchanged.
            implementation(libs.compose.mp.ui.tooling.preview)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            // lifecycle-runtime-compose / lifecycle-viewmodel-compose are no longer declared
            // directly: the JetBrains navigation-compose fork (commonMain, above) pulls in its
            // own org.jetbrains.androidx.lifecycle:{lifecycle-runtime-compose,lifecycle-viewmodel-compose},
            // and on the Android target each of *those* depends on the real
            // androidx.lifecycle:lifecycle-{runtime,viewmodel}-compose in turn (verified via the
            // published Gradle module metadata) — so the classes this app uses
            // (collectAsStateWithLifecycle, viewModel-in-compose glue) still arrive transitively.
            implementation(libs.androidx.lifecycle.process)
            // datastore-preferences (not -core): the android platformModule uses the
            // `preferencesDataStoreFile` helper to locate the on-disk settings file.
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.koin.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.koin.test)
            implementation(libs.koin.test.junit4)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core.ktx)
            // GrantPermissionRule: pre-grants POST_NOTIFICATIONS so the contextual prompt (#36)
            // can't obscure Active-Session UI tests
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.room.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            // koin-bom / compose-bom platforms are applied via the top-level dependencies
            // block (platform() was removed from the KMP source-set DSL in Kotlin 2.3, KT-58759).
            implementation(libs.koin.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            // espresso 3.7.0 fixes the API-36 InputManager.getInstance reflection crash, so Compose UI
            // tests now run on local Android-16 devices too (previously CI-only). See CLAUDE.md.
            implementation(libs.androidx.test.espresso.core)
        }
    }
}

android {
    namespace = "de.simiil.liftlog"
    compileSdk = 36

    defaultConfig {
        // applicationId is TBD with the app name (01-architecture §2)
        applicationId = "de.simiil.liftlog"
        minSdk = 31
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
        testInstrumentationRunner = "de.simiil.liftlog.KoinTestRunner"
        androidResources.localeFilters += listOf("en", "de")
    }

    signingConfigs {
        create("release") {
            // Release signing happens in CI only (GitHub secrets → env vars). Local
            // builds are debug builds, so this stays unconfigured off-CI and the
            // release buildType falls back to debug signing below. (05-roadmap M5)
            System.getenv("KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking + resource shrinking; keep rules in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // CI provides the keystore via env; otherwise fall back to debug signing
            // so a local `bundleRelease` still yields an installable artifact.
            signingConfig =
                if (System.getenv("KEYSTORE_FILE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

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
}

// Room 2.8's Gradle plugin (`androidx.room`) replaces the raw `ksp { arg("room.schemaLocation", ...) }`
// wiring. It also auto-registers a copy task that adds the exported schemas into the
// androidTest assets source set for MigrationTestHelper — this supersedes the old manual
// `android { sourceSets.getByName("androidTest").assets.srcDir(...) }` line (removed above);
// keeping both would double-add the same asset paths and fail AGP's asset merge.
room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

dependencies {
    // Room's KSP compiler must run per-target: the Android APT plus each iOS klib target, so the
    // @ConstructedBy actuals + DAO/query implementations are generated for iOS too (PR4 Task 5).
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    // BOM platforms live here (not in the KMP source-set DSL) because Kotlin 2.3 removed
    // platform() from KotlinDependencyHandler (KT-58759). platform() below is the standard
    // Gradle DependencyHandler.platform, which is unaffected.
    add("commonMainImplementation", platform(libs.koin.bom))
    add("androidInstrumentedTestImplementation", platform(libs.koin.bom))
    add("androidInstrumentedTestImplementation", platform(libs.compose.bom))
    // compose-bom supplies the version for the debug-only ui-test-manifest.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.mp.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
