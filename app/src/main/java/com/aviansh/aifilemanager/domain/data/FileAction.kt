package com.aviansh.aifilemanager.domain.data

enum class FileActionType { MOVE, DELETE, COPY, CREATE }

data class FileAction(
    val type: FileActionType,
    val sourcePath: String,
    val destinationPath: String? = null,
    val overwrite: Boolean = false,
    val comment: String = ""
)
