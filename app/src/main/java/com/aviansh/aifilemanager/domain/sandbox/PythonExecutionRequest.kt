package com.aviansh.aifilemanager.domain.sandbox

import java.io.File

data class PythonExecutionRequest(
    val code: String,
    val workspaceDir: String? = null,
    val allowedReadRoots: List<File> = emptyList(),
    val timeoutMillis: Long = 10_000L,
    val maxOutputBytes: Int = 64 * 1024,
    val denyNetwork: Boolean = true
)
