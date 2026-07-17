package de.simiil.liftlog.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row bookkeeping for the seeder (02-data-spec §7): which seed version has been applied.
 *  Local derived state — deliberately NOT part of the backup format. */
@Entity(tableName = "seed_state")
data class SeedStateEntity(
    @PrimaryKey val id: Int = 1,
    val appliedSeedVersion: Int,
)
