package com.aviansh.aifilemanager.domain.security

import android.os.Environment
import com.aviansh.aifilemanager.domain.AppPaths
import java.io.File

/**
 * Decides which real filesystem paths the agent may read and mutate.
 *
 * In the agentic architecture the agent operates directly on user storage instead of staging
 * work in a sandboxed workspace, so this policy is the single safety boundary. It is deliberately
 * simple and auditable:
 *
 *  - reads and writes are confined to shared storage (/storage/emulated/0 and friends)
 *  - the app's own private data and OS directories are never touched
 *  - '..' traversal and symlinks that escape shared storage are rejected
 */
class FileAccessPolicy(
    private val allowedRoots: List<File> = defaultAllowedRoots(),
    private val forbiddenRoots: List<File> = defaultForbiddenRoots()
) {

    companion object {
        private val SYSTEM_PREFIXES = listOf("/proc", "/sys", "/dev", "/system", "/vendor", "/data/app")

        fun defaultAllowedRoots(): List<File> = buildList {
            try {
                add(Environment.getExternalStorageDirectory())
            } catch (_: Throwable) {
                // Unit tests / non-Android runtimes
            }
        }

        fun defaultForbiddenRoots(): List<File> = buildList {
            if (AppPaths.filesDir.isNotBlank()) add(File(AppPaths.filesDir))
            if (AppPaths.cacheDir.isNotBlank()) add(File(AppPaths.cacheDir))
        }
    }

    fun canonical(path: String): File = try {
        File(path).canonicalFile
    } catch (_: Exception) {
        File(path).absoluteFile.normalize()
    }

    /**
     * Validates [rawPath] for access, returning the canonical file or a descriptive failure.
     */
    fun resolve(rawPath: String): Result<File> {
        if (rawPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Path cannot be empty"))
        }

        if (rawPath.replace('\\', '/').split('/').contains("..")) {
            return Result.failure(
                SecurityException("Path traversal ('..') is not allowed: $rawPath")
            )
        }

        val file = canonical(rawPath)
        val target = file.absolutePath

        if (SYSTEM_PREFIXES.any { target == it || target.startsWith("$it/") }) {
            return Result.failure(
                SecurityException("System directories are off limits: $target")
            )
        }

        if (forbiddenRoots.any { contains(canonical(it.absolutePath), target) }) {
            return Result.failure(
                SecurityException("The app's private storage is off limits: $target")
            )
        }

        // An empty allow-list means we are running outside Android (unit tests); skip containment.
        if (allowedRoots.isNotEmpty() &&
            allowedRoots.none { contains(canonical(it.absolutePath), target) }
        ) {
            return Result.failure(
                SecurityException(
                    "Path is outside your shared storage: $target. " +
                        "Allowed: ${allowedRoots.joinToString { it.absolutePath }}"
                )
            )
        }

        return Result.success(file)
    }

    /** True when [child] is [parent] or lives underneath it. */
    private fun contains(parent: File, child: String): Boolean {
        val p = parent.absolutePath
        return child == p || child.startsWith(if (p.endsWith(File.separator)) p else p + File.separator)
    }

    fun isAncestorOf(source: File, destination: File): Boolean {
        val src = canonical(source.absolutePath).absolutePath
        val dst = canonical(destination.absolutePath).absolutePath
        return dst == src || dst.startsWith(src + File.separator)
    }
}
