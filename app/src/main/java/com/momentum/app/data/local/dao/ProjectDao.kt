package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.momentum.app.data.local.entity.ProjectEntity

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProjectEntity)

    @Update
    suspend fun update(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT * FROM projects ORDER BY createdAtMs DESC")
    suspend fun listAllForBackup(): List<ProjectEntity>

    @Query(
        """
        SELECT * FROM projects
        WHERE completedAtMs IS NULL
        AND dueEpochDay IS NOT NULL
        AND dueEpochDay = :epochDay
        ORDER BY createdAtMs ASC
        """,
    )
    suspend fun listIncompleteDueOnEpochDay(epochDay: Long): List<ProjectEntity>

    @Query(
        """
        SELECT * FROM projects
        WHERE completedAtMs IS NULL
        AND dueEpochDay IS NOT NULL
        AND dueEpochDay >= :startEpochDay AND dueEpochDay <= :endEpochDay
        ORDER BY dueEpochDay ASC, createdAtMs ASC
        """,
    )
    suspend fun listIncompleteDueInEpochDayRange(startEpochDay: Long, endEpochDay: Long): List<ProjectEntity>

    @Query(
        """
        SELECT * FROM projects WHERE completedAtMs IS NULL
        ORDER BY (dueEpochDay IS NULL) ASC, dueEpochDay ASC, createdAtMs DESC
        """,
    )
    suspend fun listActiveOrdered(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE completedAtMs IS NOT NULL ORDER BY completedAtMs DESC")
    suspend fun listCompletedOrdered(): List<ProjectEntity>
}
