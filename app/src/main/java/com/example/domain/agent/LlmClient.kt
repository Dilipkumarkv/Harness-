package com.example.domain.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real HTTP client for any OpenAI-compatible `/chat/completions` endpoint.
 *
 * Works unchanged against OpenAI, OpenRouter, Groq, Together, Ollama
 * (`http://localhost:11434/v1`), llama.cpp server, LM Studio and LocalAI.
 * Every failure is returned as [Reply.Failure] with the status code and a slice of the response
 * body, so the UI can show what actually happened instead of guessing.
 */
class LlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    data class Message(val role: String, val content: String)

    sealed class Reply {
        data class Ok(val text: String) : Reply()
        data class Failure(val message: String) : Reply()
    }

    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Message>,
        maxTokens: Int = 1500
    ): Reply = withContext(Dispatchers.IO) {
        if (model.isBlank()) {
            return@withContext Reply.Failure("No model name configured. Set it in Settings > AI Model Engine.")
        }
        val url = buildUrl(baseUrl)
            ?: return@withContext Reply.Failure(
                "Endpoint must start with http:// or https:// — got: '$baseUrl'"
            )

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", maxTokens)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Reply.Failure(describeHttpError(response.code, body, url))
                }
                val text = extractContent(body)
                    ?: return@withContext Reply.Failure(
                        "Endpoint returned HTTP ${response.code} but no message content. Body: ${body.take(300)}"
                    )
                Reply.Ok(text)
            }
        } catch (e: IOException) {
            Reply.Failure(
                "Network request to $url failed: ${e.message ?: e::class.java.simpleName}. " +
                    "Check the URL, your connection, and the INTERNET permission."
            )
        } catch (e: Exception) {
            Reply.Failure("Model request failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    private fun buildUrl(baseUrl: String): String? {
        val base = baseUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) return null
        val trimmed = base.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    private fun extractContent(body: String): String? = try {
        val choice = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        message?.optString("content")?.takeIf { it.isNotBlank() && it != "null" }
            ?: choice?.optString("text")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun describeHttpError(code: Int, body: String, url: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?: body.take(300)
        } catch (_: Exception) {
            body.take(300)
        }
        val hint = when (code) {
            401, 403 -> "The API key was rejected."
            404 -> "Endpoint or model not found — check the URL path and model name."
            429 -> "Rate limited."
            in 500..599 -> "The provider reported a server error."
            else -> ""
        }
        return "HTTP $code from $url" + (if (hint.isNotEmpty()) ". $hint" else ". ") +
            (if (detail.isBlank()) "" else " Server said: $detail")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
