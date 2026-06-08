package de.simiil.liftlog.domain.model

/** Display/entry unit. Storage is always kg (02-data-spec §5). */
enum class WeightUnit {
    KG, LB;

    companion object {
        fun fromStorageValue(value: String?): WeightUnit =
            entries.firstOrNull { it.name == value } ?: KG
    }
}
