package com.aviansh.aifilemanager.domain.data

import com.aviansh.aifilemanager.domain.AppPaths
import java.io.File

enum class TransactionStatus{IDLE, BEGIN, EXECUTING, SUCCESS, ERROR}

data class TransactionState(
    val id: Long,
    var status: TransactionStatus,
    val actions: List<FileAction>,
    val completedActions: ArrayList<FileAction> =arrayListOf(),
    val snapshotDir: File = File(AppPaths.filesDir, id.toString())
)

sealed class TransactionResult{
    object SUCCESS: TransactionResult()
    data class FAILURE(val reason: String): TransactionResult()
}