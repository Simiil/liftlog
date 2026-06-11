package de.simiil.liftlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createTestDataStore(): DataStore<Preferences> =
        object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(emptyPreferences())
        }

    @Test
    fun `weightUnit defaults to KG when unset`() =
        runTest {
            val dataStore = createTestDataStore()
            val repo = SettingsRepositoryImpl(dataStore)

            val result = repo.weightUnit.first()
            assertEquals(WeightUnit.KG, result)
        }

    @Test
    fun `themePreference defaults to SYSTEM when unset`() =
        runTest {
            val dataStore = createTestDataStore()
            val repo = SettingsRepositoryImpl(dataStore)

            val result = repo.themePreference.first()
            assertEquals(ThemePreference.SYSTEM, result)
        }

    private fun persistingDataStore(): DataStore<Preferences> =
        object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data = state

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                val updated = transform(state.value)
                state.value = updated
                return updated
            }
        }

    @Test
    fun `setWeightUnit persists and re-reads`() =
        runTest {
            val repo = SettingsRepositoryImpl(persistingDataStore())
            repo.setWeightUnit(WeightUnit.LB)
            assertEquals(WeightUnit.LB, repo.weightUnit.first())
        }
}
