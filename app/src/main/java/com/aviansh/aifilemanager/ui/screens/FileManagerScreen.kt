package com.aviansh.aifilemanager.ui.screens

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.ui.vm.FileManagerEvent
import com.aviansh.aifilemanager.ui.vm.FileManagerViewModel
import kotlinx.coroutines.launch

/**
 * Main File Manager Screen with:
 * - File/folder listing with delete/rename operations
 * - Navigation (up/down directory tree)
 * - Bottom sheet AI chat interface
 * - Error handling and snackbar feedback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    context: Context,
    viewModel: FileManagerViewModel = hiltViewModel(),
    onPermissionDenied: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState(null)

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showChatSheet by remember { mutableStateOf(false) }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // FileRepository for formatting utilities
    val fileRepository = FileRepository(context)

    // Handle events (errors, success messages)
    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is FileManagerEvent.FileDeleted -> {
                    snackbarHostState.showSnackbar("${event.fileName} deleted")
                }

                is FileManagerEvent.FileRenamed -> {
                    snackbarHostState.showSnackbar("Renamed: ${event.oldName} → ${event.newName}")
                }

                is FileManagerEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Long)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Manager") },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = { showChatSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.bottomPadding(if (showChatSheet) 0.dp else 16.dp)
            ) {
                Icon(Icons.Default.Chat, contentDescription = "AI Chat")
            }
        },

        snackbarHost = { SnackbarHost(snackbarHostState) },

        modifier = Modifier.fillMaxSize()

    ) { padding ->
        FileListScreen(
            files = uiState.files,
            currentPath = uiState.currentPath,
            isLoading = uiState.isLoading,
            error = uiState.error,
            selectedFile = uiState.selectedFile,

            onNavigate = { fileItem ->
                viewModel.navigateToDirectory(fileItem)
            },

            onSelect = { fileItem ->
                viewModel.selectFile(fileItem)
            },

            onNavigateUp = {
                viewModel.navigateUp()
            },

            onDelete = { fileItem ->
                scope.launch {
                    // Show confirmation dialog
                    val result = snackbarHostState.showSnackbar(
                        message = "Delete ${fileItem.name}?",
                        actionLabel = "Delete",
                        duration = SnackbarDuration.Long
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.deleteFile(fileItem)
                    }
                }
            },

            onRename = { fileItem, newName ->
                viewModel.renameFile(fileItem, newName)
            },

            onRetry = {
                viewModel.loadFiles(uiState.currentPath)
            },

            getFormattedSize = { bytes ->
                fileRepository.formatFileSize(bytes)
            },

            getFormattedDate = { millis ->
                fileRepository.formatLastModified(millis)
            },

            modifier = Modifier.padding(padding)
        )
    }

    // Bottom Sheet: AI Chat
    if (showChatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatSheet = false },
            sheetState = sheetState,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f),
        ) {
            AIChatBottomSheet(
                messages = uiState.chatMessages,
                isLoading = uiState.isChatLoading,
                chatError = uiState.chatError,

                onSendMessage = { messageText ->
                    viewModel.sendChatMessage(messageText)
                },

                onClearChat = {
                    viewModel.clearChat()
                },

                sheetState = sheetState
            )
        }
    }
}

// Extension function for bottom padding in Modifier
fun Modifier.bottomPadding(value: androidx.compose.ui.unit.Dp) = this.then(
    androidx.compose.foundation.layout.padding(bottom = value)
)