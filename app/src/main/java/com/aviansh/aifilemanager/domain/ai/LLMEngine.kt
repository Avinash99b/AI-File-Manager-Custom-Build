package com.aviansh.aifilemanager.domain.ai

import com.aviansh.aifilemanager.domain.data.ChatLmMessage

interface LLMEngine {

    /**
     * Sends the complete conversation.
     * Returns assistant message.
     */
    fun generate(
        conversation: List<ChatLmMessage>
    ): ChatLmMessage
}