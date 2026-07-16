package de.simiil.liftlog.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Translation-parity gate for the Compose Multiplatform string catalogue. CMP resources are not
 * covered by Android Lint's MissingTranslation (that still guards the residual android res in
 * src/androidMain), so this test locks en ↔ de key parity for the moved strings/plurals (issue #47).
 */
class StringsParityTest {
    private fun keys(path: String): Set<String> {
        val xml = File(path).readText()
        return (
            Regex("<string name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] } +
                Regex("<plurals name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }
        ).toSet()
    }

    @Test fun germanCoversEnglish() {
        val en = keys("src/commonMain/composeResources/values/strings.xml")
        val de = keys("src/commonMain/composeResources/values-de/strings.xml")
        assertEquals("untranslated keys", emptySet<String>(), en - de)
        assertEquals("orphaned German keys", emptySet<String>(), de - en)
    }
}
