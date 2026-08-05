package com.aviansh.aifilemanager.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aviansh.aifilemanager.domain.agent.ExecutionState
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.ui.components.ExecutionTimeline
import com.aviansh.aifilemanager.ui.vm.FileManagerEvent
import com.aviansh.aifilemanager.ui.vm.FileManagerViewModel
import kotlinx.coroutines.launch

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

    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is FileManagerEvent.FileDeleted -> snackbarHostState.showSnackbar("${event.fileName} deleted")
                is FileManagerEvent.FileRenamed -> snackbarHostState.showSnackbar("Renamed: ${event.oldName} → ${event.newName}")
                is FileManagerEvent.TransactionComplete -> snackbarHostState.showSnackbar("✅ ${event.actionCount} operation(s) completed")
                is FileManagerEvent.Error -> snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Long)
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        topBar = {
            TopAppBar(
                title = { Text("AI File Manager") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
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
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
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

                // Input field
                Surface(tonalElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .windowInsetsPadding(WindowInsets.ime),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("What would you like to do?") },
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
                            enabled = promptText.isNotBlank() && (executionState is ExecutionState.Idle || executionState is ExecutionState.Completed || executionState is ExecutionState.Failed)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
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
                onNavigate = { viewModel.navigateToDirectory(it) },
                onSelect = { viewModel.selectFile(it) },
                onNavigateUp = { viewModel.navigateUp(context) },
                onDelete = { fileItem ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar("Delete ${fileItem.name}?", actionLabel = "Delete", duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.deleteFile(fileItem)
                    }
                },
                onRename = { fileItem, newName -> viewModel.renameFile(fileItem, newName) },
                onRetry = { viewModel.loadFiles(uiState.currentPath) },
                getFormattedSize = { fileRepository.formatFileSize(it) },
                getFormattedDate = { fileRepository.formatLastModified(it) }
            )

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (sheetState.bottomSheetState.isVisible) {
                            sheetState.bottomSheetState.hide()
                        } else {
                            sheetState.bottomSheetState.expand()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(16.dp)
                    .align(androidx.compose.ui.Alignment.BottomEnd)
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Agent Chat")
            }
        }
    }
}
