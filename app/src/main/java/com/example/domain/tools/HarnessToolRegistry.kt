package com.example.domain.tools

import com.example.data.dao.WorkspaceFilesDao
import com.example.data.entity.WorkspaceFileEntity
import com.example.domain.model.DiffLineType
import com.example.domain.model.DoctorTestResult
import com.example.domain.model.ToolCallStatus
import com.example.domain.model.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The nine developer tools the agent can call.
 *
 * Every tool talks to the real device: [WorkspaceFs] for files, [ProcessRunner] for `shell_exec`
 * and git, and the Room DAO purely as a *display index* for the Files tab. Failures are reported as
 * failures — no tool pretends a build or a git command succeeded.
 */
class HarnessToolRegistry(
    private val filesDao: WorkspaceFilesDao,
    private val workspace: WorkspaceFs,
    private val currentProjectId: () -> String,
    private val gitCommand: () -> String = { "git" }
) {

    companion object {
        private const val DEFAULT_SHELL_TIMEOUT_MS = 120_000L
        private const val MAX_SHELL_TIMEOUT_MS = 600_000L
        private const val MAX_OUTPUT_CHARS = 60_000

        /** Commands `shell_exec` is allowed to run. Everything else is refused. */
        private val ALLOWED_COMMANDS = setOf(
            "gradle", "gradlew", "git", "ls", "find", "grep", "cat", "head", "tail",
            "wc", "pwd", "tree", "du", "stat", "file", "echo", "uname", "date", "which"
        )

        private val BLOCKED_TOKENS = setOf("-exec", "-execdir", "-delete", "-ok", "-okdir")
    }

    val toolDefinitions = listOf(
        ToolDefinition(
            name = "read_file",
            description = "Reads a UTF-8 text file from the workspace (512 KB limit).",
            parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"Workspace relative file path"}},"required":["path"]}""",
            iconName = "read_file"
        ),
        ToolDefinition(
            name = "write_file",
            description = "Writes full content to a workspace file atomically, creating parent directories as needed.",
            parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"Workspace file path"},"content":{"type":"string","description":"Full text content to write"}},"required":["path","content"]}""",
            iconName = "write_file"
        ),
        ToolDefinition(
            name = "apply_patch",
            description = "Applies a unified diff (@@ hunks) to an existing workspace file, tolerating line drift and whitespace fuzz. Fails instead of guessing if the hunk does not match.",
            parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"File path to patch"},"patch":{"type":"string","description":"Unified diff with @@ hunks, or full replacement content"}},"required":["path","patch"]}""",
            iconName = "apply_patch"
        ),
        ToolDefinition(
            name = "list_directory",
            description = "Lists real files and directories under a workspace path.",
            parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"Directory path, empty for the workspace root"},"depth":{"type":"integer","description":"Recursion depth, default 2"}},"required":[]}""",
            iconName = "list_directory"
        ),
        ToolDefinition(
            name = "search_code",
            description = "Searches file contents on disk and returns path:line matches.",
            parametersJson = """{"type":"object","properties":{"query":{"type":"string","description":"Text to find"},"include":{"type":"string","description":"Optional glob such as *.kt"},"maxResults":{"type":"integer","description":"Default 100"}},"required":["query"]}""",
            iconName = "search_code"
        ),
        ToolDefinition(
            name = "shell_exec",
            description = "Runs one allowlisted developer command (git, gradle, ls, find, grep, cat, head, tail, wc, pwd, tree, du, stat, file, echo, uname, date, which) with a timeout. No shell, no pipes, no redirection.",
            parametersJson = """{"type":"object","properties":{"command":{"type":"string","description":"Command line, for example: git status"},"timeoutMs":{"type":"integer","description":"Default 120000"}},"required":["command"]}""",
            iconName = "terminal"
        ),
        ToolDefinition(
            name = "git_status",
            description = "Runs git status --porcelain in the workspace.",
            parametersJson = """{"type":"object","properties":{},"required":[]}""",
            iconName = "git_status"
        ),
        ToolDefinition(
            name = "git_diff",
            description = "Runs git diff on the workspace, reporting untracked files separately.",
            parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"Optional file path filter"}},"required":[]}""",
            iconName = "git_diff"
        ),
        ToolDefinition(
            name = "doctor_diagnostics",
            description = "Runs real health checks: workspace write probe, index consistency, diff round-trip, shell runner, git availability.",
            parametersJson = """{"type":"object","properties":{},"required":[]}""",
            iconName = "doctor"
        )
    )

    data class ExecutionResult(
        val status: ToolCallStatus,
        val outputText: String,
        val diffContent: String? = null
    )

    // ---------------------------------------------------------------------------------------
    // Dispatch
    // ---------------------------------------------------------------------------------------

    suspend fun executeTool(name: String, argumentsJson: String): ExecutionResult = withContext(Dispatchers.IO) {
        val json = try {
            JSONObject(argumentsJson.ifBlank { "{}" })
        } catch (_: Exception) {
            return@withContext error("Invalid tool arguments: expected a JSON object, got: ${argumentsJson.take(160)}")
        }

        try {
            when (name) {
                "read_file" -> readFile(json.optString("path", ""))
                "write_file" -> writeFile(json.optString("path", ""), json.optString("content", ""))
                "apply_patch" -> applyPatchFile(json.optString("path", ""), json.optString("patch", ""))
                "list_directory" -> listDirectory(json.optString("path", ""), json.optInt("depth", 2))
                "search_code" -> searchCode(
                    query = json.optString("query", ""),
                    include = json.optString("include", "").ifBlank { null },
                    maxResults = json.optInt("maxResults", 100)
                )
                "shell_exec" -> runCommand(
                    command = json.optString("command", ""),
                    timeoutMs = json.optLong("timeoutMs", DEFAULT_SHELL_TIMEOUT_MS)
                )
                "git_status" -> gitStatus()
                "git_diff" -> gitDiff(json.optString("path", "").ifBlank { null })
                "doctor_diagnostics" -> doctorSummary()
                else -> error("Unknown tool: '$name'")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WorkspaceFs.PathException) {
            error(e.message ?: "Invalid path")
        } catch (e: WorkspaceFs.IoFailure) {
            error(e.message ?: "File operation failed")
        } catch (e: SecurityException) {
            error("Permission denied by Android: ${e.message}")
        } catch (e: Exception) {
            error("$name failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Filesystem tools
    // ---------------------------------------------------------------------------------------

    private suspend fun readFile(path: String): ExecutionResult {
        if (path.isBlank()) throw WorkspaceFs.IoFailure("read_file requires a non-empty 'path'.")
        val file = workspace.resolve(path)
        if (!file.exists()) {
            val near = similarPaths(path)
            return error(
                "File not found: '$path'." +
                    if (near.isEmpty()) " Use list_directory to see what exists." else " Did you mean: ${near.joinToString(", ")}?"
            )
        }
        val content = workspace.readText(path)
        val lines = content.count { it == '\n' } + if (content.isEmpty()) 0 else 1
        val body = if (content.isEmpty()) "(empty file)" else content
        return ExecutionResult(
            status = ToolCallStatus.SUCCESS,
            outputText = "File: $path ($lines lines, ${file.length()} bytes)\n\n$body"
        )
    }

    private suspend fun writeFile(path: String, content: String): ExecutionResult {
        if (path.isBlank()) throw WorkspaceFs.IoFailure("write_file requires a non-empty 'path'.")
        val existed = workspace.exists(path)
        val oldContent = if (existed) {
            runCatching { workspace.readText(path) }.getOrDefault("")
        } else {
            ""
        }

        val info = workspace.writeText(path, content)
        indexFile(path, content)

        val diff = PatchEngine.computeDiff(oldContent, content, path)
        val added = diff.count { it.type == DiffLineType.ADDED }
        val removed = diff.count { it.type == DiffLineType.REMOVED }
        val lineCount = content.count { it == '\n' } + if (content.isEmpty()) 0 else 1

        return ExecutionResult(
            status = ToolCallStatus.SUCCESS,
            outputText = buildString {
                append(if (info.created) "Created " else "Updated ")
                append(path)
                append("\n  ")
                append(lineCount)
                append(" lines, ")
                append(info.bytes)
                append(" bytes")
                append(" (+$added/-$removed vs previous)")
            },
            diffContent = if (added + removed > 0) PatchEngine.formatPatchString(diff) else null
        )
    }

    private suspend fun applyPatchFile(path: String, patchText: String): ExecutionResult {
        if (path.isBlank()) throw WorkspaceFs.IoFailure("apply_patch requires a non-empty 'path'.")
        if (patchText.isBlank()) throw WorkspaceFs.IoFailure("apply_patch requires a non-empty 'patch'.")
        if (!workspace.exists(path)) {
            return error("Cannot apply a patch to missing file '$path'. Use write_file to create it first.")
        }

        val original = workspace.readText(path)
        return when (val result = PatchEngine.applyPatch(original, patchText)) {
            is PatchEngine.ApplyResult.Failure -> error(result.message)
            is PatchEngine.ApplyResult.Success -> {
                workspace.writeText(path, result.content)
                indexFile(path, result.content)

                val diff = PatchEngine.computeDiff(original, result.content, path)
                val before = original.count { it == '\n' } + if (original.isEmpty()) 0 else 1
                val after = result.content.count { it == '\n' } + if (result.content.isEmpty()) 0 else 1

                val detail = buildString {
                    if (result.hunksApplied == 0) {
                        append("Replaced the whole file (patch contained no @@ hunks).")
                    } else {
                        append("Applied ${result.hunksApplied} hunk(s) to $path.")
                    }
                    append("\n  lines: $before -> $after")
                    if (result.maxFuzz > 0) append("\n  whitespace fuzz used: level ${result.maxFuzz}")
                    if (result.maxOffset > 0) append("\n  matched ${result.maxOffset} line(s) away from the header offset")
                }

                ExecutionResult(
                    status = ToolCallStatus.SUCCESS,
                    outputText = detail,
                    diffContent = PatchEngine.formatPatchString(diff)
                )
            }
        }
    }

    private fun listDirectory(path: String, depth: Int): ExecutionResult {
        val safeDepth = depth.coerceIn(1, 6)
        val entries = workspace.listEntries(path, safeDepth, limit = 300)
        val dirs = entries.count { it.isDirectory }
        val files = entries.size - dirs
        val base = workspace.resolve(path)

        val body = buildString {
            append(if (path.isBlank()) "Workspace root: ${workspace.canonicalRoot().path}" else "Directory: $path")
            append("\n$dirs director${if (dirs == 1) "y" else "ies"}, $files file(s), depth $safeDepth\n")
            if (entries.isEmpty()) {
                append("\n(empty directory)")
            } else {
                for (entry in entries) {
                    val relativeToBase = if (base.path == workspace.canonicalRoot().path) {
                        entry.path
                    } else {
                        entry.path.removePrefix(workspace.relativeOf(base)).removePrefix("/")
                    }
                    val indent = "  ".repeat((relativeToBase.count { it == '/' }).coerceAtMost(6))
                    append('\n')
                    append(indent)
                    append(entry.path.substringAfterLast('/'))
                    if (entry.isDirectory) append('/') else append(" (${entry.size} B)")
                }
            }
            if (entries.size >= 300) append("\n\n(truncated at 300 entries)")
        }

        return ExecutionResult(ToolCallStatus.SUCCESS, body)
    }

    private fun searchCode(query: String, include: String?, maxResults: Int): ExecutionResult {
        if (query.isBlank()) throw WorkspaceFs.IoFailure("search_code requires a non-empty 'query'.")
        val matches = workspace.search(query, include, maxResults.coerceIn(1, 500))
        if (matches.isEmpty()) {
            return ExecutionResult(
                ToolCallStatus.SUCCESS,
                "No matches for '$query'" + (include?.let { " in files matching '$it'" } ?: "")
            )
        }
        return ExecutionResult(
            ToolCallStatus.SUCCESS,
            "${matches.size} match(es) for '$query':\n\n" +
                matches.joinToString("\n") { "${it.path}:${it.line}: ${it.text}" }
        )
    }

    // ---------------------------------------------------------------------------------------
    // Shell
    // ---------------------------------------------------------------------------------------

    suspend fun runCommand(command: String, timeoutMs: Long = DEFAULT_SHELL_TIMEOUT_MS): ExecutionResult =
        withContext(Dispatchers.IO) { runCommandBlocking(command, timeoutMs) }

    private fun runCommandBlocking(command: String, timeoutMs: Long): ExecutionResult {
        val raw = command.trim()
        if (raw.isEmpty()) throw WorkspaceFs.IoFailure("shell_exec requires a non-empty 'command'.")

        val argv = tokenize(raw)
        if (argv.isEmpty()) throw WorkspaceFs.IoFailure("Could not parse command: $raw")

        val program = argv.first().substringAfterLast('/')
        if (program !in ALLOWED_COMMANDS) {
            return error(
                "'$program' is not an allowlisted command. Allowed: ${ALLOWED_COMMANDS.sorted().joinToString(", ")}."
            )
        }
        val blocked = argv.firstOrNull { it in BLOCKED_TOKENS }
        if (blocked != null) {
            return error("Argument '$blocked' is blocked because it executes further commands.")
        }
        val metachar = raw.firstOrNull { it in ";|&<>`" || it == '$' }
        if (metachar != null) {
            return error(
                "Shell metacharacter '$metachar' is not supported: commands run directly, without a shell. " +
                    "Run one command at a time (use search_code instead of piping grep)."
            )
        }

        val result = ProcessRunner.run(
            argv = argv,
            workingDir = workspace.canonicalRoot(),
            timeoutMs = timeoutMs.coerceIn(1_000L, MAX_SHELL_TIMEOUT_MS),
            maxOutputBytes = MAX_OUTPUT_CHARS
        )

        if (result.launchError != null) {
            return error(
                "Could not start '$program' on this device (${result.launchError}). " +
                    "Android does not ship a Gradle or Git binary by default; point shell_exec at a binary that exists in PATH."
            )
        }

        val text = buildString {
            append("$ ${result.commandLine}\n")
            append("exit: ${result.exitCode}${if (result.timedOut) " (timed out)" else ""}  ${result.durationMs} ms\n")
            append("\n")
            append(result.stdout.take(MAX_OUTPUT_CHARS).ifBlank { "(no stdout)" })
            if (result.stderr.isNotBlank()) {
                append("\n\n--- stderr ---\n")
                append(result.stderr.take(MAX_OUTPUT_CHARS))
            }
        }

        return ExecutionResult(
            status = if (result.isSuccess) ToolCallStatus.SUCCESS else ToolCallStatus.ERROR,
            outputText = text
        )
    }

    // ---------------------------------------------------------------------------------------
    // Git
    // ---------------------------------------------------------------------------------------

    private fun git(args: List<String>, timeoutMs: Long = 30_000L): ProcessRunner.Result {
        val exe = gitCommand().ifBlank { "git" }
        return ProcessRunner.run(listOf(exe) + args, workspace.canonicalRoot(), timeoutMs)
    }

    private fun gitUnavailable(result: ProcessRunner.Result): ExecutionResult? {
        if (result.launchError == null) return null
        return error(
            "Git is not available (${result.launchError}). Install git or set the git path in Settings. " +
                "Attempted: ${result.commandLine}"
        )
    }

    suspend fun gitStatus(): ExecutionResult = withContext(Dispatchers.IO) {
        val result = git(listOf("status", "--porcelain=v1", "--branch"))
        gitUnavailable(result)?.let { return@withContext it }

        if (!result.isSuccess) {
            val stderr = result.stderr.ifBlank { result.stdout }
            return@withContext error(
                if (stderr.contains("not a git repository", ignoreCase = true)) {
                    "'${workspace.canonicalRoot().path}' is not a git repository. Run: git init"
                } else {
                    "git status failed (exit ${result.exitCode}):\n$stderr"
                }
            )
        }

        val lines = result.stdout.split("\n").map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return@withContext ExecutionResult(ToolCallStatus.SUCCESS, "Working tree clean.")
        }

        val branchLine = lines.firstOrNull { it.startsWith("##") }
        val entries = lines.filterNot { it.startsWith("##") }

        val text = buildString {
            append(branchLine?.removePrefix("## ")?.trim() ?: "git status")
            append("\n\n")
            if (entries.isEmpty()) {
                append("Working tree clean.")
            } else {
                append("${entries.size} changed path(s):\n")
                for (entry in entries) {
                    val code = entry.take(2)
                    val path = entry.drop(3)
                    append("  ")
                    append(code.trim().ifEmpty { "??" }.padEnd(2))
                    append(' ')
                    append(path)
                }
            }
        }

        ExecutionResult(ToolCallStatus.SUCCESS, text)
    }

    suspend fun gitDiff(path: String? = null): ExecutionResult = withContext(Dispatchers.IO) {
        val args = mutableListOf("diff", "--no-color")
        if (path != null) args.addAll(listOf("--", path))
        val result = git(args)
        gitUnavailable(result)?.let { return@withContext it }

        if (!result.isSuccess) {
            val stderr = result.stderr.ifBlank { result.stdout }
            return@withContext error(
                if (stderr.contains("not a git repository", ignoreCase = true)) {
                    "'${workspace.canonicalRoot().path}' is not a git repository. Run: git init"
                } else {
                    "git diff failed (exit ${result.exitCode}):\n$stderr"
                }
            )
        }

        val diffText = result.stdout.trimEnd()
        val untracked = untrackedFiles()

        val text = buildString {
            if (diffText.isEmpty()) {
                append("No tracked modifications")
                append(if (path != null) " in '$path'" else "")
                append('.')
            } else {
                append(diffText.take(MAX_OUTPUT_CHARS))
            }
            if (untracked.isNotEmpty()) {
                append("\n\n--- untracked (git diff does not show these) ---\n")
                append(untracked.take(40).joinToString("\n") { "  $it" })
                if (untracked.size > 40) append("\n  ... ${untracked.size - 40} more")
            }
        }

        ExecutionResult(
            status = ToolCallStatus.SUCCESS,
            outputText = text,
            diffContent = diffText.ifBlank { null }
        )
    }

    suspend fun gitLog(limit: Int = 10): ExecutionResult = withContext(Dispatchers.IO) {
        val result = git(listOf("log", "--no-color", "-n", limit.coerceIn(1, 50).toString(), "--pretty=format:%h %ad %an: %s", "--date=short"))
        gitUnavailable(result)?.let { return@withContext it }

        if (!result.isSuccess) {
            val stderr = result.stderr.ifBlank { result.stdout }
            val text = when {
                stderr.contains("does not have any commits", ignoreCase = true) ||
                    stderr.contains("not a git repository", ignoreCase = true) -> "No commits yet."
                else -> "git log failed (exit ${result.exitCode}):\n$stderr"
            }
            return@withContext ExecutionResult(
                if (text.startsWith("No commits")) ToolCallStatus.SUCCESS else ToolCallStatus.ERROR,
                text
            )
        }

        ExecutionResult(
            ToolCallStatus.SUCCESS,
            result.stdout.trimEnd().ifBlank { "No commits yet." }
        )
    }

    private fun untrackedFiles(): List<String> {
        val status = git(listOf("status", "--porcelain=v1"))
        if (!status.isSuccess) return emptyList()
        return status.stdout.split("\n")
            .filter { it.startsWith("??") }
            .map { it.drop(3).trim() }
    }

    // ---------------------------------------------------------------------------------------
    // Doctor
    // ---------------------------------------------------------------------------------------

    private suspend fun doctorSummary(): ExecutionResult {
        val results = runDiagnostics()
        return ExecutionResult(
            status = if (results.all { it.isSuccess }) ToolCallStatus.SUCCESS else ToolCallStatus.ERROR,
            outputText = buildString {
                append("AndroidHarness doctor — ${results.count { it.isSuccess }}/${results.size} checks passed\n")
                for (r in results) {
                    append("\n[${if (r.isSuccess) "PASS" else "FAIL"}] ${r.name} (${r.durationMs} ms)\n")
                    append("       ${r.message}\n")
                }
            }
        )
    }

    suspend fun runDiagnostics(): List<DoctorTestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DoctorTestResult>()

        // 1. Real write/read/delete probe in the workspace.
        var t0 = System.currentTimeMillis()
        try {
            val probeName = ".harness-probe"
            val payload = "probe ${System.currentTimeMillis()}"
            workspace.writeText(probeName, payload)
            val readBack = workspace.readText(probeName)
            workspace.delete(probeName)
            results.add(
                DoctorTestResult(
                    name = "Workspace filesystem",
                    description = "Write, read and delete a probe file in the project root",
                    isSuccess = readBack == payload,
                    message = if (readBack == payload) {
                        "Round-trip OK at ${workspace.canonicalRoot().path}"
                    } else {
                        "Read-back mismatch at ${workspace.canonicalRoot().path}"
                    },
                    durationMs = System.currentTimeMillis() - t0
                )
            )
        } catch (e: Exception) {
            results.add(
                DoctorTestResult(
                    name = "Workspace filesystem",
                    description = "Write, read and delete a probe file in the project root",
                    isSuccess = false,
                    message = "Failed: ${e.message}",
                    durationMs = System.currentTimeMillis() - t0
                )
            )
        }

        // 2. Disk vs Room index.
        t0 = System.currentTimeMillis()
        val diskFiles = runCatching { workspace.walkFiles() }.getOrDefault(emptyList())
        val indexed = runCatching {
            filesDao.getFilesForProject(currentProjectId()).first()
        }.getOrDefault(emptyList())
        val indexedPaths = indexed.map { it.path }.toSet()
        val missing = diskFiles.count { it !in indexedPaths }
        results.add(
            DoctorTestResult(
                name = "Workspace index",
                description = "Room index matches the files on disk",
                isSuccess = missing == 0,
                message = "${diskFiles.size} file(s) on disk, ${indexed.size} indexed" +
                    if (missing > 0) " — $missing not indexed" else "",
                durationMs = System.currentTimeMillis() - t0
            )
        )

        // 3. Real diff -> patch -> apply round trip.
        t0 = System.currentTimeMillis()
        val before = "fun a() = 1\nfun b() = 2\nfun c() = 3\n"
        val after = "fun a() = 1\nfun b() = 22\nfun c() = 3\nfun d() = 4\n"
        val patch = PatchEngine.formatPatchString(PatchEngine.computeDiff(before, after, "probe.kt"))
        val applied = PatchEngine.applyPatch(before, patch)
        val roundTripped = applied is PatchEngine.ApplyResult.Success && applied.content == after
        results.add(
            DoctorTestResult(
                name = "Patch & diff engine",
                description = "Compute a unified diff, then apply it back",
                isSuccess = roundTripped,
                message = if (roundTripped) {
                    "Round-trip identical (${PatchEngine.parseHunks(patch).size} hunk)"
                } else {
                    "Round-trip mismatch: ${(applied as? PatchEngine.ApplyResult.Failure)?.message ?: "content differs"}"
                },
                durationMs = System.currentTimeMillis() - t0
            )
        )

        // 4. Shell runner (no shell, direct exec of an always-present binary).
        t0 = System.currentTimeMillis()
        val echo = ProcessRunner.run(listOf("echo", "harness-ok"), workspace.canonicalRoot(), 5_000L)
        val shellOk = echo.isSuccess && echo.stdout.contains("harness-ok")
        results.add(
            DoctorTestResult(
                name = "Shell runner",
                description = "Execute a command with ProcessBuilder and capture its output",
                isSuccess = shellOk,
                message = if (shellOk) {
                    "'echo' answered in ${echo.durationMs} ms"
                } else {
                    "Could not run 'echo': ${echo.launchError ?: (echo.stderr.ifBlank { "exit ${echo.exitCode}" })}"
                },
                durationMs = System.currentTimeMillis() - t0
            )
        )

        // 5. Git availability (real).
        t0 = System.currentTimeMillis()
        val gitVersion = git(listOf("--version"))
        val gitOk = gitVersion.isSuccess
        results.add(
            DoctorTestResult(
                name = "Git toolchain",
                description = "git_status and git_diff need a git binary",
                isSuccess = gitOk,
                message = if (gitOk) {
                    gitVersion.stdout.trim()
                } else {
                    "Not available (${gitVersion.launchError ?: gitVersion.stderr.trim()}). " +
                        "Android has no built-in git; set the binary path in Settings."
                },
                durationMs = System.currentTimeMillis() - t0
            )
        )

        // 6. Tool definitions are consistent.
        t0 = System.currentTimeMillis()
        val names = toolDefinitions.map { it.name }
        results.add(
            DoctorTestResult(
                name = "Tool definitions",
                description = "Every tool name is unique and has a JSON schema",
                isSuccess = names.size == names.toSet().size,
                message = "${names.size} tools: ${names.joinToString(", ")}",
                durationMs = System.currentTimeMillis() - t0
            )
        )

        results
    }

    // ---------------------------------------------------------------------------------------
    // Room index (display only — the filesystem is the source of truth)
    // ---------------------------------------------------------------------------------------

    /** Rebuilds the Room index from disk and returns the number of indexed files. */
    suspend fun reindex(): Int = withContext(Dispatchers.IO) {
        val projectId = currentProjectId()
        val onDisk = workspace.walkFiles()
        val existing = filesDao.getFilesForProject(projectId).first().associateBy { it.path }
        var indexed = 0

        for (path in onDisk) {
            val file = File(workspace.canonicalRoot(), path)
            if (file.length() > WorkspaceFs.MAX_INDEX_BYTES) continue
            val content = runCatching { workspace.readText(path) }.getOrNull() ?: continue
            val row = existing[path]
            if (row == null || row.content != content) {
                filesDao.insertFile(
                    WorkspaceFileEntity(
                        id = row?.id ?: fileId(projectId, path),
                        projectId = projectId,
                        path = path,
                        content = content,
                        language = workspace.languageFor(path),
                        lastModified = file.lastModified()
                    )
                )
                indexed++
            }
        }

        val diskSet = onDisk.toSet()
        existing.keys.filter { it !in diskSet }.forEach { filesDao.deleteFile(projectId, it) }
        indexed
    }

    private suspend fun indexFile(path: String, content: String) {
        val projectId = currentProjectId()
        if (content.toByteArray(Charsets.UTF_8).size > WorkspaceFs.MAX_INDEX_BYTES) return
        val existing = filesDao.getFileByPath(projectId, path)
        filesDao.insertFile(
            WorkspaceFileEntity(
                id = existing?.id ?: fileId(projectId, path),
                projectId = projectId,
                path = path,
                content = content,
                language = workspace.languageFor(path),
                lastModified = System.currentTimeMillis()
            )
        )
    }

    private fun fileId(projectId: String, path: String): String {
        val hash = path.hashCode().toLong().let { if (it < 0) -it else it }
        return "file_${projectId}_$hash"
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun error(message: String): ExecutionResult = ExecutionResult(ToolCallStatus.ERROR, message)

    private fun similarPaths(path: String): List<String> {
        val name = path.substringAfterLast('/')
        val all = runCatching { workspace.walkFiles(400) }.getOrDefault(emptyList())
        return all.filter { it.endsWith(name) || it.substringAfterLast('/').contains(name, ignoreCase = true) }
            .take(3)
    }

    /** Splits a command line into argv, honouring single and double quotes. */
    private fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var hasToken = false
        for (c in command) {
            when {
                quote != null -> if (c == quote) quote = null else current.append(c)
                c == '"' || c == '\'' -> {
                    quote = c
                    hasToken = true
                }
                c.isWhitespace() -> {
                    if (hasToken || current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                        hasToken = false
                    }
                }
                else -> {
                    current.append(c)
                    hasToken = true
                }
            }
        }
        if (hasToken || current.isNotEmpty()) tokens.add(current.toString())
        return tokens.filter { it.isNotEmpty() }
    }
}
