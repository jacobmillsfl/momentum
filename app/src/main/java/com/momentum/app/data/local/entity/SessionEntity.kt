package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId"]),
        Index(value = ["scheduledAt"]),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val scheduledAt: Long,
    val completedAt: Long?,
    val status: String,
    val categoriesJson: String,
    val notes: String?,
    /** Recorded body weight (or other numeric completion) when [status] is COMPLETED; used for WEIGHT habits. */
    val completionValue: Double? = null,
)
