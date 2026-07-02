package com.aviansh.aifilemanager.domain.ai.providers

import android.util.Log
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds


class GeminiAIProvider(
    private val apiKey: String,
    private val modelName: String = "gemini-3.1-flash-lite",
    private val maxRetries: Int = 3,
    private val timeoutMillis: Long = 30000L
) : LLMProvider{

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


    /**
     * Send a message to Gemini and get a response with automatic retry and backoff.
     */
    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        conversation: List<ChatLmMessage>
    ): LLMGenerationResponse = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(maxRetries) { attemptIndex ->
            try {
                Log.d(tag, "Attempt ${attemptIndex + 1}/$maxRetries for prompt: ${prompt.take(50)}")

                val fullPrompt = buildString {
                    if (systemPrompt != null) {
                        appendLine("System: $systemPrompt")
                        appendLine()
                    }

                    // Add conversation context
                    conversation.forEach { (role, message) ->
                        appendLine("$role: $message")
                    }

                    appendLine("User: $prompt")
                }

                val response = generativeModel.generateContent(fullPrompt)

                val resultText = response.text ?: return@withContext LLMGenerationResponse.FAILURE(
                    error = "Empty response from Gemini"
                )

                Log.d(tag, "Success on attempt ${attemptIndex + 1}")

                return@withContext LLMGenerationResponse.SUCCESS(
                    message = resultText
                )

            } catch (e: Exception) {
                lastException = e
                Log.w(tag, "Attempt ${attemptIndex + 1} failed: ${e.message}")

                if (attemptIndex < maxRetries - 1) {
                    val delayMs = (2.0.pow(attemptIndex.toDouble()) * 1000).toLong()
                    Log.d(tag, "Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs.milliseconds)
                }
            }
        }

        Log.e(tag, "All retry attempts failed", lastException)
        LLMGenerationResponse.FAILURE(
            error = lastException?.message ?: "Unknown error after $maxRetries attempts"
        )
    }

    override suspend fun test(): Boolean {
        return !generativeModel.generateContent("Hello").text.isNullOrEmpty()
    }
}
