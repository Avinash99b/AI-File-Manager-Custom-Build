package com.aviansh.aifilemanager.ui.components

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.domain.agent.ExecutionState
import com.aviansh.aifilemanager.domain.agent.TimelineEvent
import com.aviansh.aifilemanager.domain.agent.WorkspaceEngine

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
    val context = LocalContext.current

    // Check system setting for reduced motion preference
    val isReducedMotion = remember(context) {
        try {
            val animScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            animScale == 0f
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(timeline.size) {
        if (timeline.isNotEmpty()) {
            val targetIndex = timeline.size - 1
            if (isReducedMotion) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
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
                        TimelineCard(
                            badgeText = "[PROMPT]",
                            title = "User Prompt",
                            content = event.text,
                            icon = Icons.Default.Person,
                            iconDescription = "User prompt icon",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    is TimelineEvent.AgentThought -> {
                        TimelineCard(
                            badgeText = "[STATUS]",
                            title = "Status",
                            content = "Analyzing workspace and planning workflow...",
                            icon = Icons.Default.Psychology,
                            iconDescription = "Agent reasoning status icon",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is TimelineEvent.ToolCall -> {
                        TimelineCard(
                            badgeText = "[TOOL]",
                            title = "Tool Call: ${event.toolName}",
                            content = "Args: ${event.args.take(120)}\nResult: ${event.result?.take(250) ?: event.error ?: "Pending"}",
                            icon = Icons.Default.Build,
                            iconDescription = "Tool execution icon",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is TimelineEvent.ExecutionLog -> {
                        val isErr = event.isError
                        val color = if (isErr) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                        val contentColor = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        TimelineCard(
                            badgeText = if (isErr) "[ERROR]" else "[LOG]",
                            title = if (isErr) "Error Log" else "Execution Log",
                            content = event.message,
                            icon = if (isErr) Icons.Default.Error else Icons.Default.Info,
                            iconDescription = if (isErr) "Error log icon" else "Execution log icon",
                            containerColor = color,
                            contentColor = contentColor
                        )
                    }
                    is TimelineEvent.SystemMessage -> {
                        TimelineCard(
                            badgeText = "[SYSTEM]",
                            title = "System Message",
                            content = event.message,
                            icon = Icons.Default.Info,
                            iconDescription = "System message icon",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is TimelineEvent.ProposedPlan -> {
                        val preflight = WorkspaceEngine().preflight(event.plan.actions)
                        val riskBreakdown = preflight.actionsByRisk.entries
                            .filter { it.value > 0 }
                            .joinToString(", ") { "${it.key.name}: ${it.value}" }
                        val pathsText = if (preflight.affectedPaths.size <= 3) {
                            preflight.affectedPaths.joinToString("\n")
                        } else {
                            preflight.affectedPaths.take(3).joinToString("\n") + "\n+${preflight.affectedPaths.size - 3} more path(s)"
                        }
                        val content = buildString {
                            appendLine("Explanation: ${event.plan.explanation}")
                            appendLine("Risk Breakdown: $riskBreakdown")
                            if (preflight.conflictsCount > 0) {
                                appendLine("⚠️ Conflicts: ${preflight.conflictsCount} destination file(s) already exist")
                            }
                            appendLine("Total Actions: ${event.plan.actions.size}")
                            if (pathsText.isNotBlank()) {
                                appendLine("\nAffected Paths:\n$pathsText")
                            }
                        }
                        TimelineCard(
                            badgeText = if (preflight.conflictsCount > 0) "[CONFLICT PLAN]" else "[PROPOSED PLAN]",
                            title = "Proposed Plan",
                            content = content.trim(),
                            icon = if (preflight.conflictsCount > 0) Icons.Default.Warning else Icons.AutoMirrored.Filled.Assignment,
                            iconDescription = "Proposed execution plan icon",
                            containerColor = if (preflight.conflictsCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is TimelineEvent.ProposedRepair -> {
                        TimelineCard(
                            badgeText = "[REPAIR PLAN]",
                            title = "Proposed Repair Plan",
                            content = "Explanation: ${event.repairPlan.explanation}\nFixes: ${event.repairPlan.proposedFixes.size} action(s)",
                            icon = Icons.Default.BuildCircle,
                            iconDescription = "Repair plan icon",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Action / Conflict / Failure Banner Surface
        Surface(tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Conflict resolution card if waiting for approval with conflicts
                if (executionState is ExecutionState.WaitingForApproval) {
                    val plan = executionState.plan
                    val preflight = WorkspaceEngine().preflight(plan.actions)
                    if (preflight.conflictsCount > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Conflict warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                                Column {
                                    Text("Destination Conflicts Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                                    Text("${preflight.conflictsCount} target file(s) exist. Approving will overwrite existing files safely using snapshot backups.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Recovery failure state UI card
                if (executionState is ExecutionState.Failed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = "Recovery Failure", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Column {
                                Text("Execution / Recovery Failure", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                                Text(executionState.error, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                failureHint(executionState.error)?.let { hint ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = hint,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (executionState) {
                        is ExecutionState.WaitingForApproval -> {
                            Button(
                                onClick = onApprovePlan,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text("Approve Plan")
                            }
                            OutlinedButton(
                                onClick = onSoftStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                        is ExecutionState.WaitingForRepairApproval -> {
                            Button(
                                onClick = onApproveRepairPlan,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Approve Repair")
                            }
                            OutlinedButton(
                                onClick = onHardStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text("Abort")
                            }
                        }
                        is ExecutionState.Executing, is ExecutionState.Planning, is ExecutionState.Verifying -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                when (executionState) {
                                    is ExecutionState.Planning -> "Planning..."
                                    is ExecutionState.Executing -> "Executing..."
                                    is ExecutionState.Verifying -> "Verifying..."
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onHardStop,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Text("Hard Stop")
                            }
                        }
                        is ExecutionState.Completed, is ExecutionState.Failed, is ExecutionState.Idle -> {
                            // Ready for user prompts
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(
    badgeText: String,
    title: String,
    content: String,
    icon: ImageVector,
    iconDescription: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = FontWeight.Bold)
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Turns raw agent/sandbox failures into a short, actionable suggestion for the user, so the
 * error card explains what to do next instead of only showing an internal message.
 */
internal fun failureHint(error: String): String? = when {
    error.contains("reasoning steps", ignoreCase = true) ||
        error.contains("maximum iterations", ignoreCase = true) ->
        "Tip: try a narrower request, e.g. name the folder or the specific files to act on."

    error.contains("denied outside workspace", ignoreCase = true) ->
        "The AI tried to modify a file directly instead of proposing it as an action. Re-run the request; it should propose a plan you can approve."

    error.contains("Read denied", ignoreCase = true) ->
        "That location is outside your shared storage. Try a path under /storage/emulated/0."

    error.contains("Provider not configured", ignoreCase = true) ->
        "Open the menu in the top bar to set up Gemini or an OpenAI compatible endpoint."

    error.contains("network", ignoreCase = true) ->
        "Check your internet connection and the provider endpoint in settings."

    error.contains("quota", ignoreCase = true) || error.contains("429") ->
        "Your provider rejected the request (rate limit or quota). Wait a moment or switch provider in settings."

    else -> null
}
