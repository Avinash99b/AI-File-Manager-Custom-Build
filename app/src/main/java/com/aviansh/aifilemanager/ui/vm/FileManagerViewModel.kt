package com.aviansh.aifilemanager.ui.vm

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.agent.*
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.AiProviderRepository
import com.aviansh.aifilemanager.domain.transactions.PlanBinding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC
}

enum class FilterOption {
    ALL,
    FOLDERS,
    DOCUMENTS,
    IMAGES,
    AUDIO,
    VIDEO,
    ARCHIVES
}

data class ClipboardState(
    val sourcePaths: List<String>,
    val isCut: Boolean
)

sealed class FileManagerEvent {
    data class FileDeleted(val fileName: String) : FileManagerEvent()
    data class FileRenamed(val oldName: String, val newName: String) : FileManagerEvent()
    data class FilesCopied(val count: Int) : FileManagerEvent()
    data class FilesMoved(val count: Int) : FileManagerEvent()
    data class FolderCreated(val folderName: String) : FileManagerEvent()
    data class TransactionComplete(val actionCount: Int) : FileManagerEvent()
    data class Error(val message: String) : FileManagerEvent()
}

data class FileManagerUIState(
    val files: List<FileItem> = emptyList(),
    val rawFiles: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val error: String? = null,
    val selectedFile: FileItem? = null,
    val selectedPaths: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME_ASC,
    val filterOption: FilterOption = FilterOption.ALL,
    val clipboard: ClipboardState? = null
)

