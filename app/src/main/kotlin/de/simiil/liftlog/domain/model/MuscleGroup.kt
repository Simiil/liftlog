package de.simiil.liftlog.domain.model

/** Exercise classification (02-data-spec §3). */
enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS,
    HAMSTRINGS, GLUTES, CALVES, ABS, FOREARMS, OTHER;

    companion object {
        /** Unknown/absent persisted values fall back to [OTHER] (corruption/future-version safety). */
        fun fromStorageValue(value: String?): MuscleGroup =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
