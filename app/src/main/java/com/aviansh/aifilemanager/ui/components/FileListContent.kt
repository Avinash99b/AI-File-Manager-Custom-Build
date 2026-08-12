package com.aviansh.aifilemanager.ui.components

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
import com.aviansh.aifilemanager.ui.data.ProgressQuad
import com.aviansh.aifilemanager.ui.screens.DarkThemeColors

@Composable
fun FileListContent(
    files: List<FileItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    onPreview: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            files,
            key = { it.id }
        ) { fileItem ->
            FileListItem(
                fileItem = fileItem,
                onNavigate = onNavigate,
                onSelect = onSelect,
                onDelete = onDelete,
                onRename = onRename,
                getFormattedSize = getFormattedSize,
                getFormattedDate = getFormattedDate,
                onPreview = onPreview
            )
        }
    }
}
