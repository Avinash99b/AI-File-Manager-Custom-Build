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

@Composable
fun ChatMessageBubble(
    message: ChatLmMessage,
    pendingActions: List<FileAction>? = null,
    onConfirmActions: (() -> Unit)? = null,
    onCancelActions: (() -> Unit)? = null
) {
    val isActionProposal = !pendingActions.isNullOrEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        )
                    ),
                color = if (message.isUser)
                    DarkThemeColors.Primary
                else
                    DarkThemeColors.Surface
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = if (message.isUser)
                        Color.White
                    else
                        DarkThemeColors.TextPrimary,
                    lineHeight = 18.sp
                )
            }
        }

        // Actions Card
        if (isActionProposal && onConfirmActions != null && onCancelActions != null) {
            Spacer(Modifier.height(6.dp))
            PendingActionsCard(
                actions = pendingActions,
                onConfirm = onConfirmActions,
                onCancel = onCancelActions
            )
        }
    }
}