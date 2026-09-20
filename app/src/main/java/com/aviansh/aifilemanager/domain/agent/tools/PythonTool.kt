package com.aviansh.aifilemanager.domain.agent.tools

import android.os.Environment
import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.ToolDefinition
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PythonTool(
    private val workspaceDir: String,
    private val allowedReadRoots: List<File> = defaultReadRoots()
) : AgentTool {

    companion object {
        /**
         * The agent is allowed to read anywhere the user can see their own files. Reads are
         * restricted to shared storage; app-private data and system paths stay off limits.
         */
        fun defaultReadRoots(): List<File> = buildList {
            try {
                add(Environment.getExternalStorageDirectory())
            } catch (_: Exception) {
                // Unit tests / non-Android runtimes
            }
        }
    }

    override val name: String = "PythonExecutor"

    override val description: String =
        "Executes Python code. It runs with its working directory set to the isolated workspace " +
            "and may READ any file under shared storage (e.g. /storage/emulated/0). " +
            "It may only WRITE inside the workspace — writing, deleting or renaming anything " +
            "outside the workspace is rejected, so express those changes as plan actions instead."

    override val definition: ToolDefinition = ToolDefinition(
        name = name,
        description = description,
        argsSchema = "A string containing the python script to run.",
        riskLevel = RiskLevel.MODERATE
    )

    override suspend fun execute(args: String): String = withContext(Dispatchers.IO) {
        try {
            PythonEngine.executeArbitraryCode(
                code = args,
                workspaceDir = workspaceDir,
                allowedReadRoots = allowedReadRoots
            )
        } catch (e: Exception) {
            "Error executing Python code: ${e.message}"
        }
    }
}
