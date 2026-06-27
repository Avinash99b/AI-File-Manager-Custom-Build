package com.aviansh.aifilemanager.domain.engines

import androidx.compose.ui.util.fastForEachReversed
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

    fun generateTransactionId(): Long{
        return System.currentTimeMillis()
    }

    var transactionState: TransactionState = TransactionState(generateTransactionId(),TransactionStatus.IDLE, emptyList())

    fun begin(actions: List<FileAction>){
        if (!inProgress.compareAndSet(expectedValue = false, newValue = true)) return
        transactionState = TransactionState(generateTransactionId(),TransactionStatus.BEGIN, actions)
    }
    fun getSnapShotFilesDir(tId: Long): File{
        return File(getSnapshotsDir(), tId.toString())
    }


    fun execute(): TransactionResult{
        if(transactionState.status== TransactionStatus.IDLE)return TransactionResult.FAILURE("Transaction Not begun")

        if(transactionState.snapshotDir.exists()) return TransactionResult.FAILURE("Snapshot Directory Already Exists, DIE U damn entropy")
        if (!transactionState.snapshotDir.mkdirs())
            throw IOException("Couldn't create snapshot directory")
        try{
            transactionState.actions.forEach { action->
                runAction(action)
                transactionState.completedActions.add(action)
            }
            commit()
            transactionState.status= TransactionStatus.SUCCESS
        }catch (e: Exception){
            e.printStackTrace()
            rollback()
            return TransactionResult.FAILURE(e.message?:"Unknown error by entropy")
        }finally {
            inProgress.store(false)
        }
        transactionState= TransactionState(generateTransactionId(),TransactionStatus.IDLE, emptyList())
        return TransactionResult.SUCCESS
    }

    // Runs
    fun runAction(action: FileAction){
        val snapshotDir = transactionState.snapshotDir
        val sourceFile = File(action.sourcePath)
        when(action.type){
            FileActionType.MOVE -> {
                if(action.destinationPath==null) throw FileNotFoundException("Destination Path is needed")
                FileEngine.moveFile(sourceFile, File(action.destinationPath))
            }
            FileActionType.DELETE -> {
                FileEngine.moveFile(sourceFile, File(snapshotDir, sourceFile.name))
            }
            FileActionType.COPY -> {
                if(action.destinationPath==null) throw FileNotFoundException("Destination Path is needed")
                FileEngine.copyFile(File(action.destinationPath), File(snapshotDir, File(action.destinationPath).name))
                FileEngine.copyFile(sourceFile, File(action.destinationPath))
            }
            FileActionType.CREATE -> {
                if(action.destinationPath==null) throw FileNotFoundException("Destination Path is needed")
                FileEngine.createFile(action.sourcePath, File(action.destinationPath), action.overwrite)
            }
        }
    }
    fun commit(){
        val snapShotsDir = getSnapshotsDir()
        val snapshots = snapShotsDir.list()
            ?: throw FileNotFoundException("Snapshots dir not found, curse entropy")
        val oldestSnapshot = File(snapShotsDir, snapshots.min())
        oldestSnapshot.deleteRecursively()
    }

    fun rollback(){
        transactionState.completedActions.reversed().forEach {
            try{

                runAction(it.generateInverseAction(transactionState.id))
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
        transactionState.status= TransactionStatus.ERROR
    }
}