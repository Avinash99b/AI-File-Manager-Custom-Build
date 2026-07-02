package com.aviansh.aifilemanager.ui.vm

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.ai.LLMGenerationResponse
import com.aviansh.aifilemanager.domain.data.ParsedAIResponse
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.engines.AIOrchestrationEngine
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.GeminiModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── Events ───────────────────────────────────────────────────────────────────

sealed class FileManagerEvent {
    data class FileDeleted(val fileName: String) : FileManagerEvent()
    data class FileRenamed(val oldName: String, val newName: String) : FileManagerEvent()
    data class TransactionComplete(val actionCount: Int) : FileManagerEvent()
    data class Error(val message: String) : FileManagerEvent()
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class FileManagerUIState(
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val error: String? = null,
    val selectedFile: FileItem? = null,

    // Chat
    val chatMessages: List<ChatLmMessage> = emptyList(),
    val isChatLoading: Boolean = false,
    val chatError: String? = null,

    // Transaction progress shown in the UI
    val transactionProgress: TransactionProgress = TransactionProgress.Idle,

    // Non-null when AI has proposed actions and we're waiting for user confirmation
    val pendingActions: List<FileAction>? = null
)


@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    val geminiRepository: GeminiModelRepository
) : ViewModel() {

    private val tag = "FileManagerVM"

    private val _uiState = MutableStateFlow(FileManagerUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events = _events.asSharedFlow()

    private val orchestrationEngine = AIOrchestrationEngine()

    init {
        loadFiles(_uiState.value.currentPath)
    }

    fun loadFiles(dirPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, currentPath = dirPath) }
            fileRepository.listFiles(dirPath)
                .onSuccess { files ->
                    _uiState.update { it.copy(files = files, isLoading = false) }
                    Log.d(tag, "Loaded ${files.size} files from $dirPath")
                }
                .onFailure { e ->
                    val msg = e.message ?: "Failed to load files"
                    Log.e(tag, msg, e)
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
                    Log.d(tag, "Deleted: ${fileItem.name}")
                    _events.emit(FileManagerEvent.FileDeleted(fileItem.name))
                    loadFiles(_uiState.value.currentPath)
                }
                .onFailure { e ->
                    val msg = "Failed to delete: ${e.message}"
                    Log.e(tag, msg, e)
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
                    Log.d(tag, "Renamed: ${fileItem.name} → $newName")
                    _events.emit(FileManagerEvent.FileRenamed(fileItem.name, newName))
                    loadFiles(_uiState.value.currentPath)
                }
                .onFailure { e ->
                    val msg = "Failed to rename: ${e.message}"
                    Log.e(tag, msg, e)
                    _events.emit(FileManagerEvent.Error(msg))
                }
        }
    }

    fun sendChatMessage(messageText: String) {
        if (messageText.isBlank()) return

        val userMessage = ChatLmMessage(ChatLmRole.USER, messageText)
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMessage, isChatLoading = true) }

        viewModelScope.launch(Dispatchers.Default) {
            val systemPrompt = buildSystemPrompt()
            val context = _uiState.value.chatMessages.takeLast(4)

            val aiProvider = geminiRepository.getProvider()

            if (aiProvider == null) {
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + ChatLmMessage(ChatLmRole.SYSTEM, "AI Provider not setup"),
                        isChatLoading = false,
                        chatError = "AI Provider not setup"
                    )
                }
                return@launch
            }

            val rawResponse = aiProvider.generate(
                prompt = messageText,
                systemPrompt = systemPrompt,
                conversation = context
            )

            if (rawResponse is LLMGenerationResponse.FAILURE) {
                val errMsg = ChatLmMessage(ChatLmRole.ASSISTANT, "Error: ${rawResponse.error}")
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + errMsg,
                        isChatLoading = false,
                        chatError = rawResponse.error
                    )
                }
                return@launch
            }

            val aiResponse = tryParseAIResponse((rawResponse as LLMGenerationResponse.SUCCESS).message)

            if (aiResponse != null && aiResponse.actionable) {
                handleActionableResponse(aiResponse)
            } else {
                var generatedOutput = ""
                if (aiResponse?.generatorCode != null) {
                    generatedOutput = try {
                        PythonEngine.generateMessage(aiResponse.generatorCode)
                    } catch (e: Exception) {
                        Log.e(tag, "Python code execution failed", e)
                        ""
                    }
                }

                val assistantMessage = ChatLmMessage(
                    ChatLmRole.ASSISTANT,
                    aiResponse?.message ?: rawResponse.message
                )

                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + assistantMessage,
                        isChatLoading = false,
                        chatError = null
                    )
                }

                if (generatedOutput.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            chatMessages = it.chatMessages + ChatLmMessage(
                                role = ChatLmRole.TOOL,
                                content = generatedOutput
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleActionableResponse(aiResponse: ParsedAIResponse) {
        orchestrationEngine.executeAIResponse(aiResponse).collect { progress ->
            when (progress) {
                is TransactionProgress.Pending -> {
                    val preview = buildActionSummary(progress.actions)
                    val proposalMessage = ChatLmMessage(
                        role = ChatLmRole.ASSISTANT,
                        content = (aiResponse.message ?: "I'll make these changes:") + "\n\n$preview"
                    )
                    _uiState.update {
                        it.copy(
                            chatMessages = it.chatMessages + proposalMessage,
                            isChatLoading = false,
                            pendingActions = progress.actions,
                            chatError = null
                        )
                    }
                }

                is TransactionProgress.Failed -> {
                    val errMsg = ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                        "⚠️ Couldn't prepare actions: ${progress.reason}"
                    )
                    _uiState.update {
                        it.copy(
                            chatMessages = it.chatMessages + errMsg,
                            isChatLoading = false,
                            chatError = progress.reason
                        )
                    }
                }

                else -> { /* Idle — nothing actionable */ }
            }
        }
    }

    fun confirmPendingActions() {
        val actions = _uiState.value.pendingActions ?: return
        _uiState.update { it.copy(pendingActions = null, transactionProgress = TransactionProgress.Running) }

        viewModelScope.launch(Dispatchers.IO) {
            val progress = orchestrationEngine.executeConfirmedActions(actions)
            _uiState.update { it.copy(transactionProgress = progress) }

            when (progress) {
                is TransactionProgress.Succeeded -> {
                    _events.emit(FileManagerEvent.TransactionComplete(progress.actionCount))
                    loadFiles(_uiState.value.currentPath)
                    val doneMsg = ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                        "✅ Done — ${progress.actionCount} operation(s) completed successfully."
                    )
                    _uiState.update { it.copy(chatMessages = it.chatMessages + doneMsg) }
                }
                is TransactionProgress.RolledBack -> {
                    val rollbackMsg = ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                        "↩️ Something went wrong — all changes were rolled back.\nReason: ${progress.reason}"
                    )
                    _uiState.update { it.copy(chatMessages = it.chatMessages + rollbackMsg) }
                    _events.emit(FileManagerEvent.Error(progress.reason))
                }
                is TransactionProgress.Failed -> {
                    val failMsg = ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                        "❌ Transaction failed: ${progress.reason}"
                    )
                    _uiState.update { it.copy(chatMessages = it.chatMessages + failMsg) }
                    _events.emit(FileManagerEvent.Error(progress.reason))
                }
                else -> {}
            }
        }
    }

    fun cancelPendingActions() {
        _uiState.update { it.copy(pendingActions = null) }
        val cancelMsg = ChatLmMessage(ChatLmRole.ASSISTANT, "Cancelled — no changes were made.")
        _uiState.update { it.copy(chatMessages = it.chatMessages + cancelMsg) }
    }

    fun clearTransactionProgress() {
        _uiState.update { it.copy(transactionProgress = TransactionProgress.Idle) }
    }

    fun clearChat() {
        _uiState.update { it.copy(chatMessages = emptyList(), chatError = null, pendingActions = null) }
    }

    private fun buildSystemPrompt(): String = """
        You are an AI-powered Android file manager assistant.

        CURRENT CONTEXT
        Current directory: ${_uiState.value.currentPath}
        Visible files (first 20): ${_uiState.value.files.take(20).joinToString(", ") { it.name }}
        Working root: /storage/emulated/0

        ────────────────────────────────────────────────────────
        RESPONSE FORMAT

        Respond with a single valid JSON object only. No markdown, no code
        fences, no text outside the JSON.

        Schema:
        {
          "actionable": boolean,
          "generatorCode": "python string | null",
          "message": "short user-facing summary"
        }

        - actionable = true  -> generatorCode's generate() returns a list of
          file-action objects (move/copy/delete/create) that the app executes
          via TransactionEngine.
        - actionable = false -> generatorCode (if present) returns report or
          text data (search results, file contents, storage stats). It is
          NOT executed as file actions -- its return value is appended to the
          chat message for the user to read.
        - If nothing needs to run, set generatorCode to null.

        ────────────────────────────────────────────────────────
        PATH SAFETY (applies to every generatorCode you write)

        1. Never hardcode a path that appeared anywhere in the chat -- user
           message or your own prior message. Chat-provided paths are
           unverified; always rediscover them at runtime with os/glob/pathlib.
        2. Every path you act on (move, copy, delete, overwrite, read) must
           be checked with os.path.exists() at runtime before use.
        3. The only paths you may inline directly are:
           - Fixed Android roots: /sdcard/, /storage/emulated/0/,
             /data/user/0/ (root-only)
           - Paths a PREVIOUS generatorCode already scanned and returned in
             this session (not paths typed in chat text)
        4. If a request can't be safely resolved to a runtime-discoverable
           path, ask the user to clarify instead of guessing.
        5. Start every generatorCode by calling
           os.chdir("/storage/emulated/0") before navigating to
           ${_uiState.value.currentPath} or any other target directory.

        ────────────────────────────────────────────────────────
        PYTHON CODE RULES

        - Exactly one function: generate()
        - Return JSON-serialisable data only
        - Standard library only, no stdin, no print for control flow
        - No network access, EXCEPT when the user explicitly asks to
          download something -- in that case use urllib.request (stdlib) to
          fetch into the app cache dir (${AppPaths.cacheDir}) first, then
          move the result to the user's requested destination as part of the
          same action list
        - For batch/bulk requests ("delete all screenshots", "move every
          PDF"), scan with glob/os.walk to build the full target list --
          don't ask the user to enumerate files one by one
        - To read/view a file's contents, use actionable:false and have
          generate() open the file (after an exists check) and return its
          text. There is no separate "read" action type -- the returned text
          is shown directly in the chat message.

        ────────────────────────────────────────────────────────
        ACTION SCHEMA (when actionable = true)

        {
          "action": "move | copy | delete | create",
          "source": "/absolute/path",
          "destination": "/absolute/path or null",
          "overwrite": false,
          "comment": "optional description"
        }

        ────────────────────────────────────────────────────────
        EXAMPLES

        Batch delete (safe -- runtime scan):
        {"actionable":true,"generatorCode":"def generate():\n    import os, glob\n    files = glob.glob('/sdcard/Download/*.zip')\n    return [{'action':'delete','source':p,'destination':None,'overwrite':False,'comment':'zip file'} for p in files if os.path.exists(p)]","message":"Scanning Downloads and deleting all ZIP files."}

        Search / report (non-actionable):
        {"actionable":false,"generatorCode":"def generate():\n    import os\n    found = []\n    for r,_,files in os.walk('/sdcard'):\n        for f in files:\n            if f.lower().endswith('.pdf'):\n                found.append(os.path.join(r,f))\n    return found","message":"Searching for PDF files on your device."}

        Read a file's contents:
        {"actionable":false,"generatorCode":"def generate():\n    import os\n    path = '/sdcard/Download/notes.txt'\n    if not os.path.exists(path):\n        return 'File not found.'\n    with open(path, 'r', errors='replace') as f:\n        return f.read()","message":"Reading notes.txt."}

        Simple conversational reply:
        {"actionable":false,"generatorCode":null,"message":"Hello! How can I help you manage your files?"}
    """.trimIndent()

    private fun tryParseAIResponse(raw: String): ParsedAIResponse? {
        return try {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val obj = org.json.JSONObject(trimmed)
            ParsedAIResponse(
                actionable = obj.optBoolean("actionable", false),
                generatorCode = obj.optString("generatorCode", null)?.takeIf { it != "null" },
                message = obj.optString("message", null)
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse AI response", e)
            null
        }
    }

    private fun buildActionSummary(actions: List<FileAction>): String = buildString {
        actions.forEachIndexed { i, action ->
            appendLine(
                "${i + 1}. ${action.type.name.lowercase().replaceFirstChar { it.uppercase() }}: " +
                        action.sourcePath +
                        (action.destinationPath?.let { " → $it" } ?: "")
            )
        }
    }.trimEnd()
}