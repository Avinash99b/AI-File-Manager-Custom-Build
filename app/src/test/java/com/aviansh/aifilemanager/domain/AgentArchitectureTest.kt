package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.AgentEngine
import com.aviansh.aifilemanager.domain.agent.AgentOutcome
import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.TimelineEvent
import com.aviansh.aifilemanager.domain.agent.ToolParam
import com.aviansh.aifilemanager.domain.agent.ToolRegistry
import com.aviansh.aifilemanager.domain.agent.ToolResult
import com.aviansh.aifilemanager.domain.agent.ToolSpec
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.ai.LLMRequest
import com.aviansh.aifilemanager.domain.ai.LLMStreamEvent
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the tool-calling agent loop that replaced the plan-generation architecture.
 */
@RunWith(RobolectricTestRunner::class)
class AgentArchitectureTest {

    private fun llm(replies: (turn: Int, prompt: String, conversation: List<ChatLmMessage>) -> String) =
        object : LLMProvider {
            var turns = 0
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turns++
                return LLMGenerationResponse.SUCCESS(replies(turns, prompt, conversation))
            }

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

    private fun tool(
        name: String,
        destructive: Boolean = false,
        result: (JSONObject) -> ToolResult = { ToolResult.Success("ok") }
    ) = object : AgentTool {
        var calls = 0
        override val spec = ToolSpec(
            name = name,
            description = "$name description",
            params = listOf(ToolParam("path", "string", "target path")),
            destructive = destructive
        )

        override fun previewFor(args: JSONObject): String = "$name ${args.optString("path")}"

        override suspend fun execute(args: JSONObject): ToolResult {
            calls++
            return result(args)
        }
    }

    // --- Registry ---

    @Test
    fun registryLooksUpToolsAndDescribesThemForThePrompt() {
        val registry = ToolRegistry(listOf(tool("list_directory"), tool("delete", destructive = true)))

        assertNotNull(registry.find("list_directory"))
        assertNotNull("Lookup should be case insensitive", registry.find("LIST_DIRECTORY"))
        assertNull(registry.find("nope"))

        val described = registry.describeTools()
        assertTrue(described.contains("list_directory"))
        assertTrue(described.contains("list_directory description"))
        assertTrue(
            "Destructive tools must be flagged to the model",
            described.contains("REQUIRES USER CONFIRMATION")
        )
    }

    // --- JSON parsing ---

    @Test
    fun parsesDecisionWrappedInMarkdownFencesAndProse() {
        val engine = AgentEngine(llm { _, _, _ -> "" }, ToolRegistry(emptyList()))
        val decision = engine.parseDecision(
            """
            Sure, here you go:
            ```json
            {"thought": "look first", "tool": "list_directory", "args": {"path": "/storage/emulated/0"}}
            ```
            """.trimIndent()
        )

        assertNotNull(decision)
        assertEquals("list_directory", decision!!.tool)
        assertEquals("/storage/emulated/0", decision.args.getString("path"))
        assertEquals("look first", decision.thought)
        assertNull(decision.answer)
    }

    @Test
    fun extractJsonObjectIgnoresBracesInsideStrings() {
        val engine = AgentEngine(llm { _, _, _ -> "" }, ToolRegistry(emptyList()))
        val extracted = engine.extractJsonObject(
            """{"answer": "I renamed {weird} to } normal", "thought": "done"} trailing junk"""
        )

        assertNotNull(extracted)
        val obj = JSONObject(extracted!!)
        assertEquals("I renamed {weird} to } normal", obj.getString("answer"))
    }

    @Test
    fun argsSuppliedAsAJsonStringAreStillParsed() {
        val engine = AgentEngine(llm { _, _, _ -> "" }, ToolRegistry(emptyList()))
        val decision = engine.parseDecision(
            """{"tool": "read_file", "args": "{\"path\": \"/storage/emulated/0/a.txt\"}"}"""
        )

        assertNotNull(decision)
        assertEquals("/storage/emulated/0/a.txt", decision!!.args.getString("path"))
    }

    @Test
    fun decisionWithNeitherToolNorAnswerIsRejected() {
        val engine = AgentEngine(llm { _, _, _ -> "" }, ToolRegistry(emptyList()))
        assertNull(engine.parseDecision("""{"thought": "hmm"}"""))
        assertNull(engine.parseDecision("no json here at all"))
    }

    // --- Loop behaviour ---

