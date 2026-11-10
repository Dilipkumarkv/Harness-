package com.example.domain.agent

import com.example.domain.model.LocalToolProtocol
import com.example.domain.model.ToolDefinition

/**
 * Builds the prompt a real model needs to drive the tool registry.
 *
 * The tool list is generated from [ToolDefinition]s, so adding a tool cannot leave the prompt out of
 * date. The protocol block matches what [ToolCallParser] actually understands, which means the
 * parser and the prompt cannot drift apart.
 */
object AgentPrompt {

    private const val MAX_OBSERVATION_CHARS = 4_000

    fun system(protocol: LocalToolProtocol, tools: List<ToolDefinition>): String {
        val toolList = tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}\n  arguments: ${tool.parametersJson}"
        }

        val format = when (protocol) {
            LocalToolProtocol.XML_TAGS ->
                "To call a tool, reply with exactly this and nothing else:\n" +
                    "<tool_call>{\"name\": \"tool_name\", \"arguments\": {\"arg\": \"value\"}}</tool_call>"

            LocalToolProtocol.REACT_TEXT ->
                "To call a tool, reply with exactly this:\n" +
                    "Thought: <one sentence of reasoning>\n" +
                    "Action: tool_name\n" +
                    "Action Input: {\"arg\": \"value\"}\n" +
                    "Do not write an Observation line; the harness supplies it."

            LocalToolProtocol.JSON_BLOCK ->
                "To call a tool, reply with a single fenced block and nothing else:\n" +
                    "```json\n{\"tool\": \"tool_name\", \"arguments\": {\"arg\": \"value\"}}\n```"
        }

        return """
You are AndroidHarness, a coding agent running on an Android phone.

You work inside a real project directory on the device. Tools read and write real files and run
real processes. Nothing is simulated.

AVAILABLE TOOLS
$toolList

HOW TO CALL A TOOL
$format

RULES
- Call at most one tool per reply.
- Only ever reference workspace-relative paths. Absolute paths and '..' are rejected.
- After a tool call you will receive an OBSERVATION containing the real result. Read it before deciding what to do next.
- Never claim something succeeded unless the observation says it did. If a tool reports an error, say so.
- When the task is finished, reply with plain prose and no tool call. That ends the turn.
        """.trimIndent()
    }

    /** Formats a real tool result back to the model as an observation. */
    fun observation(toolName: String, output: String): String {
        val trimmed = if (output.length <= MAX_OBSERVATION_CHARS) {
            output
        } else {
            output.take(MAX_OBSERVATION_CHARS) + "\n… (truncated, ${output.length} chars total)"
        }
        return "OBSERVATION from $toolName:\n$trimmed"
    }
}
