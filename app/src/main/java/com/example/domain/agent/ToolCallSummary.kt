package com.example.domain.agent

/**
 * Builds a one-line, human-readable summary of a pending tool call for the approval dialog.
 * Parses arguments with regexes so it stays free of Android dependencies (unit-testable on the JVM)
 * and never throws — tools pass arbitrary JSON here.
 */
object ToolCallSummary {

    fun describe(toolName: String, argumentsJson: String): String = when (toolName) {
        "write_file" -> {
            val path = jsonString(argumentsJson, "path")
            val size = jsonString(argumentsJson, "content")?.length
            buildString {
                append(if (path != null) "Write file `$path`" else "Write a file")
                if (size != null) append(" (${formatBytes(size)})")
            }
        }

        "apply_patch" -> {
            val path = jsonString(argumentsJson, "path")
            val patch = jsonString(argumentsJson, "patch")
            val hunks = patch?.let { Regex("@@ -\\d+").findAll(it).count() } ?: 0
            buildString {
                append(if (path != null) "Modify `$path`" else "Apply a patch")
                if (hunks > 0) append(" ($hunks hunk${if (hunks == 1) "" else "s"})")
                else append(" (whole-file replacement)")
            }
        }

        "shell_exec" -> {
            val command = jsonString(argumentsJson, "command")
            if (command != null) "Run `${command.take(80)}`" else "Run a command"
        }

        "read_file" -> "Read `${jsonString(argumentsJson, "path") ?: "a file"}`"

        "search_code" -> "Search for `${jsonString(argumentsJson, "query") ?: "?"}`"

        "list_directory" -> {
            val path = jsonString(argumentsJson, "path")
            if (path.isNullOrBlank()) "List workspace root" else "List `$path`"
        }

        else -> toolName
    }

    /** Extracts the raw JSON string value for [key]; null when absent or not a string. */
    private fun jsonString(json: String, key: String): String? {
        // Matches "key": "value" with basic escape handling (\" \\ \/ \n \t \r \uXXXX).
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return null
        return raw
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun formatBytes(bytes: Int): String =
        if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
}
