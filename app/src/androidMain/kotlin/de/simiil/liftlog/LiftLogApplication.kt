package de.simiil.liftlog

import android.app.Application
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.di.AppScope
import de.simiil.liftlog.di.appModules
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.notification.SessionNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LiftLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidContext(this@LiftLogApplication)
                modules(appModules)
            }.koin
        koin.get<CoroutineScope>(AppScope).launch {
            koin.get<ExerciseSeeder>().seed()
            koin.get<DefaultPlanEnsurer>().ensure()
        }
        koin.get<SessionNotificationCoordinator>().start()
    }
}
