package de.simiil.liftlog.ui.theme

import de.simiil.liftlog.domain.model.ThemePreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveDarkThemeTest {

    @Test
    fun `SYSTEM follows the system setting`() {
        assertTrue(resolveDarkTheme(ThemePreference.SYSTEM, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(ThemePreference.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `LIGHT is never dark`() {
        assertFalse(resolveDarkTheme(ThemePreference.LIGHT, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(ThemePreference.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun `DARK is always dark`() {
        assertTrue(resolveDarkTheme(ThemePreference.DARK, systemInDarkTheme = true))
        assertTrue(resolveDarkTheme(ThemePreference.DARK, systemInDarkTheme = false))
    }
}
