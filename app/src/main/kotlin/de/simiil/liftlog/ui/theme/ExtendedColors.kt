package de.simiil.liftlog.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors beyond the M3 baseline scheme. The baseline has `error` (down/red) but
 * no green, so the up-trend badge needs an explicit `success`. The PR accent is NOT here —
 * it reuses the scheme's `tertiary` so it harmonizes with the seed (design decision 2026-06-09).
 */
data class LiftLogExtendedColors(
    val success: Color,
)

// Light `success` is darkened from #1E8E3E (~4.2:1 on the light surface — under WCAG AA 4.5:1
// for the small trend-badge text) to #1B7A35 (~5.4:1). Dark #81C995 is already ~9.5:1. (a11y M5)
private val LightExtended = LiftLogExtendedColors(success = Color(0xFF1B7A35)) // green 700-ish, AA on light surface
private val DarkExtended = LiftLogExtendedColors(success = Color(0xFF81C995)) // green 300-ish

internal fun extendedColorsFor(darkTheme: Boolean) = if (darkTheme) DarkExtended else LightExtended

val LocalLiftLogColors = staticCompositionLocalOf { LightExtended }
