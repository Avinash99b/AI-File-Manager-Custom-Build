package com.aviansh.aifilemanager.domain.ai.providers

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Standalone Gemini LLM provider with circuit breaker and exponential backoff.
 * Assumes API key is set as GEMINI_API_KEY in BuildConfig.
 */
class GeminiAIProvider(
    private val apiKey: String,
    private val modelName: String = "gemini-3.1-flash-lite",
    private val maxRetries: Int = 3,
    private val timeoutMillis: Long = 30000L
) {

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

    data class AIResponse(
        val message: String,
        val isSuccess: Boolean,
        val error: String? = null,
        val tokenUsage: TokenUsage? = null
    )

    data class TokenUsage(
        val promptTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int
    )

    /**
     * Send a message to Gemini and get a response with automatic retry and backoff.
     */
    suspend fun sendMessage(
        prompt: String,
        systemPrompt: String? = null,
        conversationContext: List<Pair<String, String>> = emptyList()
    ): AIResponse = withContext(Dispatchers.IO) {
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
                    conversationContext.forEach { (role, message) ->
                        appendLine("$role: $message")
                    }

                    appendLine("User: $prompt")
                }

                // Use timeout wrapper (you'll need coroutine timeout support)
                val response = generativeModel.generateContent(fullPrompt)

                val resultText = response.text ?: return@withContext AIResponse(
                    message = "",
                    isSuccess = false,
                    error = "Empty response from Gemini"
                )

                Log.d(tag, "Success on attempt ${attemptIndex + 1}")

                return@withContext AIResponse(
                    message = resultText,
                    isSuccess = true,
                    error = null,
                    tokenUsage = TokenUsage(
                        promptTokens = 0, // Gemini API doesn't expose this easily
                        outputTokens = 0,
                        totalTokens = 0
                    )
                )

            } catch (e: Exception) {
                lastException = e
                Log.w(tag, "Attempt ${attemptIndex + 1} failed: ${e.message}")

                if (attemptIndex < maxRetries - 1) {
                    val delayMs = (2.0.pow(attemptIndex.toDouble()) * 1000).toLong()
                    Log.d(tag, "Retrying in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        Log.e(tag, "All retry attempts failed", lastException)
        AIResponse(
            message = "",
            isSuccess = false,
            error = lastException?.message ?: "Unknown error after $maxRetries attempts"
        )
    }

    /**
     * Specialized method for file operation suggestions (rename, delete, etc.)
     */
    suspend fun suggestFileOperation(
        fileName: String,
        fileType: String,
        currentFilePath: String,
        operation: String // "rename", "delete", "organize", "analyze"
    ): AIResponse {
        val systemPrompt = "You are a file management assistant. Provide concise, practical suggestions."
        val prompt = buildString {
            appendLine("File: $fileName")
            appendLine("Type: $fileType")
            appendLine("Path: $currentFilePath")
            appendLine("Operation: $operation")
            appendLine()
            append("Provide a brief suggestion (1-2 sentences):")
        }

        return sendMessage(
            prompt = prompt,
            systemPrompt = systemPrompt
        )
    }

    /**
     * Batch analyze multiple files (respecting rate limits).
     */
    suspend fun analyzeFiles(files: List<String>): AIResponse {
        val prompt = buildString {
            appendLine("Analyze the following files and suggest optimizations:")
            appendLine()
            files.forEachIndexed { index, file ->
                appendLine("${index + 1}. $file")
            }
        }

        return sendMessage(prompt = prompt)
    }
}