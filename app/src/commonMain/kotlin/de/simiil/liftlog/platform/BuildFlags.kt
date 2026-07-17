package de.simiil.liftlog.platform

/**
 * Whether this is a debug build. Gates dev-only affordances (e.g. the "seed demo data" button in
 * [de.simiil.liftlog.ui.settings.SettingsScreen]). Android reads `BuildConfig.DEBUG`; iOS reads the
 * Kotlin/Native `Platform.isDebugBinary` flag.
 */
expect val isDebugBuild: Boolean
