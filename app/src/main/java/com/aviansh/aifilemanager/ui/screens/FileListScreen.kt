package com.aviansh.aifilemanager.ui.screens

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
import java.io.File

// ─── DARK THEME PALETTE ───────────────────────────────────────────────────────
object DarkThemeColors {
    val Background      = Color(0xFF0A0A0A)      // AMOLED Black
    val Surface         = Color(0xFF1A1A1A)      // Dark gray
    val SurfaceLight    = Color(0xFF2A2A2A)      // Lighter dark gray
    val Primary         = Color(0xFF7C3AED)      // Vibrant Purple
    val PrimaryLight    = Color(0xFF9F5FFF)      // Lighter Purple
    val Accent          = Color(0xFF10B981)      // Emerald Green
    val AccentLight     = Color(0xFF34D399)      // Light Emerald
    val TextPrimary     = Color(0xFFFAFAFA)      // Almost white
    val TextSecondary   = Color(0xFFA0A0A0)      // Medium gray
    val TextTertiary    = Color(0xFF707070)      // Darker gray
    val Error           = Color(0xFFEF4444)      // Bright red
    val Warning         = Color(0xFFF59E0B)      // Amber
    val Success         = Color(0xFF10B981)      // Green
    val Divider         = Color(0xFF333333)      // Subtle divider
}

// ─── FILE LIST ITEM WITH IMPROVED STYLING ──────────────────────────────────────

@Composable
fun FileListItem(
    fileItem: FileItem,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    onPreview: (FileItem) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(fileItem.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onSelect(fileItem)
                if (fileItem.isDirectory) {
                    onNavigate(fileItem)
                } else {
                    onPreview(fileItem)
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkThemeColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (fileItem.isDirectory)
                                DarkThemeColors.Primary.copy(alpha = 0.2f)
                            else
                                DarkThemeColors.Accent.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fileItem.isDirectory)
                            Icons.Default.Folder
                        else
                            getFileIcon(fileItem.name),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (fileItem.isDirectory)
                            DarkThemeColors.Primary
                        else
                            DarkThemeColors.Accent
                    )
                }

                // File Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkThemeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (!fileItem.isDirectory) {
                            Text(
                                getFormattedSize(fileItem.size),
                                fontSize = 12.sp,
                                color = DarkThemeColors.TextTertiary
                            )
                            Text("·", fontSize = 12.sp, color = DarkThemeColors.TextTertiary)
                        }
                        Text(
                            getFormattedDate(fileItem.lastModified),
                            fontSize = 12.sp,
                            color = DarkThemeColors.TextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Menu Button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = DarkThemeColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fixed Dropdown Menu with proper positioning
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkThemeColors.SurfaceLight)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = DarkThemeColors.TextPrimary) },
                        onClick = { showMenu = false; showRenameDialog = true },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = DarkThemeColors.Primary
                            )
                        }
                    )
                    if (fileItem.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Properties", color = DarkThemeColors.TextPrimary) },
                            onClick = { showMenu = false },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = DarkThemeColors.Primary
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = DarkThemeColors.Error) },
                        onClick = { showMenu = false; onDelete(fileItem) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = DarkThemeColors.Error
                            )
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = DarkThemeColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name", color = DarkThemeColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkThemeColors.Primary,
                        unfocusedBorderColor = DarkThemeColors.Divider,
                        focusedTextColor = DarkThemeColors.TextPrimary,
                        unfocusedTextColor = DarkThemeColors.TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) onRename(fileItem, renameInput)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkThemeColors.Primary)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false },
                    colors = TextButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = DarkThemeColors.TextSecondary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = DarkThemeColors.TextTertiary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = DarkThemeColors.SurfaceLight,
            titleContentColor = DarkThemeColors.TextPrimary
        )
    }
}

// ─── FILE PREVIEW MODAL ────────────────────────────────────────────────────────

