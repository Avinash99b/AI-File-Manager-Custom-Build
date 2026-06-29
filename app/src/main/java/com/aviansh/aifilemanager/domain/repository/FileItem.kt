package com.aviansh.aifilemanager.domain.repository

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String? = null,
    val icon: Int = if (isDirectory) android.R.drawable.ic_menu_manage else android.R.drawable.ic_menu_info_details
)

/**
 * Repository for file operations. Assumes permissions are already granted.
 */
class FileRepository(private val context: Context) {

    private val tag = "FileRepository"

    /**
     * List files and folders in the given directory.
     */
    suspend fun listFiles(dirPath: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(dirPath)

            if (!directory.exists()) {
                return@withContext Result.failure(Exception("Directory does not exist: $dirPath"))
            }

            if (!directory.isDirectory) {
                return@withContext Result.failure(Exception("Not a directory: $dirPath"))
            }

            val files = directory.listFiles() ?: emptyArray()

            val items = files
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { file ->
                    FileItem(
                        id = file.absolutePath.hashCode().toString(),
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        mimeType = getMimeType(file)
                    )
                }

            Log.d(tag, "Listed ${items.size} items from $dirPath")
            Result.success(items)

        } catch (e: Exception) {
            Log.e(tag, "Error listing files in $dirPath", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a file or directory (recursively for directories).
     */
    suspend fun deleteFile(filePath: String, recursive: Boolean = false): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)

                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File does not exist: $filePath"))
                }

                val deleted = if (file.isDirectory) {
                    if (recursive) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                } else {
                    file.delete()
                }

                if (deleted) {
                    Log.d(tag, "Deleted: $filePath")
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to delete: $filePath"))
                }

            } catch (e: Exception) {
                Log.e(tag, "Error deleting $filePath", e)
                Result.failure(e)
            }
        }

    /**
     * Rename a file or directory.
     */
    suspend fun renameFile(filePath: String, newName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)

                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File does not exist: $filePath"))
                }

                // Validate new name
                if (newName.isBlank() || newName.contains("/")) {
                    return@withContext Result.failure(Exception("Invalid name: $newName"))
                }

                val parent = file.parentFile ?: run {
                    return@withContext Result.failure(Exception("Cannot determine parent directory"))
                }

                val newFile = File(parent, newName)

                if (newFile.exists()) {
                    return@withContext Result.failure(Exception("File already exists: $newName"))
                }

                val renamed = file.renameTo(newFile)

                if (renamed) {
                    Log.d(tag, "Renamed: $filePath -> ${newFile.absolutePath}")
                    Result.success(newFile.absolutePath)
                } else {
                    Result.failure(Exception("Failed to rename file"))
                }

            } catch (e: Exception) {
                Log.e(tag, "Error renaming $filePath", e)
                Result.failure(e)
            }
        }

    /**
     * Get file size in human-readable format.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Get last modified time as formatted string.
     */
    fun formatLastModified(millis: Long): String {
        val date = Date(millis)
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }

    /**
     * Get the MIME type of a file based on extension.
     */
    private fun getMimeType(file: File): String? {
        if (file.isDirectory) return "application/x-directory"

        return when (file.extension.lowercase()) {
            // Documents
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "txt" -> "text/plain"
            "md" -> "text/markdown"

            // Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"

            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"

            // Video
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"

            // Archives
            "zip", "jar" -> "application/zip"
            "rar" -> "application/x-rar-compressed"

            else -> null
        }
    }

    /**
     * Get detailed file info including permissions status.
     */
    suspend fun getFileDetails(filePath: String): Result<FileDetails> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)

                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File does not exist"))
                }

                val details = FileDetails(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    sizeFormatted = formatFileSize(file.length()),
                    lastModified = file.lastModified(),
                    lastModifiedFormatted = formatLastModified(file.lastModified()),
                    isDirectory = file.isDirectory,
                    isReadable = file.canRead(),
                    isWritable = file.canWrite(),
                    isExecutable = file.canExecute(),
                    itemCount = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0,
                    mimeType = getMimeType(file)
                )

                Result.success(details)

            } catch (e: Exception) {
                Log.e(tag, "Error getting file details for $filePath", e)
                Result.failure(e)
            }
        }
}

data class FileDetails(
    val name: String,
    val path: String,
    val size: Long,
    val sizeFormatted: String,
    val lastModified: Long,
    val lastModifiedFormatted: String,
    val isDirectory: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isExecutable: Boolean,
    val itemCount: Int,
    val mimeType: String?
)