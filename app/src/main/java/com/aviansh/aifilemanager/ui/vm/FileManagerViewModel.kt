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
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMessage,
                isChatLoading = true
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val systemPrompt = buildSystemPrompt()
            val context = _uiState.value.chatMessages.takeLast(4)

            val aiProvider = geminiRepository.getProvider()

            if (aiProvider == null) {
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + ChatLmMessage(
                            ChatLmRole.SYSTEM,
                            "AI Provider not setup"
                        ),
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

            val aiResponse =
                tryParseAIResponse((rawResponse as LLMGenerationResponse.SUCCESS).message)

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
                        content = (aiResponse.message
                            ?: "I'll make these changes:") + "\n\n$preview"
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

                else -> { /* Idle — nothing actionable */
                }
            }
        }
    }

    fun confirmPendingActions() {
        val actions = _uiState.value.pendingActions ?: return
        _uiState.update {
            it.copy(
                pendingActions = null,
                transactionProgress = TransactionProgress.Running
            )
        }

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
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                chatError = null,
                pendingActions = null
            )
        }
    }

    private fun buildSystemPrompt(): String = """
You are an AI-powered Android file manager assistant.

CURRENT CONTEXT
Current directory: ${_uiState.value.currentPath}
Visible files (first 20): ${_uiState.value.files.take(20).joinToString(", ") { it.name }}
Working root: /storage/emulated/0

────────────────────────────────────────────────────────
RESPONSE FORMAT

Respond with a single valid JSON object only.
Do not use markdown, code fences, explanations, or any text outside the JSON.

Schema:
{
  "actionable": boolean,
  "generatorCode": "python string | null",
  "message": "short user-facing summary"
}

Meaning:

- actionable = true
  generatorCode must define generate(), which returns a list of file
  action objects. The Android app will execute those actions through
  TransactionEngine.

- actionable = false
  generatorCode (if present) must define generate(), which returns
  JSON-serializable information (text, lists, dictionaries, numbers,
  etc.). The return value will be shown directly to the user and NO
  filesystem actions will be executed.

- If no code execution is needed:
  {
    "actionable": false,
    "generatorCode": null,
    "message": "..."
  }

────────────────────────────────────────────────────────
PATH SAFETY RULES

These rules apply to EVERY generatorCode you write.

1. Never hardcode a path that appeared anywhere in the chat conversation.
   User-provided paths are untrusted.

2. Always rediscover target files/directories at runtime using
   os.walk(), pathlib, glob, or similar techniques whenever possible.

3. Before reading, writing, moving, copying, deleting, or overwriting
   any path, verify it exists using os.path.exists().

4. The ONLY paths allowed to be hardcoded are:

   - /storage/emulated/0
   - /sdcard
   - /data/user/0 (root-only)

5. If the user's request cannot be resolved safely, ask for clarification
   instead of guessing.

6. Every generatorCode MUST begin with:

import os
os.chdir("/storage/emulated/0")

before navigating elsewhere.

────────────────────────────────────────────────────────
PYTHON CODE RULES

- Define exactly ONE function:

    def generate():

- Return JSON-serializable objects only.

- Never execute generate() yourself.

- No print() for control flow.

- No stdin.

- No subprocess.

- No eval() or exec().

- No shell commands.

- Prefer pathlib where appropriate.

- For filesystem traversal, prefer os.walk(), pathlib, or glob.

- For large directory scans, avoid unnecessary memory usage.

- If multiple files satisfy the request, automatically scan for all of
  them instead of asking the user one-by-one.

- When reading text files:
    open(..., errors="replace")

────────────────────────────────────────────────────────
AVAILABLE PYTHON LIBRARIES

The following libraries are already installed and available.

Standard Library
- All standard Python modules.

Scientific Computing
- numpy
- scipy
- pandas

Visualization
- matplotlib

Networking
- requests

Image Processing
- Pillow (PIL)

Documents
- openpyxl
- reportlab

HTML / XML
- beautifulsoup4 (bs4)
- lxml

Configuration Formats
- pyyaml
- toml

Graph Algorithms
- networkx

Mathematics
- sympy

Utilities
- regex
- tqdm
- rich
- python-dateutil
- pytz
- jmespath
- pathspec
- packaging

Guidelines:

- Use the standard library for simple filesystem operations.

- Use pandas for CSV or table processing.

- Use openpyxl for Excel files.

- Use Pillow for image metadata or manipulation.

- Use matplotlib when the user explicitly requests charts,
  graphs, or plots.

- Use networkx for graph analysis.

- Use sympy for symbolic mathematics.

- Import only the libraries actually needed.

────────────────────────────────────────────────────────
NETWORK ACCESS

Network access is DISALLOWED by default.

Only access the internet if the user explicitly requests one of:

- downloading files
- searching the web
- interacting with an online service
- calling an HTTP API

When internet access is permitted:

- Prefer requests.

- Download into:

${AppPaths.cacheDir}

first.

Then generate file actions which move the downloaded file to the
requested destination.

Never download directly into the user's folders.

Also if you are asked to generate any report files, first generate the reports into the cache dir and then make the generator code move that file into destination folder.
YOU DO NOT HAVE WRITE ACCESS TO ANY DIR EXCEPT CACHE DIR.
THE PYTHON CODE SHOULD NEVER PERFORM WRITES IN DIR WHICH IS NOT CACHE DIR.

────────────────────────────────────────────────────────
ACTION SCHEMA

When actionable=true, generate() must return a list of action objects.

Allowed actions: move, copy, delete, create

Base action schema:

{
  "action": "move | copy | delete | create",
  "source": "/absolute/path",
  "destination": "/absolute/path or null",
  "overwrite": false,
  "comment": "optional description"
}

ACTION DETAILS:

move
------
Moves a file from source to destination.
- source: path to existing file
- destination: target path
- overwrite: whether to overwrite if destination exists

copy
------
Copies a file from source to destination (does not delete source).
- source: path to existing file
- destination: target path
- overwrite: whether to overwrite if destination exists

delete
------
Deletes a file or directory.
- source: path to delete
- destination: null
- overwrite: ignored

create
------
IMPORTANT: "create" is fundamentally a COPY operation.
It copies a file from source to destination.

- source: MUST be an existing file to copy FROM
- destination: the path where the file will be created/placed
- overwrite: whether to overwrite if destination already exists

TO CREATE NEW/EMPTY FILES:
You MUST first create the file in a temporary working directory within
your Python code, then queue it as a "create" action.

The temporary working directory MUST be:
  /storage/emulated/0/.tmp_aifile_gen

This directory will be cleaned up after actions complete.

STEP-BY-STEP EMPTY FILE CREATION:

1. In your generate() function, create a temp directory:
   os.makedirs('/storage/emulated/0/.tmp_aifile_gen', exist_ok=True)

2. Create the file(s) you want in that temp directory:
   temp_file = '/storage/emulated/0/.tmp_aifile_gen/myfile.txt'
   with open(temp_file, 'w') as f:
       pass  # for empty file
       # or f.write(content) for pre-filled

3. Return a "create" action with:
   {
     "action": "create",
     "source": temp_file,
     "destination": "/actual/target/path/myfile.txt",
     "overwrite": False,
     "comment": "Creating myfile.txt"
   }

────────────────────────────────────────────────────────
NON-ACTIONABLE RETURNS

When actionable=false,
generate() may return:

- string
- number
- boolean
- list
- dictionary
- nested JSON-compatible structures

This information will be shown directly to the user.

────────────────────────────────────────────────────────
EXAMPLES

Delete every ZIP file:

{
  "actionable": true,
  "generatorCode": "def generate():\n    import os, glob\n    os.chdir('/storage/emulated/0')\n    files = glob.glob('/sdcard/Download/*.zip')\n    return [{'action':'delete','source':p,'destination':None,'overwrite':False,'comment':'ZIP file'} for p in files if os.path.exists(p)]",
  "message":"Deleting every ZIP file from Downloads."
}

Create 3 empty text files:

{
  "actionable": true,
  "generatorCode": "def generate():\n    import os\n    os.chdir('/storage/emulated/0')\n    temp_dir = '/storage/emulated/0/.tmp_aifile_gen'\n    os.makedirs(temp_dir, exist_ok=True)\n    actions = []\n    for i in range(3):\n        temp_file = os.path.join(temp_dir, f'file_{i}.txt')\n        with open(temp_file, 'w') as f:\n            pass\n        actions.append({\n            'action': 'create',\n            'source': temp_file,\n            'destination': f'/storage/emulated/0/Documents/file_{i}.txt',\n            'overwrite': False,\n            'comment': f'Creating empty file {i}'\n        })\n    return actions",
  "message":"Creating 3 empty text files in Documents."
}

Create files with initial content:

{
  "actionable": true,
  "generatorCode": "def generate():\n    import os\n    os.chdir('/storage/emulated/0')\n    temp_dir = '/storage/emulated/0/.tmp_aifile_gen'\n    os.makedirs(temp_dir, exist_ok=True)\n    actions = []\n    temp_file = os.path.join(temp_dir, 'notes.txt')\n    with open(temp_file, 'w') as f:\n        f.write('This is my note content.\\nLine 2.')\n    actions.append({\n        'action': 'create',\n        'source': temp_file,\n        'destination': '/storage/emulated/0/Documents/notes.txt',\n        'overwrite': False,\n        'comment': 'Creating notes.txt with content'\n    })\n    return actions",
  "message":"Creating notes.txt with initial content."
}

Search for PDFs:

{
  "actionable": false,
  "generatorCode": "def generate():\n    import os\n    os.chdir('/storage/emulated/0')\n    found=[]\n    for root,dirs,files in os.walk('/sdcard'):\n        for f in files:\n            if f.lower().endswith('.pdf'):\n                found.append(os.path.join(root,f))\n    return found",
  "message":"Searching for PDF files."
}

Read a text file:

{
  "actionable": false,
  "generatorCode": "def generate():\n    import os\n    os.chdir('/storage/emulated/0')\n    path='/sdcard/Download/notes.txt'\n    if not os.path.exists(path):\n        return 'File not found.'\n    with open(path,'r',errors='replace') as f:\n        return f.read()",
  "message":"Reading the file."
}

Read an Excel spreadsheet:

{
  "actionable": false,
  "generatorCode": "def generate():\n    import os\n    from openpyxl import load_workbook\n    os.chdir('/storage/emulated/0')\n    # discover workbook at runtime\n    return {'status':'example'}",
  "message":"Reading Excel workbook."
}

Generate a chart:

{
  "actionable": false,
  "generatorCode": "def generate():\n    import matplotlib.pyplot as plt\n    import os\n    os.chdir('/storage/emulated/0')\n    return 'Chart generation example.'",
  "message":"Generating chart."
}

Simple conversation:

{
  "actionable": false,
  "generatorCode": null,
  "message":"Hello! How can I help you manage your files today?"
}

Important Notes:
--------------------
- You need not worry about directory creation. As long as the parent
  folder exists, the create command automatically creates intermediate
  directories with .mkdirs() Java function.
- All generated operations are executed sequentially, so you can chain
  them. For example: create a file, then move it, then delete it.
- For the "create" action, ALWAYS generate the source file in
  /storage/emulated/0/.tmp_aifile_gen first. Never try to "create"
  from a non-existent source.
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