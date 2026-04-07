package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.momentum.app.data.local.entity.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query(
        """
        SELECT * FROM sessions WHERE scheduledAt >= :startMs AND scheduledAt < :endMs
        ORDER BY scheduledAt ASC
        """,
    )
    suspend fun sessionsBetween(startMs: Long, endMs: Long): List<SessionEntity>

    @Query(
        """
        SELECT * FROM sessions WHERE habitId = :habitId AND scheduledAt = :dayStart LIMIT 1
        """,
    )
    suspend fun findByHabitAndDay(habitId: String, dayStart: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SessionEntity)

    @Update
    suspend fun update(entity: SessionEntity)

    @Query(
        """
        SELECT * FROM sessions WHERE habitId = :habitId
        AND scheduledAt >= :startMs AND scheduledAt < :endMs
        ORDER BY scheduledAt ASC
        """,
    )
    suspend fun sessionsForHabitInRange(
        habitId: String,
        startMs: Long,
        endMs: Long,
    ): List<SessionEntity>

    @Query(
        """
        SELECT * FROM sessions WHERE completionValue IS NOT NULL AND status = 'COMPLETED'
        ORDER BY scheduledAt ASC
        """,
    )
    suspend fun sessionsWithCompletionValue(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY scheduledAt ASC")
    suspend fun listAllForBackup(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
