package de.sleisering.liftlog

import app.cash.turbine.test
import de.sleisering.liftlog.domain.model.ThemePreference
import de.sleisering.liftlog.testing.FakeSettingsRepository
import de.sleisering.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `defaults to SYSTEM and follows repository updates`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MainViewModel(repository)

        viewModel.themePreference.test {
            assertEquals(ThemePreference.SYSTEM, awaitItem())
            repository.setThemePreference(ThemePreference.DARK)
            assertEquals(ThemePreference.DARK, awaitItem())
        }
    }
}
