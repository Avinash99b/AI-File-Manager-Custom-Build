package com.aviansh.aifilemanager.domain.transactions

import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import java.io.File
import java.security.MessageDigest

enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH
}

fun FileAction.riskLevel(): RiskLevel = when (type) {
    FileActionType.DELETE -> RiskLevel.HIGH
    FileActionType.MOVE -> {
        val destExists = destinationPath?.let { File(it).exists() } == true
        if (destExists || overwrite) RiskLevel.HIGH else RiskLevel.MODERATE
    }
    FileActionType.COPY -> {
        val destExists = destinationPath?.let { File(it).exists() } == true
        if (destExists && overwrite) RiskLevel.HIGH else RiskLevel.SAFE
    }
    FileActionType.CREATE -> if (overwrite) RiskLevel.MODERATE else RiskLevel.SAFE
}

data class PlanBinding(
    val actions: List<FileAction>,
    val explanation: String
) {
    val planHash: String by lazy {
        val raw = actions.joinToString("|") { "${it.type}:${it.sourcePath}:${it.destinationPath}:${it.overwrite}" } + "||" + explanation
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        digest.joinToString("") { "%02x".format(it) }
    }
}

data class PreflightSummary(
    val isValid: Boolean,
    val actionsByRisk: Map<RiskLevel, Int>,
    val conflictsCount: Int,
    val affectedPaths: List<String>,
    val validationErrors: List<String>
)

sealed class RollbackOutcome {
    object Success : RollbackOutcome()
    data class Partial(val failures: List<String>) : RollbackOutcome()
    data class Failed(val error: String) : RollbackOutcome()
}

data class FailureDiagnosis(
    val failedAction: FileAction?,
    val errorMessage: String,
    val affectedPaths: List<String>,
    val rollbackOutcome: RollbackOutcome
) : Exception(errorMessage)
