package com.aviansh.aifilemanager.domain.data

enum class ChatLmRole{

    SYSTEM,

    USER,

    ASSISTANT,

    TOOL

}
data class ChatLmMessage(
    val role: ChatLmRole,
    val content: String,
    val pendingActions: List<FileAction>? = null   // non-null while awaiting user approval
) {
    val isUser: Boolean get() = role == ChatLmRole.USER
}