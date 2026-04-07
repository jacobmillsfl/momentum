package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.momentum.app.data.local.entity.HabitEntity

@Dao
interface HabitDao {
    @Query(
        """
        SELECT * FROM habits WHERE archivedAt IS NULL ORDER BY createdAt DESC
        """,
    )
    suspend fun listActive(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HabitEntity)

    @Update
    suspend fun update(entity: HabitEntity)

    @Query("SELECT * FROM habits WHERE isScheduled = 1 AND archivedAt IS NULL")
    suspend fun listScheduledActive(): List<HabitEntity>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun listAllForBackup(): List<HabitEntity>

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}
