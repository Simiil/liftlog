package de.simiil.liftlog.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [DataStore] fake for JVM tests: [updateData] writes into a [MutableStateFlow], so
 * writes are immediately visible to [data] collectors — no real disk I/O needed. Shared between
 * [de.simiil.liftlog.data.repository.SettingsRepositoryTest] and
 * [de.simiil.liftlog.data.repository.PlanRepositoryTest] (issue #30 PR1).
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
