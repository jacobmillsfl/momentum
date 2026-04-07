package com.momentum.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.momentum.app.data.local.dao.HabitDao
import com.momentum.app.data.local.dao.KvDao
import com.momentum.app.data.local.dao.LogDao
import com.momentum.app.data.local.dao.SessionDao
import com.momentum.app.data.local.dao.TaskDao
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.data.local.entity.TaskEntity

@Database(
    entities = [
        HabitEntity::class,
        SessionEntity::class,
        LogEntity::class,
        KvEntity::class,
        TaskEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MomentumDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun sessionDao(): SessionDao
    abstract fun logDao(): LogDao
    abstract fun kvDao(): KvDao
    abstract fun taskDao(): TaskDao
}
