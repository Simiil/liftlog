package de.sleisering.liftlog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Static fallback schemes for when dynamic color is off. Deliberately the M3
 * baseline: the brand palette is commissioned in 06-design-handoff §3 (seed
 * color + generated scheme) and replaces these before M2 ships.
 */
internal val LightColorScheme = lightColorScheme()
internal val DarkColorScheme = darkColorScheme()
