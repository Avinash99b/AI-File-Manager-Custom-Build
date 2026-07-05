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
import com.aviansh.aifilemanager.ui.data.ProgressQuad
import com.aviansh.aifilemanager.ui.screens.DarkThemeColors
@Composable
fun TransactionProgressBanner(
    progress: TransactionProgress,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = progress !is TransactionProgress.Idle,
        enter = slideInVertically(initialOffsetY = { -100 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -100 }) + fadeOut()
    ) {
        val (icon, text, bgColor, fgColor) = when (progress) {
            is TransactionProgress.Running ->
                ProgressQuad(
                    Icons.Default.HourglassTop,
                    "Running transaction…",
                    DarkThemeColors.Warning.copy(alpha = 0.15f),
                    DarkThemeColors.Warning
                )
            is TransactionProgress.Succeeded ->
                ProgressQuad(
                    Icons.Default.CheckCircle,
                    "✓ ${progress.actionCount} op(s) completed",
                    DarkThemeColors.Success.copy(alpha = 0.15f),
                    DarkThemeColors.Success
                )
            is TransactionProgress.RolledBack ->
                ProgressQuad(
                    Icons.Default.Undo,
                    "↩ Rolled back: ${progress.reason}",
                    DarkThemeColors.Error.copy(alpha = 0.15f),
                    DarkThemeColors.Error
                )
            is TransactionProgress.Failed ->
                ProgressQuad(
                    Icons.Default.Error,
                    "✗ ${progress.reason}",
                    DarkThemeColors.Error.copy(alpha = 0.15f),
                    DarkThemeColors.Error
                )
            else -> return@AnimatedVisibility
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress is TransactionProgress.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = fgColor,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(icon, null, modifier = Modifier.size(22.dp), tint = fgColor)
                }
                Text(
                    text,
                    fontSize = 13.sp,
                    color = fgColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (progress !is TransactionProgress.Running) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = fgColor
                        )
                    }
                }
            }
        }
    }
}
