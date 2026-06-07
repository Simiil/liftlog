package de.sleisering.liftlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.sleisering.liftlog.data.repository.SettingsRepositoryImpl
import de.sleisering.liftlog.domain.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
