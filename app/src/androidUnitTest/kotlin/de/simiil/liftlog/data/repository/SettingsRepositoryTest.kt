package de.simiil.liftlog.data.repository

import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.testing.InMemoryPreferencesDataStore
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `weightUnit defaults to KG when unset`() =
        runTest {
            val dataStore = InMemoryPreferencesDataStore()
            val repo = SettingsRepositoryImpl(dataStore)

            val result = repo.weightUnit.first()
            assertEquals(WeightUnit.KG, result)
        }

    @Test
    fun `themePreference defaults to SYSTEM when unset`() =
        runTest {
            val dataStore = InMemoryPreferencesDataStore()
            val repo = SettingsRepositoryImpl(dataStore)

            val result = repo.themePreference.first()
            assertEquals(ThemePreference.SYSTEM, result)
        }

    @Test
    fun `setWeightUnit persists and re-reads`() =
        runTest {
            val repo = SettingsRepositoryImpl(InMemoryPreferencesDataStore())
            repo.setWeightUnit(WeightUnit.LB)
            assertEquals(WeightUnit.LB, repo.weightUnit.first())
        }
}
