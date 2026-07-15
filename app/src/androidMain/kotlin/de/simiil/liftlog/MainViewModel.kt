package de.simiil.liftlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Activity-scoped: only feeds the theme into LiftLogTheme. */
class MainViewModel(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val themePreference: StateFlow<ThemePreference> =
        settingsRepository.themePreference
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.SYSTEM)
}
