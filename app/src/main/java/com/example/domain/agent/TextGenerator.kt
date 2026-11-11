package com.example.domain.agent

import com.example.domain.model.HarnessSettings
import com.example.domain.agent.LlmClient
import com.example.domain.agent.OnDeviceGenerator
import com.example.domain.agent.HttpGenerator
import com.example.llm.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one thing a provider has to do: turn a chat transcript into text.
 *
 * Both the HTTP client and the on-device model implement this, so the agent loop does not care
 * where the words come from.
 */
interface TextGenerator {
    /** Short label for logs and the UI. */
    val description: String

    suspend fun complete(messages: List<LlmClient.Message>, maxTokens: Int): LlmClient.Reply
}

/** Talks to a remote OpenAI-compatible endpoint. */
class HttpGenerator(
    private val llm: LlmClient,
    private val settings: HarnessSettings
) : TextGenerator {

    override val description: String
        get() = "remote ${settings.modelName} at ${settings.customEndpointUrl}"

    override suspend fun complete(
        messages: List<LlmClient.Message>,
        maxTokens: Int
    ): LlmClient.Reply = llm.complete(
        baseUrl = settings.customEndpointUrl,
        apiKey = settings.customApiKey,
        model = settings.modelName,
        messages = messages,
        maxTokens = maxTokens
    )
}

/** Runs a `.gguf` model inside the app through llama.cpp. */
class OnDeviceGenerator(
    private val models: ModelManager,
    private val settings: HarnessSettings
) : TextGenerator {

    override val description: String
        get() = "on-device ${settings.modelPath.substringAfterLast('/').ifBlank { "(no model)" }}"

    override suspend fun complete(
        messages: List<LlmClient.Message>,
        maxTokens: Int
    ): LlmClient.Reply = withContext(Dispatchers.IO) {
        val loadError = models.ensureLoaded(
            path = settings.modelPath,
            threads = settings.modelThreads,
            contextSize = settings.modelContextSize
        )
        if (loadError != null) return@withContext LlmClient.Reply.Failure(loadError)

        val prompt = ChatTemplate.render(messages)
        val text = try {
            models.generate(
                prompt = prompt,
                maxTokens = maxTokens,
                threads = settings.modelThreads,
                contextSize = settings.modelContextSize
            )
        } catch (e: Exception) {
            return@withContext LlmClient.Reply.Failure(
                "On-device generation failed: ${e.message ?: e.javaClass.simpleName}"
            )
        }

        if (text.isBlank()) {
            LlmClient.Reply.Failure(
                "The on-device model returned nothing. It may have run out of context " +
                    "(${settings.modelContextSize} tokens) or failed to decode."
            )
        } else {
            LlamaReply.clean(text)
        }
    }
}

/**
 * Prompt formatting for on-device models.
 *
 * ChatML is the format Qwen and several small coders are trained on, and it tolerates being used
 * with models that were trained on something close to it. It is deliberately not a per-model
 * template engine: that is the kind of complexity this project is avoiding.
 */
object ChatTemplate {

    private const val START = "<|im_start|>"
    private const val END = "<|im_end|>"

    fun render(messages: List<LlmClient.Message>): String = buildString {
        for (message in messages) {
            val role = when (message.role) {
                "system" -> "system"
                "assistant" -> "assistant"
                "tool" -> "user" // observations are shown to the model as user turns
                else -> "user"
            }
            append(START).append(role).append('\n')
            append(message.content.trim())
            append(END).append('\n')
        }
        append(START).append("assistant").append('\n')
    }

    /** Strips template tokens a model sometimes echoes back. */
    fun stripArtifacts(text: String): String = text
        .substringBefore(END)
        .replace(START, "")
        .trim()
}

/** Small adapter so [LlamaReply] reads naturally at the call site. */
private object LlamaReply {
    fun clean(text: String): LlmClient.Reply =
        LlmClient.Reply.Ok(ChatTemplate.stripArtifacts(text))
}
