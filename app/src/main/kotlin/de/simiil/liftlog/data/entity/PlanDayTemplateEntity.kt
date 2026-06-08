package de.simiil.liftlog.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_day_templates",
    foreignKeys = [ForeignKey(WorkoutPlanEntity::class, ["id"], ["planId"])],
    indices = [Index("planId")],
)
data class PlanDayTemplateEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
