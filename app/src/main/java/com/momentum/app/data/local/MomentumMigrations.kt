package com.momentum.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MomentumMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS projects (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    createdAtMs INTEGER NOT NULL,
                    dueEpochDay INTEGER,
                    completedAtMs INTEGER
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN goalDomain TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS migration_states (
                    migrationId TEXT NOT NULL PRIMARY KEY,
                    completed INTEGER NOT NULL,
                    completedAtEpochMs INTEGER
                )
                """.trimIndent(),
            )
        }
    }
}