    @Test
    fun safeToolRunsAutomaticallyThenTheAgentAnswers() = runBlocking {
        val listTool = tool("list_directory") { ToolResult.Success("a.txt\nb.txt") }
        val provider = llm { turn, _, _ ->
            if (turn == 1) {
                """{"thought":"look","tool":"list_directory","args":{"path":"/storage/emulated/0"}}"""
            } else {
                """{"thought":"done","answer":"There are 2 files."}"""
            }
        }

        val events = mutableListOf<TimelineEvent>()
        val engine = AgentEngine(provider, ToolRegistry(listOf(listTool)))
        val (_, outcome) = engine.start("what is here", "/storage/emulated/0", emptyList()) {
            events.add(it)
        }

        assertTrue(outcome is AgentOutcome.Answer)
        assertEquals("There are 2 files.", (outcome as AgentOutcome.Answer).text)
        assertEquals("Safe tools must not wait for confirmation", 1, listTool.calls)

        val finished = events.filterIsInstance<TimelineEvent.ToolCall>().last()
        assertEquals("a.txt\nb.txt", finished.result)
    }

    @Test
    fun destructiveToolPausesForConfirmationAndRunsOnApproval() = runBlocking {
        val deleteTool = tool("delete", destructive = true) { ToolResult.Success("Moved to trash") }
        val provider = llm { turn, _, _ ->
            if (turn == 1) {
                """{"thought":"remove it","tool":"delete","args":{"path":"/storage/emulated/0/a.txt"}}"""
            } else {
                """{"thought":"done","answer":"Deleted a.txt."}"""
            }
        }

        val engine = AgentEngine(provider, ToolRegistry(listOf(deleteTool)))
        val (session, first) = engine.start("delete a.txt", "/storage/emulated/0", emptyList())

        assertTrue("Destructive call must pause", first is AgentOutcome.NeedsConfirmation)
        assertEquals(0, deleteTool.calls)
        assertEquals("delete", (first as AgentOutcome.NeedsConfirmation).action.toolName)

        val resumed = engine.resume(session, "/storage/emulated/0", approved = true)
        assertEquals("Approval must execute the call", 1, deleteTool.calls)
        assertTrue(resumed is AgentOutcome.Answer)
        assertEquals("Deleted a.txt.", (resumed as AgentOutcome.Answer).text)
    }

    @Test
    fun decliningADestructiveCallSkipsItAndTellsTheModel() = runBlocking {
        val deleteTool = tool("delete", destructive = true)
        var sawDeclineNotice = false

        val provider = llm { turn, prompt, conversation ->
            if (turn > 1 &&
                (prompt.contains("DECLINED") || conversation.any { it.content.contains("DECLINED") })
            ) {
                sawDeclineNotice = true
            }
            if (turn == 1) {
                """{"thought":"remove","tool":"delete","args":{"path":"/storage/emulated/0/a.txt"}}"""
            } else {
                """{"thought":"respect it","answer":"Okay, I left a.txt alone."}"""
            }
        }

        val engine = AgentEngine(provider, ToolRegistry(listOf(deleteTool)))
        val (session, first) = engine.start("delete a.txt", "/storage/emulated/0", emptyList())
        assertTrue(first is AgentOutcome.NeedsConfirmation)

        val resumed = engine.resume(session, "/storage/emulated/0", approved = false)

        assertEquals("Declined calls must never execute", 0, deleteTool.calls)
        assertTrue("The model must learn the user declined", sawDeclineNotice)
        assertTrue(resumed is AgentOutcome.Answer)
    }

    @Test
    fun unknownToolIsReportedAndTheLoopContinues() = runBlocking {
        val provider = llm { turn, _, _ ->
            if (turn == 1) {
                """{"thought":"try","tool":"teleport","args":{}}"""
            } else {
                """{"thought":"give up","answer":"I cannot do that."}"""
            }
        }

        val events = mutableListOf<TimelineEvent>()
        val engine = AgentEngine(provider, ToolRegistry(listOf(tool("list_directory"))))
        val (_, outcome) = engine.start("teleport", "/storage/emulated/0", emptyList()) { events.add(it) }

        assertTrue(outcome is AgentOutcome.Answer)
        val failed = events.filterIsInstance<TimelineEvent.ToolCall>().first()
        assertEquals("teleport", failed.toolName)
        assertEquals("Unknown tool", failed.error)
    }

    @Test
    fun malformedJsonIsRetriedRatherThanFatal() = runBlocking {
        val provider = llm { turn, _, _ ->
            if (turn == 1) "{ not json at all" else """{"answer":"Recovered."}"""
        }

        val engine = AgentEngine(provider, ToolRegistry(emptyList()))
        val (_, outcome) = engine.start("hi", "/storage/emulated/0", emptyList())

        assertTrue(outcome is AgentOutcome.Answer)
        assertEquals("Recovered.", (outcome as AgentOutcome.Answer).text)
    }

