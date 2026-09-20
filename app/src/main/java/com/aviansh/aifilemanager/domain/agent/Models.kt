package com.aviansh.aifilemanager.domain.agent

import org.json.JSONObject

/**
 * A tool call the agent wants to make that mutates user data and is awaiting confirmation.
 */
data class PendingAction(
    val toolName: String,
    val args: JSONObject,
    val preview: String
)

sealed class TimelineEvent {
    data class UserPrompt(val text: String) : TimelineEvent()

    /** The agent's narration of what it is doing next. */
    data class AgentThought(val text: String) : TimelineEvent()

    data class ToolCall(
        val toolName: String,
        val preview: String,
        val result: String? = null,
        val error: String? = null
    ) : TimelineEvent()

    /** A destructive call waiting on the user. */
    data class ConfirmationRequest(val action: PendingAction) : TimelineEvent()

    /** The agent's final natural-language answer for the turn. */
    data class AgentAnswer(val text: String) : TimelineEvent()

    data class ExecutionLog(val message: String, val isError: Boolean = false) : TimelineEvent()

    data class SystemMessage(val message: String) : TimelineEvent()
}

sealed class AgentState {
    object Idle : AgentState()

    /** The model is deciding what to do next. */
    object Thinking : AgentState()

    /** A tool is currently running. */
    data class Working(val toolName: String, val preview: String) : AgentState()

    /** Execution is paused until the user confirms or rejects [action]. */
    data class AwaitingConfirmation(val action: PendingAction) : AgentState()

    data class Done(val summary: String) : AgentState()

    data class Failed(val error: String) : AgentState()
}
