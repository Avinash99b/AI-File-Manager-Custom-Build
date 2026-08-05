package com.aviansh.aifilemanager.domain.agent

import com.aviansh.aifilemanager.domain.data.FileAction

data class ExecutionPlan(
    val actions: List<FileAction>,
    val explanation: String
)

data class RepairPlan(
    val failedActions: List<FileAction>,
    val proposedFixes: List<FileAction>,
    val explanation: String
)

sealed class TimelineEvent {
    data class UserPrompt(val text: String) : TimelineEvent()
    data class AgentThought(val text: String) : TimelineEvent()
    data class ToolCall(val toolName: String, val args: String, val result: String? = null, val error: String? = null) : TimelineEvent()
    data class ProposedPlan(val plan: ExecutionPlan) : TimelineEvent()
    data class ExecutionLog(val message: String, val isError: Boolean = false) : TimelineEvent()
    data class ProposedRepair(val repairPlan: RepairPlan) : TimelineEvent()
    data class SystemMessage(val message: String) : TimelineEvent()
}

sealed class ExecutionState {
    object Idle : ExecutionState()
    object Planning : ExecutionState()
    data class WaitingForApproval(val plan: ExecutionPlan) : ExecutionState()
    object Executing : ExecutionState()
    object Verifying : ExecutionState()
    data class WaitingForRepairApproval(val repairPlan: RepairPlan) : ExecutionState()
    object Completed : ExecutionState()
    data class Failed(val error: String) : ExecutionState()
}
