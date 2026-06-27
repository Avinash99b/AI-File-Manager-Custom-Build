package com.aviansh.aifilemanager.domain.data

enum class ChatLmRole{

    SYSTEM,

    USER,

    ASSISTANT,

    TOOL

}
data class ChatLmMessage(
    val role: ChatLmRole,
    val content: String
)