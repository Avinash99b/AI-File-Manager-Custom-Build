package com.aviansh.aifilemanager.domain.engines

import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.data.TransactionResult
import com.aviansh.aifilemanager.domain.data.TransactionState
import com.aviansh.aifilemanager.domain.data.TransactionStatus
import com.aviansh.aifilemanager.domain.data.generateInverseAction
import com.aviansh.aifilemanager.domain.data.getSnapshotsDir
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class TransactionEngine {

    val inProgress = AtomicBoolean(false)

    var transactionState: TransactionState = TransactionState(
        id = generateTransactionId(),
        status = TransactionStatus.IDLE,
        actions = emptyList()
    )

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    fun generateTransactionId(): Long = System.currentTimeMillis()

    /**
     * Begins a new transaction with the given [actions].
     * Does nothing if a transaction is already in progress.
     */
    fun begin(actions: List<FileAction>) {
        if (!inProgress.compareAndSet(expectedValue = false, newValue = true)) return
        transactionState = TransactionState(
            id = generateTransactionId(),
            status = TransactionStatus.BEGIN,
            actions = actions
        )
    }

    /**
     * Executes a full begin → execute → commit/rollback cycle from [actions].
     * This is the primary entry point when you already have a parsed action list.
     */
    fun run(actions: List<FileAction>): TransactionResult {
        begin(actions)
        return execute()
    }

    /**
     * Executes a full begin → execute → commit/rollback cycle from Python generator code.
     * Chaquopy parses the generator output into actions, then the transaction runs.
     */
    fun runFromGeneratorCode(generatorCode: String): TransactionResult {
        return try {
            val actions = PythonEngine.generateActions(generatorCode)
            run(actions)
        } catch (e: Exception) {
            TransactionResult.FAILURE("Generator error: ${e.message ?: "unknown"}")
        }
    }

    // ─── Execution ────────────────────────────────────────────────────────────

    fun execute(): TransactionResult {
        if (transactionState.status == TransactionStatus.IDLE) {
            return TransactionResult.FAILURE("Transaction not begun")
        }

        val snapshotDir = transactionState.snapshotDir
        if (snapshotDir.exists()) {
            return TransactionResult.FAILURE(
                "Snapshot directory already exists for id=${transactionState.id}"
            )
        }
        if (!snapshotDir.mkdirs()) {
            throw IOException("Could not create snapshot directory: ${snapshotDir.absolutePath}")
        }

        return try {
            transactionState.actions.forEach { action ->
                runAction(action)
                transactionState.completedActions.add(action)
            }
            commit()
            transactionState.status = TransactionStatus.SUCCESS
            transactionState = TransactionState(
                id = generateTransactionId(),
                status = TransactionStatus.IDLE,
                actions = emptyList()
            )
            TransactionResult.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            rollback()
            TransactionResult.FAILURE(e.message ?: "Unknown error during transaction")
        } finally {
            inProgress.store(false)
        }
    }

    // ─── Action runner ────────────────────────────────────────────────────────

    fun runAction(action: FileAction) {
        val snapshotDir = transactionState.snapshotDir
        val sourceFile = File(action.sourcePath)

        when (action.type) {
            FileActionType.MOVE -> {
                val dest = File(
                    action.destinationPath
                        ?: throw FileNotFoundException("Destination path required for MOVE")
                )
                FileEngine.moveFile(sourceFile, dest)
            }

            FileActionType.DELETE -> {
                // Move to snapshot so we can restore on rollback
                FileEngine.moveFile(sourceFile, File(snapshotDir, sourceFile.name))
            }

            FileActionType.COPY -> {
                val dest = File(
                    action.destinationPath
                        ?: throw FileNotFoundException("Destination path required for COPY")
                )
                // Snapshot any existing file at dest so it can be restored
                if (dest.exists()) {
                    FileEngine.copyFile(dest, File(snapshotDir, dest.name))
                }
                FileEngine.copyFile(sourceFile, dest)
            }

            FileActionType.CREATE -> {
                val dest = File(
                    action.destinationPath
                        ?: throw FileNotFoundException("Destination path required for CREATE")
                )
                // Snapshot existing file at dest
                if (dest.exists()) {
                    FileEngine.copyFile(dest, File(snapshotDir, dest.name))
                }
                FileEngine.createFile(action.sourcePath, dest, action.overwrite)
            }

        }
    }

    // ─── Commit / Rollback ────────────────────────────────────────────────────

    /**
     * Prunes the oldest snapshot directory when we exceed [AppPaths.MAX_SNAPSHOTS].
     */
    private fun commit() {
        val snapshotsDir = getSnapshotsDir()
        val snapshots = snapshotsDir.listFiles { f -> f.isDirectory }
            ?: return
        if (snapshots.size > AppPaths.MAX_SNAPSHOTS) {
            // Sort by name (which is the transaction timestamp) — oldest first
            snapshots.sortBy { it.name.toLongOrNull() ?: 0L }
            snapshots.take(snapshots.size - AppPaths.MAX_SNAPSHOTS)
                .forEach { it.deleteRecursively() }
        }
    }

    private fun rollback() {
        transactionState.completedActions.reversed().forEach { action ->
            try {
                action.generateInverseAction(transactionState.id)?.let {
                    runAction(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Clean up snapshot dir used during this failed transaction
        transactionState.snapshotDir.deleteRecursively()
        transactionState.status = TransactionStatus.ERROR
    }
}