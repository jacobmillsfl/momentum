package com.momentum.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.momentum.app.data.local.entity.KvEntity

@Dao
interface KvDao {
    @Query("SELECT value FROM kv_store WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KvEntity)

    @Query("SELECT * FROM kv_store ORDER BY key ASC")
    suspend fun listAllForBackup(): List<KvEntity>

    @Query("DELETE FROM kv_store")
    suspend fun deleteAll()
}
