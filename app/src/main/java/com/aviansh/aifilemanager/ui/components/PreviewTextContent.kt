package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun PreviewTextContent(filePath: String) {
    val contentState by produceState<Result<String>>(
        initialValue = Result.success("Loading preview..."),
        key1 = filePath
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Result.failure(Exception("File no longer exists."))
                } else {
                    val maxBytes = 64 * 1024
                    val bytes = ByteArray(maxBytes)
                    val bytesRead = FileInputStream(file).use { input ->
                        input.read(bytes, 0, maxBytes)
                    }
                    if (bytesRead <= 0) {
                        Result.success("(Empty file)")
                    } else {
                        val text = String(bytes, 0, bytesRead, Charsets.UTF_8)
                        val trimmed = if (text.length > 500) text.take(500) + "...\n[Preview truncated]" else text
                        Result.success(trimmed)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.background
    ) {
        val text = contentState.getOrElse { e -> "Error loading preview: ${e.message}" }
        Text(
            text = text,
            color = if (contentState.isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(12.dp),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}
