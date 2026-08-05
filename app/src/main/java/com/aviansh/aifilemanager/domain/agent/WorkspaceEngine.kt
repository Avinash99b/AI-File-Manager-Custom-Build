package com.aviansh.aifilemanager.domain.agent

import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.engines.FileEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkspaceEngine {

    /**
     * Creates an isolated workspace directory.
     */
    suspend fun setupWorkspace(sourceFiles: List<String>): String = withContext(Dispatchers.IO) {
        val workspaceId = UUID.randomUUID().toString()
        val workspaceDir = File(AppPaths.filesDir, "workspace_$workspaceId")

        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        workspaceDir.absolutePath
    }

    /**
     * Commits actions from the workspace to the real filesystem.
     * Uses a snapshot mechanism to allow rollback on failure.
     */
    suspend fun commitWorkspace(actions: List<FileAction>): Result<Unit> = withContext(Dispatchers.IO) {
        val snapshotDir = File(AppPaths.filesDir, "snapshot_${UUID.randomUUID()}")
        snapshotDir.mkdirs()

        val completedActions = mutableListOf<FileAction>()

        try {
            actions.forEach { action ->
                val sourceFile = File(action.sourcePath)

                when (action.type) {
                    FileActionType.MOVE -> {
                        val dest = File(action.destinationPath ?: throw IllegalArgumentException("Destination required for MOVE"))
                        if (dest.exists()) {
                            FileEngine.copyFile(dest, File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}"))
                        }
                        FileEngine.moveFile(sourceFile, dest)
                        completedActions.add(action)
                    }
                    FileActionType.COPY -> {
                        val dest = File(action.destinationPath ?: throw IllegalArgumentException("Destination required for COPY"))
                        if (dest.exists()) {
                            FileEngine.copyFile(dest, File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}"))
                        }
                        FileEngine.copyFile(sourceFile, dest)
                        completedActions.add(action)
                    }
                    FileActionType.DELETE -> {
                        if (sourceFile.exists()) {
                            FileEngine.moveFile(sourceFile, File(snapshotDir, "${sourceFile.absolutePath.hashCode()}_${sourceFile.name}"))
                        }
                        completedActions.add(action)
                    }
                    FileActionType.CREATE -> {
                        val dest = File(action.destinationPath ?: throw IllegalArgumentException("Destination required for CREATE"))
                        if (dest.exists()) {
                            FileEngine.copyFile(dest, File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}"))
                        }
                        FileEngine.createFile(dest.absolutePath, sourceFile, action.overwrite)
                        completedActions.add(action)
                    }
                }
            }

            // Success, clean up snapshot
            snapshotDir.deleteRecursively()
            Result.success(Unit)

        } catch (e: kotlinx.coroutines.CancellationException) {
            // If the coroutine is cancelled (e.g. Hard Stop), do not rollback, just abort and throw
            snapshotDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            // Rollback
            completedActions.reversed().forEach { action ->
                try {
                    val originalSource = File(action.sourcePath)
                    when (action.type) {
                        FileActionType.MOVE -> {
                            val dest = File(action.destinationPath!!)
                            FileEngine.moveFile(dest, originalSource) // Move back
                            val snapshotFile = File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}")
                            if (snapshotFile.exists()) {
                                FileEngine.moveFile(snapshotFile, dest) // Restore overwritten
                            }
                        }
                        FileActionType.COPY -> {
                            val dest = File(action.destinationPath!!)
                            if (dest.exists()) dest.delete()
                            val snapshotFile = File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}")
                            if (snapshotFile.exists()) {
                                FileEngine.moveFile(snapshotFile, dest)
                            }
                        }
                        FileActionType.DELETE -> {
                            val snapshotFile = File(snapshotDir, "${originalSource.absolutePath.hashCode()}_${originalSource.name}")
                            if (snapshotFile.exists()) {
                                FileEngine.moveFile(snapshotFile, originalSource)
                            }
                        }
                        FileActionType.CREATE -> {
                             val dest = File(action.destinationPath!!)
                             if (dest.exists()) dest.delete()
                             val snapshotFile = File(snapshotDir, "${dest.absolutePath.hashCode()}_${dest.name}")
                             if (snapshotFile.exists()) {
                                FileEngine.moveFile(snapshotFile, dest)
                             }
                        }
                    }
                } catch (rollbackEx: Exception) {
                    rollbackEx.printStackTrace() // Log but try to continue rollback
                }
            }
            snapshotDir.deleteRecursively()
            Result.failure(e)
        }
    }

    /**
     * Deletes the workspace directory (Soft Stop / Hard Stop / Post-Commit Cleanup).
     */
    suspend fun cleanupWorkspace(workspacePath: String) = withContext(Dispatchers.IO) {
        val dir = File(workspacePath)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
