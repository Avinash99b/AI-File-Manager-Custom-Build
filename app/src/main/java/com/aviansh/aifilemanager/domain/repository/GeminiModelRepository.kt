package com.aviansh.aifilemanager.domain.repository

import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.prefs.GeminiPreferences
import com.aviansh.aifilemanager.domain.security.SecretStore
import javax.inject.Inject

class GeminiModelRepository @Inject constructor(
    private val preferences: GeminiPreferences,
    private val secretStore: SecretStore
) {

    companion object {
        const val SECRET_KEY_GEMINI_API_KEY = "gemini_api_key"
    }

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
        preferences.clear()
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

    suspend fun getModelName() =
        preferences.getModelName()

    suspend fun hasConfiguration(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    suspend fun getProvider(): GeminiAIProvider? {
        val apiKey = getApiKey() ?: return null
        val model = getModelName()

        return GeminiAIProvider(
            apiKey = apiKey,
            modelName = model
        )
    }
}
