package de.simiil.liftlog.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(PlanDayTemplateEntity::class, ["id"], ["templateId"])],
    indices = [Index("startedAt"), Index("templateId")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val templateId: String?,
    val templateNameSnapshot: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
