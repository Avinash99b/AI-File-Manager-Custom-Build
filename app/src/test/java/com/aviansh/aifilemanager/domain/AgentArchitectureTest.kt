package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.AgentEngine
import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.ToolRegistry
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.ai.LLMRequest
import com.aviansh.aifilemanager.domain.ai.LLMStreamEvent
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentArchitectureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testToolRegistry_lookupAndDescribe() {
        val mockTool = object : AgentTool {
            override val name: String = "TestTool"
            override val description: String = "Test tool description"
            override suspend fun execute(args: String): String = "ok"
        }

        val registry = ToolRegistry(listOf(mockTool))
        assertNotNull(registry.find("TestTool"))
        assertNull(registry.find("NonExistent"))

        val description = registry.describeTools()
        assertTrue(description.contains("TestTool"))
        assertTrue(description.contains("Test tool description"))
    }

    @Test
    fun testAgentLoop_iterationExhaustion_returnsFailure() = runBlocking {
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                return LLMGenerationResponse.SUCCESS("Just chatting, no JSON response")
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("do something", workspace, emptyList())
        assertFalse("Iteration exhaustion must return failure", result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("maximum iterations") == true)
    }

    @Test
    fun testAgentLoop_providerFailure_returnsFailure() = runBlocking {
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                return LLMGenerationResponse.FAILURE("API quota exceeded")
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("do something", workspace, emptyList())
        assertFalse("Provider failure must yield Result.failure", result.isSuccess)
        assertEquals("API quota exceeded", result.exceptionOrNull()?.message)
    }

    @Test
    fun testUnknownTool_handlesGracefullyInLoop() = runBlocking {
        var turn = 0
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turn++
                return if (turn == 1) {
                    LLMGenerationResponse.SUCCESS("""{"type":"tool_call","tool":"UnknownTool","args":"test"}""")
                } else {
                    LLMGenerationResponse.SUCCESS("""{"type":"final_plan","actionable":false,"explanation":"Aborting after unknown tool"}""")
                }
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("test", workspace, emptyList())
        assertTrue("Agent should complete gracefully after unknown tool handling", result.isSuccess)
        assertFalse(result.getOrThrow()!!.actions.isNotEmpty())
    }

    @Test
    fun testToolResultFollowedByFinalPlan_returnsPlanWithoutRetrying() = runBlocking {
        var turn = 0
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turn++
                return if (turn == 1) {
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "tool_call",
                          "tool": "PythonExecutor",
                          "args": "print('hello')"
                        }
                        """.trimIndent()
                    )
                } else {
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "final_plan",
                          "actionable": false,
                          "explanation": "Finished execution"
                        }
                        """.trimIndent()
                    )
                }
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("run code then finish", workspace, emptyList())
        assertTrue("Result should be success", result.isSuccess)
        assertEquals(2, turn)
        assertEquals("Finished execution", result.getOrNull()?.explanation)
    }

    @Test
    fun testGeneratorExecutionError_failsImmediatelyWithoutRetrying() = runBlocking {
        var turn = 0
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turn++
                return LLMGenerationResponse.SUCCESS(
                    """
                    {
                      "type": "final_plan",
                      "actionable": true,
                      "explanation": "Plan with broken generator",
                      "generatorCode": "def generate():\n    raise ValueError('broken')"
                    }
                    """.trimIndent()
                )
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("do something", workspace, emptyList())
        assertFalse("Generator code error should yield failure", result.isSuccess)
        assertEquals("Should stop on first turn without retrying", 1, turn)
        assertTrue(result.exceptionOrNull()?.message?.contains("Generator code execution failed") == true)
    }

    @Test
    fun testMalformedJson_retriesThenSucceeds() = runBlocking {
        var turn = 0
        val mockLlm = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turn++
                return if (turn == 1) {
                    LLMGenerationResponse.SUCCESS("{ malformed json ...")
                } else {
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "final_plan",
                          "actionable": false,
                          "explanation": "Recovered from malformed JSON"
                        }
                        """.trimIndent()
                    )
                }
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(mockLlm)
        val workspace = tempFolder.newFolder("workspace").absolutePath

        val result = engine.processPrompt("test recovery", workspace, emptyList())
        assertTrue("Agent should recover after malformed JSON retry", result.isSuccess)
        assertEquals(2, turn)
        assertEquals("Recovered from malformed JSON", result.getOrNull()?.explanation)
    }
}
