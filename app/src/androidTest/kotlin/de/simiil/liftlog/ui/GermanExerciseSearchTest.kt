package de.simiil.liftlog.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.exercises.BuiltInExerciseNames
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GermanExerciseSearchTest {
    @Test fun benchPressResolvesToGermanUnderDeLocale() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val deConfig = Configuration(base.resources.configuration).apply { setLocale(Locale.GERMAN) }
        val de = base.createConfigurationContext(deConfig)
        val benchId = "7a0737bd-d46f-4dd1-9dad-ed3e4a83869a" // Barbell Bench Press
        val resId = BuiltInExerciseNames.resById.getValue(benchId)
        assertEquals(de.getString(R.string.exercise_barbell_bench_press), de.getString(resId))
        assertEquals("Langhantel-Bankdrücken", de.getString(resId))
    }
}
