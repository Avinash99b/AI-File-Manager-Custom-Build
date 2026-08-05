package com.aviansh.aifilemanager.ui.vm

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.agent.*
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.GeminiModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class FileManagerEvent {
    data class FileDeleted(val fileName: String) : FileManagerEvent()
    data class FileRenamed(val oldName: String, val newName: String) : FileManagerEvent()
    data class TransactionComplete(val actionCount: Int) : FileManagerEvent()
    data class Error(val message: String) : FileManagerEvent()
}

data class FileManagerUIState(
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val error: String? = null,
    val selectedFile: FileItem? = null
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val geminiRepository: GeminiModelRepository
) : ViewModel() {

    private val tag = "FileManagerVM"

    private val _uiState = MutableStateFlow(FileManagerUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events = _events.asSharedFlow()

    private val _timeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timeline = _timeline.asStateFlow()

    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState = _executionState.asStateFlow()

    private val workspaceEngine = WorkspaceEngine()
    private var currentWorkspacePath: String? = null

    private var currentAgentJob: kotlinx.coroutines.Job? = null

    // Simple chat history just to provide context to the agent
    private val chatHistory = mutableListOf<ChatLmMessage>()

    init {
        loadFiles(_uiState.value.currentPath)
    }

    fun loadFiles(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, currentPath = dirPath) }
            fileRepository.listFiles(dirPath)
                .onSuccess { files ->
                    _uiState.update { it.copy(files = files, isLoading = false) }
                }
                .onFailure { e ->
                    val msg = e.message ?: "Failed to load files"
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    fun navigateToDirectory(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            loadFiles(fileItem.path)
        }
    }

    fun navigateUp(context: Context) {
        val current = _uiState.value.currentPath
        val parent = File(current).parent ?: current

        if (File(parent).absolutePath == File("/storage/emulated/").absolutePath) {
            Toast.makeText(context, "Cannot go above this directory", Toast.LENGTH_SHORT).show()
            return
        }

        if (parent != current) {
            loadFiles(parent)
        }
    }

    fun selectFile(fileItem: FileItem?) {
        _uiState.update { it.copy(selectedFile = fileItem) }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.deleteFile(filePath = fileItem.path, recursive = fileItem.isDirectory)
                .onSuccess {
                    _events.emit(FileManagerEvent.FileDeleted(fileItem.name))
                    loadFiles(_uiState.value.currentPath)
                }
                .onFailure { e ->
                    val msg = "Failed to delete: ${e.message}"
                    _events.emit(FileManagerEvent.Error(msg))
                    _uiState.update { it.copy(error = msg) }
                }
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        if (newName.isBlank()) {
            viewModelScope.launch { _events.emit(FileManagerEvent.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.renameFile(filePath = fileItem.path, newName = newName)
                .onSuccess {
                    _events.emit(FileManagerEvent.FileRenamed(fileItem.name, newName))
                    loadFiles(_uiState.value.currentPath)
                }
                .onFailure { e ->
                    _events.emit(FileManagerEvent.Error("Failed to rename: ${e.message}"))
                }
        }
    }

    // --- Agent Actions ---

    fun onSubmitPrompt(prompt: String) {
        if (prompt.isBlank() || _executionState.value !is ExecutionState.Idle && _executionState.value !is ExecutionState.Completed && _executionState.value !is ExecutionState.Failed) return

        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _timeline.update { it + TimelineEvent.UserPrompt(prompt) }
            _executionState.value = ExecutionState.Planning

            val provider = geminiRepository.getProvider()
            if (provider == null) {
                _timeline.update { it + TimelineEvent.ExecutionLog("AI Provider not configured.", true) }
                _executionState.value = ExecutionState.Failed("Provider not configured")
                return@launch
            }

            val agentEngine = AgentEngine(provider)
            val workspacePath = workspaceEngine.setupWorkspace(emptyList())
            currentWorkspacePath = workspacePath

            val result = agentEngine.processPrompt(prompt, workspacePath, chatHistory) { event ->
                _timeline.update { it + event }
            }

            result.onSuccess { plan ->
                if (plan != null && plan.actions.isNotEmpty()) {
                    _timeline.update { it + TimelineEvent.ProposedPlan(plan) }
                    _executionState.value = ExecutionState.WaitingForApproval(plan)
                } else {
                    _timeline.update { it + TimelineEvent.SystemMessage(plan?.explanation ?: "No action needed.") }
                    _executionState.value = ExecutionState.Completed
                    workspaceEngine.cleanupWorkspace(workspacePath)
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _timeline.update { it + TimelineEvent.ExecutionLog("Planning failed: ${e.message}", true) }
                _executionState.value = ExecutionState.Failed(e.message ?: "Unknown error")
                workspaceEngine.cleanupWorkspace(workspacePath)
            }
        }
    }

    fun onApprovePlan(plan: ExecutionPlan) {
        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _executionState.value = ExecutionState.Executing
            _timeline.update { it + TimelineEvent.SystemMessage("Executing plan...") }

            val result = workspaceEngine.commitWorkspace(plan.actions)
            result.onSuccess {
                _timeline.update { it + TimelineEvent.ExecutionLog("Plan executed successfully.", false) }
                _executionState.value = ExecutionState.Completed
                _events.emit(FileManagerEvent.TransactionComplete(plan.actions.size))
                loadFiles(_uiState.value.currentPath)

                currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
                currentWorkspacePath = null
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _timeline.update { it + TimelineEvent.ExecutionLog("Execution failed: ${e.message}", true) }
                _executionState.value = ExecutionState.Verifying
                generateRepairPlan(plan, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun generateRepairPlan(failedPlan: ExecutionPlan, errorLog: String) {
        _timeline.update { it + TimelineEvent.SystemMessage("Generating repair plan...") }
        val provider = geminiRepository.getProvider()
        if (provider == null) {
            _executionState.value = ExecutionState.Failed("Provider not configured for repair.")
            return
        }
        val agentEngine = AgentEngine(provider)
        val repairResult = agentEngine.verifyAndRepair(failedPlan.actions, errorLog, currentWorkspacePath ?: "")

        repairResult.onSuccess { repairPlan ->
            if (repairPlan != null) {
                _timeline.update { it + TimelineEvent.ProposedRepair(repairPlan) }
                _executionState.value = ExecutionState.WaitingForRepairApproval(repairPlan)
            } else {
                _timeline.update { it + TimelineEvent.SystemMessage("Could not generate a repair plan.") }
                _executionState.value = ExecutionState.Failed("Irreparable failure.")
                onHardStop()
            }
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            _timeline.update { ev -> ev + TimelineEvent.ExecutionLog("Repair planning failed: ${it.message}", true) }
            _executionState.value = ExecutionState.Failed("Repair planning failed.")
            onHardStop()
        }
    }

    fun onApproveRepairPlan(repairPlan: RepairPlan) {
        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _executionState.value = ExecutionState.Executing
            _timeline.update { it + TimelineEvent.SystemMessage("Executing repair plan...") }

            val result = workspaceEngine.commitWorkspace(repairPlan.proposedFixes)
            result.onSuccess {
                _timeline.update { it + TimelineEvent.ExecutionLog("Repair executed successfully.", false) }
                _executionState.value = ExecutionState.Completed
                _events.emit(FileManagerEvent.TransactionComplete(repairPlan.proposedFixes.size))
                loadFiles(_uiState.value.currentPath)

                currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
                currentWorkspacePath = null
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _timeline.update { it + TimelineEvent.ExecutionLog("Repair execution failed: ${e.message}", true) }
                _executionState.value = ExecutionState.Failed("Repair failed: ${e.message}")
                onHardStop()
            }
        }
    }

    fun onSoftStop() {
        currentAgentJob?.cancel()
        viewModelScope.launch {
            _timeline.update { it + TimelineEvent.SystemMessage("Soft stop requested. Discarding workspace.") }
            currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
            currentWorkspacePath = null
            _executionState.value = ExecutionState.Idle
            loadFiles(_uiState.value.currentPath)
        }
    }

    fun onHardStop() {
        currentAgentJob?.cancel()
        viewModelScope.launch {
            _timeline.update { it + TimelineEvent.SystemMessage("Hard stop requested. Aborting immediately.") }
            currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
            currentWorkspacePath = null
            _executionState.value = ExecutionState.Idle
        }
    }
}
