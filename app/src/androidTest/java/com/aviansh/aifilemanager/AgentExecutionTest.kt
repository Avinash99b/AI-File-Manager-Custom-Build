package com.aviansh.aifilemanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.agent.AgentEngine
import com.aviansh.aifilemanager.domain.agent.WorkspaceEngine
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AgentExecutionTest {

    private lateinit var workspaceEngine: WorkspaceEngine
    private lateinit var testDir: File

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        AppPaths.init(appContext)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        workspaceEngine = WorkspaceEngine()
        testDir = File(AppPaths.filesDir, "test_output")
        testDir.mkdirs()
    }

    @After
    fun teardown() {
        testDir.deleteRecursively()
    }

    @Test
    fun testAgentExecution_createsFileWithSpecificContent() = runBlocking {
        val targetFile = File(testDir, "output.txt")
        if (targetFile.exists()) targetFile.delete()

        // Mock LLM that returns a tool call to write the file, then a final plan
        val mockLlm = object : LLMProvider {
            var turn = 0
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
                          "args": "with open('temp.txt', 'w') as f:\n    f.write('hello world')"
                        }
                        """.trimIndent()
                    )
                } else {
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "final_plan",
                          "actionable": true,
                          "explanation": "Creating output file",
                          "generatorCode": "def generate():\n    import os\n    return [{'action': 'create', 'source': os.path.join(os.getcwd(), 'temp.txt'), 'destination': '${targetFile.absolutePath}', 'overwrite': True}]"
                        }
                        """.trimIndent()
                    )
                }
            }

            override suspend fun test(): Boolean = true
        }

        val agentEngine = AgentEngine(mockLlm)
        val workspacePath = workspaceEngine.setupWorkspace(emptyList())

        try {
            val result = agentEngine.processPrompt("write a file with contents 'hello world'", workspacePath, emptyList())

            assertTrue("Agent should successfully return a plan", result.isSuccess)

            val plan = result.getOrNull()
            assertNotNull("Plan should not be null", plan)
            assertEquals("Should have exactly 1 action", 1, plan!!.actions.size)

            val commitResult = workspaceEngine.commitWorkspace(plan.actions)
            assertTrue("Commit should succeed", commitResult.isSuccess)

            assertTrue("Target file should exist", targetFile.exists())
            assertEquals("Target file content should match", "hello world", targetFile.readText())

        } finally {
            workspaceEngine.cleanupWorkspace(workspacePath)
        }
    }
}
