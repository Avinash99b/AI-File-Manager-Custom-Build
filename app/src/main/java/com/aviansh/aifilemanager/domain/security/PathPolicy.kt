package com.aviansh.aifilemanager.domain.security

import java.io.File

sealed class PathValidationError : Exception() {
    data class PathTraversalDenied(val path: String) : PathValidationError() {
        override val message: String get() = "Path traversal ('..') is not allowed: $path"
    }
    data class SymlinkEscapeDenied(val path: String, val target: String) : PathValidationError() {
        override val message: String get() = "Symlink escape rejected: $path points to $target outside allowed roots"
    }
    data class ForbiddenSystemOrAppPrivatePath(val path: String) : PathValidationError() {
        override val message: String get() = "Access to system or protected app path is forbidden: $path"
    }
    data class PathOutsideAllowedRoots(val path: String) : PathValidationError() {
        override val message: String get() = "Path is outside explicitly allowed roots: $path"
    }
    data class SourceDoesNotExist(val path: String) : PathValidationError() {
        override val message: String get() = "Source file or directory does not exist: $path"
    }
    data class SameSourceAndDestination(val path: String) : PathValidationError() {
        override val message: String get() = "Source and destination cannot be identical: $path"
    }
    data class MoveOrCopyIntoSelfOrDescendant(val source: String, val destination: String) : PathValidationError() {
        override val message: String get() = "Cannot move/copy directory into itself or descendant: $source -> $destination"
    }
    data class OverwriteDenied(val destination: String) : PathValidationError() {
        override val message: String get() = "Destination already exists and overwrite is set to false: $destination"
    }
}

class PathPolicy(
    private val allowedRoots: List<File>,
    private val forbiddenAppPrivatePaths: List<File> = emptyList()
) {

    fun canonicalize(path: String): File {
        val file = File(path)
        return try {
            file.canonicalFile
        } catch (e: Exception) {
            file.absoluteFile.normalize()
        }
    }

    fun validatePath(pathStr: String): Result<File> {
        if (pathStr.isBlank()) {
            return Result.failure(IllegalArgumentException("Path cannot be empty"))
        }

        // Reject explicit '..' components
        val parts = pathStr.replace('\\', '/').split('/')
        if (parts.contains("..")) {
            return Result.failure(PathValidationError.PathTraversalDenied(pathStr))
        }

        val file = File(pathStr)
        val canonical = canonicalize(pathStr)

        // Symlink escape check
        if (file.exists() && isSymlink(file)) {
            val realTarget = try {
                file.toPath().toRealPath().toFile()
            } catch (e: Exception) {
                canonical
            }
            if (!isPathWithinRoots(realTarget, allowedRoots)) {
                return Result.failure(PathValidationError.SymlinkEscapeDenied(pathStr, realTarget.absolutePath))
            }
        }

        // System or protected app private check
        if (isSystemOrProtectedPath(canonical)) {
            return Result.failure(PathValidationError.ForbiddenSystemOrAppPrivatePath(canonical.absolutePath))
        }

        // Root containment check
        if (!isPathWithinRoots(canonical, allowedRoots)) {
            return Result.failure(PathValidationError.PathOutsideAllowedRoots(canonical.absolutePath))
        }

        return Result.success(canonical)
    }

    fun isSymlink(file: File): Boolean {
        return try {
            java.nio.file.Files.isSymbolicLink(file.toPath())
        } catch (e: Exception) {
            false
        }
    }

    private fun isPathWithinRoots(file: File, roots: List<File>): Boolean {
        val targetPath = file.absolutePath
        return roots.any { root ->
            val rootCanonical = canonicalize(root.absolutePath).absolutePath
            targetPath == rootCanonical || targetPath.startsWith(rootCanonical + File.separator)
        }
    }

    private fun isSystemOrProtectedPath(file: File): Boolean {
        val forbiddenSystemPrefixes = listOf(
            "/proc", "/sys", "/dev", "/system", "/vendor"
        )
        val targetPath = file.absolutePath
        if (forbiddenSystemPrefixes.any { targetPath == it || targetPath.startsWith(it + "/") }) {
            return true
        }

        return forbiddenAppPrivatePaths.any { forbidden ->
            val forbiddenCanonical = canonicalize(forbidden.absolutePath).absolutePath
            val isInsideAppPrivate = targetPath == forbiddenCanonical || targetPath.startsWith(forbiddenCanonical + File.separator)

            if (isInsideAppPrivate) {
                val isWorkspacePath = targetPath.contains("${File.separator}workspace_")
                !isWorkspacePath
            } else {
                false
            }
        }
    }

    fun checkAncestorDescendantConflict(source: File, destination: File): Boolean {
        val srcCanonical = canonicalize(source.absolutePath).absolutePath
        val destCanonical = canonicalize(destination.absolutePath).absolutePath

        if (srcCanonical == destCanonical) return true

        return destCanonical.startsWith(srcCanonical + File.separator)
    }
}
