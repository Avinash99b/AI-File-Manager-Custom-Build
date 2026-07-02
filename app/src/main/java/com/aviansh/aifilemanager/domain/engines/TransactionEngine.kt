package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.data.TransactionResult
import com.aviansh.aifilemanager.domain.data.TransactionState
import com.aviansh.aifilemanager.domain.data.TransactionStatus
import com.aviansh.aifilemanager.domain.data.generateInverseAction
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class TransactionEngine {

    val inProgress = AtomicBoolean(false)

    val tmpDir = File(AppPaths.filesDir, "tmpFilesDir")
    var transactionState: TransactionState = TransactionState(
        status = TransactionStatus.IDLE,
        actions = emptyList()
    )


    fun begin(actions: List<FileAction>) {
        if (!inProgress.compareAndSet(expectedValue = false, newValue = true)) return
        tmpDir.deleteRecursively()
        transactionState = TransactionState(
            status = TransactionStatus.BEGIN,
            actions = actions
        )
    }

    fun run(actions: List<FileAction>): TransactionResult {
        begin(actions)
        return execute()
    }



    fun execute(): TransactionResult {
        if (transactionState.status == TransactionStatus.IDLE) {
            return TransactionResult.FAILURE("Transaction not begun")
        }

        return try {
            transactionState.actions.forEach { action ->
                runAction(action)
                transactionState.completedActions.add(action)
            }
            commit()
            transactionState.status = TransactionStatus.SUCCESS
            transactionState = TransactionState(
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
                FileEngine.moveFile(sourceFile, File(tmpDir, sourceFile.name))
            }

            FileActionType.COPY -> {
                val dest = File(
                    action.destinationPath
                        ?: throw FileNotFoundException("Destination path required for COPY")
                )
                // Snapshot any existing file at dest so it can be restored
                if (dest.exists()) {
                    FileEngine.copyFile(dest, File(tmpDir, dest.name))
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
                    FileEngine.copyFile(dest, File(tmpDir, dest.name))
                }
                FileEngine.createFile(action.sourcePath, dest, action.overwrite)
            }

        }
    }


    private fun commit() {
        Log.e("TransactionEngine", "All operations completed and committed")
    }

    private fun rollback() {
        transactionState.completedActions.reversed().forEach { action ->
            try {
                action.generateInverseAction()?.let {
                    runAction(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        transactionState.status = TransactionStatus.ERROR
    }
}