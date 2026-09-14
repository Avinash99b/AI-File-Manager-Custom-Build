package com.aviansh.aifilemanager.domain.agent.tools

import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.ToolDefinition
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonTool(private val workspaceDir: String) : AgentTool {
    override val name: String = "PythonExecutor"
    override val description: String = "Executes arbitrary Python code to generate file actions or perform read-only operations. It runs in the isolated workspace but may READ files from any directory on the device; it must never write outside the workspace."

    override val definition: ToolDefinition = ToolDefinition(
        name = name,
        description = description,
        argsSchema = "A string containing the python script to run.",
        riskLevel = RiskLevel.MODERATE
    )

    override suspend fun execute(args: String): String = withContext(Dispatchers.IO) {
        try {
            PythonEngine.executeArbitraryCode(args, workspaceDir)
        } catch (e: Exception) {
            "Error executing Python code: ${e.message}"
        }
    }
}
