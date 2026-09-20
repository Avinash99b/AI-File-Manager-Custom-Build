package com.aviansh.aifilemanager.domain.ai.providers

import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMMessage
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.ai.LLMRequest
import com.aviansh.aifilemanager.domain.ai.LLMRole
import com.aviansh.aifilemanager.domain.ai.LLMStreamEvent
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OpenAICompatibleProvider(
    private val apiKey: String,
    private val modelName: String,
    private val rawBaseUrl: String = "https://api.openai.com/v1",
    private val timeoutMillis: Int = 30000
) : LLMProvider {

    /**
     * Normalizes whatever the user typed in settings into a usable base URL.
     *
     * - "https://api.openai.com"            -> "https://api.openai.com/v1"
     * - "https://api.openai.com/v1/"        -> "https://api.openai.com/v1"
     * - "http://127.0.0.1:11434"            -> "http://127.0.0.1:11434/v1"
     * - "https://host/openai/deployments/x" -> left untouched (custom gateways / Azure style)
     */
    private val baseUrl: String by lazy {
        var trimmed = rawBaseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) trimmed = "https://api.openai.com/v1"
        if (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.removeSuffix("/chat/completions")
        }
        val schemeEnd = trimmed.indexOf("://")
        val pathStart = if (schemeEnd >= 0) trimmed.indexOf('/', schemeEnd + 3) else trimmed.indexOf('/')
        val hasPath = pathStart >= 0
        if (hasPath) trimmed else "$trimmed/v1"
    }

    /** Exposed for tests / diagnostics: the URL actually used for requests. */
    fun resolvedBaseUrl(): String = baseUrl

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        conversation: List<ChatLmMessage>
    ): LLMGenerationResponse = withContext(Dispatchers.IO) {
        val messagesArray = JSONArray()

        if (!systemPrompt.isNullOrBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        conversation.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put(
                    "role",
                    when (msg.role.name) {
                        "USER" -> "user"
                        "SYSTEM" -> "system"
                        else -> "assistant"
                    }
                )
                put("content", msg.content)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("stream", false)
        }

        try {
            val url = URL("$baseUrl/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            if (statusCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val jsonResp = JSONObject(responseStr)
                val choices = jsonResp.getJSONArray("choices")
                if (choices.length() > 0) {
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    LLMGenerationResponse.SUCCESS(content)
                } else {
                    LLMGenerationResponse.FAILURE("OpenAI-compatible endpoint returned empty choices")
                }
            } else {
                val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                LLMGenerationResponse.FAILURE("HTTP $statusCode Error: $errorStr")
            }
        } catch (e: Exception) {
            LLMGenerationResponse.FAILURE("Network error: ${e.message}")
        }
    }

    override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = flow {
        val messagesArray = JSONArray()

        if (!request.systemPrompt.isNullOrBlank()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }

        request.messages.forEach { msg ->
            val roleStr = when (msg.role) {
                LLMRole.SYSTEM -> "system"
                LLMRole.USER -> "user"
                LLMRole.ASSISTANT -> "assistant"
                LLMRole.TOOL -> "user"
            }
            messagesArray.put(JSONObject().apply {
                put("role", roleStr)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", request.temperature)
            put("stream", true)
        }

        val fullTextBuilder = StringBuilder()

        try {
            val url = URL("$baseUrl/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            if (statusCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val text = delta?.optString("content", "") ?: ""
                                    if (text.isNotEmpty()) {
                                        fullTextBuilder.append(text)
                                        emit(LLMStreamEvent.TextDelta(text))
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore malformed SSE chunk
                            }
                        }
                    }
                }
                emit(LLMStreamEvent.Completed(fullTextBuilder.toString()))
            } else {
                val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                emit(LLMStreamEvent.Error("HTTP $statusCode: $errorStr", retryable = statusCode == 429 || statusCode >= 500))
            }
        } catch (e: Exception) {
            emit(LLMStreamEvent.Error("Streaming connection error: ${e.message}", retryable = true))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        // Most gateways expose /models, but some (Azure style deployments, a few proxies) do not.
        // Fall back to a minimal chat completion so those endpoints still validate correctly.
        if (probeModels()) return@withContext true
        probeChatCompletion()
    }

    private fun probeModels(): Boolean {
        return try {
            val url = URL("$baseUrl/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            val statusCode = conn.responseCode
            conn.disconnect()
            statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun probeChatCompletion(): Boolean {
        return try {
            val body = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                }))
                put("max_tokens", 1)
                put("stream", false)
            }

            val url = URL("$baseUrl/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            conn.disconnect()
            statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
