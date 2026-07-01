package com.aviansh.aifilemanager.domain.engines

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

object FileEngine {

    /**
     * Copies [source] → [dest].
     * Stream direction was previously inverted; fixed here.
     */
    fun copyFile(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Moves [source] → [dest] (copy then delete source).
     */
    fun moveFile(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        source.delete()
    }

    /**
     * Writes the content of [tmpFile] to [path].
     * If the file already exists and [overwrite] is false, throws FileAlreadyExistsException.
     */
    fun createFile(path: String, tmpFile: File, overwrite: Boolean): File {
        val dest = File(path)
        if (dest.exists() && !overwrite) throw FileAlreadyExistsException(dest)
        dest.parentFile?.mkdirs()
        copyFile(tmpFile, dest)
        return dest
    }

    /**
     * Overwrites the file at [path] with the content of [tmpFile].
     * If the file does not exist and [create] is false, throws FileNotFoundException.
     */
    fun updateFile(path: String, tmpFile: File, create: Boolean = false): File {
        val dest = File(path)
        if (!dest.exists() && !create) throw FileNotFoundException("File not found: $path")
        dest.parentFile?.mkdirs()
        copyFile(tmpFile, dest)
        return dest
    }

    fun readFile(path: String): File = File(path)

    fun listDir(basePath: String): List<File> {
        val dir = File(basePath)
        if (!dir.exists()) throw FileNotFoundException("Directory not found: $basePath")
        return dir.listFiles()?.toList() ?: emptyList()
    }
}