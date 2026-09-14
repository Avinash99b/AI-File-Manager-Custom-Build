package com.aviansh.aifilemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.ui.components.EmptyState
import com.aviansh.aifilemanager.ui.components.ErrorState
import com.aviansh.aifilemanager.ui.components.FileListContent
import com.aviansh.aifilemanager.ui.components.FilePreviewModal
import com.aviansh.aifilemanager.ui.components.LoadingPlaceholder
import com.aviansh.aifilemanager.ui.components.PathHeader
import com.aviansh.aifilemanager.ui.vm.FilterOption
import com.aviansh.aifilemanager.ui.vm.SortOption

// Legacy DarkThemeColors alias referencing MaterialTheme.colorScheme for backward compatibility
object DarkThemeColors {
    val Background      @Composable get() = MaterialTheme.colorScheme.background
    val Surface         @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceLight    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val Primary         @Composable get() = MaterialTheme.colorScheme.primary
    val PrimaryLight    @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Accent          @Composable get() = MaterialTheme.colorScheme.tertiary
    val AccentLight     @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val TextPrimary     @Composable get() = MaterialTheme.colorScheme.onSurface
    val TextSecondary   @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val TextTertiary    @Composable get() = MaterialTheme.colorScheme.outline
    val Error           @Composable get() = MaterialTheme.colorScheme.error
    val Warning         @Composable get() = MaterialTheme.colorScheme.errorContainer
    val Success         @Composable get() = MaterialTheme.colorScheme.primary
    val Divider         @Composable get() = MaterialTheme.colorScheme.outlineVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    files: List<FileItem>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    selectedFile: FileItem?,
    selectedPaths: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    searchQuery: String = "",
    sortOption: SortOption = SortOption.NAME_ASC,
    filterOption: FilterOption = FilterOption.ALL,
    hasClipboard: Boolean = false,
    onNavigate: (FileItem) -> Unit,
    onNavigateToPath: (String) -> Unit = {},
    onSelect: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onNavigateUp: () -> Unit,
    onDelete: (FileItem) -> Unit,
    onDeleteSelected: () -> Unit = {},
    onCopySelected: (isCut: Boolean) -> Unit = {},
    onPaste: () -> Unit = {},
    onShareSelected: () -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSortChange: (SortOption) -> Unit = {},
    onFilterChange: (FilterOption) -> Unit = {},
    onRename: (FileItem, String) -> Unit,
    onRetry: () -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    modifier: Modifier = Modifier
) {
    var previewFile by remember { mutableStateOf<FileItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Multi-select Contextual Action Bar or Standard Path Header
        if (isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                        Text(
                            text = "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            onClick = { onCopySelected(false) },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(
                            onClick = { onCopySelected(true) },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                        }
                        IconButton(
                            onClick = onShareSelected,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(
                            onClick = onDeleteSelected,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            PathHeader(
                currentPath = currentPath,
                onNavigateUp = onNavigateUp,
                onNavigateToPath = onNavigateToPath
            )
        }

        // Search Bar & Utility Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search files...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp)
            )

            // Create Folder Button
            IconButton(
                onClick = { showCreateFolderDialog = true },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Folder", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            // Paste Button (if clipboard active)
            if (hasClipboard) {
                IconButton(
                    onClick = onPaste,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Files", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Name (A-Z)") },
                        onClick = { onSortChange(SortOption.NAME_ASC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.NAME_ASC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (Z-A)") },
                        onClick = { onSortChange(SortOption.NAME_DESC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.NAME_DESC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Newest)") },
                        onClick = { onSortChange(SortOption.DATE_DESC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.DATE_DESC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest)") },
                        onClick = { onSortChange(SortOption.DATE_ASC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.DATE_ASC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest)") },
                        onClick = { onSortChange(SortOption.SIZE_DESC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.SIZE_DESC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Smallest)") },
                        onClick = { onSortChange(SortOption.SIZE_ASC); showSortMenu = false },
                        leadingIcon = { if (sortOption == SortOption.SIZE_ASC) Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterOption.entries.forEach { filter ->
                FilterChip(
                    selected = filterOption == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                )
            }
        }

        // Main File List / States
        when {
            isLoading -> LoadingPlaceholder()
            error != null -> ErrorState(error, onRetry)
            files.isEmpty() -> EmptyState()
            else -> FileListContent(
                files = files,
                listState = listState,
                selectedPaths = selectedPaths,
                isSelectionMode = isSelectionMode,
                onNavigate = onNavigate,
                onSelect = onSelect,
                onToggleSelect = onToggleSelect,
                onLongClickSelect = { fileItem ->
                    onToggleSelect(fileItem)
                },
                onDelete = onDelete,
                onRename = onRename,
                getFormattedSize = getFormattedSize,
                getFormattedDate = getFormattedDate,
                onPreview = { previewFile = it }
            )
        }
    }

    if (previewFile != null) {
        FilePreviewModal(previewFile) { previewFile = null }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            onCreateFolder(folderNameInput)
                            folderNameInput = ""
                        }
                        showCreateFolderDialog = false
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateFolderDialog = false },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
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
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
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
