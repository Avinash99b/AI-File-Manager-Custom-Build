package com.aviansh.aifilemanager.domain.agent

import android.util.Log
import com.aviansh.aifilemanager.domain.agent.tools.PythonTool
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.ai.LLMProvider
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AgentEngine(
    private val llmProvider: LLMProvider,
    private val customToolRegistry: ToolRegistry? = null
) {

    private val tag = "AgentEngine"

    suspend fun processPrompt(
        prompt: String,
        workspacePath: String,
        history: List<ChatLmMessage>,
        onEvent: suspend (TimelineEvent) -> Unit = {}
    ): Result<ExecutionPlan?> = withContext(Dispatchers.IO) {

        Log.d(tag, "Processing prompt: ${prompt.take(50)}...")

        val toolRegistry = customToolRegistry ?: ToolRegistry(listOf(PythonTool(workspacePath)))

        val systemPrompt = """
You are an AI File Management Agent. You operate in an isolated workspace.
The absolute path to your isolated workspace is: $workspacePath

FILESYSTEM ACCESS:
- You have READ-ONLY access to the entire device filesystem. This app holds the "All files access" permission, so you may read files from any directory (e.g. /storage/emulated/0, /storage/emulated/0/Download, /storage/emulated/0/Pictures).
- Read ONLY what the task actually needs. Prefer cheap discovery first (e.g. os.listdir, glob) to find the right files before opening anything, and open only the specific files you need. Do NOT recursively dump the contents of whole directories, and do NOT read the full contents of large files when metadata (name, size, type) or a quick check is enough.
- You MUST NEVER create, modify, move, copy, or delete any file OUTSIDE the workspace.

WORKSPACE RULE:
- All writes happen for the final plan, never while exploring. If a new file is needed for the plan (generated report, converted image, renamed copy, etc.), you MUST write it into the workspace ($workspacePath) using the PythonExecutor tool, then generate a "create" action whose "source" is that file inside the workspace and whose "destination" is the real absolute path where the user wants the file to appear.

PDF TOOLKIT (available Python libraries):
- READ / EXTRACT TEXT from a PDF: use pypdf, e.g.:
  from pypdf import PdfReader
  reader = PdfReader("/path/to/file.pdf")
  text = "".join(p.extract_text() or "" for p in reader.pages)
- MERGE, SPLIT, ROTATE, ENCRYPT, or delete pages: use pypdf's PdfWriter with PdfReader, e.g.:
  from pypdf import PdfWriter, PdfReader
  writer = PdfWriter()
  writer.append("/source1.pdf")
  writer.append("/source2.pdf")
  writer.write("/workspace_path/merged.pdf")
- CREATE new PDFs: use reportlab (canvas or platypus).
- EMBED images into PDFs: use reportlab in combination with Pillow.
Reading a PDF is READ-ONLY. Any PDF you generate must be written into the workspace and declared with a "create" action whose destination is the real output path, exactly like any other generated file.

To process the user request, you can use the following tools by responding with a JSON tool call:

TOOLS:
${toolRegistry.describeTools()}

To call a tool, your response MUST be exactly this JSON and nothing else:
{
  "type": "tool_call",
  "tool": "ToolName",
  "args": "your python code or input string"
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

Field rules:
- "source" is REQUIRED for every action and must be a real absolute path (for "create" it is the path of the file you produced inside the workspace).
- "destination" is REQUIRED and must be a real absolute path for "move", "copy" and "create". It is ONLY null for "delete".
- "overwrite": true means the destination file may be replaced if it already exists.
Action Schema:
{
  "action": "move | copy | delete | create",
  "source": "/absolute/path",
  "destination": "/absolute/path REQUIRED for move/copy/create, null ONLY for delete",
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
                prompt = currentContext.last().content,
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

                            val tool = toolRegistry.find(toolName)
                            if (tool != null) {
                                val result = tool.execute(args)
                                Log.d(tag, "Tool result: ${result.take(100)}...")
                                currentContext.add(ChatLmMessage(ChatLmRole.USER, "[UNTRUSTED DATA FROM TOOL $toolName]:\n$result"))
                                onEvent(TimelineEvent.ToolCall(toolName, args, result))
                            } else {
                                val errMsg = "Error: Unknown tool $toolName"
                                currentContext.add(ChatLmMessage(ChatLmRole.USER, errMsg))
                                onEvent(TimelineEvent.ToolCall(toolName, args, error = errMsg))
                            }
                        } else if (type == "final_plan") {
                            onEvent(TimelineEvent.AgentThought("Ready to propose final plan."))
                            val actionable = jsonObj.optBoolean("actionable", false)
                            val explanation = jsonObj.optString("explanation", "No explanation provided.")

                            if (actionable) {
                                val code = jsonObj.getString("generatorCode")
                                val actions = try {
                                    PythonEngine.generateActions(code, workspacePath)
                                } catch (e: Exception) {
                                    Log.e(tag, "Failed to execute generator code in final_plan", e)
                                    return@withContext Result.failure(Exception("Generator code execution failed: ${e.message}", e))
                                }
                                return@withContext Result.success(ExecutionPlan(actions, explanation))
                            } else {
                                return@withContext Result.success(ExecutionPlan(emptyList(), explanation))
                            }
                        } else {
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
        val prompt = """
The previous execution plan failed with error:
$errorLog

Failed Actions:
${failedActions.joinToString("\n") { "${it.type}: ${it.sourcePath} -> ${it.destinationPath}" }}

Please propose a repaired plan using the final_plan format. Ensure your repair stays within the authorized workspace ($workspacePath) and does not expand path authority beyond the user's intent.
""".trimIndent()

        val result = processPrompt(prompt, workspacePath, emptyList())
        result.mapCatching { plan ->
            if (plan != null && plan.actions.isNotEmpty()) {
                // Preflight validation pass to ensure repair does not gain broader authority
                val preflight = WorkspaceEngine().preflight(plan.actions)
                if (!preflight.isValid) {
                    throw IllegalArgumentException("Repair plan rejected due to policy validation: " + preflight.validationErrors.joinToString("; "))
                }
                RepairPlan(failedActions, plan.actions, plan.explanation)
            } else {
                null
            }
        }
    }
}
