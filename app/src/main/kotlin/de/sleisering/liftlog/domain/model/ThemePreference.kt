package de.sleisering.liftlog.domain.model

/**
 * Manual theme override (00-product-spec §5.8). Dynamic color is always on;
 * the user only chooses system/light/dark. Persisted via SettingsRepository
 * using [name] as the storage value (matches the export format's
 * settings.theme, 02-data-spec §6).
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** Unknown or absent persisted values fall back to [SYSTEM]. */
        fun fromStorageValue(value: String?): ThemePreference =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
