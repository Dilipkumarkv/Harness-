package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val activeSessionId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val providerType: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val thought: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

@Entity(tableName = "tool_calls")
data class ToolCallEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val status: String = "RUNNING",
    val durationMs: Long = 0L,
    val diffContent: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspace_files")
data class WorkspaceFileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val path: String,
    val content: String,
    val language: String,
    val lastModified: Long = System.currentTimeMillis()
)
