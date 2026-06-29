package com.aviansh.aifilemanager.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.repository.FileItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Single file/folder list item with long-press menu.
 */
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
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                onSelect(fileItem)
                if (fileItem.isDirectory) {
                    onNavigate(fileItem)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon and Info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (fileItem.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (fileItem.isDirectory) Color(0xFFFFA500) else Color(0xFF6C63FF)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!fileItem.isDirectory) {
                            Text(
                                text = getFormattedSize(fileItem.size),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Text(
                            text = getFormattedDate(fileItem.lastModified),
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right: Options Menu
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        }
    }

    // Context menu dropdown
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = {
                showMenu = false
                showRenameDialog = true
            },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )

        if (fileItem.isDirectory) {
            DropdownMenuItem(
                text = { Text("Properties") },
                onClick = {
                    showMenu = false
                    // TODO: Show properties dialog
                },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }

        DropdownMenuItem(
            text = { Text("Delete", color = Color.Red) },
            onClick = {
                showMenu = false
                onDelete(fileItem)
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    placeholder = { Text("Enter new name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRename(fileItem, renameInput)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * List of all files and folders with navigation header.
 */
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
    getFormattedDate: (Long) -> String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with path navigation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate up"
                    )
                }

                Text(
                    text = currentPath,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Red
                    )

                    Text(
                        text = error,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }

            files.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files or folders", color = Color.Gray)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
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
}

/**
 * Bottom sheet with AI chat interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatBottomSheet(
    messages: List<ChatLmMessage>,
    isLoading: Boolean,
    chatError: String?,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
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
            .fillMaxHeight(0.7f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI File Assistant",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (messages.isNotEmpty()) {
                IconButton(onClick = onClearChat) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                }
            }
        }

        Divider()

        // Messages
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start a conversation...", color = Color.Gray)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for(message in messages) {
                        ChatMessageBubble(message)
                    }

                    if (isLoading) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI is thinking...", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    if (chatError != null) {
                        Text(
                            text = "⚠️ $chatError",
                            fontSize = 12.sp,
                            color = Color.Red,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        Divider()

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Ask AI...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                singleLine = false,
                shape = RoundedCornerShape(8.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            FilledIconButton(
                onClick = {
                    if (messageInput.isNotBlank()) {
                        onSendMessage(messageInput)
                        messageInput = ""
                    }
                },
                enabled = messageInput.isNotBlank() && !isLoading,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Individual chat message bubble.
 */
@Composable
fun ChatMessageBubble(message: ChatLmMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (message.isUser) 12.dp else 0.dp,
                        bottomEnd = if (message.isUser) 0.dp else 12.dp
                    )
                ),
            color = if (message.isUser) Color(0xFF6C63FF) else Color(0xFFF0F0F0),
            tonalElevation = if (message.isUser) 4.dp else 0.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = if (message.isUser) Color.White else Color.Black,
                maxLines = Int.MAX_VALUE
            )
        }
    }
}