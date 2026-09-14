package com.aviansh.aifilemanager.domain.ai

import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import kotlinx.coroutines.flow.Flow

enum class LLMRole { SYSTEM, USER, ASSISTANT, TOOL }

data class LLMMessage(
    val role: LLMRole,
    val content: String,
    val name: String? = null
)

data class LLMRequest(
    val messages: List<LLMMessage>,
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048
)

sealed interface LLMStreamEvent {
    data class TextDelta(val text: String) : LLMStreamEvent
    data class Completed(val fullText: String) : LLMStreamEvent
    data class Error(val message: String, val retryable: Boolean = false) : LLMStreamEvent
}

sealed class LLMGenerationResponse {
    data class SUCCESS(val message: String) : LLMGenerationResponse()
    data class FAILURE(val error: String) : LLMGenerationResponse()
}

interface LLMProvider {

    /**
     * Sends conversation context and prompt.
     * Returns assistant response.
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        conversation: List<ChatLmMessage>
    ): LLMGenerationResponse

    /**
     * Streams response text deltas.
     */
    fun stream(request: LLMRequest): Flow<LLMStreamEvent>

    /**
     * Tests connection to provider endpoint.
     */
    suspend fun test(): Boolean
}
