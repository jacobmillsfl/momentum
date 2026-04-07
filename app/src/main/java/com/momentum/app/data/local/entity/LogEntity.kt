package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs",
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
        Index(value = ["loggedAt"]),
    ],
)
data class LogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val loggedAt: Long,
    val valence: String,
    val booleanValue: Int?,
    val numericValue: Double?,
    val unit: String?,
    val notes: String?,
)
