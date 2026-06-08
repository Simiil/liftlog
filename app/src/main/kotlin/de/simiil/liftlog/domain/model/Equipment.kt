package de.simiil.liftlog.domain.model

/** Equipment classification (02-data-spec §3). */
enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT;

    companion object {
        /** Unknown/absent → [MACHINE] (least-specific generic bucket; real gate is M5 import validation). */
        fun fromStorageValue(value: String?): Equipment =
            entries.firstOrNull { it.name == value } ?: MACHINE
    }
}
