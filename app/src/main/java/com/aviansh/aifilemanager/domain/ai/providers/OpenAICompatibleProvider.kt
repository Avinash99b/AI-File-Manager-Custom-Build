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

    private val baseUrl: String by lazy {
        val trimmed = rawBaseUrl.trim().trimEnd('/')
        if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }

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
                put("role", if (msg.role.name == "USER") "user" else "assistant")
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
        try {
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
            statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
