package com.aviansh.aifilemanager.domain.ai

import com.aviansh.aifilemanager.domain.data.ChatLmMessage

sealed class LLMGenerationResponse{

    data class SUCCESS(
        val message: String
    ): LLMGenerationResponse()
    data class FAILURE(
        val error: String
    ): LLMGenerationResponse()
}

interface LLMProvider {

    /**
     * Sends the complete conversation.
     * Returns assistant message.
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String?,
        conversation: List<ChatLmMessage>
    ): LLMGenerationResponse

    suspend fun test(): Boolean
}