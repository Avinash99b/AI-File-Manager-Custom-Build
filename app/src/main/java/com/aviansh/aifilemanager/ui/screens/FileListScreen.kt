package com.aviansh.aifilemanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonColors
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.FileAction

import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.ui.components.EmptyState
import com.aviansh.aifilemanager.ui.components.ErrorState
import com.aviansh.aifilemanager.ui.components.FileListContent
import com.aviansh.aifilemanager.ui.components.FilePreviewModal
import com.aviansh.aifilemanager.ui.components.LoadingPlaceholder
import com.aviansh.aifilemanager.ui.components.PathHeader
import java.io.File

// ─── DARK THEME PALETTE ───────────────────────────────────────────────────────
object DarkThemeColors {
    val Background      = Color(0xFF0A0A0A)      // AMOLED Black
    val Surface         = Color(0xFF1A1A1A)      // Dark gray
    val SurfaceLight    = Color(0xFF2A2A2A)      // Lighter dark gray
    val Primary         = Color(0xFF7C3AED)      // Vibrant Purple
    val PrimaryLight    = Color(0xFF9F5FFF)      // Lighter Purple
    val Accent          = Color(0xFF10B981)      // Emerald Green
    val AccentLight     = Color(0xFF34D399)      // Light Emerald
    val TextPrimary     = Color(0xFFFAFAFA)      // Almost white
    val TextSecondary   = Color(0xFFA0A0A0)      // Medium gray
    val TextTertiary    = Color(0xFF707070)      // Darker gray
    val Error           = Color(0xFFEF4444)      // Bright red
    val Warning         = Color(0xFFF59E0B)      // Amber
    val Success         = Color(0xFF10B981)      // Green
    val Divider         = Color(0xFF333333)      // Subtle divider
}

@Composable
fun FileListScreen(
    files: List<FileItem>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    selectedFile: FileItem?,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    onRetry: () -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    modifier: Modifier = Modifier
) {
    var previewFile by remember { mutableStateOf<FileItem?>(null) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkThemeColors.Background)
    ) {
        // Path Header
        PathHeader(currentPath, onNavigateUp)

        when {
            isLoading -> LoadingPlaceholder()
            error != null -> ErrorState(error, onRetry)
            files.isEmpty() -> EmptyState()
            else -> FileListContent(
                files,
                listState,
                onNavigate,
                onSelect,
                onDelete,
                onRename,
                getFormattedSize,
                getFormattedDate,
                onPreview = { previewFile = it }
            )
        }
    }

    // File Preview Modal
    if (previewFile != null) {
        FilePreviewModal(previewFile) { previewFile = null }
    }
}







fun getFileIcon(fileName: String): ImageVector =
    when {
        fileName.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z") ->
            Icons.Default.Archive
        fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".m4a") ->
            Icons.Default.AudioFile
        fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".mov") ->
            Icons.Default.VideoFile
        fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".gif") ->
            Icons.Default.Image
        fileName.endsWith(".txt") || fileName.endsWith(".doc") || fileName.endsWith(".docx") ->
            Icons.Default.Description
        fileName.endsWith(".xls") || fileName.endsWith(".xlsx") || fileName.endsWith(".csv") ->
            Icons.Default.TableChart
        else -> Icons.Default.InsertDriveFile
    }

fun getFileType(fileName: String): String =
    when {
        fileName.endsWith(".pdf") -> "PDF Document"
        fileName.endsWith(".zip") || fileName.endsWith(".rar") -> "Compressed Archive"
        fileName.endsWith(".mp3") -> "MP3 Audio"
        fileName.endsWith(".mp4") -> "MP4 Video"
        fileName.endsWith(".jpg") || fileName.endsWith(".png") -> "Image"
        fileName.endsWith(".txt") -> "Text File"
        else -> "File"
    }

fun isTextFile(fileName: String): Boolean =
    fileName.endsWith(".txt") ||
            fileName.endsWith(".md") ||
            fileName.endsWith(".log") ||
            fileName.endsWith(".json") ||
            fileName.endsWith(".xml")

fun getFormattedSizeForPreview(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

fun getFormattedDateForPreview(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}