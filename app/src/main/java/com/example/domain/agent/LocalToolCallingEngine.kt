package com.example.domain.agent

import com.example.domain.model.HarnessSettings
import com.example.domain.model.ProviderType
import com.example.domain.model.ToolCallItem
import com.example.domain.model.ToolCallStatus
import com.example.domain.tools.HarnessToolRegistry
import com.example.domain.agent.LlmClient
import com.example.domain.agent.ToolCallParser
import org.json.JSONObject

/**
 * Drives the tool loop without a language model.
 *
 * This is a *router*, not an assistant: it matches a few keywords in the prompt to pick a real
 * tool, runs it, and reports exactly what the tool returned. It never invents a tool result, never
 * writes content it did not read, and never claims a build or test passed when the tool reported a
 * failure. When it cannot do something (authoring code, reasoning about a request), it says so.
 *
 * Everything the user sees in the chat is derived from an actual [HarnessToolRegistry.ExecutionResult].
 */
class LocalToolCallingEngine(
    private val toolRegistry: HarnessToolRegistry,
    private val llm: LlmClient = LlmClient(),
    val approvalGate: ApprovalGate = ApprovalGate()
) {

    @Volatile
    private var activeSettings: HarnessSettings = HarnessSettings()

    data class AgentStepResult(
        val thought: String?,
        val explanation: String,
        val toolCall: ToolCallItem?,
        val isFinalStep: Boolean
    )

    companion object {
        private const val MAX_QUOTE_CHARS = 400

        private val FILE_PATH_REGEX = Regex("""[\w][\w./-]*\.[A-Za-z][A-Za-z0-9]{0,7}""")

        private val BUILD_WORDS = listOf("build", "compile", "gradle", "assemble", "apk")
        private val DOCTOR_WORDS = listOf("doctor", "diagnostic", "health", "self-test", "selftest")
        private val GIT_WORDS = listOf("git", "diff", "commit", "status", "branch")
        private val SEARCH_WORDS = listOf("search", "find", "where", "grep", "usage", "reference")
        private val LIST_WORDS = listOf("list", "tree", "structure", "files", "explore", "what is in")
    }

    suspend fun executeNextStep(
        userPrompt: String,
        stepNumber: Int,
        previousToolCalls: List<ToolCallItem>,
        settings: HarnessSettings
    ): AgentStepResult {
        activeSettings = settings

        // A real model, driven over an OpenAI-compatible endpoint. The keyword router below is used
        // only when no model is configured.
        if (settings.providerType == ProviderType.BYOK_CUSTOM) {
            return byokStep(userPrompt, previousToolCalls, settings)
        }

        val prompt = userPrompt.lowercase()

        return when {
            containsAny(prompt, BUILD_WORDS) -> buildFlow(stepNumber, previousToolCalls)
            containsAny(prompt, DOCTOR_WORDS) -> doctorFlow(stepNumber, previousToolCalls)
            containsAny(prompt, GIT_WORDS) -> gitFlow(stepNumber, previousToolCalls)
            containsAny(prompt, SEARCH_WORDS) -> searchFlow(stepNumber, previousToolCalls, userPrompt)
            containsAny(prompt, LIST_WORDS) -> listFlow(stepNumber, previousToolCalls)
            else -> exploreFlow(stepNumber, previousToolCalls, userPrompt)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Flows. Each ends by reporting the accumulated, real tool results.
    // ---------------------------------------------------------------------------------------

    private suspend fun buildFlow(
        step: Int,
        previous: List<ToolCallItem>
    ): AgentStepResult = when (step) {
        1 -> runTool(
            tool = "shell_exec",
            args = """{"command":"gradle assembleDebug"}""",
            thought = "The prompt mentions a build. Running 'gradle assembleDebug' in the workspace.",
            intent = "Executing 'gradle assembleDebug' in the workspace directory."
        )

        else -> finish(
            thought = "Reporting the build result exactly as the tool returned it.",
            calls = previous,
            headline = buildString {
                append("Build check finished. ")
                append(reportCounts(previous))
                if (previous.any { it.status == ToolCallStatus.ERROR }) {
                    append("\n\nNo build was produced. That is the tool's real answer, not an inference: ")
                    append("either Gradle is not installed on this device, or the project genuinely failed to compile.")
                }
            },
            tail = "I cannot amend or author code from this prompt: no language model is connected, so I only run tools."
        )
    }

    private suspend fun doctorFlow(step: Int, previous: List<ToolCallItem>): AgentStepResult = when (step) {
        1 -> runTool(
            tool = "doctor_diagnostics",
            args = "{}",
            thought = "Running the real diagnostic probes.",
            intent = "Running filesystem, index, patch, shell and git probes."
        )

        else -> finish(
            thought = "Restating the doctor output verbatim.",
            calls = previous,
            headline = "Diagnostics complete. " + reportCounts(previous),
            tail = "Each line above came from a probe that actually ran on this device."
        )
    }

    private suspend fun gitFlow(step: Int, previous: List<ToolCallItem>): AgentStepResult = when (step) {
        1 -> runTool(
            tool = "git_status",
            args = "{}",
            thought = "Querying the repository with git status.",
            intent = "Reading git status for this workspace."
        )

        2 -> runTool(
            tool = "git_diff",
            args = "{}",
            thought = "Fetching the working-tree diff.",
            intent = "Reading the uncommitted diff for this workspace."
        )

        else -> finish(
            thought = "Summarising the git results as returned.",
            calls = previous,
            headline = "Git inspection complete. " + reportCounts(previous),
            tail = if (previous.any { it.status == ToolCallStatus.ERROR }) {
                "Git reported a problem, shown above. Android does not ship a git binary; set its path in Settings if you have one installed."
            } else {
                "The status and diff above are the command output, unedited."
            }
        )
    }

    private suspend fun searchFlow(
        step: Int,
        previous: List<ToolCallItem>,
        userPrompt: String
    ): AgentStepResult = when (step) {
        1 -> {
            val term = extractSearchTerm(userPrompt)
            if (term == null) {
                finish(
                    thought = "No search term could be extracted from the prompt.",
                    calls = previous,
                    headline = "I could not tell what to search for. Mention a symbol, file or word to look for.",
                    tail = "I match keywords; I do not parse intent."
                )
            } else {
                runTool(
                    tool = "search_code",
                    args = JSONObject().put("query", term).toString(),
                    thought = "Searching file contents for '$term'.",
                    intent = "Searching the workspace on disk for '$term'."
                )
            }
        }

        else -> finish(
            thought = "Reporting the matches found.",
            calls = previous,
            headline = "Search finished. " + reportCounts(previous),
            tail = "Matches are read from files on disk; nothing was modified."
        )
    }

    private suspend fun listFlow(step: Int, previous: List<ToolCallItem>): AgentStepResult = when (step) {
        1 -> runTool(
            tool = "list_directory",
            args = """{"path":"","depth":2}""",
            thought = "Listing the real workspace tree.",
            intent = "Listing the workspace directory."
        )

        else -> finish(
            thought = "Reporting the directory listing.",
            calls = previous,
            headline = "Workspace listing complete. " + reportCounts(previous),
            tail = "The tree above is read from the filesystem, not a sample."
        )
    }

    /**
     * Anything else. Earlier this wrote a hardcoded MainActivity.kt and claimed success; that was
     * destructive once write_file became real. It now reads what exists and states its limits.
     */
    private suspend fun exploreFlow(
        step: Int,
        previous: List<ToolCallItem>,
        userPrompt: String
    ): AgentStepResult = when (step) {
        1 -> {
            val path = extractFilePath(userPrompt)
            if (path != null) {
                runTool(
                    tool = "read_file",
                    args = JSONObject().put("path", path).toString(),
                    thought = "The prompt names '$path'. Reading it.",
                    intent = "Reading '$path' from the workspace."
                )
            } else {
                runTool(
                    tool = "list_directory",
                    args = """{"path":"","depth":2}""",
                    thought = "No file was named, so I will list the workspace first.",
                    intent = "Listing the workspace to see what exists."
                )
            }
        }

        else -> finish(
            thought = "Explaining what this engine can and cannot do.",
            calls = previous,
            headline = "Here is what I found. " + reportCounts(previous),
            tail = "I cannot write or edit code for you: no language model is connected, so I cannot generate " +
                "a patch, choose a design, or reason about your intent. I matched keywords in your message and ran " +
                "the matching tool. Nothing was modified."
        )
    }

    // ---------------------------------------------------------------------------------------
    // Building blocks — every explanation is derived from a real result
    // ---------------------------------------------------------------------------------------

    private suspend fun runTool(
        tool: String,
        args: String,
        thought: String,
        intent: String
    ): AgentStepResult {
        val started = System.currentTimeMillis()
        val result = toolRegistry.executeTool(tool, args)
        val duration = System.currentTimeMillis() - started

        val call = ToolCallItem(
            id = "call_${started}_$tool",
            toolName = tool,
            argumentsJson = args,
            resultJson = result.outputText,
            status = result.status,
            durationMs = duration,
            diffContent = result.diffContent
        )

        return AgentStepResult(
            thought = thought,
            explanation = intent + "\n\n" + describe(tool, result),
            toolCall = call,
            isFinalStep = false
        )
    }

    private fun finish(
        thought: String,
        calls: List<ToolCallItem>,
        headline: String,
        tail: String
    ): AgentStepResult = AgentStepResult(
        thought = thought,
        explanation = listOf(headline, tail).filter { it.isNotBlank() }.joinToString("\n\n"),
        toolCall = null,
        isFinalStep = true
    )

    /** One truthful line about what a tool returned. */
    private fun describe(tool: String, result: HarnessToolRegistry.ExecutionResult): String {
        val first = firstMeaningfulLine(result.outputText)
        return when (result.status) {
            ToolCallStatus.SUCCESS -> "$tool succeeded: $first"
            ToolCallStatus.ERROR -> "$tool failed: $first"
            ToolCallStatus.RUNNING -> "$tool is still running."
        }
    }

    private fun reportCounts(calls: List<ToolCallItem>): String {
        if (calls.isEmpty()) return "No tool was run."
        val ok = calls.count { it.status == ToolCallStatus.SUCCESS }
        val failed = calls.count { it.status == ToolCallStatus.ERROR }
        return buildString {
            append("${calls.size} tool call(s): $ok succeeded, $failed failed.")
            calls.filter { it.status == ToolCallStatus.ERROR }.forEach { failedCall ->
                append("\n• ${failedCall.toolName} error: ")
                append(firstMeaningfulLine(failedCall.resultJson.orEmpty()))
            }
        }
    }

    private fun firstMeaningfulLine(text: String): String {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("$ ") }
            .toList()
        if (lines.isEmpty()) return "(no output)"

        // Prefer a line that actually reports an outcome over an echoed command or heading.
        val signal = lines.firstOrNull { line ->
            val low = line.lowercase()
            low.startsWith("exit:") ||
                low.contains("failed") ||
                low.contains("error") ||
                low.contains("not found") ||
                low.contains("could not") ||
                low.contains("cannot") ||
                low.contains("missing") ||
                low.contains("no such")
        }
        val chosen = signal ?: lines.first()
        return if (chosen.length <= MAX_QUOTE_CHARS) chosen else chosen.take(MAX_QUOTE_CHARS) + "…"
    }

    private fun containsAny(prompt: String, words: List<String>): Boolean =
        words.any { prompt.contains(it) }

    private fun extractFilePath(prompt: String): String? {
        val candidate = FILE_PATH_REGEX.find(prompt)?.value ?: return null
        val extension = candidate.substringAfterLast('.', "")
        // Reject bare words like "v1.0" that are not plausible source paths.
        val looksLikeSource = extension.matches(Regex("[A-Za-z]{1,5}"))
        return if (looksLikeSource || candidate.contains('/')) candidate else null
    }

    private fun extractSearchTerm(prompt: String): String? {
        // "search for foo" / "find foo" / "where is foo"
        val afterKeyword = Regex("""(?:search(?:\s+for)?|find|grep|where\s+is)\s+["'`]?([\w.$-]{2,60})""",
            RegexOption.IGNORE_CASE).find(prompt)
        if (afterKeyword != null) return afterKeyword.groupValues[1]

        // Otherwise the longest word that is not a stop word.
        val stop = setOf(
            "search", "find", "where", "grep", "usage", "reference", "the", "for", "and",
            "code", "file", "files", "please", "workspace", "this", "that", "with", "from"
        )
        return prompt.split(Regex("""[^\w.$-]+"""))
            .filter { it.length >= 3 && it.lowercase() !in stop }
            .maxByOrNull { it.length }
    }

    // ---------------------------------------------------------------------------------------
    // Real model path (BYOK_CUSTOM).
    // ---------------------------------------------------------------------------------------

    private suspend fun byokStep(
        userPrompt: String,
        previous: List<ToolCallItem>,
        settings: HarnessSettings
    ): AgentStepResult {
        val toolNames = toolRegistry.toolDefinitions.map { it.name }.toSet()

        val messages = buildMessages(userPrompt, previous, settings)

        val reply = llm.complete(
            baseUrl = settings.customEndpointUrl,
            apiKey = settings.customApiKey,
            model = settings.modelName,
            messages = messages,
            maxTokens = 1500
        )

        return when (reply) {
            is LlmClient.Reply.Failure -> AgentStepResult(
                thought = "Model request failed.",
                explanation = "The model endpoint did not answer: ${reply.message}",
                toolCall = null,
                isFinalStep = true
            )

            is LlmClient.Reply.Ok -> {
                val parsed = ToolCallParser.parse(reply.text)
                if (parsed.toolCall != null && parsed.toolCall?.name in toolNames) {
                    val approved = if (settings.autoApproveTools) {
                        true
                    } else {
                        approvalGate.request(parsed.toolCall!.name, parsed.toolCall!.argumentsJson)
                    }

                    if (!approved) {
                        return AgentStepResult(
                            thought = "User declined the tool call.",
                            explanation = "You denied ${parsed.toolCall!.name}. The model sees this as an error observation.",
                            toolCall = ToolCallItem(
                                id = "call_${System.currentTimeMillis()}_denied",
                                toolName = parsed.toolCall!.name,
                                argumentsJson = parsed.toolCall!.argumentsJson,
                                resultJson = "{\"error\": \"User declined\"}",
                                status = ToolCallStatus.ERROR,
                                durationMs = 0
                            ),
                            isFinalStep = false
                        )
                    }

                    val call = runTool(
                        tool = parsed.toolCall!.name,
                        args = parsed.toolCall!.argumentsJson,
                        thought = parsed.thought ?: "Model selected ${parsed.toolCall!.name}.",
                        intent = "Model-driven ${parsed.toolCall!.name}."
                    )
                    return call
                }

                AgentStepResult(
                    thought = parsed.thought,
                    explanation = parsed.conversationalText.ifBlank { reply.text },
                    toolCall = null,
                    isFinalStep = true
                )
            }
        }
    }

    private fun buildMessages(
        userPrompt: String,
        previous: List<ToolCallItem>,
        settings: HarnessSettings
    ): List<LlmClient.Message> {
        val sys = AgentPrompt.system(settings.localProtocol, toolRegistry.toolDefinitions)
        val msgs = mutableListOf<LlmClient.Message>(LlmClient.Message("system", sys))

        previous.forEach { tc ->
            msgs.add(LlmClient.Message("assistant", ""))
            msgs.add(LlmClient.Message("tool", AgentPrompt.observation(tc.toolName, tc.resultJson ?: "")))
        }

        msgs.add(LlmClient.Message("user", userPrompt))
        return msgs
    }
}
