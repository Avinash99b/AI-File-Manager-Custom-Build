package com.aviansh.aifilemanager.domain.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

private val Context.geminiDataStore by preferencesDataStore(
    name = "gemini_preferences"
)
private object Keys {
    val API_KEY = stringPreferencesKey("gemini_api_key")
    val MODEL = stringPreferencesKey("gemini_model")
    val SYSTEM_PROMPT = stringPreferencesKey("gemini_system_prompt")
}
class GeminiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
)  {

    companion object {
        private val API_KEY = Keys.API_KEY
        private val MODEL_NAME = Keys.MODEL
        private val SYSTEM_PROMPT = Keys.SYSTEM_PROMPT

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
    }

    suspend fun saveApiKey(apiKey: String) {
        context.geminiDataStore.edit { prefs ->
            prefs[API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveModelName(modelName: String) {
        context.geminiDataStore.edit { prefs ->
            prefs[MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun saveSystemPrompt(systemPrompt: String) {
        context.geminiDataStore.edit { prefs ->
            prefs[SYSTEM_PROMPT] = systemPrompt.trim()
        }
    }

    suspend fun save(
        apiKey: String,
        modelName: String
    ) {
        context.geminiDataStore.edit { prefs ->
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
        context.geminiDataStore.edit { prefs ->
            prefs.remove(API_KEY)
        }
    }

    suspend fun clearModelName() {
        context.geminiDataStore.edit { prefs ->
            prefs.remove(MODEL_NAME)
        }
    }

    suspend fun clear() {
        context.geminiDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private suspend fun preferences(): Preferences {
        return context.geminiDataStore.data
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