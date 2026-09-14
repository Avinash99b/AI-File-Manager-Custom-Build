package com.aviansh.aifilemanager.domain.ai

enum class ProviderKind {
    GEMINI,
    OPENAI_COMPATIBLE
}

data class ModelCapabilities(
    val streaming: Boolean = true,
    val toolCalling: Boolean = false,
    val jsonMode: Boolean = false
)

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String? = null,
    val apiKey: String,
    val modelName: String,
    val enabled: Boolean = true,
    val capabilities: ModelCapabilities = ModelCapabilities()
)
