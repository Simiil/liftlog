package de.simiil.liftlog.di

import kotlin.test.Test
import kotlin.test.assertTrue

class IosAppInfoTest {
    @Test
    fun versionName_neverBlank() {
        // Under simctl spawn there is no real app bundle; the provider must still
        // yield a non-blank version (falls back to "dev"), and never crash.
        val info = iosAppInfo()
        assertTrue(info.versionName.isNotBlank())
        assertTrue(info.name == "LiftLog")
    }
}
