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
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // @Preview functions live in androidMain and import
            // androidx.compose.ui.tooling.preview.Preview, which is compiled into ALL
            // Android variants (not just debug). On the Android target this CMP artifact
            // maps to androidx.compose.ui:ui-tooling-preview, so the imports are unchanged.
            implementation(libs.compose.mp.ui.tooling.preview)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.koin.android)
            implementation(libs.reorderable)
            implementation(libs.androidx.room.runtime)
            // BundledSQLiteDriver: Room reads/writes via SQLite compiled from source rather than the
            // OS's framework SQLite, so behavior is identical across Android versions. Moves to
            // commonMain in PR4 Task 5 once the DB builder lives behind a platform module.
            implementation(libs.androidx.sqlite.bundled)
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
        versionCode = 5
        versionName = "0.5.0"
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
    add("kspAndroid", libs.androidx.room.compiler)
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
