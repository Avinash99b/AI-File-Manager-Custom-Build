package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.ai.ProviderKind
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.ai.providers.OpenAICompatibleProvider
import com.aviansh.aifilemanager.domain.prefs.AiProviderPreferences
import com.aviansh.aifilemanager.domain.repository.AiProviderRepository
import com.aviansh.aifilemanager.domain.security.InMemorySecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AiProviderSettingsTest {

    private fun repository(): AiProviderRepository {
        val context = RuntimeEnvironment.getApplication()
        return AiProviderRepository(AiProviderPreferences(context), InMemorySecretStore())
    }

    @Before
    fun resetPreferences() = runBlocking {
        // The DataStore file is shared across tests in the same JVM; start from a clean slate.
        repository().clear()
    }

    @Test
    fun defaultProviderIsGemini() = runBlocking {
        assertEquals(ProviderKind.GEMINI, repository().getSelectedProvider())
    }

    @Test
    fun selectingOpenAiProviderIsPersistedAndResolvesOpenAiProvider() = runBlocking {
        val repo = repository()

        repo.saveOpenAi(
            apiKey = "sk-test-key",
            modelName = "llama-3.3-70b-versatile",
            baseUrl = "https://api.groq.com/openai/v1"
        )
        repo.setSelectedProvider(ProviderKind.OPENAI_COMPATIBLE)

        assertEquals(ProviderKind.OPENAI_COMPATIBLE, repo.getSelectedProvider())

        val config = repo.getActiveConfig()
        assertNotNull(config)
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, config!!.kind)
        assertEquals("https://api.groq.com/openai/v1", config.baseUrl)
        assertEquals("llama-3.3-70b-versatile", config.modelName)
        assertEquals("sk-test-key", config.apiKey)

        val provider = repo.getProvider()
        assertTrue(
            "Selected OpenAI provider must not fall back to Gemini",
            provider is OpenAICompatibleProvider
        )
    }

    @Test
    fun switchingBackToGeminiKeepsBothConfigurations() = runBlocking {
        val repo = repository()

        repo.save(apiKey = "gemini-key", modelName = "gemini-2.5-pro")
        repo.saveOpenAi(
            apiKey = "openai-key",
            modelName = "gpt-4o-mini",
            baseUrl = "https://api.openai.com/v1"
        )

        repo.setSelectedProvider(ProviderKind.OPENAI_COMPATIBLE)
        assertTrue(repo.getProvider() is OpenAICompatibleProvider)

        repo.setSelectedProvider(ProviderKind.GEMINI)
        assertTrue(repo.getProvider() is GeminiAIProvider)

        // Both credential sets survive the switch.
        assertEquals("gemini-key", repo.getApiKey())
        assertEquals("openai-key", repo.getOpenAiApiKey())
        assertEquals("gemini-2.5-pro", repo.getModelName())
        assertEquals("gpt-4o-mini", repo.getOpenAiModelName())
    }

    @Test
    fun keylessLocalEndpointIsSupported() = runBlocking {
        val repo = repository()

        repo.saveOpenAi(apiKey = "", modelName = "qwen2.5:7b", baseUrl = "http://127.0.0.1:11434/v1")
        repo.setSelectedProvider(ProviderKind.OPENAI_COMPATIBLE)

        assertNull(repo.getOpenAiApiKey())
        val config = repo.getActiveConfig()
        assertNotNull(config)
        assertEquals("", config!!.apiKey)
        assertEquals("http://127.0.0.1:11434/v1", config.baseUrl)
        assertTrue(repo.getProvider() is OpenAICompatibleProvider)
    }

    @Test
    fun clearingOneProviderDoesNotAffectTheOther() = runBlocking {
        val repo = repository()

        repo.save(apiKey = "gemini-key", modelName = "gemini-2.5-flash")
        repo.saveOpenAi(apiKey = "openai-key", modelName = "gpt-4o", baseUrl = "https://api.openai.com/v1")

        repo.clearOpenAi()

        assertEquals("gemini-key", repo.getApiKey())
        assertNull(repo.getOpenAiApiKey())
        assertEquals(
            AiProviderPreferences.DEFAULT_OPENAI_MODEL,
            repo.getOpenAiModelName()
        )
    }

    @Test
    fun baseUrlIsNormalizedForEndpointsWithoutPath() {
        val provider = OpenAICompatibleProvider(
            apiKey = "k",
            modelName = "m",
            rawBaseUrl = "https://api.openai.com"
        )
        assertEquals("https://api.openai.com/v1", provider.resolvedBaseUrl())

        assertEquals(
            "https://api.groq.com/openai/v1",
            OpenAICompatibleProvider("k", "m", "https://api.groq.com/openai/v1/").resolvedBaseUrl()
        )

        assertEquals(
            "http://127.0.0.1:11434/v1",
            OpenAICompatibleProvider("k", "m", "http://127.0.0.1:11434").resolvedBaseUrl()
        )

        // Users often paste the full completions URL; strip it so requests are not doubled up.
        assertEquals(
            "https://api.openai.com/v1",
            OpenAICompatibleProvider("k", "m", "https://api.openai.com/v1/chat/completions").resolvedBaseUrl()
        )
    }
}
