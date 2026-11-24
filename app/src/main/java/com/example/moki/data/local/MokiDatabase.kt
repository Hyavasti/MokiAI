package com.example.moki.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, ChatMessageEntity::class, TraceLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MokiDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun traceLogDao(): TraceLogDao

    companion object {
        @Volatile
        private var INSTANCE: MokiDatabase? = null

        fun getInstance(context: Context): MokiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MokiDatabase::class.java,
                    "moki_memory.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}