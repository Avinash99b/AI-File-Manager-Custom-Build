package com.aviansh.aifilemanager.domain.repository

import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.ai.ProviderConfig
import com.aviansh.aifilemanager.domain.ai.ProviderKind
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.ai.providers.OpenAICompatibleProvider
import com.aviansh.aifilemanager.domain.prefs.AiProviderPreferences
import com.aviansh.aifilemanager.domain.security.SecretStore
import javax.inject.Inject

/**
 * Single source of truth for AI provider configuration.
 *
 * Supports Gemini and any OpenAI compatible endpoint (OpenAI, OpenRouter, Groq, Together,
 * LM Studio, Ollama, vLLM, ...). Each provider keeps its own credentials so the user can switch
 * back and forth without retyping anything.
 */
class AiProviderRepository @Inject constructor(
    private val preferences: AiProviderPreferences,
    private val secretStore: SecretStore
) {

    companion object {
        const val SECRET_KEY_GEMINI_API_KEY = "gemini_api_key"
        const val SECRET_KEY_OPENAI_API_KEY = "openai_api_key"
    }

    // ---------------- Provider selection ----------------

    suspend fun getSelectedProvider(): ProviderKind = preferences.getSelectedProvider()

    suspend fun setSelectedProvider(kind: ProviderKind) = preferences.saveSelectedProvider(kind)

    // ---------------- Gemini ----------------

    suspend fun save(
        apiKey: String,
        modelName: String
    ) {
        if (apiKey.isNotBlank()) {
            secretStore.saveSecret(SECRET_KEY_GEMINI_API_KEY, apiKey.trim())
            preferences.clearApiKey() // Ensure plaintext key is purged from legacy DataStore
        }
        preferences.saveModelName(modelName)
    }

    suspend fun updateApiKey(apiKey: String) {
        if (apiKey.isNotBlank()) {
            secretStore.saveSecret(SECRET_KEY_GEMINI_API_KEY, apiKey.trim())
            preferences.clearApiKey()
        }
    }

    suspend fun updateModel(modelName: String) {
        preferences.saveModelName(modelName)
    }

    suspend fun clear() {
        secretStore.deleteSecret(SECRET_KEY_GEMINI_API_KEY)
        secretStore.deleteSecret(SECRET_KEY_OPENAI_API_KEY)
        preferences.clear()
    }

    suspend fun clearGemini() {
        secretStore.deleteSecret(SECRET_KEY_GEMINI_API_KEY)
        preferences.clearGeminiConfiguration()
    }

    suspend fun getApiKey(): String? {
        val secret = secretStore.getSecret(SECRET_KEY_GEMINI_API_KEY)
        if (!secret.isNullOrBlank()) {
            return secret
        }

        // Migration logic: check if legacy DataStore contains raw API key
        val legacyKey = preferences.getApiKey()
        if (!legacyKey.isNullOrBlank()) {
            secretStore.saveSecret(SECRET_KEY_GEMINI_API_KEY, legacyKey)
            preferences.clearApiKey()
            return legacyKey
        }

        return null
    }

    suspend fun getModelName() = preferences.getModelName()

    suspend fun hasConfiguration(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    // ---------------- OpenAI compatible ----------------

    suspend fun saveOpenAi(
        apiKey: String,
        modelName: String,
        baseUrl: String
    ) {
        if (apiKey.isNotBlank()) {
            secretStore.saveSecret(SECRET_KEY_OPENAI_API_KEY, apiKey.trim())
            preferences.clearOpenAiApiKey()
        } else {
            // Local endpoints (Ollama / LM Studio / vLLM) often need no key at all.
            secretStore.deleteSecret(SECRET_KEY_OPENAI_API_KEY)
            preferences.clearOpenAiApiKey()
        }
        preferences.saveOpenAiModelName(modelName)
        preferences.saveOpenAiBaseUrl(baseUrl)
    }

    suspend fun clearOpenAi() {
        secretStore.deleteSecret(SECRET_KEY_OPENAI_API_KEY)
        preferences.clearOpenAiConfiguration()
    }

    suspend fun getOpenAiApiKey(): String? {
        val secret = secretStore.getSecret(SECRET_KEY_OPENAI_API_KEY)
        if (!secret.isNullOrBlank()) {
            return secret
        }

        val legacyKey = preferences.getOpenAiApiKey()
        if (!legacyKey.isNullOrBlank()) {
            secretStore.saveSecret(SECRET_KEY_OPENAI_API_KEY, legacyKey)
            preferences.clearOpenAiApiKey()
            return legacyKey
        }

        return null
    }

    suspend fun getOpenAiModelName() = preferences.getOpenAiModelName()

    suspend fun getOpenAiBaseUrl() = preferences.getOpenAiBaseUrl()

    /** True once the user has saved an OpenAI compatible endpoint (key optional). */
    suspend fun hasOpenAiConfiguration(): Boolean {
        val savedBaseUrl = preferences.getStoredOpenAiBaseUrl()
        return !savedBaseUrl.isNullOrBlank() || !getOpenAiApiKey().isNullOrBlank()
    }

    // ---------------- Resolution ----------------

    suspend fun getActiveConfig(): ProviderConfig? {
        return when (getSelectedProvider()) {
            ProviderKind.GEMINI -> {
                val key = getApiKey() ?: return null
                ProviderConfig(
                    id = "gemini",
                    displayName = "Gemini",
                    kind = ProviderKind.GEMINI,
                    apiKey = key,
                    modelName = getModelName()
                )
            }

            ProviderKind.OPENAI_COMPATIBLE -> {
                if (!hasOpenAiConfiguration()) return null
                val baseUrl = getOpenAiBaseUrl()
                val model = getOpenAiModelName()
                if (baseUrl.isBlank() || model.isBlank()) return null
                ProviderConfig(
                    id = "openai_compatible",
                    displayName = "OpenAI compatible",
                    kind = ProviderKind.OPENAI_COMPATIBLE,
                    baseUrl = baseUrl,
                    apiKey = getOpenAiApiKey().orEmpty(),
                    modelName = model
                )
            }
        }
    }

    /**
     * Builds the [LLMProvider] the rest of the app should talk to, honouring the provider the
     * user picked in settings. Returns null when the active provider is not configured yet.
     */
    suspend fun getProvider(): LLMProvider? {
        val config = getActiveConfig() ?: return null
        return createProvider(config)
    }

    fun createProvider(config: ProviderConfig): LLMProvider = when (config.kind) {
        ProviderKind.GEMINI -> GeminiAIProvider(
            apiKey = config.apiKey,
            modelName = config.modelName
        )

        ProviderKind.OPENAI_COMPATIBLE -> OpenAICompatibleProvider(
            apiKey = config.apiKey,
            modelName = config.modelName,
            rawBaseUrl = config.baseUrl?.takeIf { it.isNotBlank() }
                ?: AiProviderPreferences.DEFAULT_OPENAI_BASE_URL
        )
    }
}

/**
 * Legacy name kept so existing call sites / tests keep compiling.
 */
typealias GeminiModelRepository = AiProviderRepository
