package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val createdAtMs: Long,
    /** [java.time.LocalDate.toEpochDay] when a due date is set; null = no due date. */
    val dueEpochDay: Long?,
    /** Null while incomplete; set when marked complete. */
    val completedAtMs: Long?,
)
