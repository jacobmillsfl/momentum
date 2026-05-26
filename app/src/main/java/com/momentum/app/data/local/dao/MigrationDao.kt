package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.momentum.app.data.local.entity.MigrationStateEntity

@Dao
interface MigrationDao {
    @Query("SELECT * FROM migration_states WHERE migrationId = :id LIMIT 1")
    suspend fun getById(id: String): MigrationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MigrationStateEntity)

    @Query("DELETE FROM migration_states")
    suspend fun deleteAll()
}
