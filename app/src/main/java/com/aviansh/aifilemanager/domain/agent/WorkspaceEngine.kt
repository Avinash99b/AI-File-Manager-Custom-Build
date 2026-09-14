package com.aviansh.aifilemanager.domain.agent

import android.os.Environment
import android.util.Log
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.engines.FileEngine
import com.aviansh.aifilemanager.domain.security.PathPolicy
import com.aviansh.aifilemanager.domain.transactions.FailureDiagnosis
import com.aviansh.aifilemanager.domain.transactions.PreflightSummary
import com.aviansh.aifilemanager.domain.transactions.RiskLevel
import com.aviansh.aifilemanager.domain.transactions.RollbackOutcome
import com.aviansh.aifilemanager.domain.transactions.riskLevel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkspaceEngine(
    private val customAllowedRoots: List<File>? = null
) {

    private val TAG = "WorkspaceEngine"

    private fun defaultAllowedRoots(): List<File> {
        val roots = mutableListOf<File>()
        try {
            if (AppPaths.filesDir.isNotBlank()) {
                roots.add(File(AppPaths.filesDir))
            }
        } catch (e: Exception) {
            // Ignore in unit tests
        }
        try {
            roots.add(Environment.getExternalStorageDirectory())
        } catch (e: Exception) {
            // Ignore in unit tests
        }
        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        roots.add(tmpDir)
        return roots
    }

    private fun getPathPolicy(): PathPolicy {
        val roots = customAllowedRoots ?: defaultAllowedRoots()
        val forbiddenAppPrivate = mutableListOf<File>()

        try {
            if (AppPaths.filesDir.isNotBlank()) {
                forbiddenAppPrivate.add(File(AppPaths.filesDir))
            }
            if (AppPaths.cacheDir.isNotBlank()) {
                forbiddenAppPrivate.add(File(AppPaths.cacheDir))
            }
        } catch (e: Exception) {
            // Ignore in unit tests
        }

        return PathPolicy(
            allowedRoots = roots,
            forbiddenAppPrivatePaths = forbiddenAppPrivate
        )
    }

    private fun getBaseDirectory(): File {
        return try {
            if (AppPaths.filesDir.isNotBlank()) File(AppPaths.filesDir)
            else File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        } catch (e: Exception) {
            File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        }
    }

    /**
     * Creates an isolated workspace directory.
     */
    suspend fun setupWorkspace(sourceFiles: List<String>): String = withContext(Dispatchers.IO) {
        val workspaceId = UUID.randomUUID().toString()
        val baseDir = getBaseDirectory()
        val workspaceDir = File(baseDir, "workspace_$workspaceId")

        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        try {
            workspaceDir.canonicalPath
        } catch (_: Exception) {
            workspaceDir.absolutePath
        }
    }

    /**
     * Performs a preflight validation pass on all actions before execution.
     */
    fun preflight(actions: List<FileAction>): PreflightSummary {
        val policy = getPathPolicy()
        val errors = mutableListOf<String>()
        val affectedPaths = mutableSetOf<String>()
        var conflictsCount = 0
        val riskCounts = mutableMapOf(
            RiskLevel.SAFE to 0,
            RiskLevel.MODERATE to 0,
            RiskLevel.HIGH to 0
        )

        for (action in actions) {
            val risk = action.riskLevel()
            riskCounts[risk] = (riskCounts[risk] ?: 0) + 1

            // Validate source path
            val sourceRes = policy.validatePath(action.sourcePath)
            if (sourceRes.isFailure) {
                errors.add(sourceRes.exceptionOrNull()?.message ?: "Invalid source path: ${action.sourcePath}")
                continue
            }
            val canonicalSource = sourceRes.getOrThrow()
            affectedPaths.add(canonicalSource.absolutePath)

            if (action.type != FileActionType.CREATE && !canonicalSource.exists()) {
                errors.add("Source file does not exist: ${action.sourcePath}")
            }

            if (action.type == FileActionType.MOVE || action.type == FileActionType.COPY || action.type == FileActionType.CREATE) {
                val destPathStr = action.destinationPath
                if (destPathStr.isNullOrBlank()) {
                    errors.add("Destination path is required for action ${action.type}")
                    continue
                }

                val destRes = policy.validatePath(destPathStr)
                if (destRes.isFailure) {
                    errors.add(destRes.exceptionOrNull()?.message ?: "Invalid destination path: $destPathStr")
                    continue
                }
                val canonicalDest = destRes.getOrThrow()
                affectedPaths.add(canonicalDest.absolutePath)

                // Same source and destination check
                if (canonicalSource.absolutePath == canonicalDest.absolutePath) {
                    errors.add("Source and destination paths are identical: ${action.sourcePath}")
                }

                // Move/Copy into self or descendant check
                if (policy.checkAncestorDescendantConflict(canonicalSource, canonicalDest)) {
                    errors.add("Cannot ${action.type.name.lowercase()} directory into itself or descendant: ${action.sourcePath} -> $destPathStr")
                }

                // Overwrite check
                if (canonicalDest.exists()) {
                    conflictsCount++
                    if (!action.overwrite) {
                        errors.add("Destination already exists and overwrite is false: $destPathStr")
                    }
                }
            }
        }

        return PreflightSummary(
            isValid = errors.isEmpty(),
            actionsByRisk = riskCounts,
            conflictsCount = conflictsCount,
            affectedPaths = affectedPaths.toList(),
            validationErrors = errors
        )
    }

    /**
     * Commits actions from the workspace to the real filesystem.
     * Uses a deterministic per-action snapshot manifest for collision-safe rollback.
     */
    suspend fun commitWorkspace(actions: List<FileAction>): Result<Unit> = withContext(Dispatchers.IO) {
        val baseDir = getBaseDirectory()
        val snapshotDir = File(baseDir, "snapshot_${UUID.randomUUID()}")
        snapshotDir.mkdirs()

        val completedActions = mutableListOf<Pair<Int, FileAction>>()
        val snapshotManifest = mutableMapOf<Int, String>()

        // 1. Preflight all actions first (all-or-nothing validation)
        val preflightSummary = preflight(actions)
        if (!preflightSummary.isValid) {
            snapshotDir.deleteRecursively()
            return@withContext Result.failure(
                IllegalArgumentException("Preflight validation failed:\n" + preflightSummary.validationErrors.joinToString("\n"))
            )
        }

        try {
            actions.forEachIndexed { index, action ->
                val sourceFile = File(action.sourcePath)

                when (action.type) {
                    FileActionType.MOVE -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            val snapshotFile = File(snapshotDir, "backup_action_${index}_${dest.name}")
                            if (dest.isDirectory) {
                                FileEngine.copyRecursively(dest, snapshotFile, overwrite = true)
                            } else {
                                FileEngine.copyFile(dest, snapshotFile)
                            }
                            snapshotManifest[index] = snapshotFile.absolutePath
                        }
                        FileEngine.moveFile(sourceFile, dest, overwrite = action.overwrite)
                        completedActions.add(index to action)
                    }
                    FileActionType.COPY -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            val snapshotFile = File(snapshotDir, "backup_action_${index}_${dest.name}")
                            if (dest.isDirectory) {
                                FileEngine.copyRecursively(dest, snapshotFile, overwrite = true)
                            } else {
                                FileEngine.copyFile(dest, snapshotFile)
                            }
                            snapshotManifest[index] = snapshotFile.absolutePath
                        }
                        if (sourceFile.isDirectory) {
                            FileEngine.copyRecursively(sourceFile, dest, overwrite = action.overwrite)
                        } else {
                            FileEngine.copyFile(sourceFile, dest)
                        }
                        completedActions.add(index to action)
                    }
                    FileActionType.DELETE -> {
                        if (sourceFile.exists()) {
                            val snapshotFile = File(snapshotDir, "backup_action_${index}_${sourceFile.name}")
                            if (sourceFile.isDirectory) {
                                FileEngine.copyRecursively(sourceFile, snapshotFile, overwrite = true)
                                sourceFile.deleteRecursively()
                            } else {
                                FileEngine.moveFile(sourceFile, snapshotFile, overwrite = true)
                            }
                            snapshotManifest[index] = snapshotFile.absolutePath
                        }
                        completedActions.add(index to action)
                    }
                    FileActionType.CREATE -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            val snapshotFile = File(snapshotDir, "backup_action_${index}_${dest.name}")
                            if (dest.isDirectory) {
                                FileEngine.copyRecursively(dest, snapshotFile, overwrite = true)
                            } else {
                                FileEngine.copyFile(dest, snapshotFile)
                            }
                            snapshotManifest[index] = snapshotFile.absolutePath
                        }
                        FileEngine.createFile(dest.absolutePath, sourceFile, action.overwrite)
                        completedActions.add(index to action)
                    }
                }
            }

            // Success, clean up snapshot
            snapshotDir.deleteRecursively()
            Result.success(Unit)

        } catch (e: kotlinx.coroutines.CancellationException) {
            val rollbackFailures = performRollback(completedActions, snapshotManifest)
            if (rollbackFailures.isEmpty()) {
                snapshotDir.deleteRecursively()
            } else {
                Log.e(TAG, "Cancellation rollback failed partway. Snapshot retained at ${snapshotDir.absolutePath}")
                e.addSuppressed(IllegalStateException("Rollback failed during cancellation: ${rollbackFailures.joinToString("; ")}"))
            }
            throw e
        } catch (e: Exception) {
            val rollbackFailures = performRollback(completedActions, snapshotManifest)
            if (rollbackFailures.isEmpty()) {
                snapshotDir.deleteRecursively()
            } else {
                Log.e(TAG, "Execution rollback failed partway. Snapshot retained at ${snapshotDir.absolutePath}")
            }

            val rollbackOutcome = if (rollbackFailures.isEmpty()) {
                RollbackOutcome.Success
            } else {
                RollbackOutcome.Partial(rollbackFailures)
            }

            val diagnosis = FailureDiagnosis(
                failedAction = actions.getOrNull(completedActions.size),
                errorMessage = e.message ?: "Execution failed",
                affectedPaths = completedActions.map { it.second.sourcePath },
                rollbackOutcome = rollbackOutcome
            )

            Result.failure(diagnosis)
        }
    }

    private fun performRollback(
        completedActions: List<Pair<Int, FileAction>>,
        snapshotManifest: Map<Int, String>
    ): List<String> {
        val rollbackFailures = mutableListOf<String>()

        completedActions.reversed().forEach { (index, action) ->
            try {
                val originalSource = File(action.sourcePath)
                val snapshotPath = snapshotManifest[index]
                val snapshotFile = snapshotPath?.let { File(it) }

                when (action.type) {
                    FileActionType.MOVE -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            FileEngine.moveFile(dest, originalSource, overwrite = true)
                        }
                        if (snapshotFile != null && snapshotFile.exists()) {
                            if (snapshotFile.isDirectory) {
                                FileEngine.copyRecursively(snapshotFile, dest, overwrite = true)
                                snapshotFile.deleteRecursively()
                            } else {
                                FileEngine.moveFile(snapshotFile, dest, overwrite = true)
                            }
                        }
                    }
                    FileActionType.COPY -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                        }
                        if (snapshotFile != null && snapshotFile.exists()) {
                            if (snapshotFile.isDirectory) {
                                FileEngine.copyRecursively(snapshotFile, dest, overwrite = true)
                                snapshotFile.deleteRecursively()
                            } else {
                                FileEngine.moveFile(snapshotFile, dest, overwrite = true)
                            }
                        }
                    }
                    FileActionType.DELETE -> {
                        if (snapshotFile != null && snapshotFile.exists()) {
                            if (snapshotFile.isDirectory) {
                                FileEngine.copyRecursively(snapshotFile, originalSource, overwrite = true)
                                snapshotFile.deleteRecursively()
                            } else {
                                FileEngine.moveFile(snapshotFile, originalSource, overwrite = true)
                            }
                        }
                    }
                    FileActionType.CREATE -> {
                        val dest = File(action.destinationPath!!)
                        if (dest.exists()) {
                            if (dest.isDirectory) dest.deleteRecursively() else dest.delete()
                        }
                        if (snapshotFile != null && snapshotFile.exists()) {
                            if (snapshotFile.isDirectory) {
                                FileEngine.copyRecursively(snapshotFile, dest, overwrite = true)
                                snapshotFile.deleteRecursively()
                            } else {
                                FileEngine.moveFile(snapshotFile, dest, overwrite = true)
                            }
                        }
                    }
                }
            } catch (rollbackEx: Exception) {
                rollbackFailures.add("Failed to rollback action index $index ($action): ${rollbackEx.message}")
            }
        }
        return rollbackFailures
    }

    /**
     * Deletes the workspace directory.
     */
    suspend fun cleanupWorkspace(workspacePath: String) = withContext(Dispatchers.IO) {
        val dir = File(workspacePath)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
