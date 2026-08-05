package com.aviansh.aifilemanager.domain.agent

interface AgentTool {
    val name: String
    val description: String
    suspend fun execute(args: String): String
}
