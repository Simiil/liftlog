package de.simiil.liftlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import de.simiil.liftlog.ui.exercises.ResourceExerciseNameResolver
import de.simiil.liftlog.ui.plans.ResourceDefaultPlanNameProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class UiBindingsModule {
    @Binds abstract fun bindExerciseNameResolver(impl: ResourceExerciseNameResolver): ExerciseNameResolver

    @Binds abstract fun bindDefaultPlanNameProvider(impl: ResourceDefaultPlanNameProvider): DefaultPlanNameProvider
}
