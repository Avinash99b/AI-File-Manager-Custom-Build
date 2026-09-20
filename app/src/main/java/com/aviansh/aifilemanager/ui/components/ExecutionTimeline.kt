package com.aviansh.aifilemanager.ui.components

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import com.aviansh.aifilemanager.domain.agent.AgentState
import com.aviansh.aifilemanager.domain.agent.TimelineEvent

@Composable
fun ExecutionTimeline(
    timeline: List<TimelineEvent>,
    agentState: AgentState,
    onConfirmAction: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(timeline.size) {
        if (timeline.isNotEmpty()) {
            val target = timeline.size - 1
            if (isReducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
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
                    is TimelineEvent.UserPrompt -> TimelineCard(
                        badgeText = "[YOU]",
                        title = "You asked",
                        content = event.text,
                        icon = Icons.Default.Person,
                        iconDescription = "Your message",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    is TimelineEvent.AgentThought -> TimelineCard(
                        badgeText = "[THINKING]",
                        title = "Agent",
                        content = event.text,
                        icon = Icons.Default.Psychology,
                        iconDescription = "Agent reasoning",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    is TimelineEvent.ToolCall -> {
                        val running = event.result == null && event.error == null
                        val failed = event.error != null
                        TimelineCard(
                            badgeText = when {
                                running -> "[RUNNING]"
                                failed -> "[FAILED]"
                                else -> "[DONE]"
                            },
                            title = event.preview.ifBlank { event.toolName },
                            content = when {
                                running -> "Working…"
                                failed -> event.error.orEmpty()
                                else -> event.result.orEmpty()
                            },
                            icon = when {
                                running -> Icons.Default.Autorenew
                                failed -> Icons.Default.ErrorOutline
                                else -> Icons.Default.CheckCircle
                            },
                            iconDescription = "Tool ${event.toolName}",
                            containerColor = when {
                                failed -> MaterialTheme.colorScheme.errorContainer
                                running -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = when {
                                failed -> MaterialTheme.colorScheme.onErrorContainer
                                running -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }

                    is TimelineEvent.ConfirmationRequest -> TimelineCard(
                        badgeText = "[NEEDS YOU]",
                        title = "Confirmation requested",
                        content = event.action.preview,
                        icon = Icons.Default.Warning,
                        iconDescription = "Confirmation required",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    is TimelineEvent.AgentAnswer -> TimelineCard(
                        badgeText = "[ANSWER]",
                        title = "Agent",
                        content = event.text,
                        icon = Icons.Default.AutoAwesome,
                        iconDescription = "Agent answer",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    is TimelineEvent.ExecutionLog -> TimelineCard(
                        badgeText = if (event.isError) "[ERROR]" else "[LOG]",
                        title = if (event.isError) "Error" else "Log",
                        content = event.message,
                        icon = if (event.isError) Icons.Default.Error else Icons.Default.Info,
                        iconDescription = if (event.isError) "Error" else "Log",
                        containerColor = if (event.isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = if (event.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    is TimelineEvent.SystemMessage -> TimelineCard(
                        badgeText = "[SYSTEM]",
                        title = "System",
                        content = event.message,
                        icon = Icons.Default.Info,
                        iconDescription = "System message",
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Surface(tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (agentState) {
                    is AgentState.AwaitingConfirmation -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Confirmation required",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        "Allow this change?",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    agentState.action.preview,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 13.sp
                                )
                                if (agentState.action.toolName == "delete") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Deleted items go to the app trash and can be restored.",
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onConfirmAction(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) { Text("Allow") }

                            OutlinedButton(
                                onClick = { onConfirmAction(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) { Text("Skip") }
                        }
                    }

                    is AgentState.Thinking, is AgentState.Working -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = when (agentState) {
                                    is AgentState.Working -> agentState.preview
                                    else -> "Thinking…"
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            OutlinedButton(
                                onClick = onStop,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) { Text("Stop") }
                        }
                    }

                    is AgentState.Failed -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Failure",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Column {
                                    Text(
                                        "Could not finish",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        agentState.error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp
                                    )
                                    failureHint(agentState.error)?.let { hint ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            hint,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is AgentState.Idle, is AgentState.Done -> Unit
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
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Turns raw agent failures into a short, actionable suggestion so the error card explains what
 * to do next instead of only showing an internal message.
 */
internal fun failureHint(error: String): String? = when {
    error.contains("steps without finishing", ignoreCase = true) ||
        error.contains("reasoning steps", ignoreCase = true) ->
        "Tip: try a narrower request, e.g. name the folder or the specific files to act on."

    error.contains("outside your shared storage", ignoreCase = true) ||
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
