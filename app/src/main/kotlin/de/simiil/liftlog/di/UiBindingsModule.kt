package de.simiil.liftlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import de.simiil.liftlog.ui.exercises.ResourceExerciseNameResolver

@Module
@InstallIn(SingletonComponent::class)
abstract class UiBindingsModule {
    @Binds abstract fun bindExerciseNameResolver(
        impl: ResourceExerciseNameResolver,
    ): ExerciseNameResolver
}
