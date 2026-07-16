package de.simiil.liftlog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The platform's dynamic (wallpaper-derived) color scheme, where one exists. Android: Material
 * You's `dynamicDark/LightColorScheme(context)` (minSdk 31 ⇒ always available). iOS has no
 * equivalent concept, so the actual returns `null` and [LiftLogTheme] falls back to the static
 * [DarkColorScheme]/[LightColorScheme] palette.
 */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
