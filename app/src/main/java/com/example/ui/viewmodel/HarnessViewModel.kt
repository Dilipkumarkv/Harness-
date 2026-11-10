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
import com.example.domain.tools.WorkspaceFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HarnessViewModel(application: Application) : AndroidViewModel(application) {

    private val db = HarnessDatabase.getInstance(application)
    private val projectsDao = db.projectsDao()
    private val sessionsDao = db.sessionsDao()
    private val messagesDao = db.messagesDao()
    private val toolCallsDao = db.toolCallsDao()
    private val filesDao = db.workspaceFilesDao()

    private val defaultProjectId = "project_main_harness"
    private val defaultSessionId = "session_main_harness"

    /** The real, app-private project directory every tool operates on. */
    private val workspace: WorkspaceFs = WorkspaceFs(
        File(File(application.filesDir, "workspace"), defaultProjectId)
    )

    private val _settings = MutableStateFlow(HarnessSettings())
    val settings: StateFlow<HarnessSettings> = _settings.asStateFlow()

    private val settingsStore = SettingsStore(getApplication())

    val toolRegistry = HarnessToolRegistry(
        filesDao = filesDao,
        workspace = workspace,
        currentProjectId = { defaultProjectId },
        gitCommand = { _settings.value.gitPath }
    )
    val agentEngine = LocalToolCallingEngine(toolRegistry)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileEntity>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileEntity>> = _workspaceFiles.asStateFlow()

    private val _selectedFile = MutableStateFlow<WorkspaceFileEntity?>(null)
    val selectedFile: StateFlow<WorkspaceFileEntity?> = _selectedFile.asStateFlow()

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

    // Real git data for the Diffs tab.
    private val _gitStatusText = MutableStateFlow("")
    val gitStatusText: StateFlow<String> = _gitStatusText.asStateFlow()

    private val _gitDiffText = MutableStateFlow("")
    val gitDiffText: StateFlow<String> = _gitDiffText.asStateFlow()

    private val _gitLogText = MutableStateFlow("")
    val gitLogText: StateFlow<String> = _gitLogText.asStateFlow()

    private val _isGitRefreshing = MutableStateFlow(false)
    val isGitRefreshing: StateFlow<Boolean> = _isGitRefreshing.asStateFlow()

    private var agentExecutionJob: Job? = null

    val workspaceRootPath: String get() = workspace.canonicalRoot().path

    init {
        viewModelScope.launch {
            _settings.value = settingsStore.load()
            initializeWorkspace()
        }
        observeDatabase()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            filesDao.getFilesForProject(defaultProjectId).collect { list ->
                _workspaceFiles.value = list
                val current = _selectedFile.value
                if (current == null && list.isNotEmpty()) {
                    _selectedFile.value = list.firstOrNull { it.path.endsWith("MainActivity.kt") } ?: list.first()
                } else if (current != null) {
                    _selectedFile.value = list.firstOrNull { it.id == current.id } ?: current
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Workspace setup
    // ---------------------------------------------------------------------------------------

    private suspend fun initializeWorkspace() {
        val createdFresh = withContext(Dispatchers.IO) { seedWorkspaceOnDisk() }

        if (createdFresh) {
            // A real git repo so git_status / git_diff have something to read.
            runCatching { toolRegistry.runCommand("git init") }
        }

        val existingProject = projectsDao.getProjectById(defaultProjectId)
        if (existingProject == null) {
            projectsDao.insertProject(
                ProjectEntity(
                    id = defaultProjectId,
                    name = "AndroidHarnessWorkspace",
                    description = "Real on-device workspace at ${workspace.canonicalRoot().path}",
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
            messagesDao.insertMessage(
                MessageEntity(
                    id = "msg_welcome_1",
                    sessionId = defaultSessionId,
                    role = "assistant",
                    content = buildString {
                        append("👋 **AndroidHarness** is wired to a real workspace on this device.\n\n")
                        append("Root: `")
                        append(workspace.canonicalRoot().path)
                        append("`\n\n")
                        append("Tools run for real: `read_file`, `write_file`, `apply_patch`, `list_directory`, ")
                        append("`search_code`, `shell_exec`, `git_status`, `git_diff`, `doctor_diagnostics`.\n\n")
                        append("`shell_exec` runs one allowlisted command without a shell, and git tools report ")
                        append("honestly if no git binary is installed.")
                    },
                    thought = "Workspace initialised on disk and indexed into Room. 9 tools registered.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        val indexed = runCatching { toolRegistry.reindex() }.getOrDefault(0)
        loadMessages()
        refreshGit()
        if (indexed > 0) {
            // Files tab reads the Room index, which reindex() has just rebuilt from disk.
            _workspaceFiles.value = filesDao.getFilesForProject(defaultProjectId).first()
        }
    }

    /** Writes the starter project to disk. Returns true when the workspace was empty. */
    private suspend fun seedWorkspaceOnDisk(): Boolean = withContext(Dispatchers.IO) {
        val existing = runCatching { workspace.walkFiles() }.getOrDefault(emptyList())
        if (existing.isNotEmpty()) return@withContext false

        val seeds = listOf(
            "README.md" to """
# AndroidHarness Workspace

This is a real directory on your device, not a simulated one.

Every file the agent reads or writes lives here. Edits are atomic (temporary file + rename),
paths outside this root are refused, and `shell_exec` runs one allowlisted command at a time.
            """.trimIndent() + "\n",

            ".gitignore" to "build/\n.gradle/\n*.iml\nlocal.properties\n",

            "app/build.gradle.kts" to """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
            """.trimIndent() + "\n",

            "app/src/main/res/values/strings.xml" to """
<resources>
    <string name="app_name">AndroidHarness</string>
    <string name="action_run">Run Agent</string>
    <string name="action_stop">Stop Agent</string>
</resources>
            """.trimIndent() + "\n",

            "app/src/main/java/com/example/MainActivity.kt" to """
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Hello from the AndroidHarness workspace")
                }
            }
        }
    }
}
            """.trimIndent() + "\n"
        )

        seeds.forEach { (path, content) ->
            runCatching { workspace.writeText(path, content) }
        }
        true
    }

    // ---------------------------------------------------------------------------------------
    // Chat + agent loop
    // ---------------------------------------------------------------------------------------

    private suspend fun loadMessages() {
        val entities = messagesDao.getMessagesForSession(defaultSessionId).first()
        val chatMessages = entities.map { msg ->
            val toolCalls = toolCallsDao.getToolCallsForMessage(msg.id).first().map { tc ->
                ToolCallItem(
                    id = tc.id,
                    toolName = tc.toolName,
                    argumentsJson = tc.argumentsJson,
                    resultJson = tc.resultJson,
                    status = try {
                        ToolCallStatus.valueOf(tc.status)
                    } catch (_: Exception) {
                        ToolCallStatus.SUCCESS
                    },
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
            messagesDao.insertMessage(
                MessageEntity(
                    id = userMsgId,
                    sessionId = defaultSessionId,
                    role = "user",
                    content = trimmed,
                    timestamp = System.currentTimeMillis()
                )
            )
            messagesDao.insertMessage(
                MessageEntity(
                    id = assistantMsgId,
                    sessionId = defaultSessionId,
                    role = "assistant",
                    content = "Thinking...",
                    thought = "Evaluating prompt and selecting tools...",
                    timestamp = System.currentTimeMillis() + 1,
                    isStreaming = true
                )
            )
            loadMessages()
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

                    if (stepResult.toolCall != null) {
                        val tc = stepResult.toolCall
                        accumulatedToolCalls.add(tc)
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

                    if (stepResult.isFinalStep) break

                    step++
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface the failure instead of pretending the step completed.
                currentExplanation += if (currentExplanation.isBlank()) {
                    "Agent step failed: ${e.message}"
                } else {
                    "\n\nAgent step failed: ${e.message}"
                }
                messagesDao.updateMessage(
                    MessageEntity(
                        id = assistantMsgId,
                        sessionId = defaultSessionId,
                        role = "assistant",
                        content = currentExplanation,
                        thought = _currentThought.value,
                        timestamp = System.currentTimeMillis(),
                        isStreaming = false
                    )
                )
            } finally {
                _isAgentRunning.value = false
                _currentThought.value = null
                messagesDao.getMessagesForSession(defaultSessionId).first()
                    .find { it.id == assistantMsgId }
                    ?.let { messagesDao.updateMessage(it.copy(isStreaming = false)) }
                loadMessages()
                // Files may have changed while the agent ran.
                runCatching { toolRegistry.reindex() }
                refreshGit()
            }
        }
    }

    fun stopAgent() {
        agentExecutionJob?.cancel()
        _isAgentRunning.value = false
        _currentThought.value = null
    }

    // ---------------------------------------------------------------------------------------
    // Git
    // ---------------------------------------------------------------------------------------

    fun refreshGit() {
        if (_isGitRefreshing.value) return
        viewModelScope.launch {
            _isGitRefreshing.value = true
            try {
                val status = toolRegistry.gitStatus()
                val diff = toolRegistry.gitDiff()
                val log = toolRegistry.gitLog()
                _gitStatusText.value = status.outputText
                _gitDiffText.value = diff.outputText
                _gitLogText.value = log.outputText
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _gitStatusText.value = "Could not read git state: ${e.message}"
            } finally {
                _isGitRefreshing.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // UI actions
    // ---------------------------------------------------------------------------------------

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
        if (tab == 2) refreshGit()
    }

    fun selectFile(file: WorkspaceFileEntity) {
        _selectedFile.value = file
    }

    fun updateSettings(newSettings: HarnessSettings) {
        _settings.value = newSettings
        settingsStore.save(newSettings)
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
            try {
                _doctorResults.value = toolRegistry.runDiagnostics()
            } finally {
                _isDoctorRunning.value = false
            }
        }
    }

    fun createFile(path: String, content: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            try {
                workspace.writeText(path, content)
                toolRegistry.reindex()
                refreshGit()
                _selectedFile.value = filesDao.getFileByPath(defaultProjectId, path)
            } catch (e: Exception) {
                // Nothing was written; leave the existing index untouched.
                android.util.Log.e("HarnessViewModel", "createFile failed for '$path'", e)
            }
        }
    }
}
