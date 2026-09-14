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

    @Test
    fun testAgentExecution_toolResultFollowedByIndentedGeneratorCodeInFinalPlan() = runBlocking {
        val targetFile = File(testDir, "indented_output.txt")
        if (targetFile.exists()) targetFile.delete()

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
                          "args": "with open('data.txt', 'w') as f:\n    f.write('indented text')"
                        }
                        """.trimIndent()
                    )
                } else {
                    // Test multiline indented Python generator code as returned by real LLMs
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "final_plan",
                          "actionable": true,
                          "explanation": "Copying created file",
                          "generatorCode": "    def generate():\n        import os\n        src = os.path.join(os.getcwd(), 'data.txt')\n        dst = '${targetFile.absolutePath}'\n        return [{'action': 'create', 'source': src, 'destination': dst, 'overwrite': True}]"
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
            val result = agentEngine.processPrompt("create file with indented generator", workspacePath, emptyList())

            assertTrue("Agent should successfully return plan without retrying/failing", result.isSuccess)
            val plan = result.getOrNull()
            assertNotNull("Plan should not be null", plan)
            assertEquals("Should have 1 action", 1, plan!!.actions.size)

            val commitResult = workspaceEngine.commitWorkspace(plan.actions)
            assertTrue("Commit should succeed", commitResult.isSuccess)
            assertTrue("Target file should exist", targetFile.exists())
            assertEquals("indented text", targetFile.readText())
        } finally {
            workspaceEngine.cleanupWorkspace(workspacePath)
        }
    }

    @Test
    fun testAgentExecution_imageConversionAndOpenInWorkspaceWithSymlinkPath() = runBlocking {
        val targetImage = File(testDir, "converted_image.png")
        if (targetImage.exists()) targetImage.delete()

        val mockLlm = object : LLMProvider {
            var turn = 0
            override suspend fun generate(
                prompt: String,
                systemPrompt: String?,
                conversation: List<ChatLmMessage>
            ): LLMGenerationResponse {
                turn++
                return if (turn == 1) {
                    // Test tool call that uses PIL to generate an image and open() inside workspace
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "tool_call",
                          "tool": "PythonExecutor",
                          "args": "from PIL import Image\nimg = Image.new('RGB', (50, 50), color='blue')\nimg.save('intermediate.png')\nwith open('text_data.txt', 'w') as f:\n    f.write('img_created')"
                        }
                        """.trimIndent()
                    )
                } else {
                    // Test final_plan generator code writing output image using explicit absolute path with possible /data/data vs /data/user/0 symlink
                    LLMGenerationResponse.SUCCESS(
                        """
                        {
                          "type": "final_plan",
                          "actionable": true,
                          "explanation": "Image generated and saved to destination",
                          "generatorCode": "def generate():\n    import os\n    from PIL import Image\n    ws = os.getcwd()\n    alias_ws = ws.replace('/data/user/0/', '/data/data/') if '/data/user/0/' in ws else ws.replace('/data/data/', '/data/user/0/')\n    out_ws = os.path.join(alias_ws, 'out_image.png')\n    img = Image.new('RGB', (100, 100), color='red')\n    img.save(out_ws)\n    return [{'action': 'create', 'source': out_ws, 'destination': '${targetImage.absolutePath}', 'overwrite': True}]"
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
            val result = agentEngine.processPrompt("convert image", workspacePath, emptyList())

            assertTrue("Agent should successfully return plan when PIL saves files with symlink path aliases", result.isSuccess)
            val plan = result.getOrNull()
            assertNotNull("Plan should not be null", plan)
            assertEquals("Should have 1 action", 1, plan!!.actions.size)

            val commitResult = workspaceEngine.commitWorkspace(plan.actions)
            assertTrue("Commit should succeed", commitResult.isSuccess)
            assertTrue("Target image file should exist after commit", targetImage.exists())
            assertTrue("Target image file should not be empty", targetImage.length() > 0)
        } finally {
            workspaceEngine.cleanupWorkspace(workspacePath)
        }
    }
}