private fun <T> MutableList<T>.trimToLast(maxSize: Int) {
    if (size > maxSize) {
        subList(0, size - maxSize).clear()
    }
}

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val aiProviderRepository: AiProviderRepository
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

    private val chatHistory = mutableListOf<ChatLmMessage>()

    companion object {
        private const val MAX_CHAT_HISTORY = 20
    }

    init {
        loadPersistedTimeline()
        loadFiles(_uiState.value.currentPath)
    }

    private fun serializeFileAction(action: FileAction): JSONObject {
        return JSONObject().apply {
            put("type", action.type.name)
            put("sourcePath", action.sourcePath)
            action.destinationPath?.let { put("destinationPath", it) }
            put("overwrite", action.overwrite)
            put("comment", action.comment)
        }
    }

    private fun deserializeFileAction(obj: JSONObject): FileAction {
        return FileAction(
            type = FileActionType.valueOf(obj.getString("type")),
            sourcePath = obj.getString("sourcePath"),
            destinationPath = if (obj.has("destinationPath") && !obj.isNull("destinationPath")) obj.getString("destinationPath") else null,
            overwrite = obj.optBoolean("overwrite", false),
            comment = obj.optString("comment", "")
        )
    }

    private fun serializeExecutionPlan(plan: ExecutionPlan): JSONObject {
        val json = JSONObject()
        json.put("explanation", plan.explanation)
        val actionsArray = JSONArray()
        plan.actions.forEach { actionsArray.put(serializeFileAction(it)) }
        json.put("actions", actionsArray)
        return json
    }

    private fun deserializeExecutionPlan(obj: JSONObject): ExecutionPlan {
        val explanation = obj.optString("explanation", "")
        val actionsArray = obj.optJSONArray("actions") ?: JSONArray()
        val actions = mutableListOf<FileAction>()
        for (i in 0 until actionsArray.length()) {
            actions.add(deserializeFileAction(actionsArray.getJSONObject(i)))
        }
        return ExecutionPlan(actions = actions, explanation = explanation)
    }

    private fun serializeRepairPlan(repairPlan: RepairPlan): JSONObject {
        val json = JSONObject()
        json.put("explanation", repairPlan.explanation)
        val failedArray = JSONArray()
        repairPlan.failedActions.forEach { failedArray.put(serializeFileAction(it)) }
        json.put("failedActions", failedArray)
        val fixesArray = JSONArray()
        repairPlan.proposedFixes.forEach { fixesArray.put(serializeFileAction(it)) }
        json.put("proposedFixes", fixesArray)
        return json
    }

    private fun deserializeRepairPlan(obj: JSONObject): RepairPlan {
        val explanation = obj.optString("explanation", "")
        val failedArray = obj.optJSONArray("failedActions") ?: JSONArray()
        val failedActions = mutableListOf<FileAction>()
        for (i in 0 until failedArray.length()) {
            failedActions.add(deserializeFileAction(failedArray.getJSONObject(i)))
        }
        val fixesArray = obj.optJSONArray("proposedFixes") ?: JSONArray()
        val proposedFixes = mutableListOf<FileAction>()
        for (i in 0 until fixesArray.length()) {
            proposedFixes.add(deserializeFileAction(fixesArray.getJSONObject(i)))
        }
        return RepairPlan(
            failedActions = failedActions,
            proposedFixes = proposedFixes,
            explanation = explanation
        )
    }

    private fun loadPersistedTimeline() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = AppPaths.filesDir.ifBlank { System.getProperty("java.io.tmpdir") ?: "/tmp" }
                val cacheFile = File(cacheDir, "timeline_cache.json")
                if (cacheFile.exists()) {
                    val jsonStr = cacheFile.readText(Charsets.UTF_8)
                    val jsonArray = JSONArray(jsonStr)
                    val loadedEvents = mutableListOf<TimelineEvent>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        when (obj.optString("type")) {
                            "UserPrompt" -> loadedEvents.add(TimelineEvent.UserPrompt(obj.getString("text")))
                            "AgentThought" -> loadedEvents.add(TimelineEvent.AgentThought(obj.getString("text")))
                            "ToolCall" -> loadedEvents.add(
                                TimelineEvent.ToolCall(
                                    obj.getString("toolName"),
                                    obj.getString("args"),
                                    if (obj.has("result") && !obj.isNull("result")) obj.getString("result") else null,
                                    if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null
                                )
                            )
                            "ExecutionLog" -> loadedEvents.add(
                                TimelineEvent.ExecutionLog(
                                    obj.getString("message"),
                                    obj.optBoolean("isError", false)
                                )
                            )
                            "SystemMessage" -> loadedEvents.add(TimelineEvent.SystemMessage(obj.getString("message")))
                            "ProposedPlan" -> {
                                val planObj = obj.getJSONObject("plan")
                                val plan = deserializeExecutionPlan(planObj)
                                loadedEvents.add(TimelineEvent.ProposedPlan(plan))
                            }
                            "ProposedRepair" -> {
                                val repairObj = obj.getJSONObject("repairPlan")
                                val repairPlan = deserializeRepairPlan(repairObj)
                                loadedEvents.add(TimelineEvent.ProposedRepair(repairPlan))
                            }
                        }
                    }
                    if (loadedEvents.isNotEmpty()) {
                        _timeline.value = loadedEvents
                        val lastEvent = loadedEvents.lastOrNull()
                        if (lastEvent is TimelineEvent.ProposedPlan) {
                            _executionState.value = ExecutionState.WaitingForApproval(lastEvent.plan)
                        } else if (lastEvent is TimelineEvent.ProposedRepair) {
                            _executionState.value = ExecutionState.WaitingForRepairApproval(lastEvent.repairPlan)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load timeline cache", e)
            }
        }
    }

    private fun persistTimeline(events: List<TimelineEvent>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = AppPaths.filesDir.ifBlank { System.getProperty("java.io.tmpdir") ?: "/tmp" }
                val cacheFile = File(cacheDir, "timeline_cache.json")
                val jsonArray = JSONArray()
                events.forEach { event ->
                    val obj = JSONObject()
                    when (event) {
                        is TimelineEvent.UserPrompt -> {
                            obj.put("type", "UserPrompt")
                            obj.put("text", event.text)
                        }
                        is TimelineEvent.AgentThought -> {
                            obj.put("type", "AgentThought")
                            obj.put("text", event.text)
                        }
                        is TimelineEvent.ToolCall -> {
                            obj.put("type", "ToolCall")
                            obj.put("toolName", event.toolName)
                            obj.put("args", event.args)
                            event.result?.let { obj.put("result", it) }
                            event.error?.let { obj.put("error", it) }
                        }
                        is TimelineEvent.ExecutionLog -> {
                            obj.put("type", "ExecutionLog")
                            obj.put("message", event.message)
                            obj.put("isError", event.isError)
                        }
                        is TimelineEvent.SystemMessage -> {
                            obj.put("type", "SystemMessage")
                            obj.put("message", event.message)
                        }
                        is TimelineEvent.ProposedPlan -> {
                            obj.put("type", "ProposedPlan")
                            obj.put("plan", serializeExecutionPlan(event.plan))
                        }
                        is TimelineEvent.ProposedRepair -> {
                            obj.put("type", "ProposedRepair")
                            obj.put("repairPlan", serializeRepairPlan(event.repairPlan))
                        }
                    }
                    if (obj.length() > 0) jsonArray.put(obj)
                }
                cacheFile.writeText(jsonArray.toString(), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist timeline cache", e)
            }
        }
    }

    fun loadFiles(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, currentPath = dirPath) }
            fileRepository.listFiles(dirPath)
                .onSuccess { raw ->
                    _uiState.update { state ->
                        val processed = applyFilterAndSort(raw, state.searchQuery, state.sortOption, state.filterOption)
                        state.copy(files = processed, rawFiles = raw, isLoading = false)
                    }
                }
                .onFailure { e ->
                    val msg = e.message ?: "Failed to load files"
                    _uiState.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    private fun applyFilterAndSort(
        rawFiles: List<FileItem>,
        query: String,
        sort: SortOption,
        filter: FilterOption
    ): List<FileItem> {
        // 1. Filter by category
        val categoryFiltered = when (filter) {
            FilterOption.ALL -> rawFiles
            FilterOption.FOLDERS -> rawFiles.filter { it.isDirectory }
            FilterOption.DOCUMENTS -> rawFiles.filter {
                !it.isDirectory && (it.name.endsWith(".pdf", ignoreCase = true) ||
                        it.name.endsWith(".txt", ignoreCase = true) ||
                        it.name.endsWith(".docx", ignoreCase = true) ||
                        it.name.endsWith(".doc", ignoreCase = true) ||
                        it.name.endsWith(".md", ignoreCase = true) ||
                        it.name.endsWith(".json", ignoreCase = true))
            }
            FilterOption.IMAGES -> rawFiles.filter {
                !it.isDirectory && (it.name.endsWith(".jpg", ignoreCase = true) ||
                        it.name.endsWith(".jpeg", ignoreCase = true) ||
                        it.name.endsWith(".png", ignoreCase = true) ||
                        it.name.endsWith(".webp", ignoreCase = true) ||
                        it.name.endsWith(".gif", ignoreCase = true))
            }
            FilterOption.AUDIO -> rawFiles.filter {
                !it.isDirectory && (it.name.endsWith(".mp3", ignoreCase = true) ||
                        it.name.endsWith(".wav", ignoreCase = true) ||
                        it.name.endsWith(".m4a", ignoreCase = true))
            }
            FilterOption.VIDEO -> rawFiles.filter {
                !it.isDirectory && (it.name.endsWith(".mp4", ignoreCase = true) ||
                        it.name.endsWith(".mkv", ignoreCase = true) ||
                        it.name.endsWith(".avi", ignoreCase = true))
            }
            FilterOption.ARCHIVES -> rawFiles.filter {
                !it.isDirectory && (it.name.endsWith(".zip", ignoreCase = true) ||
                        it.name.endsWith(".rar", ignoreCase = true) ||
                        it.name.endsWith(".7z", ignoreCase = true))
            }
        }

        // 2. Search query filter
        val searchFiltered = if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { it.name.contains(query, ignoreCase = true) }
        }

        // 3. Sort
        return when (sort) {
            SortOption.NAME_ASC -> searchFiltered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortOption.NAME_DESC -> searchFiltered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortOption.DATE_DESC -> searchFiltered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortOption.DATE_ASC -> searchFiltered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified })
            SortOption.SIZE_DESC -> searchFiltered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
            SortOption.SIZE_ASC -> searchFiltered.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.size })
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val processed = applyFilterAndSort(state.rawFiles, query, state.sortOption, state.filterOption)
            val visiblePaths = processed.map { it.path }.toSet()
            val pruned = state.selectedPaths.filter { it in visiblePaths }.toSet()
            state.copy(
                searchQuery = query,
                files = processed,
                selectedPaths = pruned,
                isSelectionMode = pruned.isNotEmpty()
            )
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { state ->
            val processed = applyFilterAndSort(state.rawFiles, state.searchQuery, sort, state.filterOption)
            state.copy(sortOption = sort, files = processed)
        }
    }

    fun setFilterOption(filter: FilterOption) {
        _uiState.update { state ->
            val processed = applyFilterAndSort(state.rawFiles, state.searchQuery, state.sortOption, filter)
            val visiblePaths = processed.map { it.path }.toSet()
            val pruned = state.selectedPaths.filter { it in visiblePaths }.toSet()
            state.copy(
                filterOption = filter,
                files = processed,
                selectedPaths = pruned,
                isSelectionMode = pruned.isNotEmpty()
            )
        }
    }

    fun navigateToDirectory(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            clearSelection()
            loadFiles(fileItem.path)
        }
    }

    fun navigateToPath(path: String) {
        clearSelection()
        loadFiles(path)
    }

    fun navigateUp(context: Context) {
        clearSelection()
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

    // --- Multi-Select Methods ---

    fun toggleFileSelection(filePath: String) {
        _uiState.update { state ->
            val newSelected = state.selectedPaths.toMutableSet()
            if (newSelected.contains(filePath)) {
                newSelected.remove(filePath)
            } else {
                newSelected.add(filePath)
            }
            state.copy(
                selectedPaths = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun selectAllFiles() {
        _uiState.update { state ->
            val allPaths = state.files.map { it.path }.toSet()
            state.copy(selectedPaths = allPaths, isSelectionMode = allPaths.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet(), isSelectionMode = false, selectedFile = null) }
    }

    // --- Manual Action Methods ---

    fun deleteSelectedFiles() {
        val targets = _uiState.value.selectedPaths.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            for (path in targets) {
                val file = File(path)
                val res = fileRepository.deleteFile(path, recursive = file.isDirectory)
                if (res.isSuccess) deletedCount++
            }
            _events.emit(FileManagerEvent.FileDeleted("$deletedCount item(s)"))
            clearSelection()
            loadFiles(_uiState.value.currentPath)
        }
    }

    fun copySelectedFiles(isCut: Boolean) {
        val targets = _uiState.value.selectedPaths.toList()
        if (targets.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                clipboard = ClipboardState(targets, isCut),
                selectedPaths = emptySet(),
                isSelectionMode = false
            )
        }
        viewModelScope.launch {
            if (isCut) {
                _events.emit(FileManagerEvent.FilesMoved(targets.size))
            } else {
                _events.emit(FileManagerEvent.FilesCopied(targets.size))
            }
        }
    }

    fun pasteFiles() {
        val clip = _uiState.value.clipboard ?: return
        val currentPath = _uiState.value.currentPath

        viewModelScope.launch(Dispatchers.IO) {
            val res = fileRepository.copyOrMoveFiles(clip.sourcePaths, currentPath, clip.isCut)
            res.onSuccess { count ->
                if (clip.isCut) {
                    _events.emit(FileManagerEvent.FilesMoved(count))
                    _uiState.update { it.copy(clipboard = null) }
                } else {
                    _events.emit(FileManagerEvent.FilesCopied(count))
                }
                loadFiles(currentPath)
            }.onFailure { e ->
                _events.emit(FileManagerEvent.Error("Failed to paste: ${e.message}"))
            }
        }
    }

    fun createFolder(folderName: String) {
        val currentPath = _uiState.value.currentPath
        viewModelScope.launch(Dispatchers.IO) {
            fileRepository.createFolder(currentPath, folderName)
                .onSuccess {
                    _events.emit(FileManagerEvent.FolderCreated(folderName))
                    loadFiles(currentPath)
                }
                .onFailure { e ->
                    _events.emit(FileManagerEvent.Error("Failed to create folder: ${e.message}"))
                }
        }
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
            val contextualPrompt = "[ACTIVE DIRECTORY CONTEXT: ${_uiState.value.currentPath}]\n$prompt"
            _timeline.update { old ->
                val updated = old + TimelineEvent.UserPrompt(prompt)
                persistTimeline(updated)
                updated
            }
            _executionState.value = ExecutionState.Planning

            val provider = aiProviderRepository.getProvider()
            if (provider == null) {
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog(
                        "AI provider not configured. Open the menu in the top bar and set up Gemini or an OpenAI compatible endpoint.",
                        true
                    )
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Failed("Provider not configured")
                return@launch
            }

            val agentEngine = AgentEngine(provider)
            val workspacePath = workspaceEngine.setupWorkspace(emptyList())
            currentWorkspacePath = workspacePath

            val result = agentEngine.processPrompt(contextualPrompt, workspacePath, chatHistory) { event ->
                _timeline.update { old ->
                    val updated = old + event
                    persistTimeline(updated)
                    updated
                }
            }

            result.onSuccess { plan ->
                val explanation = plan?.explanation ?: "No action needed."
                chatHistory.add(ChatLmMessage(ChatLmRole.USER, prompt))
                chatHistory.add(ChatLmMessage(ChatLmRole.ASSISTANT, explanation))
                chatHistory.trimToLast(MAX_CHAT_HISTORY)

                if (plan != null && plan.actions.isNotEmpty()) {
                    _timeline.update { old ->
                        val updated = old + TimelineEvent.ProposedPlan(plan)
                        persistTimeline(updated)
                        updated
                    }
                    _executionState.value = ExecutionState.WaitingForApproval(plan)
                } else {
                    _timeline.update { old ->
                        val updated = old + TimelineEvent.SystemMessage(explanation)
                        persistTimeline(updated)
                        updated
                    }
                    _executionState.value = ExecutionState.Completed
                    workspaceEngine.cleanupWorkspace(workspacePath)
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                chatHistory.add(ChatLmMessage(ChatLmRole.USER, prompt))
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog("Planning failed: ${e.message}", true)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Failed(e.message ?: "Unknown error")
                workspaceEngine.cleanupWorkspace(workspacePath)
            }
        }
    }

    fun onApprovePlan(plan: ExecutionPlan) {
        val currentState = _executionState.value
        if (currentState !is ExecutionState.WaitingForApproval) {
            viewModelScope.launch {
                _events.emit(FileManagerEvent.Error("Invalid state for approval."))
            }
            return
        }

        val expectedHash = PlanBinding(currentState.plan.actions, currentState.plan.explanation).planHash
        val approvedHash = PlanBinding(plan.actions, plan.explanation).planHash

        if (expectedHash != approvedHash) {
            viewModelScope.launch {
                val msg = "Plan hash mismatch: cannot approve a modified or stale plan."
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog(msg, isError = true)
                    persistTimeline(updated)
                    updated
                }
                _events.emit(FileManagerEvent.Error(msg))
                _executionState.value = ExecutionState.Failed(msg)
            }
            return
        }

        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _executionState.value = ExecutionState.Executing
            _timeline.update { old ->
                val updated = old + TimelineEvent.SystemMessage("Executing plan...")
                persistTimeline(updated)
                updated
            }

            val result = workspaceEngine.commitWorkspace(plan.actions)
            result.onSuccess {
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog("Plan executed successfully.", false)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Completed
                _events.emit(FileManagerEvent.TransactionComplete(plan.actions.size))
                loadFiles(_uiState.value.currentPath)

                currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
                currentWorkspacePath = null
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog("Execution failed: ${e.message}", true)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Verifying
                generateRepairPlan(plan, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun generateRepairPlan(failedPlan: ExecutionPlan, errorLog: String) {
        _timeline.update { old ->
            val updated = old + TimelineEvent.SystemMessage("Generating repair plan...")
            persistTimeline(updated)
            updated
        }
        val provider = aiProviderRepository.getProvider()
        if (provider == null) {
            _executionState.value = ExecutionState.Failed("Provider not configured for repair.")
            return
        }
        val agentEngine = AgentEngine(provider)
        val repairResult = agentEngine.verifyAndRepair(failedPlan.actions, errorLog, currentWorkspacePath ?: "")

        repairResult.onSuccess { repairPlan ->
            if (repairPlan != null) {
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ProposedRepair(repairPlan)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.WaitingForRepairApproval(repairPlan)
            } else {
                _timeline.update { old ->
                    val updated = old + TimelineEvent.SystemMessage("Could not generate a repair plan.")
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Failed("Irreparable failure.")
                onHardStop()
            }
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            _timeline.update { ev ->
                val updated = ev + TimelineEvent.ExecutionLog("Repair planning failed: ${it.message}", true)
                persistTimeline(updated)
                updated
            }
            _executionState.value = ExecutionState.Failed("Repair planning failed.")
            onHardStop()
        }
    }

    fun onApproveRepairPlan(repairPlan: RepairPlan) {
        val currentState = _executionState.value
        if (currentState !is ExecutionState.WaitingForRepairApproval) {
            viewModelScope.launch {
                _events.emit(FileManagerEvent.Error("Invalid state for repair approval."))
            }
            return
        }

        val expectedHash = PlanBinding(currentState.repairPlan.proposedFixes, currentState.repairPlan.explanation).planHash
        val approvedHash = PlanBinding(repairPlan.proposedFixes, repairPlan.explanation).planHash

        if (expectedHash != approvedHash) {
            viewModelScope.launch {
                val msg = "Repair plan hash mismatch: cannot approve a modified or stale repair plan."
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog(msg, isError = true)
                    persistTimeline(updated)
                    updated
                }
                _events.emit(FileManagerEvent.Error(msg))
                _executionState.value = ExecutionState.Failed(msg)
            }
            return
        }

        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _executionState.value = ExecutionState.Executing
            _timeline.update { old ->
                val updated = old + TimelineEvent.SystemMessage("Executing repair plan...")
                persistTimeline(updated)
                updated
            }

            val result = workspaceEngine.commitWorkspace(repairPlan.proposedFixes)
            result.onSuccess {
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog("Repair executed successfully.", false)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Completed
                _events.emit(FileManagerEvent.TransactionComplete(repairPlan.proposedFixes.size))
                loadFiles(_uiState.value.currentPath)

                currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
                currentWorkspacePath = null
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _timeline.update { old ->
                    val updated = old + TimelineEvent.ExecutionLog("Repair execution failed: ${e.message}", true)
                    persistTimeline(updated)
                    updated
                }
                _executionState.value = ExecutionState.Failed("Repair failed: ${e.message}")
                onHardStop()
            }
        }
    }

    fun onSoftStop() {
        currentAgentJob?.cancel()
        viewModelScope.launch {
            _timeline.update { old ->
                val updated = old + TimelineEvent.SystemMessage("Soft stop requested. Discarding workspace.")
                persistTimeline(updated)
                updated
            }
            currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
            currentWorkspacePath = null
            _executionState.value = ExecutionState.Idle
            loadFiles(_uiState.value.currentPath)
        }
    }

    fun onHardStop() {
        currentAgentJob?.cancel()
        viewModelScope.launch {
            _timeline.update { old ->
                val updated = old + TimelineEvent.SystemMessage("Hard stop requested. Aborting immediately.")
                persistTimeline(updated)
                updated
            }
            currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
            currentWorkspacePath = null
            _executionState.value = ExecutionState.Idle
        }
    }

    fun onClearSession() {
        currentAgentJob?.cancel()
        viewModelScope.launch {
            currentWorkspacePath?.let { workspaceEngine.cleanupWorkspace(it) }
            currentWorkspacePath = null
            _timeline.value = emptyList()
            persistTimeline(emptyList())
            chatHistory.clear()
            _executionState.value = ExecutionState.Idle
        }
    }
}
