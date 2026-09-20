package com.aviansh.aifilemanager.domain.agent

import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import org.json.JSONObject

/**
 * Declares a single parameter of a tool so the model can be told exactly what to send.
 */
data class ToolParam(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ToolParam> = emptyList(),
    /**
     * Destructive tools mutate or remove existing user data and therefore require explicit
     * user confirmation before they run. Read-only and additive tools run automatically.
     */
    val destructive: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.SAFE
) {
    fun describe(): String = buildString {
        append("- ").append(name)
        append("(")
        append(params.joinToString(", ") { if (it.required) it.name else "${it.name}?" })
        append("): ")
        append(description)
        if (destructive) append(" [REQUIRES USER CONFIRMATION]")
        if (params.isNotEmpty()) {
            params.forEach { p ->
                append("\n    • ").append(p.name).append(" (").append(p.type)
                if (!p.required) append(", optional")
                append("): ").append(p.description)
            }
        }
    }
}

sealed interface ToolResult {
    /** [output] is fed back to the model; [summary] is shown to the user. */
    data class Success(val output: String, val summary: String = output) : ToolResult

    data class Failure(val error: String) : ToolResult
}

interface AgentTool {
    val spec: ToolSpec

    val name: String get() = spec.name

    /**
     * Describes what this call will do in plain language, used for the confirmation prompt.
     */
    fun previewFor(args: JSONObject): String = "$name(${args})"

    suspend fun execute(args: JSONObject): ToolResult
}
