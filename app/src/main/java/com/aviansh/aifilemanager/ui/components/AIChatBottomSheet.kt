package com.aviansh.aifilemanager.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonColors
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.ui.screens.DarkThemeColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatBottomSheet(
    messages: List<ChatLmMessage>,
    isLoading: Boolean,
    chatError: String?,
    pendingActions: List<FileAction>?,
    pendingExplanation: String?,
    transactionProgress: TransactionProgress,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onConfirmActions: () -> Unit,
    onCancelActions: () -> Unit,
    onDismissProgress: () -> Unit,
    sheetState: SheetState
) {
    var messageInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        try {
            scrollState.animateScrollTo(scrollState.maxValue)
        } catch (e: Exception) {
            // Ignore scroll errors
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.80f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(DarkThemeColors.SurfaceLight)
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(DarkThemeColors.Divider)
            )
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkThemeColors.Primary.copy(alpha = 0.2f)),
                    Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        Modifier.size(20.dp),
                        DarkThemeColors.Primary
                    )
                }
                Text(
                    "AI Assistant",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkThemeColors.TextPrimary
                )
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = onClearChat, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        "Clear",
                        Modifier.size(20.dp),
                        DarkThemeColors.TextSecondary
                    )
                }
            }
        }

        Divider(color = DarkThemeColors.Divider)

        // Transaction Progress
        TransactionProgressBanner(transactionProgress, onDismissProgress)

        // Messages
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && pendingActions == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Chat,
                            null,
                            Modifier.size(44.dp),
                            DarkThemeColors.TextTertiary
                        )
                        Text(
                            "Ask me to manage your files",
                            color = DarkThemeColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    messages.forEach { message ->
                        ChatMessageBubble(
                            message = message,
                            pendingActions = pendingActions,
                            explanation = pendingExplanation,
                            onConfirmActions = onConfirmActions,
                            onCancelActions = onCancelActions
                        )
                    }

                    if (isLoading) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = DarkThemeColors.Primary,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                "Thinking…",
                                fontSize = 13.sp,
                                color = DarkThemeColors.TextSecondary
                            )
                        }
                    }

                    if (chatError != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = DarkThemeColors.Error.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = DarkThemeColors.Error
                                )
                                Text(
                                    chatError,
                                    fontSize = 12.sp,
                                    color = DarkThemeColors.Error
                                )
                            }
                        }
                    }
                }
            }
        }

        Divider(color = DarkThemeColors.Divider)

        // Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = {
                    Text(
                        "Ask AI…",
                        fontSize = 13.sp,
                        color = DarkThemeColors.TextTertiary
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                singleLine = false,
                shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = DarkThemeColors.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkThemeColors.Primary,
                    unfocusedBorderColor = DarkThemeColors.Divider,
                    focusedTextColor = DarkThemeColors.TextPrimary,
                    unfocusedTextColor = DarkThemeColors.TextPrimary,
                    cursorColor = DarkThemeColors.Primary
                )
            )
            FilledIconButton(
                onClick = {
                    if (messageInput.isNotBlank()) {
                        onSendMessage(messageInput)
                        messageInput = ""
                    }
                },
                enabled = messageInput.isNotBlank() && !isLoading,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DarkThemeColors.Primary,
                    disabledContainerColor = DarkThemeColors.Primary.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Send, "Send", Modifier.size(20.dp))
            }
        }
    }
}
