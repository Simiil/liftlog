package de.simiil.liftlog.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.ui.exercises.BuiltInExerciseNames
import kotlinx.coroutines.test.runTest
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.exercise_barbell_bench_press
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GermanExerciseSearchTest {
    /** CMP's non-composable getString() resolves against the platform default locale; force German
     *  here to prove the built-in resource map yields German names (was context.getString + R.string). */
    @Test fun benchPressResolvesToGermanUnderDeLocale() =
        runTest {
            val original = Locale.getDefault()
            Locale.setDefault(Locale.GERMAN)
            try {
                val benchId = "7a0737bd-d46f-4dd1-9dad-ed3e4a83869a" // Barbell Bench Press
                val res = BuiltInExerciseNames.resById.getValue(benchId)
                assertEquals(getString(Res.string.exercise_barbell_bench_press), getString(res))
                assertEquals("Langhantel-Bankdrücken", getString(res))
            } finally {
                Locale.setDefault(original)
            }
        }
}
