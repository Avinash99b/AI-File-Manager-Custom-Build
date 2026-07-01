package com.aviansh.aifilemanager.domain.data

sealed class TransactionProgress {
    object Idle : TransactionProgress()
    data class Pending(val actions: List<FileAction>) : TransactionProgress()
    object Running : TransactionProgress()
    data class Succeeded(val actionCount: Int) : TransactionProgress()
    data class Failed(val reason: String) : TransactionProgress()
    data class RolledBack(val reason: String) : TransactionProgress()
}
