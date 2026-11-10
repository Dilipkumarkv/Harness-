package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.HarnessDatabase
import com.example.data.entity.MessageEntity
import com.example.data.entity.ProjectEntity
import com.example.data.entity.SessionEntity
import com.example.data.entity.ToolCallEntity
import com.example.data.entity.WorkspaceFileEntity
import com.example.domain.agent.LocalToolCallingEngine
import com.example.domain.model.ChatMessage
import com.example.domain.model.DoctorTestResult
import com.example.domain.model.HarnessSettings
import com.example.domain.model.ToolCallItem
import com.example.domain.model.ToolCallStatus
import com.example.domain.tools.HarnessToolRegistry
import com.example.domain.tools.PatchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HarnessViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HarnessDatabase.getInstance(application)
    private val projectsDao = db.projectsDao()
    private val sessionsDao = db.sessionsDao()
    private val messagesDao = db.messagesDao()
    private val toolCallsDao = db.toolCallsDao()
    private val filesDao = db.workspaceFilesDao()

    private val defaultProjectId = "project_main_harness"
    private val defaultSessionId = "session_main_harness"

    val toolRegistry = HarnessToolRegistry(filesDao) { defaultProjectId }
    val agentEngine = LocalToolCallingEngine(toolRegistry)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileEntity>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileEntity>> = _workspaceFiles.asStateFlow()

    private val _selectedFile = MutableStateFlow<WorkspaceFileEntity?>(null)
    val selectedFile: StateFlow<WorkspaceFileEntity?> = _selectedFile.asStateFlow()

    private val _settings = MutableStateFlow(HarnessSettings())
    val settings: StateFlow<HarnessSettings> = _settings.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _currentThought = MutableStateFlow<String?>(null)
    val currentThought: StateFlow<String?> = _currentThought.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _doctorResults = MutableStateFlow<List<DoctorTestResult>>(emptyList())
    val doctorResults: StateFlow<List<DoctorTestResult>> = _doctorResults.asStateFlow()

    private val _isDoctorRunning = MutableStateFlow(false)
    val isDoctorRunning: StateFlow<Boolean> = _isDoctorRunning.asStateFlow()

    private var agentExecutionJob: Job? = null

    init {
        initializeWorkspace()
        observeDatabase()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            filesDao.getFilesForProject(defaultProjectId).collect { list ->
                _workspaceFiles.value = list
                if (_selectedFile.value == null && list.isNotEmpty()) {
                    _selectedFile.value = list.firstOrNull { it.path.contains("MainActivity") } ?: list.first()
                }
            }
        }
    }

    private fun initializeWorkspace() {
        viewModelScope.launch {
            // Seed Project
            val existingProject = projectsDao.getProjectById(defaultProjectId)
            if (existingProject == null) {
                projectsDao.insertProject(
                    ProjectEntity(
                        id = defaultProjectId,
                        name = "AndroidAppSample",
                        description = "Sample Android Jetpack Compose codebase managed by AndroidHarness",
                        activeSessionId = defaultSessionId
                    )
                )
                sessionsDao.insertSession(
                    SessionEntity(
                        id = defaultSessionId,
                        projectId = defaultProjectId,
                        title = "Main Coding Session",
                        providerType = "LOCAL_TOOL_LLM"
                    )
                )

                // Seed files
                seedWorkspaceFiles()

                // Seed welcome message
                val welcomeMsgId = "msg_welcome_1"
                val welcomeMsg = MessageEntity(
                    id = welcomeMsgId,
                    sessionId = defaultSessionId,
                    role = "assistant",
                    content = "👋 Welcome to **AndroidHarness**! I am your mobile autonomous coding agent.\n\nI can read and edit files, apply patches with unified diffs, run terminal commands, and inspect git history directly from your phone. Tap any suggestion chip below or enter your prompt to begin.",
                    thought = "AndroidHarness virtual workspace initialized. 8 developer tools loaded: read_file, write_file, apply_patch, list_directory, search_code, shell_exec, git_status, git_diff, doctor_diagnostics.",
                    timestamp = System.currentTimeMillis()
                )
                messagesDao.insertMessage(welcomeMsg)
            }

            loadMessages()
        }
    }

    private suspend fun seedWorkspaceFiles() {
        val files = listOf(
            WorkspaceFileEntity(
                id = "file_1",
                projectId = defaultProjectId,
                path = "app/src/main/java/com/example/MainActivity.kt",
                language = "kotlin",
                content = """
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.AndroidHarnessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidHarnessTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(name = "AndroidHarness", modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello " + name + "!", modifier = modifier)
}
                """.trimIndent()
            ),
            WorkspaceFileEntity(
                id = "file_2",
                projectId = defaultProjectId,
                path = "app/build.gradle.kts",
                language = "gradle",
                content = """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.androidharness.zqmvkr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
                """.trimIndent()
            ),
            WorkspaceFileEntity(
                id = "file_3",
                projectId = defaultProjectId,
                path = "app/src/main/res/values/strings.xml",
                language = "xml",
                content = """
<resources>
    <string name="app_name">AndroidHarness</string>
    <string name="action_run">Run Agent</string>
    <string name="action_stop">Stop Agent</string>
</resources>
                """.trimIndent()
            ),
            WorkspaceFileEntity(
                id = "file_4",
                projectId = defaultProjectId,
                path = "README.md",
                language = "markdown",
                content = """
# AndroidHarness Mobile AI Coding Agent
Native Android coding harness featuring on-device autonomous agent loop, interactive tool cards, virtual project filesystem, and unified diff viewer.
                """.trimIndent()
            )
        )

        files.forEach { filesDao.insertFile(it) }
    }

    private suspend fun loadMessages() {
        val entities = messagesDao.getMessagesForSession(defaultSessionId).first()
        val chatMessages = entities.map { msg ->
            val toolCalls = toolCallsDao.getToolCallsForMessage(msg.id).first().map { tc ->
                ToolCallItem(
                    id = tc.id,
                    toolName = tc.toolName,
                    argumentsJson = tc.argumentsJson,
                    resultJson = tc.resultJson,
                    status = try { ToolCallStatus.valueOf(tc.status) } catch (_: Exception) { ToolCallStatus.SUCCESS },
                    durationMs = tc.durationMs,
                    diffContent = tc.diffContent
                )
            }
            ChatMessage(
                id = msg.id,
                role = msg.role,
                content = msg.content,
                thought = msg.thought,
                toolCalls = toolCalls,
                timestamp = msg.timestamp,
                isStreaming = msg.isStreaming
            )
        }
        _messages.value = chatMessages
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || _isAgentRunning.value) return

        val userMsgId = "msg_${System.currentTimeMillis()}"
        val assistantMsgId = "msg_${System.currentTimeMillis() + 1}"

        viewModelScope.launch {
            // Save user message
            val userMsg = MessageEntity(
                id = userMsgId,
                sessionId = defaultSessionId,
                role = "user",
                content = trimmed,
                timestamp = System.currentTimeMillis()
            )
            messagesDao.insertMessage(userMsg)

            // Create initial placeholder for assistant
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                sessionId = defaultSessionId,
                role = "assistant",
                content = "Thinking...",
                thought = "Evaluating prompt and selecting tools...",
                timestamp = System.currentTimeMillis() + 1,
                isStreaming = true
            )
            messagesDao.insertMessage(assistantMsg)
            loadMessages()

            // Launch autonomous agent loop
            runAgentLoop(assistantMsgId, trimmed)
        }
    }

    private fun runAgentLoop(assistantMsgId: String, prompt: String) {
        agentExecutionJob?.cancel()
        agentExecutionJob = viewModelScope.launch {
            _isAgentRunning.value = true
            val accumulatedToolCalls = mutableListOf<ToolCallItem>()
            var currentExplanation = ""
            var step = 1
            val maxSteps = _settings.value.maxAutonomousIterations

            try {
                while (step <= maxSteps && _isAgentRunning.value) {
                    val stepResult = agentEngine.executeNextStep(
                        userPrompt = prompt,
                        stepNumber = step,
                        previousToolCalls = accumulatedToolCalls,
                        settings = _settings.value
                    )

                    _currentThought.value = stepResult.thought

                    // If a tool was invoked
                    if (stepResult.toolCall != null) {
                        val tc = stepResult.toolCall
                        accumulatedToolCalls.add(tc)

                        // Save tool call in database
                        toolCallsDao.insertToolCall(
                            ToolCallEntity(
                                id = tc.id,
                                messageId = assistantMsgId,
                                toolName = tc.toolName,
                                argumentsJson = tc.argumentsJson,
                                resultJson = tc.resultJson,
                                status = tc.status.name,
                                durationMs = tc.durationMs,
                                diffContent = tc.diffContent
                            )
                        )
                    }

                    currentExplanation = if (currentExplanation.isBlank()) {
                        stepResult.explanation
                    } else {
                        currentExplanation + "\n\n" + stepResult.explanation
                    }

                    // Update assistant message in DB
                    messagesDao.updateMessage(
                        MessageEntity(
                            id = assistantMsgId,
                            sessionId = defaultSessionId,
                            role = "assistant",
                            content = currentExplanation,
                            thought = stepResult.thought ?: _currentThought.value,
                            timestamp = System.currentTimeMillis(),
                            isStreaming = !stepResult.isFinalStep
                        )
                    )

                    loadMessages()

                    if (stepResult.isFinalStep) {
                        break
                    }

                    step++
                    delay(300L) // UI pace pacing
                }
            } catch (_: Exception) {
                // Handle cancellation or error
            } finally {
                _isAgentRunning.value = false
                _currentThought.value = null
                // Ensure streaming flag cleared
                messagesDao.getMessagesForSession(defaultSessionId).first().find { it.id == assistantMsgId }?.let {
                    messagesDao.updateMessage(it.copy(isStreaming = false))
                }
                loadMessages()
            }
        }
    }

    fun stopAgent() {
        agentExecutionJob?.cancel()
        _isAgentRunning.value = false
        _currentThought.value = null
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectFile(file: WorkspaceFileEntity) {
        _selectedFile.value = file
    }

    fun updateSettings(newSettings: HarnessSettings) {
        _settings.value = newSettings
    }

    fun clearSessionHistory() {
        viewModelScope.launch {
            messagesDao.clearSession(defaultSessionId)
            loadMessages()
        }
    }

    fun runDoctorDiagnostics() {
        viewModelScope.launch {
            _isDoctorRunning.value = true
            _doctorResults.value = emptyList()
            delay(500L)
            val results = toolRegistry.runDiagnostics()
            _doctorResults.value = results
            _isDoctorRunning.value = false
        }
    }

    fun createFile(path: String, content: String) {
        viewModelScope.launch {
            val language = when {
                path.endsWith(".kt") -> "kotlin"
                path.endsWith(".xml") -> "xml"
                path.endsWith(".gradle.kts") -> "gradle"
                path.endsWith(".json") -> "json"
                path.endsWith(".md") -> "markdown"
                else -> "text"
            }
            val newFile = WorkspaceFileEntity(
                id = "file_${System.currentTimeMillis()}_${path.hashCode()}",
                projectId = defaultProjectId,
                path = path,
                content = content,
                language = language,
                lastModified = System.currentTimeMillis()
            )
            filesDao.insertFile(newFile)
            _selectedFile.value = newFile
        }
    }
}
