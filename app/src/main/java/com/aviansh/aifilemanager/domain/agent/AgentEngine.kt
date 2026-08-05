package com.aviansh.aifilemanager.domain.agent

import android.util.Log
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.agent.tools.PythonTool
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AgentEngine(
    private val llmProvider: LLMProvider
) {

    private val tag = "AgentEngine"

    suspend fun processPrompt(
        prompt: String,
        workspacePath: String,
        history: List<ChatLmMessage>,
        onEvent: suspend (TimelineEvent) -> Unit = {}
    ): Result<ExecutionPlan?> = withContext(Dispatchers.IO) {

        Log.d(tag, "Processing prompt: ${prompt.take(50)}...")

        val pythonTool = PythonTool(workspacePath)

        val systemPrompt = """
You are an AI File Management Agent. You operate in an isolated workspace.
The absolute path to your isolated workspace is: $workspacePath

You MUST NEVER perform real file operations (e.g. shutil.move) outside the workspace.
If you need to create a new file or write data for the final plan, you MUST write the file into this exact workspace directory using the PythonExecutor tool, and then generate a "create" action where the "source" is the path to that new file inside the workspace.

To process the user request, you can use the following tools by responding with a JSON tool call:

TOOLS:
1. ${pythonTool.name}:
   Description: ${pythonTool.description}
   Input: A string containing the python script to run.

To call a tool, your response MUST be exactly this JSON and nothing else:
{
  "type": "tool_call",
  "tool": "PythonExecutor",
  "args": "your python code as a string"
}

You can call tools repeatedly to explore the filesystem, read files, or generate data.
The results of the tool will be provided to you in the next turn.

Once you have gathered enough information and are ready to propose a plan to the user, your response MUST be exactly this JSON and nothing else:
{
  "type": "final_plan",
  "actionable": true,
  "explanation": "A user friendly explanation of what this plan will do",
  "generatorCode": "def generate():\n    import os\n    # python code that returns a JSON list of actions"
}

The Python `generate()` function in the final plan MUST return a JSON-encoded list of actions.
Allowed action types: move, copy, delete, create.
Action Schema:
{
  "action": "move | copy | delete | create",
  "source": "/absolute/path",
  "destination": "/absolute/path or null",
  "overwrite": false
}

If no action is needed (e.g., you just answered a question), return:
{
  "type": "final_plan",
  "actionable": false,
  "explanation": "Your answer to the user's question",
  "generatorCode": null
}
""".trimIndent()

        val maxIterations = 5
        var iterations = 0
        val currentContext = history.toMutableList()
        currentContext.add(ChatLmMessage(ChatLmRole.USER, prompt))

        while (iterations < maxIterations) {
            iterations++
            Log.d(tag, "ReAct Loop Iteration $iterations")

            val response = llmProvider.generate(
                prompt = currentContext.last().content, // LLMProvider impl in this app usually appends the context itself, we just need to pass the latest or prompt
                systemPrompt = systemPrompt,
                conversation = currentContext.dropLast(1)
            )

            when (response) {
                is LLMGenerationResponse.SUCCESS -> {
                    try {
                        val rawText = response.message
                        val jsonStr = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val jsonObj = JSONObject(jsonStr)

                        val type = jsonObj.optString("type")

                        if (type == "tool_call") {
                            val toolName = jsonObj.getString("tool")
                            val args = jsonObj.getString("args")

                            Log.d(tag, "Agent called tool: $toolName")
                            currentContext.add(ChatLmMessage(ChatLmRole.ASSISTANT, jsonStr))

                            if (toolName == pythonTool.name) {
                                val result = pythonTool.execute(args)
                                Log.d(tag, "Tool result: ${result.take(100)}...")
                                currentContext.add(ChatLmMessage(ChatLmRole.USER, "Tool Result:\n$result"))
                                onEvent(TimelineEvent.ToolCall(toolName, args, result))
                            } else {
                                currentContext.add(ChatLmMessage(ChatLmRole.USER, "Error: Unknown tool $toolName"))
                                onEvent(TimelineEvent.ToolCall(toolName, args, error = "Unknown tool"))
                            }
                        } else if (type == "final_plan") {
                            onEvent(TimelineEvent.AgentThought("Ready to propose final plan."))
                            val actionable = jsonObj.optBoolean("actionable", false)
                            val explanation = jsonObj.optString("explanation", "No explanation provided.")

                            if (actionable) {
                                val code = jsonObj.getString("generatorCode")
                                val actions = PythonEngine.generateActions(code, workspacePath)
                                return@withContext Result.success(ExecutionPlan(actions, explanation))
                            } else {
                                return@withContext Result.success(ExecutionPlan(emptyList(), explanation))
                            }
                        } else {
                             // Fallback if the model didn't format correctly
                             currentContext.add(ChatLmMessage(ChatLmRole.ASSISTANT, jsonStr))
                             currentContext.add(ChatLmMessage(ChatLmRole.USER, "Error: Response must be a JSON object with 'type' equal to 'tool_call' or 'final_plan'."))
                        }

                    } catch (e: Exception) {
                        Log.e(tag, "Failed to parse Agent response. Raw: ${response.message}", e)
                        currentContext.add(ChatLmMessage(ChatLmRole.ASSISTANT, response.message))
                        currentContext.add(ChatLmMessage(ChatLmRole.USER, "Error parsing JSON. Ensure your response is strictly the requested JSON format. Error: ${e.message}"))
                    }
                }
                is LLMGenerationResponse.FAILURE -> {
                    return@withContext Result.failure(Exception(response.error))
                }
            }
        }

        Result.failure(Exception("Agent reached maximum iterations without producing a final plan."))
    }

    suspend fun verifyAndRepair(
        failedActions: List<FileAction>,
        errorLog: String,
        workspacePath: String
    ): Result<RepairPlan?> = withContext(Dispatchers.IO) {
        // Simple repair implementation for now without a full loop.
        val prompt = """
The previous execution plan failed.
Error log: $errorLog

Failed Actions:
${failedActions.joinToString("\n") { it.toString() }}

Generate a new JSON response with `actionable: true` that fixes these issues, or `actionable: false` if it cannot be fixed automatically.
Use the `final_plan` format as specified in the system prompt.
""".trimIndent()

        val dummyTool = PythonTool(workspacePath) // just for prompt format
        val minimalPrompt = "Use the final_plan JSON format."

        val response = llmProvider.generate(
            prompt = prompt,
            systemPrompt = minimalPrompt, // reusing minimal for repair
            conversation = emptyList()
        )

        when (response) {
             is LLMGenerationResponse.SUCCESS -> {
                try {
                    val rawText = response.message
                    val jsonStr = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val jsonObj = JSONObject(jsonStr)

                    val actionable = jsonObj.optBoolean("actionable", false)
                    val explanation = jsonObj.optString("explanation", "No explanation provided.")

                    if (actionable) {
                        val code = jsonObj.getString("generatorCode")
                        val proposedFixes = PythonEngine.generateActions(code, workspacePath)
                        Result.success(RepairPlan(failedActions, proposedFixes, explanation))
                    } else {
                        Result.success(null) // Could not generate a repair
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is LLMGenerationResponse.FAILURE -> {
                Result.failure(Exception(response.error))
            }
        }
    }
}