    @Test
    fun repeatedToolFailuresNudgeTheAgentToStop() = runBlocking {
        val failing = tool("read_file") { ToolResult.Failure("Read denied outside allowed roots") }
        var sawStopHint = false

        val provider = llm { _, prompt, conversation ->
            if (prompt.contains("failed in a row") ||
                conversation.any { it.content.contains("failed in a row") }
            ) {
                sawStopHint = true
                return@llm """{"answer":"I cannot read that location."}"""
            }
            """{"tool":"read_file","args":{"path":"/proc/version"}}"""
        }

        val engine = AgentEngine(provider, ToolRegistry(listOf(failing)), maxConsecutiveFailures = 3)
        val (_, outcome) = engine.start("read /proc/version", "/storage/emulated/0", emptyList())

        assertTrue("The agent must be told to stop retrying", sawStopHint)
        assertTrue(outcome is AgentOutcome.Answer)
    }

    @Test
    fun theLastStepForcesAnAnswerInsteadOfADeadEnd() = runBlocking {
        var sawConcludeInstruction = false
        val noop = tool("list_directory")

        val provider = llm { _, prompt, _ ->
            if (prompt.contains("Step budget reached")) {
                sawConcludeInstruction = true
                return@llm """{"answer":"Here is what I managed to do."}"""
            }
            """{"tool":"list_directory","args":{"path":"/storage/emulated/0"}}"""
        }

        val engine = AgentEngine(provider, ToolRegistry(listOf(noop)), maxSteps = 4)
        val (_, outcome) = engine.start("browse forever", "/storage/emulated/0", emptyList())

        assertTrue("Final step must ask for a conclusion", sawConcludeInstruction)
        assertTrue(outcome is AgentOutcome.Answer)
    }

    @Test
    fun exhaustingTheStepBudgetReturnsAnActionableFailure() = runBlocking {
        val provider = llm { _, _, _ -> "just chatting, no json" }
        val engine = AgentEngine(provider, ToolRegistry(emptyList()), maxSteps = 3)

        val (_, outcome) = engine.start("do something", "/storage/emulated/0", emptyList())

        assertTrue(outcome is AgentOutcome.Failed)
        val message = (outcome as AgentOutcome.Failed).error
        assertTrue(
            "Exhaustion message should be actionable, was: $message",
            message.contains("steps without finishing") && message.contains("more specific")
        )
    }

    @Test
    fun providerFailurePropagates() = runBlocking {
        val provider = object : LLMProvider {
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse = LLMGenerationResponse.FAILURE("API quota exceeded")

            override fun stream(request: LLMRequest): Flow<LLMStreamEvent> = emptyFlow()
            override suspend fun test(): Boolean = true
        }

        val engine = AgentEngine(provider, ToolRegistry(emptyList()))
        val (_, outcome) = engine.start("do something", "/storage/emulated/0", emptyList())

        assertTrue(outcome is AgentOutcome.Failed)
        assertEquals("API quota exceeded", (outcome as AgentOutcome.Failed).error)
    }

    @Test
    fun toolExceptionsBecomeFailuresInsteadOfCrashingTheLoop() = runBlocking {
        val exploding = object : AgentTool {
            override val spec = ToolSpec("copy", "copies things")
            override suspend fun execute(args: JSONObject): ToolResult = throw IllegalStateException("disk on fire")
        }

        val provider = llm { turn, _, _ ->
            if (turn == 1) """{"tool":"copy","args":{}}""" else """{"answer":"Copy failed."}"""
        }

        val events = mutableListOf<TimelineEvent>()
        val engine = AgentEngine(provider, ToolRegistry(listOf(exploding)))
        val (_, outcome) = engine.start("copy", "/storage/emulated/0", emptyList()) { events.add(it) }

        assertTrue(outcome is AgentOutcome.Answer)
        assertEquals(
            "disk on fire",
            events.filterIsInstance<TimelineEvent.ToolCall>().last().error
        )
    }

    @Test
    fun systemPromptAdvertisesTheCurrentDirectoryAndTools() {
        val engine = AgentEngine(
            llm { _, _, _ -> "" },
            ToolRegistry(listOf(tool("list_directory"), tool("delete", destructive = true)))
        )
        val prompt = engine.systemPrompt("/storage/emulated/0/Download")

        assertTrue(prompt.contains("/storage/emulated/0/Download"))
        assertTrue(prompt.contains("list_directory"))
        assertTrue(prompt.contains("delete"))
        assertTrue(prompt.contains("\"answer\""))
    }
}
