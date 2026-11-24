package com.example.moki.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TraceLogDao {
    @Insert
    suspend fun insertTrace(trace: TraceLogEntity): Long

    @Query("SELECT * FROM trace_logs ORDER BY timestamp DESC LIMIT 25")
    fun observeRecentTraces(): Flow<List<TraceLogEntity>>
}