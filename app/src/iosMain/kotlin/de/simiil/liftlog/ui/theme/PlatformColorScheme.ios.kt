package de.simiil.liftlog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// iOS has no Material You / wallpaper-derived dynamic color concept; LiftLogTheme falls back to
// the static DarkColorScheme/LightColorScheme palette (Color.kt).
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
