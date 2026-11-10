package com.example.domain.model

enum class ToolCallStatus {
    RUNNING,
    SUCCESS,
    ERROR
}

enum class ProviderType {
    LOCAL_TOOL_LLM,
    BYOK_CUSTOM
}

enum class LocalToolProtocol {
    XML_TAGS,     // <tool_call>{"name": "...", "arguments": {...}}</tool_call>
    REACT_TEXT,   // Thought: ... Action: read_file ... Action Input: {...}
    JSON_BLOCK    // ```json { "tool": "...", "args": {...} } ```
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
    val iconName: String
)

data class ToolCallItem(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
    val durationMs: Long = 0L,
    val diffContent: String? = null
)

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED,
    HEADER
}

data class ChatMessage(
    val id: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thought: String? = null,
    val toolCalls: List<ToolCallItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class WorkspaceFile(
    val id: String,
    val path: String,
    val content: String,
    val language: String = "kotlin",
    val lastModified: Long = System.currentTimeMillis()
)

data class GitCommit(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val changedFilesCount: Int
)

data class DoctorTestResult(
    val name: String,
    val description: String,
    val isSuccess: Boolean,
    val message: String,
    val durationMs: Long
)

data class HarnessSettings(
    val providerType: ProviderType = ProviderType.LOCAL_TOOL_LLM,
    val localProtocol: LocalToolProtocol = LocalToolProtocol.XML_TAGS,
    val customApiKey: String = "",
    val customEndpointUrl: String = "https://api.openai.com/v1",
    val modelName: String = "gpt-4o-mini",
    val maxAutonomousIterations: Int = 6,
    val autoApproveTools: Boolean = true,
    /** Full path or bare name of the git binary to use. Default "git". */
    val gitPath: String = "git"
)
