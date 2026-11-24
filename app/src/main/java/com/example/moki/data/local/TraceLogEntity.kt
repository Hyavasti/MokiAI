package com.example.moki.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trace_logs")
data class TraceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userQuery: String,
    val tierSelected: String,
    val skillInvoked: String?,
    val latencyMs: Long,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)