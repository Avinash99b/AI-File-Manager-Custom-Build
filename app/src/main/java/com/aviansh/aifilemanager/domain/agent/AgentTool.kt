package com.aviansh.aifilemanager.domain.agent

import com.aviansh.aifilemanager.domain.transactions.RiskLevel

data class ToolDefinition(
    val name: String,
    val description: String,
    val argsSchema: String = "String argument",
    val riskLevel: RiskLevel = RiskLevel.SAFE
)

interface AgentTool {
    val name: String
    val description: String
    val definition: ToolDefinition
        get() = ToolDefinition(name = name, description = description)

    suspend fun execute(args: String): String
}
