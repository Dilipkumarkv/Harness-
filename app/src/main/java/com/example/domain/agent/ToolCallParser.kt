package com.example.domain.agent

import org.json.JSONObject

data class ParsedAgentResponse(
    val thought: String?,
    val conversationalText: String,
    val toolCall: ExtractedToolCall?
)

data class ExtractedToolCall(
    val name: String,
    val argumentsJson: String
)

object ToolCallParser {

    private val XML_TOOL_REGEX = Regex("<tool_call>([\\s\\S]*?)</tool_call>", RegexOption.IGNORE_CASE)
    private val THOUGHT_REGEX = Regex("<thought>([\\s\\S]*?)</thought>", RegexOption.IGNORE_CASE)
    private val REACT_ACTION_REGEX = Regex("Action:\\s*([a-zA-Z0-9_]+)\\s*Action Input:\\s*([\\s\\S]*?)(?=(?:Observation:|$))", RegexOption.IGNORE_CASE)
    private val JSON_BLOCK_REGEX = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): ParsedAgentResponse {
        var text = rawText

        // 1. Extract thought
        var thought: String? = null
        val thoughtMatch = THOUGHT_REGEX.find(text)
        if (thoughtMatch != null) {
            thought = thoughtMatch.groupValues[1].trim()
            text = text.replace(thoughtMatch.value, "").trim()
        } else if (text.startsWith("Thought:", ignoreCase = true)) {
            val thoughtEnd = text.indexOf("Action:", ignoreCase = true)
            if (thoughtEnd > 0) {
                thought = text.substring("Thought:".length, thoughtEnd).trim()
                text = text.substring(thoughtEnd).trim()
            }
        }

        // 2. Extract tool call (Format 1: <tool_call> JSON </tool_call>)
        val xmlMatch = XML_TOOL_REGEX.find(text)
        if (xmlMatch != null) {
            val payload = xmlMatch.groupValues[1].trim()
            val cleanText = text.replace(xmlMatch.value, "").trim()
            val parsedTool = parseToolJson(payload)
            if (parsedTool != null) {
                return ParsedAgentResponse(
                    thought = thought,
                    conversationalText = cleanText,
                    toolCall = parsedTool
                )
            }
        }

        // Format 2: ReAct syntax (Action: tool_name \n Action Input: {...})
        val reactMatch = REACT_ACTION_REGEX.find(text)
        if (reactMatch != null) {
            val toolName = reactMatch.groupValues[1].trim()
            val toolArgs = reactMatch.groupValues[2].trim()
            val cleanText = text.replace(reactMatch.value, "").trim()
            return ParsedAgentResponse(
                thought = thought,
                conversationalText = cleanText,
                toolCall = ExtractedToolCall(toolName, sanitizeJson(toolArgs))
            )
        }

        // Format 3: ```json { "tool": "name", "args": {...} } ```
        val jsonMatch = JSON_BLOCK_REGEX.find(text)
        if (jsonMatch != null) {
            val jsonPayload = jsonMatch.groupValues[1].trim()
            val parsedTool = parseToolJson(jsonPayload)
            if (parsedTool != null) {
                val cleanText = text.replace(jsonMatch.value, "").trim()
                return ParsedAgentResponse(
                    thought = thought,
                    conversationalText = cleanText,
                    toolCall = parsedTool
                )
            }
        }

        return ParsedAgentResponse(
            thought = thought,
            conversationalText = text,
            toolCall = null
        )
    }

    private fun parseToolJson(jsonString: String): ExtractedToolCall? {
        return try {
            val obj = JSONObject(sanitizeJson(jsonString))
            val name = when {
                obj.has("name") -> obj.getString("name")
                obj.has("tool") -> obj.getString("tool")
                obj.has("action") -> obj.getString("action")
                else -> return null
            }

            val args = when {
                obj.has("arguments") -> obj.get("arguments").toString()
                obj.has("args") -> obj.get("args").toString()
                obj.has("parameters") -> obj.get("parameters").toString()
                else -> "{}"
            }

            ExtractedToolCall(name, args)
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeJson(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed
        } else {
            "{$trimmed}"
        }
    }
}
