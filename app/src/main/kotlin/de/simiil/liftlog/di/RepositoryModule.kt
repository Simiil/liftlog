package de.simiil.liftlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.simiil.liftlog.data.repository.AnalyticsRepositoryImpl
import de.simiil.liftlog.data.repository.ExerciseRepositoryImpl
import de.simiil.liftlog.data.repository.PlanRepositoryImpl
import de.simiil.liftlog.data.repository.SessionRepositoryImpl
import de.simiil.liftlog.data.repository.SettingsRepositoryImpl
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.ExerciseRepository
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.domain.repository.SessionRepository
import de.simiil.liftlog.domain.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository
    @Binds abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository
    @Binds abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
    @Binds abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository
}
