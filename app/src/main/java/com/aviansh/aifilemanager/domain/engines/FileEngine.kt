package com.aviansh.aifilemanager.domain.engines

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object FileEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64KB chunk buffer

    /**
     * Copies a single file from [source] to [dest].
     */
    fun copyFile(source: File, dest: File) {
        if (!source.exists()) throw FileNotFoundException("Source file does not exist: ${source.absolutePath}")
        if (source.isDirectory) throw IllegalArgumentException("Source is a directory, use copyRecursively: ${source.absolutePath}")
        if (source.canonicalPath == dest.canonicalPath) return

        dest.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
    }

    /**
     * Recursively copies [source] to [dest].
     * Streaming and chunked file copy. Respects [overwrite].
     */
    fun copyRecursively(source: File, dest: File, overwrite: Boolean = false) {
        if (!source.exists()) throw FileNotFoundException("Source does not exist: ${source.absolutePath}")
        if (source.canonicalPath == dest.canonicalPath) return

        if (source.isFile) {
            if (dest.exists()) {
                if (!overwrite) throw FileAlreadyExistsException(dest.absolutePath)
            }
            copyFile(source, dest)
            return
        }

        if (source.isDirectory) {
            if (!dest.exists()) {
                dest.mkdirs()
            }
            val children = source.listFiles() ?: return
            for (child in children) {
                val childDest = File(dest, child.name)
                copyRecursively(child, childDest, overwrite)
            }
        }
    }

    /**
     * Moves [source] to [dest].
     * Never uses implicit replacement unless [overwrite] is explicitly true.
     */
    fun moveFile(source: File, dest: File, overwrite: Boolean = false) {
        if (!source.exists()) throw FileNotFoundException("Source file does not exist: ${source.absolutePath}")
        if (source.canonicalPath == dest.canonicalPath) return
        if (dest.exists() && !overwrite) {
            throw FileAlreadyExistsException(dest.absolutePath)
        }

        dest.parentFile?.mkdirs()

        val sourcePath = Paths.get(source.absolutePath)
        val targetPath = Paths.get(dest.absolutePath)

        if (overwrite) {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.move(sourcePath, targetPath)
        }
    }

    /**
     * Writes the content of [tmpFile] to [path].
     * If the file already exists and [overwrite] is false, throws FileAlreadyExistsException.
     */
    fun createFile(path: String, tmpFile: File, overwrite: Boolean): File {
        val dest = File(path)
        if (dest.exists() && !overwrite) throw FileAlreadyExistsException(dest.absolutePath)
        dest.parentFile?.mkdirs()
        if (tmpFile.isDirectory) {
            copyRecursively(tmpFile, dest, overwrite)
        } else {
            copyFile(tmpFile, dest)
        }
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
        if (tmpFile.isDirectory) {
            copyRecursively(tmpFile, dest, overwrite = true)
        } else {
            copyFile(tmpFile, dest)
        }
        return dest
    }

    fun readFile(path: String): File = File(path)

    fun listDir(basePath: String): List<File> {
        val dir = File(basePath)
        if (!dir.exists()) throw FileNotFoundException("Directory not found: $basePath")
        return dir.listFiles()?.toList() ?: emptyList()
    }
}
