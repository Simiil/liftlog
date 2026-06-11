package de.simiil.liftlog

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `defaults to SYSTEM and follows repository updates`() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = MainViewModel(repository)

            viewModel.themePreference.test {
                assertEquals(ThemePreference.SYSTEM, awaitItem())
                repository.setThemePreference(ThemePreference.DARK)
                assertEquals(ThemePreference.DARK, awaitItem())
            }
        }

    @Test
    fun `persisted preference replaces the SYSTEM default`() =
        runTest {
            val repository = FakeSettingsRepository(initial = ThemePreference.DARK)
            val viewModel = MainViewModel(repository)

            viewModel.themePreference.test {
                // Under UnconfinedTestDispatcher the upstream value propagates during
                // subscription, so stateIn's SYSTEM initial is conflated away — the
                // first observed state is already the persisted value. (On device the
                // DataStore read is async; the brief SYSTEM first frame is the
                // cold-start flash tracked as an M5 follow-up.)
                assertEquals(ThemePreference.DARK, awaitItem())
            }
        }
}
