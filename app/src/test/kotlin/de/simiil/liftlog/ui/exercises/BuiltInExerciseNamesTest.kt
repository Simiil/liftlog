package de.simiil.liftlog.ui.exercises

import de.simiil.liftlog.data.seed.SeedFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BuiltInExerciseNamesTest {
    private val seed =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<SeedFile>(File("src/main/assets/seed/exercises.v1.json").readText())

    /** name="exercise_xxx" -> "Display Value" parsed straight from the English strings.xml. */
    private val exerciseStrings: Map<String, String> =
        run {
            val xml = File("src/main/res/values/strings.xml").readText()
            // NOTE: exercise_* values must stay plain text (no XML entities, escaped
            // apostrophes, or CDATA) — this regex reads them verbatim.
            Regex("""<string name="(exercise_[a-z0-9_]+)">(.*?)</string>""")
                .findAll(xml)
                .associate { it.groupValues[1] to it.groupValues[2] }
        }

    @Test fun mapCoversExactlyTheSeedIds() {
        assertEquals(seed.exercises.map { it.id }.toSet(), BuiltInExerciseNames.resById.keys)
    }

    @Test fun everyBuiltInHasMapEntry() {
        assertEquals(seed.exercises.size, BuiltInExerciseNames.resById.size)
    }

    @Test fun englishResourceValuesMatchSeedNames() {
        // Set equality: every seed name has a matching exercise_* English value and vice-versa.
        assertEquals(seed.exercises.map { it.name }.toSet(), exerciseStrings.values.toSet())
    }
}
