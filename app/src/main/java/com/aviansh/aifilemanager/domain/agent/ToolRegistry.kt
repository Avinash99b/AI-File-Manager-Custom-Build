package com.aviansh.aifilemanager.domain.agent

class ToolRegistry(
    tools: List<AgentTool> = emptyList()
) {
    private val toolMap = tools.associateBy { it.name }.toMutableMap()

    fun register(tool: AgentTool) {
        toolMap[tool.name] = tool
    }

    fun find(name: String): AgentTool? = toolMap[name]

    fun describeTools(): String = buildString {
        toolMap.values.forEachIndexed { idx, tool ->
            appendLine("${idx + 1}. ${tool.name}:")
            appendLine("   Description: ${tool.description}")
            appendLine("   Input: ${tool.definition.argsSchema}")
        }
    }

    fun getAllTools(): List<AgentTool> = toolMap.values.toList()
}
