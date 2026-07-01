package com.aviansh.aifilemanager.domain.data


data class AIResponse(
    val actionable: Boolean,
    val generatorCode: String?,
    val message: String?,
    val actions: List<FileAction>? = null
)