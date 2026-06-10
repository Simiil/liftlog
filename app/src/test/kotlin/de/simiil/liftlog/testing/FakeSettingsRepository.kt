package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    initial: ThemePreference = ThemePreference.SYSTEM,
    initialWeightUnit: WeightUnit = WeightUnit.KG,
) : SettingsRepository {
    private val theme = MutableStateFlow(initial)
    private val weightUnitState = MutableStateFlow(initialWeightUnit)

    override val themePreference: Flow<ThemePreference> = theme
    override val weightUnit: Flow<WeightUnit> = weightUnitState

    override suspend fun setThemePreference(preference: ThemePreference) {
        theme.value = preference
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        weightUnitState.value = unit
    }
}
