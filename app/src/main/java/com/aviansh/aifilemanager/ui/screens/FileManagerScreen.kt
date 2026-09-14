package com.aviansh.aifilemanager.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aviansh.aifilemanager.domain.agent.ExecutionState
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.ui.components.ExecutionTimeline
import com.aviansh.aifilemanager.ui.vm.FileManagerEvent
import com.aviansh.aifilemanager.ui.vm.FileManagerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    context: Context,
    viewModel: FileManagerViewModel = hiltViewModel(),
    onPermissionDenied: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val executionState by viewModel.executionState.collectAsState()
    val events by viewModel.events.collectAsState(null)

    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fileRepository = FileRepository(context)

    var promptText by remember { mutableStateOf("") }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is FileManagerEvent.FileDeleted -> snackbarHostState.showSnackbar("${event.fileName} deleted")
                is FileManagerEvent.FileRenamed -> snackbarHostState.showSnackbar("Renamed: ${event.oldName} → ${event.newName}")
                is FileManagerEvent.FilesCopied -> snackbarHostState.showSnackbar("Copied ${event.count} item(s) to clipboard")
                is FileManagerEvent.FilesMoved -> snackbarHostState.showSnackbar("Cut ${event.count} item(s) to clipboard")
                is FileManagerEvent.FolderCreated -> snackbarHostState.showSnackbar("Created folder: ${event.folderName}")
                is FileManagerEvent.TransactionComplete -> snackbarHostState.showSnackbar("✅ ${event.actionCount} operation(s) completed")
                is FileManagerEvent.Error -> snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Long)
            }
        }
    }

    fun shareFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            val authority = "${context.packageName}.fileprovider"
            val uris = paths.map { FileProvider.getUriForFile(context, authority, File(it)) }
            val intent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    type = "*/*"
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    type = "*/*"
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share file(s)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Sharing failed: ${e.message}") }
        }
    }

    @Composable
    fun AiCommandSurface(modifier: Modifier = Modifier) {
        val quickPrompts = listOf(
            "📁 Organize files by type",
            "🔍 Find duplicate files",
            "📊 Find largest files",
            "✏️ Rename with pattern",
            "📝 Create folder summary"
        )
        val promptScrollState = rememberScrollState()

        Column(modifier = modifier) {
            ExecutionTimeline(
                timeline = timeline,
                executionState = executionState,
                onApprovePlan = {
                    if (executionState is ExecutionState.WaitingForApproval) {
                        viewModel.onApprovePlan((executionState as ExecutionState.WaitingForApproval).plan)
                    }
                },
                onSoftStop = { viewModel.onSoftStop() },
                onHardStop = { viewModel.onHardStop() },
                onApproveRepairPlan = {
                    if (executionState is ExecutionState.WaitingForRepairApproval) {
                        viewModel.onApproveRepairPlan((executionState as ExecutionState.WaitingForRepairApproval).repairPlan)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Surface(tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .windowInsetsPadding(WindowInsets.ime)
                ) {
                    // Directory Context Chip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Context: ${uiState.currentPath.split("/").takeLast(2).joinToString("/")}",
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }

                    // Horizontally Scrollable Quick Prompt Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(promptScrollState)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickPrompts.forEach { chipText ->
                            SuggestionChip(
                                onClick = {
                                    promptText = chipText
                                    viewModel.onSubmitPrompt(chipText)
                                    promptText = ""
                                },
                                label = { Text(chipText, fontSize = 12.sp) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }

                    // Input Field & Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.onClearSession() },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Session", tint = MaterialTheme.colorScheme.error)
                        }
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask AI to manage files...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (promptText.isNotBlank()) {
                                    viewModel.onSubmitPrompt(promptText)
                                    promptText = ""
                                }
                            }),
                            enabled = executionState is ExecutionState.Idle || executionState is ExecutionState.Completed || executionState is ExecutionState.Failed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (promptText.isNotBlank()) {
                                    viewModel.onSubmitPrompt(promptText)
                                    promptText = ""
                                }
                            },
                            enabled = promptText.isNotBlank() && (executionState is ExecutionState.Idle || executionState is ExecutionState.Completed || executionState is ExecutionState.Failed),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLargeScreen = maxWidth >= 720.dp

        if (isLargeScreen) {
            // Responsive 2-column split-screen layout for tablets / desktop / wide screens
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("AI File Manager") },
                        actions = {
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Left Pane: File Browser
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        FileListScreen(
                            files = uiState.files,
                            currentPath = uiState.currentPath,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            selectedFile = uiState.selectedFile,
                            selectedPaths = uiState.selectedPaths,
                            isSelectionMode = uiState.isSelectionMode,
                            searchQuery = uiState.searchQuery,
                            sortOption = uiState.sortOption,
                            filterOption = uiState.filterOption,
                            hasClipboard = uiState.clipboard != null,
                            onNavigate = { viewModel.navigateToDirectory(it) },
                            onNavigateToPath = { viewModel.navigateToPath(it) },
                            onSelect = { viewModel.selectFile(it) },
                            onToggleSelect = { viewModel.toggleFileSelection(it.path) },
                            onSelectAll = { viewModel.selectAllFiles() },
                            onClearSelection = { viewModel.clearSelection() },
                            onNavigateUp = { viewModel.navigateUp(context) },
                            onDelete = { fileItem ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar("Delete ${fileItem.name}?", actionLabel = "Delete", duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed) viewModel.deleteFile(fileItem)
                                }
                            },
                            onDeleteSelected = { showBulkDeleteDialog = true },
                            onCopySelected = { isCut -> viewModel.copySelectedFiles(isCut) },
                            onPaste = { viewModel.pasteFiles() },
                            onShareSelected = { shareFiles(uiState.selectedPaths.toList()) },
                            onCreateFolder = { folderName -> viewModel.createFolder(folderName) },
                            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                            onSortChange = { viewModel.setSortOption(it) },
                            onFilterChange = { viewModel.setFilterOption(it) },
                            onRename = { fileItem, newName -> viewModel.renameFile(fileItem, newName) },
                            onRetry = { viewModel.loadFiles(uiState.currentPath) },
                            getFormattedSize = { fileRepository.formatFileSize(it) },
                            getFormattedDate = { fileRepository.formatLastModified(it) }
                        )
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Right Pane: AI Execution & Timeline Surface
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AiCommandSurface(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            // Standard smartphone layout with BottomSheetScaffold
            BottomSheetScaffold(
                scaffoldState = sheetState,
                topBar = {
                    TopAppBar(
                        title = { Text("AI File Manager") },
                        actions = {
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                sheetContent = {
                    AiCommandSurface(modifier = Modifier.fillMaxHeight(0.85f))
                },
                sheetPeekHeight = 0.dp
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    FileListScreen(
                        files = uiState.files,
                        currentPath = uiState.currentPath,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        selectedFile = uiState.selectedFile,
                        selectedPaths = uiState.selectedPaths,
                        isSelectionMode = uiState.isSelectionMode,
                        searchQuery = uiState.searchQuery,
                        sortOption = uiState.sortOption,
                        filterOption = uiState.filterOption,
                        hasClipboard = uiState.clipboard != null,
                        onNavigate = { viewModel.navigateToDirectory(it) },
                        onNavigateToPath = { viewModel.navigateToPath(it) },
                        onSelect = { viewModel.selectFile(it) },
                        onToggleSelect = { viewModel.toggleFileSelection(it.path) },
                        onSelectAll = { viewModel.selectAllFiles() },
                        onClearSelection = { viewModel.clearSelection() },
                        onNavigateUp = { viewModel.navigateUp(context) },
                        onDelete = { fileItem ->
                            scope.launch {
                                val result = snackbarHostState.showSnackbar("Delete ${fileItem.name}?", actionLabel = "Delete", duration = SnackbarDuration.Long)
                                if (result == SnackbarResult.ActionPerformed) viewModel.deleteFile(fileItem)
                            }
                        },
                        onDeleteSelected = { viewModel.deleteSelectedFiles() },
                        onCopySelected = { isCut -> viewModel.copySelectedFiles(isCut) },
                        onPaste = { viewModel.pasteFiles() },
                        onShareSelected = { shareFiles(uiState.selectedPaths.toList()) },
                        onCreateFolder = { folderName -> viewModel.createFolder(folderName) },
                        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                        onSortChange = { viewModel.setSortOption(it) },
                        onFilterChange = { viewModel.setFilterOption(it) },
                        onRename = { fileItem, newName -> viewModel.renameFile(fileItem, newName) },
                        onRetry = { viewModel.loadFiles(uiState.currentPath) },
                        getFormattedSize = { fileRepository.formatFileSize(it) },
                        getFormattedDate = { fileRepository.formatLastModified(it) }
                    )

                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (sheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
                                    sheetState.bottomSheetState.hide()
                                } else {
                                    sheetState.bottomSheetState.expand()
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(16.dp)
                            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Agent Chat Surface")
                    }
                }
            }
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete Selected Files") },
            text = { Text("Are you sure you want to delete ${uiState.selectedPaths.size} selected item(s)? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteDialog = false
                        viewModel.deleteSelectedFiles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteDialog = false },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
