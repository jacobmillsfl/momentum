package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks completion of one-time UX/data migrations (e.g. assigning Mind vs Body to habits).
 */
@Entity(tableName = "migration_states")
data class MigrationStateEntity(
    @PrimaryKey val migrationId: String,
    val completed: Boolean,
    /** Wall-clock time when the user finished this migration, if [completed]. */
    val completedAtEpochMs: Long?,
)
