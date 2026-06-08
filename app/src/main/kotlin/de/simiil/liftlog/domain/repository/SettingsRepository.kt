package de.simiil.liftlog.domain.repository

import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * Settings live in DataStore, not Room (02-data-spec §2). Grows the
 * weight-unit preference when logging lands (M2); theme-only at M0.
 */
interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
    val weightUnit: Flow<WeightUnit>
    // setWeightUnit + the Settings toggle UI are deferred to M5 (flagged decision #1).
    suspend fun setThemePreference(preference: ThemePreference)
}
