package com.aviansh.aifilemanager.domain.ai.providers

import android.util.Log
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMMessage
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.ai.LLMRequest
import com.aviansh.aifilemanager.domain.ai.LLMRole
import com.aviansh.aifilemanager.domain.ai.LLMStreamEvent
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

class GeminiAIProvider(
    private val apiKey: String,
    private val modelName: String = "gemini-3.1-flash-lite",
    private val maxRetries: Int = 3,
    private val timeoutMillis: Long = 30000L
) : LLMProvider {

    private val tag = "GeminiAIProvider"

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topP = 0.95f
                topK = 40
                maxOutputTokens = 2048
                responseMimeType = "text/plain"
            }
        )
    }

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        conversation: List<ChatLmMessage>
    ): LLMGenerationResponse = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(maxRetries) { attemptIndex ->
            try {
                val fullPrompt = buildString {
                    if (systemPrompt != null) {
                        appendLine("System: $systemPrompt")
                        appendLine()
                    }

                    conversation.forEach { (role, message) ->
                        appendLine("$role: $message")
                    }

                    appendLine("User: $prompt")
                }

                val response = generativeModel.generateContent(fullPrompt)
                val resultText = response.text ?: return@withContext LLMGenerationResponse.FAILURE(
                    error = "Empty response from Gemini"
                )

                return@withContext LLMGenerationResponse.SUCCESS(message = resultText)

            } catch (e: Exception) {
                lastException = e
                if (attemptIndex < maxRetries - 1) {
                    val delayMs = (2.0.pow(attemptIndex.toDouble()) * 1000).toLong()
                    kotlinx.coroutines.delay(delayMs.milliseconds)
                }
            }
        }

        LLMGenerationResponse.FAILURE(
            error = lastException?.message ?: "Unknown error after $maxRetries attempts"
        )
    }

    override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = flow {
        val fullPrompt = buildString {
            if (request.systemPrompt != null) {
                appendLine("System: ${request.systemPrompt}")
                appendLine()
            }
            request.messages.forEach { msg ->
                val roleName = when (msg.role) {
                    LLMRole.SYSTEM -> "System"
                    LLMRole.USER -> "User"
                    LLMRole.ASSISTANT -> "Assistant"
                    LLMRole.TOOL -> "Tool Result"
                }
                appendLine("$roleName: ${msg.content}")
            }
        }

        val fullTextBuilder = StringBuilder()
        try {
            generativeModel.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    fullTextBuilder.append(text)
                    emit(LLMStreamEvent.TextDelta(text))
                }
            }
            emit(LLMStreamEvent.Completed(fullTextBuilder.toString()))
        } catch (e: Exception) {
            emit(LLMStreamEvent.Error(e.message ?: "Gemini streaming error", retryable = false))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun test(): Boolean {
        return try {
            val response = generativeModel.generateContent("Hello")
            !response.text.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
