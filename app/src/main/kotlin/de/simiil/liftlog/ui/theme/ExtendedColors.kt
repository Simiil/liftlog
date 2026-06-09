package de.simiil.liftlog.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors beyond the M3 baseline scheme. The baseline has `error` (down/red) but
 * no green, so the up-trend badge needs an explicit `success`. The PR accent is NOT here —
 * it reuses the scheme's `tertiary` so it harmonizes with the seed (design decision 2026-06-09).
 */
data class LiftLogExtendedColors(val success: Color)

private val LightExtended = LiftLogExtendedColors(success = Color(0xFF1E8E3E)) // green 600-ish
private val DarkExtended = LiftLogExtendedColors(success = Color(0xFF81C995))  // green 300-ish

internal fun extendedColorsFor(darkTheme: Boolean) = if (darkTheme) DarkExtended else LightExtended

val LocalLiftLogColors = staticCompositionLocalOf { LightExtended }
