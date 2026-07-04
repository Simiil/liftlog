package de.simiil.liftlog

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.di.ApplicationScope
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LiftLogApplication : Application() {
    @Inject lateinit var seeder: ExerciseSeeder

    @Inject lateinit var defaultPlanEnsurer: DefaultPlanEnsurer

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seeder.seed()
            defaultPlanEnsurer.ensure()
        }
    }
}
