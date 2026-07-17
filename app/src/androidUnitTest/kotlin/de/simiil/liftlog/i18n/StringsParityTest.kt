package de.simiil.liftlog.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Translation-parity gate for the Compose Multiplatform string catalogue. CMP resources are not
 * covered by Android Lint's MissingTranslation (that still guards the residual android res in
 * src/androidMain), so this test locks en ↔ de key parity for the moved strings/plurals (issue #47).
 *
 * It also gates the *format-placeholder* bug class behind issue #47: CMP 1.11.1's resource-string
 * substitution (`org.jetbrains.compose.resources.StringResourcesUtilsKt`, decompiled to confirm)
 * runs a single fixed regex, `%(\d+)\$[ds]`, over the raw string — nothing else. That means:
 *  - only plain positional `%N$s` / `%N$d` tokens are substituted;
 *  - a width/zero-pad flag (`%2$02d`) does not match the regex and passes through *literally*
 *    (this shipped as a real bug: `session_duration_value` rendered "2:%2$02d" in History →
 *    workout detail instead of "2:05");
 *  - Android/aapt2's `%%`-escapes-a-literal-percent convention (java.util.Formatter semantics)
 *    does *not* apply here — CMP never removes an unmatched `%`, so a resource written as `%%`
 *    (the aapt2-correct way to emit one literal `%` in a formatted string) renders as two literal
 *    percent characters, not one (this shipped as a second live bug in `trend_up`/`trend_down`/
 *    `trend_flat`/`analytics_week_delta`, e.g. "↑ 5%%" instead of "↑ 5%").
 *
 * The two tests below make both failure modes a hard build gate: [noUnsupportedFormatSpecifiers]
 * rejects any specifier shape CMP can't substitute, and [placeholdersMatchBetweenLocales] rejects
 * any en/de drift in which positional placeholders a string uses.
 */
class StringsParityTest {
    private val enPath = "src/commonMain/composeResources/values/strings.xml"
    private val dePath = "src/commonMain/composeResources/values-de/strings.xml"

    /** The one substitution CMP 1.11.1 performs: `StringResourcesUtilsKt.SimpleStringFormatRegex`. */
    private val supportedPlaceholder = Regex("""%\d+\$[ds]""")

    private fun keys(path: String): Set<String> {
        val xml = File(path).readText()
        return (
            Regex("<string name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] } +
                Regex("<plurals name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }
        ).toSet()
    }

    /**
     * name -> every value text under that name: one entry for a `<string>`, one per `<item>` of a
     * `<plurals>` (so a plurals' "one"/"other" variants are each checked independently).
     */
    private fun stringValues(path: String): Map<String, List<String>> {
        val xml = File(path).readText()
        val values = mutableMapOf<String, MutableList<String>>()
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .forEach { m -> values.getOrPut(m.groupValues[1]) { mutableListOf() }.add(m.groupValues[2]) }
        Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .forEach { plural ->
                val name = plural.groupValues[1]
                Regex("""<item[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(plural.groupValues[2])
                    .forEach { item -> values.getOrPut(name) { mutableListOf() }.add(item.groupValues[1]) }
            }
        return values
    }

    /**
     * Everything that looks like an attempted format specifier or escape but isn't one of the
     * supported plain `%N$s`/`%N$d` tokens: a `%%` escape, a positional token with a width/flag
     * (`%2$02d`), an uppercase conversion (`%1$S`), or a flagless conversion with no position
     * (`%d`, `%s`).
     */
    private fun unsupportedSpecifiers(text: String): List<String> {
        val hits = mutableListOf<String>()
        var remaining = text
        Regex("""%%""").findAll(remaining).forEach { hits += it.value }
        remaining = remaining.replace("%%", "")
        // A positional attempt: %N$ + optional width/flag digits + exactly one conversion letter.
        // The single trailing letter (not `+`) matters: it must not swallow a literal letter that
        // merely follows a valid placeholder, e.g. the "t" in "%1$st" ("80t" for a unit suffix).
        val positional = Regex("""%\d+\$[0-9]*[A-Za-z]""")
        positional.findAll(remaining).forEach { m ->
            if (!supportedPlaceholder.matches(m.value)) hits += m.value
        }
        remaining = remaining.replace(positional, "")
        Regex("""%[A-Za-z]""").findAll(remaining).forEach { hits += it.value }
        return hits
    }

    private fun placeholders(text: String): Set<String> = supportedPlaceholder.findAll(text).map { it.value }.toSet()

    @Test fun germanCoversEnglish() {
        val en = keys(enPath)
        val de = keys(dePath)
        assertEquals("untranslated keys", emptySet<String>(), en - de)
        assertEquals("orphaned German keys", emptySet<String>(), de - en)
    }

    @Test fun noUnsupportedFormatSpecifiers() {
        for (path in listOf(enPath, dePath)) {
            val offenders =
                stringValues(path).flatMap { (name, texts) ->
                    texts.flatMap { text -> unsupportedSpecifiers(text).map { spec -> "$name: \"$spec\" in \"$text\"" } }
                }
            assertEquals(
                "$path contains format specifiers CMP resources cannot substitute " +
                    "(only plain %N\$s/%N\$d are supported; see #47)",
                emptyList<String>(),
                offenders,
            )
        }
    }

    @Test fun placeholdersMatchBetweenLocales() {
        val en = stringValues(enPath)
        val de = stringValues(dePath)
        val mismatches = mutableListOf<String>()
        for ((name, enTexts) in en) {
            val deTexts = de[name] ?: continue // key-parity itself is germanCoversEnglish's job
            if (enTexts.size != deTexts.size) {
                mismatches += "$name: en has ${enTexts.size} variant(s), de has ${deTexts.size}"
                continue
            }
            enTexts.zip(deTexts).forEachIndexed { i, (enText, deText) ->
                val enPh = placeholders(enText)
                val dePh = placeholders(deText)
                if (enPh != dePh) {
                    mismatches += "$name[$i]: en=$enPh de=$dePh"
                }
            }
        }
        assertEquals("placeholder mismatch between en/de", emptyList<String>(), mismatches)
    }
}
