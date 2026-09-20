package com.aviansh.aifilemanager.domain.agent.tools

import com.aviansh.aifilemanager.domain.agent.AgentTool
import com.aviansh.aifilemanager.domain.agent.ToolParam
import com.aviansh.aifilemanager.domain.agent.ToolResult
import com.aviansh.aifilemanager.domain.agent.ToolSpec
import com.aviansh.aifilemanager.domain.engines.FileEngine
import com.aviansh.aifilemanager.domain.engines.TrashEngine
import com.aviansh.aifilemanager.domain.security.FileAccessPolicy
import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_TEXT_BYTES = 60_000
private const val MAX_LIST_ENTRIES = 300

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun stamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** Shared helper that resolves an argument into an allowed file. */
private fun JSONObject.pathArg(policy: FileAccessPolicy, key: String): Result<File> {
    val raw = optString(key).takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalArgumentException("Missing required argument '$key'"))
    return policy.resolve(raw)
}

// ---------------------------------------------------------------- read-only

class ListDirectoryTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "list_directory",
        description = "Lists the files and folders in a directory. Use this to explore storage.",
        params = listOf(
            ToolParam("path", "string", "Absolute directory path, e.g. /storage/emulated/0/Download"),
            ToolParam("recursive", "boolean", "Include nested files. Defaults to false.", required = false)
        )
    )

    override fun previewFor(args: JSONObject) = "List ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val dir = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        if (!dir.exists()) return@withContext ToolResult.Failure("No such directory: ${dir.absolutePath}")
        if (!dir.isDirectory) return@withContext ToolResult.Failure("Not a directory: ${dir.absolutePath}")

        val recursive = args.optBoolean("recursive", false)
        val entries = if (recursive) {
            dir.walkTopDown().maxDepth(6).filter { it != dir }.take(MAX_LIST_ENTRIES).toList()
        } else {
            (dir.listFiles() ?: emptyArray()).sortedWith(
                compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
            ).take(MAX_LIST_ENTRIES)
        }

        if (entries.isEmpty()) return@withContext ToolResult.Success("(empty directory)", "Empty directory")

        val body = entries.joinToString("\n") { f ->
            if (f.isDirectory) "DIR  ${f.absolutePath}"
            else "FILE ${f.absolutePath}  ${humanSize(f.length())}  ${stamp(f.lastModified())}"
        }
        ToolResult.Success(body, "${entries.size} item(s) in ${dir.name}")
    }
}

class ReadFileTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "read_file",
        description = "Reads a text file's contents. Truncates very large files.",
        params = listOf(ToolParam("path", "string", "Absolute file path"))
    )

    override fun previewFor(args: JSONObject) = "Read ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val file = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        if (!file.exists()) return@withContext ToolResult.Failure("No such file: ${file.absolutePath}")
        if (file.isDirectory) return@withContext ToolResult.Failure("That is a directory; use list_directory instead.")

        try {
            val bytes = file.readBytes()
            val text = String(bytes.take(MAX_TEXT_BYTES).toByteArray(), Charsets.UTF_8)
            val suffix = if (bytes.size > MAX_TEXT_BYTES) "\n[truncated — file is ${humanSize(file.length())}]" else ""
            ToolResult.Success(text + suffix, "Read ${file.name} (${humanSize(file.length())})")
        } catch (e: Exception) {
            ToolResult.Failure("Could not read ${file.name}: ${e.message}")
        }
    }
}

class FileInfoTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "file_info",
        description = "Returns metadata (size, type, modified date) without reading the contents.",
        params = listOf(ToolParam("path", "string", "Absolute file or directory path"))
    )

    override fun previewFor(args: JSONObject) = "Inspect ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val file = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        if (!file.exists()) return@withContext ToolResult.Failure("No such path: ${file.absolutePath}")

        val info = buildString {
            appendLine("path: ${file.absolutePath}")
            appendLine("type: ${if (file.isDirectory) "directory" else "file"}")
            appendLine("size: ${humanSize(if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length())}")
            appendLine("modified: ${stamp(file.lastModified())}")
            if (file.isDirectory) appendLine("children: ${file.listFiles()?.size ?: 0}")
            append("extension: ${file.extension.ifBlank { "(none)" }}")
        }
        ToolResult.Success(info, "Inspected ${file.name}")
    }
}

class SearchFilesTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "search_files",
        description = "Finds files by name pattern (glob-like substring) under a directory.",
        params = listOf(
            ToolParam("path", "string", "Directory to search in"),
            ToolParam("query", "string", "Case-insensitive substring or extension, e.g. '.pdf' or 'invoice'")
        )
    )

    override fun previewFor(args: JSONObject) = "Search '${args.optString("query")}' in ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val dir = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        val query = args.optString("query").lowercase()
        if (query.isBlank()) return@withContext ToolResult.Failure("Missing required argument 'query'")
        if (!dir.isDirectory) return@withContext ToolResult.Failure("Not a directory: ${dir.absolutePath}")

        val hits = dir.walkTopDown().maxDepth(8)
            .filter { it.isFile && it.name.lowercase().contains(query) }
            .take(MAX_LIST_ENTRIES)
            .toList()

        if (hits.isEmpty()) return@withContext ToolResult.Success("No matches for '$query'.", "No matches")

        val body = hits.joinToString("\n") { "${it.absolutePath}  ${humanSize(it.length())}" }
        ToolResult.Success(body, "${hits.size} match(es) for '$query'")
    }
}

// ---------------------------------------------------------------- additive

class CreateFolderTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "create_folder",
        description = "Creates a new folder (including parents).",
        params = listOf(ToolParam("path", "string", "Absolute folder path to create"))
    )

    override fun previewFor(args: JSONObject) = "Create folder ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val dir = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        if (dir.exists()) return@withContext ToolResult.Success("Already exists: ${dir.absolutePath}", "Folder already existed")
        if (!dir.mkdirs()) return@withContext ToolResult.Failure("Could not create ${dir.absolutePath}")
        ToolResult.Success("Created ${dir.absolutePath}", "Created folder ${dir.name}")
    }
}

class WriteFileTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "write_file",
        description = "Writes text content to a file. Overwriting an existing file needs confirmation.",
        params = listOf(
            ToolParam("path", "string", "Absolute destination path"),
            ToolParam("content", "string", "Text to write"),
            ToolParam("overwrite", "boolean", "Replace an existing file. Defaults to false.", required = false)
        ),
        destructive = true,
        riskLevel = RiskLevel.MODERATE
    )

    override fun previewFor(args: JSONObject): String {
        val path = args.optString("path")
        return if (File(path).exists()) "Overwrite $path" else "Write new file $path"
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val file = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        val content = args.optString("content")
        val overwrite = args.optBoolean("overwrite", false)

        if (file.exists() && !overwrite) {
            return@withContext ToolResult.Failure(
                "${file.absolutePath} already exists. Pass overwrite=true to replace it."
            )
        }

        try {
            if (file.exists()) TrashEngine.moveToTrash(file)
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.Success("Wrote ${content.length} chars to ${file.absolutePath}", "Wrote ${file.name}")
        } catch (e: Exception) {
            ToolResult.Failure("Could not write ${file.name}: ${e.message}")
        }
    }
}

class CopyTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "copy",
        description = "Copies a file or folder to a new location.",
        params = listOf(
            ToolParam("source", "string", "Absolute path to copy from"),
            ToolParam("destination", "string", "Absolute path to copy to"),
            ToolParam("overwrite", "boolean", "Replace the destination if it exists. Defaults to false.", required = false)
        )
    )

    override fun previewFor(args: JSONObject) =
        "Copy ${args.optString("source")} → ${args.optString("destination")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val source = args.pathArg(policy, "source").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid source") }
        val dest = args.pathArg(policy, "destination").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid destination") }
        val overwrite = args.optBoolean("overwrite", false)

        if (!source.exists()) return@withContext ToolResult.Failure("No such source: ${source.absolutePath}")
        if (dest.exists() && !overwrite) {
            return@withContext ToolResult.Failure("${dest.absolutePath} already exists. Pass overwrite=true to replace it.")
        }
        if (source.isDirectory && policy.isAncestorOf(source, dest)) {
            return@withContext ToolResult.Failure("Cannot copy a folder into itself.")
        }

        try {
            if (dest.exists() && overwrite) TrashEngine.moveToTrash(dest)
            if (source.isDirectory) FileEngine.copyRecursively(source, dest, overwrite)
            else FileEngine.copyFile(source, dest)
            ToolResult.Success("Copied to ${dest.absolutePath}", "Copied ${source.name}")
        } catch (e: Exception) {
            ToolResult.Failure("Copy failed: ${e.message}")
        }
    }
}

// ---------------------------------------------------------------- destructive

class MoveTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "move",
        description = "Moves or renames a file or folder.",
        params = listOf(
            ToolParam("source", "string", "Absolute path to move"),
            ToolParam("destination", "string", "Absolute target path"),
            ToolParam("overwrite", "boolean", "Replace the destination if it exists. Defaults to false.", required = false)
        ),
        destructive = true,
        riskLevel = RiskLevel.MODERATE
    )

    override fun previewFor(args: JSONObject) =
        "Move ${args.optString("source")} → ${args.optString("destination")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val source = args.pathArg(policy, "source").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid source") }
        val dest = args.pathArg(policy, "destination").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid destination") }
        val overwrite = args.optBoolean("overwrite", false)

        if (!source.exists()) return@withContext ToolResult.Failure("No such source: ${source.absolutePath}")
        if (dest.exists() && !overwrite) {
            return@withContext ToolResult.Failure("${dest.absolutePath} already exists. Pass overwrite=true to replace it.")
        }
        if (source.isDirectory && policy.isAncestorOf(source, dest)) {
            return@withContext ToolResult.Failure("Cannot move a folder into itself.")
        }

        try {
            if (dest.exists() && overwrite) TrashEngine.moveToTrash(dest)
            FileEngine.moveFile(source, dest, overwrite)
            ToolResult.Success("Moved to ${dest.absolutePath}", "Moved ${source.name}")
        } catch (e: Exception) {
            ToolResult.Failure("Move failed: ${e.message}")
        }
    }
}

class DeleteTool(private val policy: FileAccessPolicy) : AgentTool {
    override val spec = ToolSpec(
        name = "delete",
        description = "Deletes a file or folder. Deleted items go to the app trash and can be restored.",
        params = listOf(ToolParam("path", "string", "Absolute path to delete")),
        destructive = true,
        riskLevel = RiskLevel.HIGH
    )

    override fun previewFor(args: JSONObject) = "Delete ${args.optString("path")}"

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val file = args.pathArg(policy, "path").getOrElse { return@withContext ToolResult.Failure(it.message ?: "Invalid path") }
        if (!file.exists()) return@withContext ToolResult.Failure("No such path: ${file.absolutePath}")

        TrashEngine.moveToTrash(file).fold(
            onSuccess = {
                ToolResult.Success(
                    "Moved ${file.absolutePath} to trash (recoverable).",
                    "Deleted ${file.name} (in trash)"
                )
            },
            onFailure = { ToolResult.Failure("Delete failed: ${it.message}") }
        )
    }
}
