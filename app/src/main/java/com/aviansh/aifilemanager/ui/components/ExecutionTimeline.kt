package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aviansh.aifilemanager.domain.agent.TimelineEvent
import com.aviansh.aifilemanager.domain.agent.ExecutionState

@Composable
fun ExecutionTimeline(
    timeline: List<TimelineEvent>,
    executionState: ExecutionState,
    onApprovePlan: () -> Unit,
    onSoftStop: () -> Unit,
    onHardStop: () -> Unit,
    onApproveRepairPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(timeline.size) {
        if (timeline.isNotEmpty()) {
            listState.animateScrollToItem(timeline.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(timeline) { event ->
                when (event) {
                    is TimelineEvent.UserPrompt -> {
                        TimelineCard("User Prompt", event.text, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    is TimelineEvent.AgentThought -> {
                        TimelineCard("Agent Thought", event.text, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is TimelineEvent.ToolCall -> {
                        TimelineCard("Tool Call: ${event.toolName}", "Args: ${event.args}\nResult: ${event.result ?: event.error ?: "Pending"}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    is TimelineEvent.ExecutionLog -> {
                        val color = if (event.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                        val contentColor = if (event.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        TimelineCard(if (event.isError) "Error Log" else "Execution Log", event.message, color, contentColor)
                    }
                    is TimelineEvent.SystemMessage -> {
                        TimelineCard("System", event.message, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
                    }
                    is TimelineEvent.ProposedPlan -> {
                        TimelineCard("Proposed Plan", "Explanation: ${event.plan.explanation}\nActions: ${event.plan.actions.size}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is TimelineEvent.ProposedRepair -> {
                        TimelineCard("Proposed Repair", "Explanation: ${event.repairPlan.explanation}\nFixes: ${event.repairPlan.proposedFixes.size}", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Action Buttons based on state
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (executionState) {
                    is ExecutionState.WaitingForApproval -> {
                        Button(onClick = onApprovePlan, modifier = Modifier.weight(1f)) {
                            Text("Approve")
                        }
                        OutlinedButton(onClick = onSoftStop, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }
                    }
                    is ExecutionState.WaitingForRepairApproval -> {
                        Button(onClick = onApproveRepairPlan, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Approve Repair")
                        }
                        OutlinedButton(onClick = onHardStop, modifier = Modifier.weight(1f)) {
                            Text("Abort")
                        }
                    }
                    is ExecutionState.Executing, is ExecutionState.Planning, is ExecutionState.Verifying -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(when(executionState) {
                            is ExecutionState.Planning -> "Planning..."
                            is ExecutionState.Executing -> "Executing..."
                            is ExecutionState.Verifying -> "Verifying..."
                            else -> "Working..."
                        }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onHardStop) {
                            Text("Hard Stop")
                        }
                    }
                    is ExecutionState.Completed, is ExecutionState.Failed, is ExecutionState.Idle -> {
                        // Just waiting for the next prompt.
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(title: String, content: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
