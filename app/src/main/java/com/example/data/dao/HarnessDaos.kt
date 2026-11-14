package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.MessageEntity
import com.example.data.entity.ProjectEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.ToolCallEntity
import com.example.data.entity.WorkspaceFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectsDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)
}

@Dao
interface SessionsDao {
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getSessionsForProject(projectId: String): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)
}

@Dao
interface MessagesDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}

@Dao
interface ToolCallsDao {
    @Query("DELETE FROM tool_calls WHERE messageId = :messageId")
    suspend fun deleteToolCallsForMessage(messageId: String)

    /** Deletes tool calls whose parent messages belong to [sessionId]. Call before clearing the session. */
    @Query("DELETE FROM tool_calls WHERE messageId IN (SELECT id FROM messages WHERE sessionId = :sessionId)")
    suspend fun deleteToolCallsForSession(sessionId: String)

    @Query("SELECT * FROM tool_calls WHERE messageId = :messageId ORDER BY createdAt ASC")
    fun getToolCallsForMessage(messageId: String): Flow<List<ToolCallEntity>>

    @Query("SELECT * FROM tool_calls ORDER BY createdAt DESC LIMIT 50")
    fun getRecentToolCalls(): Flow<List<ToolCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolCall(toolCall: ToolCallEntity)

    @Update
    suspend fun updateToolCall(toolCall: ToolCallEntity)
}

@Dao
interface WorkspaceFilesDao {
    @Query("SELECT * FROM workspace_files WHERE projectId = :projectId ORDER BY path ASC")
    fun getFilesForProject(projectId: String): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files WHERE projectId = :projectId AND path = :path LIMIT 1")
    suspend fun getFileByPath(projectId: String, path: String): WorkspaceFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: WorkspaceFileEntity)

    @Query("DELETE FROM workspace_files WHERE projectId = :projectId AND path = :path")
    suspend fun deleteFile(projectId: String, path: String)
}
