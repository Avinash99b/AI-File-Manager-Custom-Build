package com.aviansh.aifilemanager.domain.sandbox

import java.io.File

data class PythonExecutionRequest(
    val code: String,
    val workspaceDir: String? = null,
    val allowedReadRoots: List<File> = emptyList(),
    /**
     * Directories the Python runtime itself must be able to write to for imports to work
     * (Chaquopy asset extraction, bytecode caches, temp files created by libraries such as
     * pypdf, pandas or matplotlib). These are infrastructure paths, not user data, and are
     * always writable regardless of the workspace restriction.
     */
    val infraWriteRoots: List<File> = emptyList(),
    val timeoutMillis: Long = 60_000L,
    val maxOutputBytes: Int = 64 * 1024,
    val denyNetwork: Boolean = true
)
