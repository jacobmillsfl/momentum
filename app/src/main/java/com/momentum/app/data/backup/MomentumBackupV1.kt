package com.momentum.app.data.backup

import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.data.local.entity.TaskEntity
import kotlinx.serialization.Serializable

const val MOMENTUM_BACKUP_SCHEMA_VERSION = 1

@Serializable
data class MomentumBackupV1(
    val schemaVersion: Int = MOMENTUM_BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val habits: List<HabitBackupRow>,
    val sessions: List<SessionBackupRow>,
    val tasks: List<TaskBackupRow>,
    val logs: List<LogBackupRow>,
    val kv: List<KvBackupRow>,
)

@Serializable
data class HabitBackupRow(
    val id: String,
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

@Serializable
data class SessionBackupRow(
    val id: String,
    val habitId: String,
    val scheduledAt: Long,
    val completedAt: Long?,
    val status: String,
    val categoriesJson: String,
    val notes: String?,
    val completionValue: Double? = null,
)

@Serializable
data class TaskBackupRow(
    val id: String,
    val sessionId: String,
    val title: String,
    val completed: Boolean,
    val sortOrder: Int,
    val notes: String?,
)

@Serializable
data class LogBackupRow(
    val id: String,
    val habitId: String,
    val loggedAt: Long,
    val valence: String,
    val booleanValue: Int?,
    val numericValue: Double?,
    val unit: String?,
    val notes: String?,
)

@Serializable
data class KvBackupRow(
    val key: String,
    val value: String,
)

fun HabitEntity.toBackupRow() = HabitBackupRow(
    id = id,
    title = title,
    valence = valence,
    isScheduled = isScheduled,
    trackingMode = trackingMode,
    unit = unit,
    recurrenceJson = recurrenceJson,
    notes = notes,
    archivedAt = archivedAt,
    createdAt = createdAt,
)

fun HabitBackupRow.toEntity() = HabitEntity(
    id = id,
    title = title,
    valence = valence,
    isScheduled = isScheduled,
    trackingMode = trackingMode,
    unit = unit,
    recurrenceJson = recurrenceJson,
    notes = notes,
    archivedAt = archivedAt,
    createdAt = createdAt,
)

fun SessionEntity.toBackupRow() = SessionBackupRow(
    id = id,
    habitId = habitId,
    scheduledAt = scheduledAt,
    completedAt = completedAt,
    status = status,
    categoriesJson = categoriesJson,
    notes = notes,
    completionValue = completionValue,
)

fun SessionBackupRow.toEntity() = SessionEntity(
    id = id,
    habitId = habitId,
    scheduledAt = scheduledAt,
    completedAt = completedAt,
    status = status,
    categoriesJson = categoriesJson,
    notes = notes,
    completionValue = completionValue,
)

fun TaskEntity.toBackupRow() = TaskBackupRow(
    id = id,
    sessionId = sessionId,
    title = title,
    completed = completed,
    sortOrder = sortOrder,
    notes = notes,
)

fun TaskBackupRow.toEntity() = TaskEntity(
    id = id,
    sessionId = sessionId,
    title = title,
    completed = completed,
    sortOrder = sortOrder,
    notes = notes,
)

fun LogEntity.toBackupRow() = LogBackupRow(
    id = id,
    habitId = habitId,
    loggedAt = loggedAt,
    valence = valence,
    booleanValue = booleanValue,
    numericValue = numericValue,
    unit = unit,
    notes = notes,
)

fun LogBackupRow.toEntity() = LogEntity(
    id = id,
    habitId = habitId,
    loggedAt = loggedAt,
    valence = valence,
    booleanValue = booleanValue,
    numericValue = numericValue,
    unit = unit,
    notes = notes,
)

fun KvEntity.toBackupRow() = KvBackupRow(key = key, value = value)

fun KvBackupRow.toEntity() = KvEntity(key = key, value = value)
