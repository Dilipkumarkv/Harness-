package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /** v1 → v2: indexes on the hot lookup columns (message/tool-call/file listings). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_projectId` ON `sessions` (`projectId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_calls_messageId` ON `tool_calls` (`messageId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_files_projectId_path` ON `workspace_files` (`projectId`, `path`)")
            }
        }

        fun getInstance(context: Context): HarnessDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HarnessDatabase::class.java,
                    "android_harness.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
