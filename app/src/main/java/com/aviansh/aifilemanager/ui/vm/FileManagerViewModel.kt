package com.aviansh.aifilemanager.ui.vm

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.agent.*
import com.aviansh.aifilemanager.domain.agent.tools.*
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.engines.TrashEngine
import com.aviansh.aifilemanager.domain.engines.TrashedItem
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.AiProviderRepository
import com.aviansh.aifilemanager.domain.security.FileAccessPolicy
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
    data class FileRestored(val fileName: String) : FileManagerEvent()
    data class TrashEmptied(val count: Int) : FileManagerEvent()
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

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState = _agentState.asStateFlow()

    private val _trashItems = MutableStateFlow<List<TrashedItem>>(emptyList())
    val trashItems = _trashItems.asStateFlow()

    private var currentAgentJob: kotlinx.coroutines.Job? = null
    private var currentEngine: AgentEngine? = null
    private var currentSession: AgentEngine.Session? = null

    private val chatHistory = mutableListOf<ChatLmMessage>()

    companion object {
        private const val MAX_CHAT_HISTORY = 20
    }

    init {
        loadPersistedTimeline()
        loadFiles(_uiState.value.currentPath)
    }

    private fun loadPersistedTimeline() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = timelineCacheFile()
                if (!file.exists()) return@launch

                val jsonArray = JSONArray(file.readText(Charsets.UTF_8))
                val loaded = mutableListOf<TimelineEvent>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    when (obj.optString("type")) {
                        "UserPrompt" -> loaded.add(TimelineEvent.UserPrompt(obj.getString("text")))
                        "AgentThought" -> loaded.add(TimelineEvent.AgentThought(obj.getString("text")))
                        "AgentAnswer" -> loaded.add(TimelineEvent.AgentAnswer(obj.getString("text")))
                        "ToolCall" -> loaded.add(
                            TimelineEvent.ToolCall(
                                obj.getString("toolName"),
                                obj.optString("preview"),
                                obj.optString("result").takeIf { it.isNotBlank() && !obj.isNull("result") },
                                obj.optString("error").takeIf { it.isNotBlank() && !obj.isNull("error") }
                            )
                        )
                        "ExecutionLog" -> loaded.add(
                            TimelineEvent.ExecutionLog(
                                obj.getString("message"),
                                obj.optBoolean("isError", false)
                            )
                        )
                        "SystemMessage" -> loaded.add(TimelineEvent.SystemMessage(obj.getString("message")))
                        // A pending confirmation cannot be resumed after process death, because
                        // the agent's in-memory transcript is gone. Surface it as a note instead.
                        "ConfirmationRequest" -> loaded.add(
                            TimelineEvent.SystemMessage(
                                "Previous session ended while awaiting confirmation for: " +
                                    obj.optString("preview")
                            )
                        )
                    }
                }
                if (loaded.isNotEmpty()) _timeline.value = loaded
            } catch (e: Exception) {
                Log.e(tag, "Failed to load timeline cache", e)
            }
        }
    }

    private fun timelineCacheFile(): File {
        val dir = AppPaths.filesDir.ifBlank { System.getProperty("java.io.tmpdir") ?: "/tmp" }
        return File(dir, "timeline_cache.json")
    }

    private fun persistTimeline(events: List<TimelineEvent>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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
                        is TimelineEvent.AgentAnswer -> {
                            obj.put("type", "AgentAnswer")
                            obj.put("text", event.text)
                        }
                        is TimelineEvent.ToolCall -> {
                            obj.put("type", "ToolCall")
                            obj.put("toolName", event.toolName)
                            obj.put("preview", event.preview)
                            event.result?.let { obj.put("result", it) }
                            event.error?.let { obj.put("error", it) }
                        }
                        is TimelineEvent.ConfirmationRequest -> {
                            obj.put("type", "ConfirmationRequest")
                            obj.put("preview", event.action.preview)
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
                    }
                    jsonArray.put(obj)
                }
                timelineCacheFile().writeText(jsonArray.toString(), Charsets.UTF_8)
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

    // --- Agent ---

    fun onSubmitPrompt(prompt: String) {
        if (prompt.isBlank()) return
        if (_agentState.value is AgentState.Thinking ||
            _agentState.value is AgentState.Working ||
            _agentState.value is AgentState.AwaitingConfirmation
        ) return

        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            appendTimeline(TimelineEvent.UserPrompt(prompt))
            _agentState.value = AgentState.Thinking

            val provider = aiProviderRepository.getProvider()
            if (provider == null) {
                appendTimeline(
                    TimelineEvent.ExecutionLog(
                        "AI provider not configured. Open the menu in the top bar and set up " +
                            "Gemini or an OpenAI compatible endpoint.",
                        true
                    )
                )
                _agentState.value = AgentState.Failed("Provider not configured")
                return@launch
            }

            val engine = buildEngine(provider)
            currentEngine = engine

            val (session, outcome) = engine.start(
                userPrompt = prompt,
                currentDirectory = _uiState.value.currentPath,
                history = chatHistory.toList(),
                onEvent = ::onAgentEvent
            )
            currentSession = session
            handleOutcome(prompt, outcome)
        }
    }

    /** User approved or declined the pending destructive tool call. */
    fun onConfirmAction(approved: Boolean) {
        val engine = currentEngine
        val session = currentSession
        if (engine == null || session == null || _agentState.value !is AgentState.AwaitingConfirmation) {
            viewModelScope.launch { _events.emit(FileManagerEvent.Error("Nothing is awaiting confirmation.")) }
            return
        }

        currentAgentJob?.cancel()
        currentAgentJob = viewModelScope.launch {
            _agentState.value = AgentState.Thinking
            val outcome = engine.resume(
                session = session,
                currentDirectory = _uiState.value.currentPath,
                approved = approved,
                onEvent = ::onAgentEvent
            )
            handleOutcome(session.userPrompt, outcome)
        }
    }

    private fun buildEngine(provider: com.aviansh.aifilemanager.domain.ai.LLMProvider): AgentEngine {
        val policy = FileAccessPolicy()
        val scratch = File(
            AppPaths.cacheDir.ifBlank { System.getProperty("java.io.tmpdir") ?: "/tmp" },
            "agent_scratch"
        ).apply { mkdirs() }.absolutePath

        return AgentEngine(
            llmProvider = provider,
            toolRegistry = ToolRegistry(
                listOf(
                    ListDirectoryTool(policy),
                    ReadFileTool(policy),
                    FileInfoTool(policy),
                    SearchFilesTool(policy),
                    CreateFolderTool(policy),
                    WriteFileTool(policy),
                    CopyTool(policy),
                    MoveTool(policy),
                    DeleteTool(policy),
                    RunPythonTool(policy, scratch)
                )
            )
        )
    }

    private suspend fun onAgentEvent(event: TimelineEvent) {
        when (event) {
            is TimelineEvent.ToolCall -> {
                if (event.result == null && event.error == null) {
                    _agentState.value = AgentState.Working(event.toolName, event.preview)
                    appendTimeline(event)
                } else {
                    // Replace the in-flight entry with its finished form.
                    replaceLastToolCall(event)
                    if (event.error == null) refreshCurrentDirectory()
                }
            }

            is TimelineEvent.ConfirmationRequest -> {
                _agentState.value = AgentState.AwaitingConfirmation(event.action)
                appendTimeline(event)
            }

            else -> appendTimeline(event)
        }
    }

    private suspend fun handleOutcome(prompt: String, outcome: AgentOutcome) {
        when (outcome) {
            is AgentOutcome.Answer -> {
                chatHistory.add(ChatLmMessage(ChatLmRole.USER, prompt))
                chatHistory.add(ChatLmMessage(ChatLmRole.ASSISTANT, outcome.text))
                chatHistory.trimToLast(MAX_CHAT_HISTORY)
                _agentState.value = AgentState.Done(outcome.text)
                currentSession = null
                refreshCurrentDirectory()
            }

            is AgentOutcome.NeedsConfirmation -> {
                // State already set by the event callback; nothing more to do until the user acts.
                _agentState.value = AgentState.AwaitingConfirmation(outcome.action)
            }

            is AgentOutcome.Failed -> {
                appendTimeline(TimelineEvent.ExecutionLog(outcome.error, isError = true))
                _agentState.value = AgentState.Failed(outcome.error)
                currentSession = null
                refreshCurrentDirectory()
            }
        }
    }

    private fun refreshCurrentDirectory() {
        loadFiles(_uiState.value.currentPath)
    }

    private fun appendTimeline(event: TimelineEvent) {
        _timeline.update { old ->
            val updated = old + event
            persistTimeline(updated)
            updated
        }
    }

    private fun replaceLastToolCall(finished: TimelineEvent.ToolCall) {
        _timeline.update { old ->
            val index = old.indexOfLast {
                it is TimelineEvent.ToolCall && it.result == null && it.error == null &&
                    it.toolName == finished.toolName
            }
            val updated = if (index >= 0) {
                old.toMutableList().apply { set(index, finished) }
            } else {
                old + finished
            }
            persistTimeline(updated)
            updated
        }
    }

    // --- Trash ---

    fun loadTrash() {
        viewModelScope.launch {
            _trashItems.value = TrashEngine.list()
        }
    }

    fun restoreFromTrash(id: String) {
        viewModelScope.launch {
            TrashEngine.restore(id).fold(
                onSuccess = {
                    _events.emit(FileManagerEvent.FileRestored(it.name))
                    loadTrash()
                    refreshCurrentDirectory()
                },
                onFailure = { _events.emit(FileManagerEvent.Error(it.message ?: "Restore failed")) }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val count = TrashEngine.empty()
            _events.emit(FileManagerEvent.TrashEmptied(count))
            loadTrash()
        }
    }

    fun onStop() {
        currentAgentJob?.cancel()
        currentSession = null
        viewModelScope.launch {
            appendTimeline(TimelineEvent.SystemMessage("Stopped."))
            _agentState.value = AgentState.Idle
            refreshCurrentDirectory()
        }
    }

    fun onClearSession() {
        currentAgentJob?.cancel()
        currentSession = null
        viewModelScope.launch {
            _timeline.value = emptyList()
            persistTimeline(emptyList())
            chatHistory.clear()
            _agentState.value = AgentState.Idle
        }
    }
}
