package de.simiil.liftlog.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinGraphTest {
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test
    fun koinGraph_resolves() {
        // A single module's verify() only builds its definition index from its OWN mappings plus
        // whatever it `includes()` — it does not see other top-level modules' definitions. So
        // dataModule/uiModule/androidPlatformModule/viewModelModule (which depend on types bound
        // in infraModule, e.g. ExerciseSeeder -> ExerciseDao) can only be checked together by
        // wrapping the whole app graph in one module via includes(), Koin's documented pattern for
        // verifying a multi-module app (module-by-module verify()/verifyAll() would both under-report).
        // Context comes from androidContext(); SavedStateHandle from the VM factory extras.
        module { includes(appModules) }
            .verify(extraTypes = listOf(Context::class, SavedStateHandle::class, Long::class))
    }
}
