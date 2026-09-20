package com.aviansh.aifilemanager.domain.agent

class ToolRegistry(private val tools: List<AgentTool>) {

    fun find(name: String): AgentTool? =
        tools.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun all(): List<AgentTool> = tools

    fun describeTools(): String = tools.joinToString("\n") { it.spec.describe() }
}
