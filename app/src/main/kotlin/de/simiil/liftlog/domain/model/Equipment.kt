package de.simiil.liftlog.domain.model

/** Equipment classification (02-data-spec §3). Entry order drives the picker filter chip row. */
enum class Equipment {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    BODYWEIGHT,
    KETTLEBELL,
    MEDICINE_BALL,
    FOAM_ROLLER,
    BANDS,
    EXERCISE_BALL,
    OTHER,
    ;

    companion object {
        /** Unknown/absent persisted values fall back to [OTHER] (corruption/future-version safety). */
        fun fromStorageValue(value: String?): Equipment = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
