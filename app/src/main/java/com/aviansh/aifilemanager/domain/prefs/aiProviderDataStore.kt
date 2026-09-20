package com.aviansh.aifilemanager.domain.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aviansh.aifilemanager.domain.ai.ProviderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

// DataStore file name kept as "gemini_preferences" so existing installs keep their settings.
private val Context.aiProviderDataStore by preferencesDataStore(
    name = "gemini_preferences"
)

private object Keys {
    val API_KEY = stringPreferencesKey("gemini_api_key")
    val MODEL = stringPreferencesKey("gemini_model")
    val SYSTEM_PROMPT = stringPreferencesKey("gemini_system_prompt")

    val SELECTED_PROVIDER = stringPreferencesKey("selected_ai_provider")
    val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
    val OPENAI_MODEL = stringPreferencesKey("openai_model")
    val OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
}

/**
 * Local persistence for every supported AI provider (Gemini and any OpenAI compatible endpoint).
 *
 * API keys are only ever written here by legacy builds; the current code stores them encrypted in
 * the [com.aviansh.aifilemanager.domain.security.SecretStore] and these keys are used exclusively
 * for one-time migration.
 */
class AiProviderPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val API_KEY = Keys.API_KEY
        private val MODEL_NAME = Keys.MODEL
        private val SYSTEM_PROMPT = Keys.SYSTEM_PROMPT
        private val SELECTED_PROVIDER = Keys.SELECTED_PROVIDER
        private val OPENAI_API_KEY = Keys.OPENAI_API_KEY
        private val OPENAI_MODEL = Keys.OPENAI_MODEL
        private val OPENAI_BASE_URL = Keys.OPENAI_BASE_URL

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    }

    // ---------------- Provider selection ----------------

    suspend fun getSelectedProvider(): ProviderKind {
        val raw = preferences()[SELECTED_PROVIDER]
        return ProviderKind.entries.firstOrNull { it.name == raw } ?: ProviderKind.GEMINI
    }

    suspend fun saveSelectedProvider(kind: ProviderKind) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[SELECTED_PROVIDER] = kind.name
        }
    }

    // ---------------- Gemini ----------------

    suspend fun saveApiKey(apiKey: String) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveModelName(modelName: String) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun saveSystemPrompt(systemPrompt: String) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[SYSTEM_PROMPT] = systemPrompt.trim()
        }
    }

    suspend fun save(
        apiKey: String,
        modelName: String
    ) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[API_KEY] = apiKey.trim()
            prefs[MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun getApiKey(): String? {
        return preferences()[API_KEY]
    }

    suspend fun getModelName(): String {
        return preferences()[MODEL_NAME] ?: DEFAULT_MODEL
    }

    suspend fun hasConfiguration(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    suspend fun clearApiKey() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.remove(API_KEY)
        }
    }

    suspend fun clearModelName() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.remove(MODEL_NAME)
        }
    }

    // ---------------- OpenAI compatible ----------------

    suspend fun getOpenAiApiKey(): String? {
        return preferences()[OPENAI_API_KEY]
    }

    suspend fun clearOpenAiApiKey() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.remove(OPENAI_API_KEY)
        }
    }

    suspend fun getOpenAiModelName(): String {
        return preferences()[OPENAI_MODEL] ?: DEFAULT_OPENAI_MODEL
    }

    suspend fun saveOpenAiModelName(modelName: String) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[OPENAI_MODEL] = modelName.trim()
        }
    }

    /** Raw stored base URL (null when the user never saved an endpoint). */
    suspend fun getStoredOpenAiBaseUrl(): String? {
        return preferences()[OPENAI_BASE_URL]
    }

    suspend fun getOpenAiBaseUrl(): String {
        val stored = preferences()[OPENAI_BASE_URL]
        return if (stored.isNullOrBlank()) DEFAULT_OPENAI_BASE_URL else stored
    }

    suspend fun saveOpenAiBaseUrl(baseUrl: String) {
        context.aiProviderDataStore.edit { prefs ->
            prefs[OPENAI_BASE_URL] = baseUrl.trim()
        }
    }

    suspend fun clearOpenAiConfiguration() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.remove(OPENAI_API_KEY)
            prefs.remove(OPENAI_MODEL)
            prefs.remove(OPENAI_BASE_URL)
        }
    }

    suspend fun clearGeminiConfiguration() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.remove(API_KEY)
            prefs.remove(MODEL_NAME)
            prefs.remove(SYSTEM_PROMPT)
        }
    }

    suspend fun clear() {
        context.aiProviderDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private suspend fun preferences(): Preferences {
        return context.aiProviderDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()
    }
}

/**
 * Legacy name kept so existing call sites / tests keep compiling.
 */
typealias GeminiPreferences = AiProviderPreferences
