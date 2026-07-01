package com.aviansh.aifilemanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.repository.FileItem

// ─── Palette ─────────────────────────────────────────────────────────────────
private val Purple       = Color(0xFF6C63FF)
private val PurpleLight  = Color(0xFFEDECFF)
private val Orange       = Color(0xFFFFA500)
private val SurfaceGray  = Color(0xFFF5F5F5)
private val TextGray     = Color(0xFF888888)
private val DangerRed    = Color(0xFFD32F2F)
private val SuccessGreen = Color(0xFF2E7D32)

// ─── File List Item ───────────────────────────────────────────────────────────

@Composable
fun FileListItem(
    fileItem: FileItem,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(fileItem.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                onSelect(fileItem)
                if (fileItem.isDirectory) onNavigate(fileItem)
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (fileItem.isDirectory) Color(0xFFFFF3E0) else PurpleLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fileItem.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (fileItem.isDirectory) Orange else Purple
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!fileItem.isDirectory) {
                            Text(getFormattedSize(fileItem.size), fontSize = 11.sp, color = TextGray)
                            Text("·", fontSize = 11.sp, color = TextGray)
                        }
                        Text(getFormattedDate(fileItem.lastModified), fontSize = 11.sp, color = TextGray, maxLines = 1)
                    }
                }
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextGray)
            }
        }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { showMenu = false; showRenameDialog = true },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        if (fileItem.isDirectory) {
            DropdownMenuItem(
                text = { Text("Properties") },
                onClick = { showMenu = false },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete", color = DangerRed) },
            onClick = { showMenu = false; onDelete(fileItem) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) onRename(fileItem, renameInput)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── File List Screen ─────────────────────────────────────────────────────────

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
    Column(modifier = modifier.fillMaxSize()) {
        // Path header
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceGray)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onNavigateUp, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                }
                Text(
                    text = currentPath,
                    fontSize = 12.sp,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Purple)
            }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), DangerRed)
                Text(error, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                    Text("Retry")
                }
            }
            files.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(48.dp), TextGray)
                    Text("Empty folder", color = TextGray, modifier = Modifier.padding(top = 8.dp))
                }
            }
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(files, key = { it.id }) { fileItem ->
                    FileListItem(
                        fileItem = fileItem,
                        onNavigate = onNavigate,
                        onSelect = onSelect,
                        onDelete = onDelete,
                        onRename = onRename,
                        getFormattedSize = getFormattedSize,
                        getFormattedDate = getFormattedDate
                    )
                }
            }
        }
    }
}

// ─── Transaction Progress Banner ─────────────────────────────────────────────

@Composable
fun TransactionProgressBanner(
    progress: TransactionProgress,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = progress !is TransactionProgress.Idle,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        val (icon, text, bg, fg) = when (progress) {
            is TransactionProgress.Running   -> quadOf(Icons.Default.HourglassTop,  "Running transaction…",                   Color(0xFFFFF8E1), Color(0xFFF57F17))
            is TransactionProgress.Succeeded -> quadOf(Icons.Default.CheckCircle,   "✓ ${progress.actionCount} op(s) done",   Color(0xFFE8F5E9), SuccessGreen)
            is TransactionProgress.RolledBack-> quadOf(Icons.Default.Undo,          "↩ Rolled back: ${progress.reason}",      Color(0xFFFFF3E0), Color(0xFFE65100))
            is TransactionProgress.Failed    -> quadOf(Icons.Default.Error,         "✗ ${progress.reason}",                   Color(0xFFFFEBEE), DangerRed)
            else -> return@AnimatedVisibility
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            color = bg,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (progress is TransactionProgress.Running) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
                } else {
                    Icon(icon, null, Modifier.size(20.dp), fg)
                }
                Text(text, fontSize = 13.sp, color = fg, modifier = Modifier.weight(1f))
                if (progress !is TransactionProgress.Running) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(16.dp), fg)
                    }
                }
            }
        }
    }
}

// ─── Pending Actions Card ─────────────────────────────────────────────────────

@Composable
fun PendingActionsCard(
    actions: List<FileAction>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleLight),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PlaylistAddCheck, null, tint = Purple, modifier = Modifier.size(20.dp))
                Text("Proposed operations", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Purple)
            }

            Spacer(Modifier.height(10.dp))

            actions.forEachIndexed { idx, action ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Purple.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = action.type.name.take(3),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Purple,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Column {
                        Text(action.sourcePath, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (action.destinationPath != null) {
                            Text(
                                "→ ${action.destinationPath}",
                                fontSize = 11.sp, color = TextGray,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Cancel", color = Purple) }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run ${actions.size} op${if (actions.size != 1) "s" else ""}")
                }
            }
        }
    }
}

// ─── AI Chat Bottom Sheet ─────────────────────────────────────────────────────

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
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color.White)
    ) {
        // Handle bar
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFDDDDDD)))
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(PurpleLight),
                    Alignment.Center
                ) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), Purple) }
                Text("AI File Assistant", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = onClearChat, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteSweep, "Clear", Modifier.size(18.dp), TextGray)
                }
            }
        }

        HorizontalDivider()

        // Transaction progress banner (inside the sheet)
        TransactionProgressBanner(transactionProgress, onDismissProgress)

        // Messages
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && pendingActions == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Chat, null, Modifier.size(40.dp), Color(0xFFCCCCCC))
                        Text("Ask me to manage your files", color = TextGray, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Purple, strokeWidth = 2.dp)
                            Text("Thinking…", fontSize = 12.sp, color = TextGray)
                        }
                    }

                    if (chatError != null) {
                        Text("⚠️ $chatError", fontSize = 12.sp, color = DangerRed, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        HorizontalDivider()

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Ask AI…", fontSize = 13.sp) },
                modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = 120.dp),
                singleLine = false,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple,
                    unfocusedBorderColor = Color(0xFFDDDDDD)
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
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple)
            ) {
                Icon(Icons.Default.Send, "Send", Modifier.size(18.dp))
            }
        }
    }
}

// ─── Chat Message Bubble ──────────────────────────────────────────────────────

@Composable
fun ChatMessageBubble(
    message: ChatLmMessage,
    pendingActions: List<FileAction>? = null,
    onConfirmActions: (() -> Unit)? = null,
    onCancelActions: (() -> Unit)? = null
) {
    val isActionProposal = message.pendingActions != null && message.pendingActions == pendingActions

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 290.dp).clip(
                    RoundedCornerShape(
                        topStart = 14.dp, topEnd = 14.dp,
                        bottomStart = if (message.isUser) 14.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 14.dp
                    )
                ),
                color = if (message.isUser) Purple else SurfaceGray
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(10.dp),
                    fontSize = 13.sp,
                    color = if (message.isUser) Color.White else Color.Black,
                    lineHeight = 18.sp
                )
            }
        }

        // Inline action confirmation card (shown below the proposal bubble)
        if (isActionProposal && onConfirmActions != null && onCancelActions != null) {
            Spacer(Modifier.height(4.dp))
            PendingActionsCard(
                actions = pendingActions!!,
                onConfirm = onConfirmActions,
                onCancel = onCancelActions
            )
        }
    }
}

// ─── Utility ──────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
private fun <A, B, C, D> quadOf(a: A, b: B, c: C, d: D) = Quad(a, b, c, d)