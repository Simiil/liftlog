package de.simiil.liftlog.ui.exercises

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.data.seed.ExerciseSeeder
import de.simiil.liftlog.data.seed.SeedFile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import liftlog.app.generated.resources.Res
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuiltInExerciseNameBindingTest {
    @Test fun everyBuiltInResolvesToItsEnglishSeedName() =
        runTest {
            // Seed file + strings are now CMP bundled resources; resById maps to StringResource
            // handles resolved via the suspend getString() (default/English locale on the device).
            val text = Res.readBytes(ExerciseSeeder.ASSET).decodeToString()
            val seed = Json { ignoreUnknownKeys = true }.decodeFromString<SeedFile>(text)
            seed.exercises.forEach { e ->
                val res =
                    BuiltInExerciseNames.resById[e.id]
                        ?: error("no resource mapped for ${e.name} (${e.id})")
                assertEquals(e.name, getString(res))
            }
        }
}
