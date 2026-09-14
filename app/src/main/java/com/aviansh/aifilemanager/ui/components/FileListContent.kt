package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aviansh.aifilemanager.domain.repository.FileItem

@Composable
fun FileListContent(
    files: List<FileItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPaths: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit = {},
    onLongClickSelect: (FileItem) -> Unit = {},
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
                isSelected = selectedPaths.contains(fileItem.path),
                isSelectionMode = isSelectionMode,
                onNavigate = onNavigate,
                onSelect = onSelect,
                onToggleSelect = onToggleSelect,
                onLongClickSelect = onLongClickSelect,
                onDelete = onDelete,
                onRename = onRename,
                getFormattedSize = getFormattedSize,
                getFormattedDate = getFormattedDate,
                onPreview = onPreview
            )
        }
    }
}
