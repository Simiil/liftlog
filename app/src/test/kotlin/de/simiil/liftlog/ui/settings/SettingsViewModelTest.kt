package de.simiil.liftlog.ui.settings

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selecting a theme persists it and updates ui state`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ThemePreference.SYSTEM, awaitItem().theme)
            viewModel.onThemeSelected(ThemePreference.DARK)
            assertEquals(ThemePreference.DARK, awaitItem().theme)
        }
    }
}
