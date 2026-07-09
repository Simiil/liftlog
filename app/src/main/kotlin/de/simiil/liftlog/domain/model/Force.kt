package de.simiil.liftlog.domain.model

/** Force classification (push/pull/static). Nullable everywhere: absent = unclassified. */
enum class Force {
    PUSH,
    PULL,
    STATIC,
    ;

    companion object {
        /** Unknown/absent → `null` — unlike the other enums there is no sensible catch-all member. */
        fun fromStorageValue(value: String?): Force? = entries.firstOrNull { it.name == value }
    }
}