@Composable
fun FilePreviewModal(
    fileItem: FileItem?,
    onDismiss: () -> Unit
) {
    if (fileItem == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp)),
        containerColor = DarkThemeColors.SurfaceLight,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileItem.name,
                    color = DarkThemeColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = DarkThemeColors.TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // File Icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(DarkThemeColors.Surface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIcon(fileItem.name),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = DarkThemeColors.Primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // File Details
                PreviewDetailRow("Name", fileItem.name)
                PreviewDetailRow("Size", getFormattedSizeForPreview(fileItem.size))
                PreviewDetailRow("Type", getFileType(fileItem.name))
                PreviewDetailRow("Path", fileItem.path)
                PreviewDetailRow(
                    "Modified",
                    getFormattedDateForPreview(fileItem.lastModified)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File Content Preview (for text files)
                if (isTextFile(fileItem.name)) {
                    Text(
                        "Content Preview",
                        color = DarkThemeColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    PreviewTextContent(fileItem.path)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkThemeColors.Primary)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PreviewDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            color = DarkThemeColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = DarkThemeColors.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Divider(color = DarkThemeColors.Divider, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PreviewTextContent(filePath: String) {

        val content = File(filePath).readText(Charsets.UTF_8).take(500)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            color = DarkThemeColors.Background
        ) {
            Text(
                text = content,
                color = DarkThemeColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(12.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
}

// ─── FILE LIST SCREEN ─────────────────────────────────────────────────────────

@Composable
fun FileListScreen(
    files: List<FileItem>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    selectedFile: FileItem?,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    onRetry: () -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    modifier: Modifier = Modifier
) {
    var previewFile by remember { mutableStateOf<FileItem?>(null) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkThemeColors.Background)
    ) {
        // Path Header
        PathHeader(currentPath, onNavigateUp)

        when {
            isLoading -> LoadingPlaceholder()
            error != null -> ErrorState(error, onRetry)
            files.isEmpty() -> EmptyState()
            else -> FileListContent(
                files,
                listState,
                onNavigate,
                onSelect,
                onDelete,
                onRename,
                getFormattedSize,
                getFormattedDate,
                onPreview = { previewFile = it }
            )
        }
    }

    // File Preview Modal
    if (previewFile != null) {
        FilePreviewModal(previewFile) { previewFile = null }
    }
}

@Composable
private fun PathHeader(currentPath: String, onNavigateUp: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkThemeColors.Surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkThemeColors.Primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Up",
                    tint = DarkThemeColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = currentPath,
                fontSize = 12.sp,
                color = DarkThemeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = DarkThemeColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Loading files…",
                color = DarkThemeColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            null,
            modifier = Modifier.size(56.dp),
            tint = DarkThemeColors.Error
        )
        Text(
            error,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
            color = DarkThemeColors.TextPrimary,
            fontSize = 14.sp
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkThemeColors.Primary)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(verticalArrangement = Arrangement.Center) {
            Icon(
                Icons.Default.FolderOpen,
                null,
                modifier = Modifier.size(56.dp),
                tint = DarkThemeColors.TextTertiary
            )
            Text(
                "Empty folder",
                color = DarkThemeColors.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun FileListContent(
    files: List<FileItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    onPreview: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            files,
            key = { it.id }
        ) { fileItem ->
            FileListItem(
                fileItem = fileItem,
                onNavigate = onNavigate,
                onSelect = onSelect,
                onDelete = onDelete,
                onRename = onRename,
                getFormattedSize = getFormattedSize,
                getFormattedDate = getFormattedDate,
                onPreview = onPreview
            )
        }
    }
}

// ─── TRANSACTION PROGRESS BANNER ──────────────────────────────────────────────

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

private data class ProgressQuad(
    val icon: ImageVector,
    val text: String,
    val bgColor: Color,
    val fgColor: Color
)

// ─── PENDING ACTIONS CARD ─────────────────────────────────────────────────────

@Composable
fun PendingActionsCard(
    actions: List<FileAction>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkThemeColors.Primary.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.PlaylistAddCheck,
                    null,
                    tint = DarkThemeColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "Proposed operations",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DarkThemeColors.TextPrimary
                )
            }

            Spacer(Modifier.height(12.dp))

            actions.forEachIndexed { idx, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkThemeColors.Primary.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = action.type.name.take(3).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkThemeColors.Primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            action.sourcePath,
                            fontSize = 12.sp,
                            color = DarkThemeColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (action.destinationPath != null) {
                            Text(
                                "→ ${action.destinationPath}",
                                fontSize = 11.sp,
                                color = DarkThemeColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.5.dp,
                        DarkThemeColors.Primary
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DarkThemeColors.Primary
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkThemeColors.Primary)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Execute",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─── AI CHAT BOTTOM SHEET ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatBottomSheet(
    messages: List<ChatLmMessage>,
    isLoading: Boolean,
    chatError: String?,
    pendingActions: List<FileAction>?,
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

// ─── CHAT MESSAGE BUBBLE ─────────────────────────────────────────────────────

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
                actions = pendingActions!!,
                onConfirm = onConfirmActions,
                onCancel = onCancelActions
            )
        }
    }
}

// ─── UTILITY FUNCTIONS ────────────────────────────────────────────────────────

private fun getFileIcon(fileName: String): ImageVector =
    when {
        fileName.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z") ->
            Icons.Default.Archive
        fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".m4a") ->
            Icons.Default.AudioFile
        fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".mov") ->
            Icons.Default.VideoFile
        fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".gif") ->
            Icons.Default.Image
        fileName.endsWith(".txt") || fileName.endsWith(".doc") || fileName.endsWith(".docx") ->
            Icons.Default.Description
        fileName.endsWith(".xls") || fileName.endsWith(".xlsx") || fileName.endsWith(".csv") ->
            Icons.Default.TableChart
        else -> Icons.Default.InsertDriveFile
    }

private fun getFileType(fileName: String): String =
    when {
        fileName.endsWith(".pdf") -> "PDF Document"
        fileName.endsWith(".zip") || fileName.endsWith(".rar") -> "Compressed Archive"
        fileName.endsWith(".mp3") -> "MP3 Audio"
        fileName.endsWith(".mp4") -> "MP4 Video"
        fileName.endsWith(".jpg") || fileName.endsWith(".png") -> "Image"
        fileName.endsWith(".txt") -> "Text File"
        else -> "File"
    }

private fun isTextFile(fileName: String): Boolean =
    fileName.endsWith(".txt") ||
            fileName.endsWith(".md") ||
            fileName.endsWith(".log") ||
            fileName.endsWith(".json") ||
            fileName.endsWith(".xml")

private fun getFormattedSizeForPreview(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

private fun getFormattedDateForPreview(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}