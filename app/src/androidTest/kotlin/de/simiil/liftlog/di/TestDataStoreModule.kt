package de.simiil.liftlog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.UUID
import javax.inject.Singleton

/**
 * Replaces [DataStoreModule] in instrumented tests. The production module points every DataStore at
 * the single file "settings"; because Hilt creates a fresh SingletonComponent per `@HiltAndroidTest`
 * method, a second test would open a SECOND DataStore on that same file while the first one's scope
 * is still alive — Preferences DataStore throws "There are multiple DataStores active for the same
 * file". Giving each test instance a unique file keeps every test isolated and conflict-free.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("settings_test_${UUID.randomUUID()}")
        }
}
