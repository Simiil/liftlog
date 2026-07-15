package de.simiil.liftlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.simiil.liftlog.domain.model.ThemePreference

/** Pure resolution of the manual theme toggle (00-product-spec §5.8). */
fun resolveDarkTheme(
    preference: ThemePreference,
    systemInDarkTheme: Boolean,
): Boolean =
    when (preference) {
        ThemePreference.SYSTEM -> systemInDarkTheme
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

@Composable
fun LiftLogTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    // minSdk 31 ⇒ dynamic color is always available (01-architecture §6);
    // the flag exists so previews can pin the static palette.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themePreference, isSystemInDarkTheme())
    val colorScheme =
        when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
            dynamicColor -> dynamicLightColorScheme(LocalContext.current)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiftLogColors provides extendedColorsFor(darkTheme),
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
