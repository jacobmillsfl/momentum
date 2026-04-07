package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.momentum.app.data.local.entity.LogEntity

@Dao
interface LogDao {
    @Query("SELECT * FROM logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LogEntity)

    @Query(
        """
        SELECT COALESCE(SUM(numericValue), 0) FROM logs
        WHERE habitId = :habitId AND loggedAt >= :startMs AND loggedAt < :endMs
        """,
    )
    suspend fun sumNumericForDay(habitId: String, startMs: Long, endMs: Long): Double?

    @Query(
        """
        SELECT COUNT(*) FROM logs
        WHERE habitId = :habitId AND loggedAt >= :startMs AND loggedAt < :endMs
        AND booleanValue = 1
        """,
    )
    suspend fun countBooleanTrueForDay(habitId: String, startMs: Long, endMs: Long): Int

    @Query(
        """
        DELETE FROM logs WHERE habitId = :habitId AND loggedAt >= :startMs AND loggedAt < :endMs
        """,
    )
    suspend fun deleteLogsForHabitDay(habitId: String, startMs: Long, endMs: Long)

    @Query(
        """
        SELECT id FROM logs WHERE habitId = :habitId AND loggedAt >= :startMs AND loggedAt < :endMs
        ORDER BY loggedAt DESC LIMIT 1
        """,
    )
    suspend fun latestLogIdForDay(habitId: String, startMs: Long, endMs: Long): String?

    @Query("DELETE FROM logs WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query(
        """
        SELECT * FROM logs WHERE habitId = :habitId
        AND loggedAt >= :startMs AND loggedAt < :endMs
        ORDER BY loggedAt DESC
        """,
    )
    suspend fun logsForHabitDay(
        habitId: String,
        startMs: Long,
        endMs: Long,
    ): List<LogEntity>

    @Query(
        """
        SELECT * FROM logs WHERE habitId = :habitId
        AND loggedAt >= :startMs AND loggedAt < :endMs
        ORDER BY loggedAt ASC
        """,
    )
    suspend fun logsForHabitInRange(
        habitId: String,
        startMs: Long,
        endMs: Long,
    ): List<LogEntity>

    @Update
    suspend fun update(entity: LogEntity)

    @Query("SELECT * FROM logs ORDER BY loggedAt ASC")
    suspend fun listAllForBackup(): List<LogEntity>

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
