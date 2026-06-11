package de.simiil.liftlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test
    fun `absent storage value falls back to SYSTEM`() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorageValue(null))
    }

    @Test
    fun `unknown storage value falls back to SYSTEM`() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorageValue("SOLARIZED"))
    }

    @Test
    fun `every preference round-trips through its storage name`() {
        ThemePreference.entries.forEach { preference ->
            assertEquals(preference, ThemePreference.fromStorageValue(preference.name))
        }
    }
}
