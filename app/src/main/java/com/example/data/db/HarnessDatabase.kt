package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.MessagesDao
import com.example.data.dao.ProjectsDao
import com.example.data.dao.SessionsDao
import com.example.data.dao.ToolCallsDao
import com.example.data.dao.WorkspaceFilesDao
import com.example.data.entity.MessageEntity
import com.example.data.entity.ProjectEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.ToolCallEntity
import com.example.data.entity.WorkspaceFileEntity

@Database(
    entities = [
        ProjectEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        ToolCallEntity::class,
        WorkspaceFileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HarnessDatabase : RoomDatabase() {
    abstract fun projectsDao(): ProjectsDao
    abstract fun sessionsDao(): SessionsDao
    abstract fun messagesDao(): MessagesDao
    abstract fun toolCallsDao(): ToolCallsDao
    abstract fun workspaceFilesDao(): WorkspaceFilesDao

    companion object {
        @Volatile
        private var INSTANCE: HarnessDatabase? = null

        fun getInstance(context: Context): HarnessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HarnessDatabase::class.java,
                    "android_harness.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
