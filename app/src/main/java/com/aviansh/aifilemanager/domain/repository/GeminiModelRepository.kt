package com.aviansh.aifilemanager.domain.repository

import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.prefs.GeminiPreferences
import javax.inject.Inject

class GeminiModelRepository @Inject constructor(
    private val preferences: GeminiPreferences
) {

    suspend fun save(
        apiKey: String,
        modelName: String
    ) {
        preferences.saveApiKey(apiKey)
        preferences.saveModelName(modelName)
    }

    suspend fun updateApiKey(apiKey: String) {
        preferences.saveApiKey(apiKey)
    }

    suspend fun updateModel(modelName: String) {
        preferences.saveModelName(modelName)
    }

    suspend fun clear() {
        preferences.clear()
    }

    suspend fun getApiKey() =
        preferences.getApiKey()

    suspend fun getModelName() =
        preferences.getModelName()

    suspend fun hasConfiguration(): Boolean {
        return !preferences.getApiKey().isNullOrBlank()
    }

    suspend fun getProvider(): GeminiAIProvider? {

        val apiKey = preferences.getApiKey()
            ?: return null

        val model = preferences.getModelName()

        return GeminiAIProvider(
            apiKey = apiKey,
            modelName = model
        )
    }
}