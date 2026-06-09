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
    suspend fun setThemePreference(preference: ThemePreference)
    /** Persistence for the kg/lb unit. Toggle UI lands in M5 PR2; import restores it now. */
    suspend fun setWeightUnit(unit: WeightUnit)
}
