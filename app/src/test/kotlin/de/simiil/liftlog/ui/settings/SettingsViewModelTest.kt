package de.simiil.liftlog.ui.settings

import app.cash.turbine.test
import de.simiil.liftlog.data.seed.SyntheticHistorySeeder
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun noOpSeeder() = SyntheticHistorySeeder(FakeSessionDao(), Clock.systemUTC())

    @Test
    fun `selecting a theme persists it and updates ui state`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, noOpSeeder())

        viewModel.uiState.test {
            assertEquals(ThemePreference.SYSTEM, awaitItem().theme)
            viewModel.onThemeSelected(ThemePreference.DARK)
            assertEquals(ThemePreference.DARK, awaitItem().theme)
        }
    }
}
