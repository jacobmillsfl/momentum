package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val valence: String,
    val isScheduled: Boolean,
    val trackingMode: String?,
    val unit: String?,
    val recurrenceJson: String?,
    val notes: String?,
    val archivedAt: Long?,
    val createdAt: Long,
)
