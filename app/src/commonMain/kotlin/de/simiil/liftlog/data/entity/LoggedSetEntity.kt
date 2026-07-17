package de.simiil.liftlog.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logged_sets",
    foreignKeys = [ForeignKey(SessionExerciseEntity::class, ["id"], ["sessionExerciseId"])],
    indices = [Index("sessionExerciseId")],
)
data class LoggedSetEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val position: Int,
    val completedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
