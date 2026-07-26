package com.aviansh.aifilemanager.domain.data


data class ParsedAIResponse(
    val actionable: Boolean,
    val generatorCode: String?,
    val message: String?,
    val explanation: String? = null,
    val actions: List<FileAction>? = null
)