package com.aviansh.aifilemanager.ui.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class FileManagerEvent {
    data class FileDeleted(val fileName: String) : FileManagerEvent()
    data class FileRenamed(val oldName: String, val newName: String) : FileManagerEvent()
    data class Error(val message: String) : FileManagerEvent()
}

data class FileManagerUIState(
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val currentPath: String = "/sdcard/Download",
    val error: String? = null,
    val selectedFile: FileItem? = null,
    val chatMessages: List<ChatLmMessage> = emptyList(),
    val isChatLoading: Boolean = false,
    val chatError: String? = null
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val aiProvider: GeminiAIProvider
) : ViewModel() {

    private val tag = "FileManagerVM"

    private val _uiState = MutableStateFlow(FileManagerUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileManagerEvent>()
    val events = _events.asSharedFlow()

    init {
        loadFiles(_uiState.value.currentPath)
    }

    /**
     * Load files from the current directory.
     */
    fun loadFiles(dirPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPath = dirPath
            )

            fileRepository.listFiles(dirPath).onSuccess { files ->
                _uiState.value = _uiState.value.copy(
                    files = files,
                    isLoading = false,
                    error = null
                )
                Log.d(tag, "Loaded ${files.size} files from $dirPath")

            }.onFailure { exception ->
                val errorMsg = exception.message ?: "Failed to load files"
                Log.e(tag, errorMsg, exception)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            }
        }
    }

    /**
     * Navigate to a directory if the selected item is a folder.
     */
    fun navigateToDirectory(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            loadFiles(fileItem.path)
        }
    }

    /**
     * Navigate up one level.
     */
    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        val parentPath = File(currentPath).parent ?: currentPath

        if (parentPath != currentPath) {
            loadFiles(parentPath)
        }
    }

    /**
     * Delete a file with confirmation flow.
     */
    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
            fileRepository.deleteFile(
                filePath = fileItem.path,
                recursive = fileItem.isDirectory
            ).onSuccess {
                Log.d(tag, "Deleted: ${fileItem.name}")
                _events.emit(FileManagerEvent.FileDeleted(fileItem.name))
                loadFiles(_uiState.value.currentPath)

            }.onFailure { exception ->
                val errorMsg = "Failed to delete: ${exception.message}"
                Log.e(tag, errorMsg, exception)
                _events.emit(FileManagerEvent.Error(errorMsg))
                _uiState.value = _uiState.value.copy(error = errorMsg)
            }
        }
    }

    /**
     * Rename a file.
     */
    fun renameFile(fileItem: FileItem, newName: String) {
        if (newName.isBlank()) {
            val msg = "Name cannot be empty"
            viewModelScope.launch {
                _events.emit(FileManagerEvent.Error(msg))
            }
            return
        }

        viewModelScope.launch {
            fileRepository.renameFile(
                filePath = fileItem.path,
                newName = newName
            ).onSuccess { newPath ->
                Log.d(tag, "Renamed: ${fileItem.name} -> $newName")
                _events.emit(FileManagerEvent.FileRenamed(fileItem.name, newName))
                loadFiles(_uiState.value.currentPath)

            }.onFailure { exception ->
                val errorMsg = "Failed to rename: ${exception.message}"
                Log.e(tag, errorMsg, exception)
                _events.emit(FileManagerEvent.Error(errorMsg))
            }
        }
    }

    /**
     * Select a file for details/operations.
     */
    fun selectFile(fileItem: FileItem?) {
        _uiState.value = _uiState.value.copy(selectedFile = fileItem)
    }

    /**
     * Send a message to AI chat and get a response.
     */
    fun sendChatMessage(messageText: String) {
        if (messageText.isBlank()) return

        // Add user message to chat
        val userMessage = ChatLmMessage(
            ChatLmRole.USER,
             messageText
        )

        _uiState.value = _uiState.value.copy(
            chatMessages = _uiState.value.chatMessages + userMessage,
            isChatLoading = true
        )

        viewModelScope.launch {
            // Build system prompt with context
            val systemPrompt = buildString {
                appendLine("You are an AI file manager assistant.")
                appendLine("Help users manage their files and folders.")
                appendLine("Current directory: ${_uiState.value.currentPath}")
                appendLine("Available files: ${_uiState.value.files.take(5).joinToString(", ") { it.name }}")
                appendLine("Be concise and practical in your responses.")
            }

            // Build conversation context from recent messages
            val context = _uiState.value.chatMessages
                .takeLast(4) // Keep last 4 messages for context
                .map { msg ->
                    if (msg.isUser) "User" to msg.content
                    else "Assistant" to msg.content
                }

            aiProvider.sendMessage(
                prompt = messageText,
                systemPrompt = systemPrompt,
                conversationContext = context
            ).let { response ->
                val assistantMessage = if (response.isSuccess) {
                    ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                       response.message
                    )
                } else {
                    ChatLmMessage(
                        ChatLmRole.ASSISTANT,
                        "Error: ${response.error}"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + assistantMessage,
                    isChatLoading = false,
                    chatError = if (response.isSuccess) null else response.error
                )

                Log.d(tag, "Chat response received: ${response.isSuccess}")
            }
        }
    }

    /**
     * Clear chat history.
     */
    fun clearChat() {
        _uiState.value = _uiState.value.copy(
            chatMessages = emptyList(),
            chatError = null
        )
    }

    /**
     * Get AI suggestion for a file operation.
     */
    fun getAISuggestion(fileItem: FileItem, operation: String) {
        viewModelScope.launch {
            val response = aiProvider.suggestFileOperation(
                fileName = fileItem.name,
                fileType = fileItem.mimeType ?: "unknown",
                currentFilePath = fileItem.path,
                operation = operation
            )

            if (response.isSuccess) {
                val suggestion = ChatLmMessage(
                    ChatLmRole.ASSISTANT,
                    response.message
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + suggestion
                )
            } else {
                Log.e(tag, "AI suggestion failed: ${response.error}")
            }
        }
    }
}
