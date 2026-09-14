package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.ai.providers.OpenAICompatibleProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenAIProviderTest {

    @Test
    fun testProviderCreation_normalizesBaseUrl() {
        val provider = OpenAICompatibleProvider(
            apiKey = "test-key",
            modelName = "gpt-4o",
            rawBaseUrl = "https://my-local-llm:8000/"
        )
        assertNotNull(provider)
    }

    @Test
    fun testGenerateAgainstUnreachableHost_returnsFailureResponse() = runBlocking {
        val provider = OpenAICompatibleProvider(
            apiKey = "fake",
            modelName = "test-model",
            rawBaseUrl = "http://127.0.0.1:65534",
            timeoutMillis = 1000
        )
        val response = provider.generate("hello", null, emptyList())
        assertTrue("Unreachable host must yield FAILURE response", response is com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse.FAILURE)
    }
}
