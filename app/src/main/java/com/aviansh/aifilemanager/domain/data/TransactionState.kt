package com.aviansh.aifilemanager.domain.data

import com.aviansh.aifilemanager.domain.AppPaths
import java.io.File

enum class TransactionStatus{IDLE, BEGIN, SUCCESS, ERROR}

data class TransactionState(
    var status: TransactionStatus,
    val actions: List<FileAction>,
    val completedActions: ArrayList<FileAction> =arrayListOf()
)

sealed class TransactionResult{
    object SUCCESS: TransactionResult()
    data class FAILURE(val reason: String): TransactionResult()
}