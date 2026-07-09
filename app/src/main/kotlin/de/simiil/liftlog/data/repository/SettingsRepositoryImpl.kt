package de.simiil.liftlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override val themePreference: Flow<ThemePreference> =
            dataStore.data.map { preferences ->
                ThemePreference.fromStorageValue(preferences[KEY_THEME])
            }

        override val weightUnit: Flow<WeightUnit> =
            dataStore.data.map { preferences ->
                WeightUnit.fromStorageValue(preferences[KEY_WEIGHT_UNIT])
            }

        override val notificationPromptShown: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[KEY_NOTIFICATION_PROMPT_SHOWN] ?: false
            }

        override suspend fun setThemePreference(preference: ThemePreference) {
            dataStore.edit { preferences ->
                preferences[KEY_THEME] = preference.name
            }
        }

        override suspend fun setWeightUnit(unit: WeightUnit) {
            dataStore.edit { preferences ->
                preferences[KEY_WEIGHT_UNIT] = unit.name
            }
        }

        override suspend fun setNotificationPromptShown() {
            dataStore.edit { preferences ->
                preferences[KEY_NOTIFICATION_PROMPT_SHOWN] = true
            }
        }

        private companion object {
            // Key name mirrors the export format's settings object (02-data-spec §6)
            val KEY_THEME = stringPreferencesKey("theme")
            val KEY_WEIGHT_UNIT = stringPreferencesKey("weight_unit")

            // Device-local; excluded from export/import by construction (02-data-spec §6)
            val KEY_NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        }
    }
