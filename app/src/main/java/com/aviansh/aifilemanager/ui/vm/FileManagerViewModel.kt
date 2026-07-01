package com.aviansh.aifilemanager.ui.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.data.AIResponse
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.engines.AIOrchestrationEngine
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.chaquo.python.Python
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
    val currentPath: String = "/sdcard/",
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

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val tag = "FileManagerVM"

    private val _uiState = MutableStateFlow(FileManagerUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events = _events.asSharedFlow()

    private val aiProvider = GeminiAIProvider("")
    private val orchestrationEngine = AIOrchestrationEngine()

    init {
        loadFiles(_uiState.value.currentPath)
    }

    // ─── File browsing ────────────────────────────────────────────────────────

    fun loadFiles(dirPath: String) {
        viewModelScope.launch {
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
        if (fileItem.isDirectory) loadFiles(fileItem.path)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        val parent = File(current).parent ?: current
        if (parent != current) loadFiles(parent)
    }

    fun selectFile(fileItem: FileItem?) {
        _uiState.update { it.copy(selectedFile = fileItem) }
    }

    // ─── Direct file operations (non-AI) ─────────────────────────────────────

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    // ─── AI Chat ──────────────────────────────────────────────────────────────

    /**
     * Sends a chat message to Gemini, then:
     *  - If the response is conversational → appends a chat bubble.
     *  - If the response is actionable → parses generator code via Chaquopy,
     *    surfaces the pending action list to the UI for user confirmation.
     */
    fun sendChatMessage(messageText: String) {
        if (messageText.isBlank()) return

        val userMessage = ChatLmMessage(ChatLmRole.USER, messageText)
        _uiState.update { it.copy(chatMessages = it.chatMessages + userMessage, isChatLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val systemPrompt = buildSystemPrompt()
            val context = _uiState.value.chatMessages
                .takeLast(4)
                .map { if (it.isUser) "User" to it.content else "Assistant" to it.content }

            val rawResponse = aiProvider.sendMessage(
                prompt = messageText,
                systemPrompt = systemPrompt,
                conversationContext = context
            )

            if (!rawResponse.isSuccess) {
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

            // Try to parse as AIResponse (structured); fall back to plain chat bubble
            val aiResponse = tryParseAIResponse(rawResponse.message)

            if (aiResponse != null && aiResponse.actionable) {
                handleActionableResponse(aiResponse)
            } else {
                var generatedOutput: String=""
                if(aiResponse?.generatorCode!=null){
                    generatedOutput = PythonEngine.generateMessage(aiResponse.generatorCode)
                }
                // Plain conversational reply
                val assistantMessage = ChatLmMessage(
                    ChatLmRole.ASSISTANT,
                    aiResponse?.message ?: rawResponse.message
                )

                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + assistantMessage + ChatLmMessage(role = ChatLmRole.TOOL, content = generatedOutput),
                        isChatLoading = false,
                        chatError = null
                    )
                }
            }
        }
    }

    /**
     * Called when the AI response is actionable:
     * runs the generator code via Chaquopy and shows the pending action list.
     */
    private suspend fun handleActionableResponse(aiResponse: AIResponse) {
        orchestrationEngine.executeAIResponse(aiResponse).collect { progress ->
            when (progress) {
                is TransactionProgress.Pending -> {
                    val preview = buildActionSummary(progress.actions)
                    val proposalMessage = ChatLmMessage(
                        role = ChatLmRole.ASSISTANT,
                        content = (aiResponse.message ?: "I'll make these changes:") + "\n\n$preview",
                        pendingActions = progress.actions
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

    // ─── Transaction confirmation flow ────────────────────────────────────────

    /**
     * Called when the user taps "Confirm" on the pending action card.
     */
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

    /**
     * Called when the user taps "Cancel" on the pending action card.
     */
    fun cancelPendingActions() {
        _uiState.update { it.copy(pendingActions = null) }
        val cancelMsg = ChatLmMessage(ChatLmRole.ASSISTANT, "Cancelled — no changes were made.")
        _uiState.update { it.copy(chatMessages = it.chatMessages + cancelMsg) }
    }

    fun clearTransactionProgress() {
        _uiState.update { it.copy(transactionProgress = TransactionProgress.Idle) }
    }

    // ─── Chat helpers ─────────────────────────────────────────────────────────

    fun clearChat() {
        _uiState.update { it.copy(chatMessages = emptyList(), chatError = null, pendingActions = null) }
    }

    private fun buildSystemPrompt(): String = """
        You are an AI-powered Android file manager assistant.

        CURRENT CONTEXT
        Current directory: {currentPath}
        Visible files (first 20): {visibleFiles}

        ────────────────────────────────────────────────────────
        RESPONSE FORMAT

        Your response MUST always be a single valid JSON object.
        Never output markdown, explanations, or code fences.

        Schema:
        {
          "actionable": boolean,
          "generatorCode": "python string | null",
          "message": "short user-facing summary"
        }

        ────────────────────────────────────────────────────────
        CRITICAL: PATH SAFETY RULES (read before every response)

        1. NEVER hardcode a path that was mentioned in the chat conversation.
           File paths from previous messages are UNVERIFIED. Always rediscover
           them at runtime using Python's os / glob / pathlib.

        2. For ANY destructive action (delete, move, overwrite):
           - generatorCode MUST scan the filesystem at runtime to build
             the list of paths to act on.
           - The generated Python must verify each path exists with
             os.path.exists() before including it in the returned list.
           - NEVER build an action list from a path string the user typed
             or that appeared in a prior assistant message.

        3. The only paths you may inline into generatorCode are:
           - Well-known Android roots that always exist:
             /sdcard/, /storage/emulated/0/, /data/user/0/ (root only)
           - Paths explicitly confirmed by a PREVIOUS generatorCode scan
             returned in this same session (not from chat text).

        4. If you cannot safely discover paths at runtime, ask the user
           to confirm the exact location before proceeding.

        ────────────────────────────────────────────────────────
        PYTHON CODE RULES

        - Define exactly one function: generate()
        - generate() must return JSON-serialisable data
        - Never print, read stdin, or use the network
        - Use only the Python standard library
        - Always check os.path.exists(path) before acting on a path

        ────────────────────────────────────────────────────────
        WHEN actionable = true

        generate() must return a list of file-action objects.

        Action schema:
        {
          "action": "move | copy | delete | create",
          "source": "/absolute/path",
          "destination": "/absolute/path or null",
          "overwrite": false,
          "comment": "optional description"
        }

        CORRECT pattern for delete based on a user request:

          def generate():
              import os, glob
              # Rediscover files at runtime — never trust paths from chat
              targets = glob.glob('/sdcard/Download/*.zip', recursive=False)
              return [
                  {"action": "delete", "source": p,
                   "destination": None, "overwrite": False,
                   "comment": f"Deleting {os.path.basename(p)}"}
                  for p in targets if os.path.exists(p)
              ]

        WRONG — do not do this:

          def generate():
              # BAD: path was copied from the conversation, not verified
              return [{"action": "delete",
                       "source": "/sdcard/Download/debug-apk.zip", ...}]

        ────────────────────────────────────────────────────────
        WHEN actionable = false

        Use generatorCode only if a filesystem scan is needed:
          • Find / search files
          • Filter by extension, size, or date
          • List duplicates
          • Calculate directory sizes
          • Generate storage reports

        generate() should return report data, NOT file actions.

        If no scan is needed, set generatorCode to null and return
        only a conversational message.

        ────────────────────────────────────────────────────────
        EXAMPLES

        Delete all ZIPs in Downloads (safe — runtime scan):
        {"actionable":true,"generatorCode":"def generate():\n    import os,glob\n    files=glob.glob('/sdcard/Download/*.zip')\n    return [{'action':'delete','source':p,'destination':None,'overwrite':False,'comment':'zip file'} for p in files if os.path.exists(p)]","message":"Scanning Downloads and deleting all ZIP files."}

        Find all PDFs on device:
        {"actionable":false,"generatorCode":"def generate():\n    import os\n    found=[]\n    for r,_,files in os.walk('/sdcard'):\n        for f in files:\n            if f.lower().endswith('.pdf'):\n                found.append(os.path.join(r,f))\n    return found","message":"Searching for PDF files on your device."}

        Simple question:
        {"actionable":false,"generatorCode":null,"message":"Hello! How can I help you manage your files?"}
    """.trimIndent()

    /**
     * Attempts to parse the model output as a structured [AIResponse].
     * Returns null if the text is not JSON or doesn't match the expected shape.
     */
    private fun tryParseAIResponse(raw: String): AIResponse? {
        return try {
            val trimmed = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = org.json.JSONObject(trimmed)
            AIResponse(
                actionable = obj.optBoolean("actionable", false),
                generatorCode = if (obj.has("generatorCode")) obj.getString("generatorCode") else null,
                message = if (obj.has("message")) obj.getString("message") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildActionSummary(actions: List<FileAction>): String = buildString {
        actions.forEachIndexed { i, action ->
            appendLine("${i + 1}. ${action.type.name.lowercase().replaceFirstChar { it.uppercase() }}: " +
                    action.sourcePath +
                    (action.destinationPath?.let { " → $it" } ?: ""))
        }
    }.trimEnd()
}
