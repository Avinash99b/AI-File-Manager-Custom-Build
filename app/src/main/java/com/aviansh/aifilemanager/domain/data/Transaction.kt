package com.aviansh.aifilemanager.domain.data

data class Transaction(
    val id: String,
    val actions: List<FileAction>,
    val prompt: String,
    val fullAiInteraction: String,
    val createdAt: Long
)
