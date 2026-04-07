package com.momentum.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kv_store")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String,
)
