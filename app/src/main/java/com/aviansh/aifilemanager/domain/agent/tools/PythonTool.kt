package com.aviansh.aifilemanager.domain.agent.tools

import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonTool(private val workspaceDir: String) : AgentTool {
    override val name: String = "PythonExecutor"
    override val description: String = "Executes arbitrary Python code to generate file actions or perform operations. It will run in the isolated workspace."

    override suspend fun execute(args: String): String = withContext(Dispatchers.IO) {
        try {
            PythonEngine.executeArbitraryCode(args, workspaceDir)
        } catch (e: Exception) {
            "Error executing Python code: ${e.message}"
        }
    }
}
