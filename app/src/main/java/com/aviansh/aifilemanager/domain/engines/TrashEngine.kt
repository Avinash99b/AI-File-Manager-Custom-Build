package com.aviansh.aifilemanager.domain.engines

import com.aviansh.aifilemanager.domain.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrashedItem(
    val id: String,
    val originalPath: String,
    val trashedAt: Long,
    val sizeBytes: Long
) {
    val name: String get() = File(originalPath).name

    val trashedAtFormatted: String
        get() = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(trashedAt))
}

/**
 * Recoverable deletion.
 *
 * The agent never removes user data outright: deletes move the file into an app-private trash
 * directory and record where it came from, so anything removed by mistake can be restored.
 */
object TrashEngine {

    private const val TRASH_DIR_NAME = "trash"
    private const val INDEX_FILE_NAME = "index.json"

    private fun trashDir(): File =
        File(AppPaths.filesDir.ifBlank { System.getProperty("java.io.tmpdir") ?: "/tmp" }, TRASH_DIR_NAME)
            .apply { if (!exists()) mkdirs() }

    private fun indexFile(): File = File(trashDir(), INDEX_FILE_NAME)

    private fun readIndex(): MutableList<TrashedItem> {
        val f = indexFile()
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapTo(mutableListOf()) { i ->
                val o = arr.getJSONObject(i)
                TrashedItem(
                    id = o.getString("id"),
                    originalPath = o.getString("originalPath"),
                    trashedAt = o.optLong("trashedAt"),
                    sizeBytes = o.optLong("sizeBytes")
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeIndex(items: List<TrashedItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("originalPath", item.originalPath)
                    put("trashedAt", item.trashedAt)
                    put("sizeBytes", item.sizeBytes)
                }
            )
        }
        indexFile().writeText(arr.toString())
    }

    /** Moves [file] into the trash. Returns the trash entry. */
    suspend fun moveToTrash(file: File): Result<TrashedItem> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(java.io.FileNotFoundException("No such file: ${file.absolutePath}"))
        }

        try {
            val id = "${System.currentTimeMillis()}_${file.name.hashCode().toUInt()}"
            val payload = File(trashDir(), id)
            val size = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

            // Prefer an atomic rename; fall back to copy+delete across filesystems.
            if (!file.renameTo(payload)) {
                if (file.isDirectory) {
                    FileEngine.copyRecursively(file, payload, overwrite = true)
                    if (!file.deleteRecursively()) {
                        return@withContext Result.failure(IllegalStateException("Could not remove ${file.absolutePath}"))
                    }
                } else {
                    FileEngine.copyFile(file, payload)
                    if (!file.delete()) {
                        payload.delete()
                        return@withContext Result.failure(IllegalStateException("Could not remove ${file.absolutePath}"))
                    }
                }
            }

            val item = TrashedItem(id, file.absolutePath, System.currentTimeMillis(), size)
            writeIndex(readIndex().apply { add(item) })
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(id: String): Result<File> = withContext(Dispatchers.IO) {
        val items = readIndex()
        val item = items.firstOrNull { it.id == id }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown trash entry: $id"))

        val payload = File(trashDir(), item.id)
        if (!payload.exists()) {
            items.removeAll { it.id == id }
            writeIndex(items)
            return@withContext Result.failure(IllegalStateException("Trashed data is gone: ${item.name}"))
        }

        val destination = File(item.originalPath)
        if (destination.exists()) {
            return@withContext Result.failure(
                IllegalStateException("Something already exists at ${item.originalPath}")
            )
        }

        try {
            destination.parentFile?.mkdirs()
            if (!payload.renameTo(destination)) {
                if (payload.isDirectory) {
                    FileEngine.copyRecursively(payload, destination, overwrite = false)
                    payload.deleteRecursively()
                } else {
                    FileEngine.copyFile(payload, destination)
                    payload.delete()
                }
            }
            items.removeAll { it.id == id }
            writeIndex(items)
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun list(): List<TrashedItem> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.trashedAt }
    }

    /** Restores everything trashed at or after [sinceMillis]; used for "undo this turn". */
    suspend fun restoreSince(sinceMillis: Long): List<Result<File>> = withContext(Dispatchers.IO) {
        readIndex().filter { it.trashedAt >= sinceMillis }
            .sortedByDescending { it.trashedAt }
            .map { restore(it.id) }
    }

    suspend fun empty(): Int = withContext(Dispatchers.IO) {
        val items = readIndex()
        items.forEach { File(trashDir(), it.id).deleteRecursively() }
        writeIndex(emptyList())
        items.size
    }
}
