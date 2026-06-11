package de.simiil.liftlog.ui.exercises

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.data.seed.SeedFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuiltInExerciseNameBindingTest {
    @Test fun everyBuiltInResolvesToItsEnglishSeedName() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val text = context.assets.open("seed/exercises.v1.json").bufferedReader().use { it.readText() }
        val seed = Json { ignoreUnknownKeys = true }.decodeFromString<SeedFile>(text)
        seed.exercises.forEach { e ->
            val resId = BuiltInExerciseNames.resById[e.id]
                ?: error("no resource mapped for ${e.name} (${e.id})")
            assertEquals(e.name, context.getString(resId))
        }
    }
}
