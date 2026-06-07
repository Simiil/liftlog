package de.sleisering.liftlog.testing

import de.sleisering.liftlog.domain.model.ThemePreference
import de.sleisering.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    initial: ThemePreference = ThemePreference.SYSTEM,
) : SettingsRepository {
    private val theme = MutableStateFlow(initial)

    override val themePreference: Flow<ThemePreference> = theme

    override suspend fun setThemePreference(preference: ThemePreference) {
        theme.value = preference
    }
}
