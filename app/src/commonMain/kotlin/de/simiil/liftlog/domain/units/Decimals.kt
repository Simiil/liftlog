package de.simiil.liftlog.domain.units

/**
 * Locale-aware decimal entry/format helpers (08-i18n-spec §5.2). Pure; no platform UI deps.
 * Implementations read the platform default locale; there is no locale parameter here so that
 * common callers (ViewModels, pure domain functions) stay platform-agnostic. Tests that need a
 * specific locale pin the JVM-wide default (`Locale.setDefault`) or use the Android actual's
 * extra locale-param overloads.
 */
expect object Decimals {
    fun separator(): Char

    /** Up to 2 decimals, trailing zeros stripped, locale decimal separator. */
    fun format(value: Double): String

    /**
     * Parse user-entered text that may use the locale decimal separator.
     * Uniformly lenient: both '.' and ',' are accepted under any locale, so a
     * numpad buffer survives a per-app language switch mid-entry; "1.000" is
     * 1.0, never 1000. Safe because input comes from the in-app numpad,
     * which controls the buffer.
     */
    fun parse(text: String): Double?
}
