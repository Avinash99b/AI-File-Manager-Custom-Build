package com.aviansh.aifilemanager.domain.agent.tools

import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.ToolParam
import com.aviansh.aifilemanager.domain.agent.ToolResult
import com.aviansh.aifilemanager.domain.agent.ToolSpec
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.security.FileAccessPolicy
import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Python as a capability tool rather than the plan generator.
 *
 * It exists for things the native tools cannot express — extracting PDF text, converting images,
 * parsing spreadsheets, computing statistics. It is READ-ONLY with respect to user storage:
 * anything it produces is written to a scratch directory, and the agent then uses the normal
 * file tools (move/copy) to place the result, so every mutation goes through the same
 * confirmation and trash safety net.
 */
class RunPythonTool(
    private val policy: FileAccessPolicy,
    private val scratchDir: String
) : AgentTool {

    override val spec = ToolSpec(
        name = "run_python",
        description = "Runs Python for analysis or file conversion (pypdf, Pillow, pandas, openpyxl, " +
            "reportlab are available). It can READ any file in shared storage but may only WRITE " +
            "into the scratch directory: $scratchDir. Print the result you want to see. " +
            "To place a produced file for the user, afterwards call move or copy.",
        params = listOf(
            ToolParam("code", "string", "Python source to execute. Use print() to return output.")
        ),
        riskLevel = RiskLevel.MODERATE
    )

    override fun previewFor(args: JSONObject): String =
        "Run Python (${args.optString("code").lines().size} lines)"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val code = args.optString("code")
        if (code.isBlank()) return@withContext ToolResult.Failure("Missing required argument 'code'")

        File(scratchDir).mkdirs()

        val output = PythonEngine.executeArbitraryCode(
            code = code,
            workspaceDir = scratchDir,
            allowedReadRoots = FileAccessPolicy.defaultAllowedRoots()
        )

        if (output.startsWith("Execution Error:")) {
            ToolResult.Failure(output.removePrefix("Execution Error:").trim())
        } else {
            val shown = output.ifBlank { "(no output — remember to print() what you need)" }
            ToolResult.Success(shown, "Ran Python")
        }
    }
}
