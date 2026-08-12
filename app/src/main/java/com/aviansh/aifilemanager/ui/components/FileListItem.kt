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
import com.aviansh.aifilemanager.ui.screens.getFileIcon

@Composable
fun FileListItem(
    fileItem: FileItem,
    onNavigate: (FileItem) -> Unit,
    onSelect: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit,
    getFormattedSize: (Long) -> String,
    getFormattedDate: (Long) -> String,
    onPreview: (FileItem) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(fileItem.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onSelect(fileItem)
                if (fileItem.isDirectory) {
                    onNavigate(fileItem)
                } else {
                    onPreview(fileItem)
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkThemeColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (fileItem.isDirectory)
                                DarkThemeColors.Primary.copy(alpha = 0.2f)
                            else
                                DarkThemeColors.Accent.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fileItem.isDirectory)
                            Icons.Default.Folder
                        else
                            getFileIcon(fileItem.name),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (fileItem.isDirectory)
                            DarkThemeColors.Primary
                        else
                            DarkThemeColors.Accent
                    )
                }

                // File Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkThemeColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (!fileItem.isDirectory) {
                            Text(
                                getFormattedSize(fileItem.size),
                                fontSize = 12.sp,
                                color = DarkThemeColors.TextTertiary
                            )
                            Text("·", fontSize = 12.sp, color = DarkThemeColors.TextTertiary)
                        }
                        Text(
                            getFormattedDate(fileItem.lastModified),
                            fontSize = 12.sp,
                            color = DarkThemeColors.TextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Menu Button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = DarkThemeColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fixed Dropdown Menu with proper positioning
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkThemeColors.SurfaceLight)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = DarkThemeColors.TextPrimary) },
                        onClick = { showMenu = false; showRenameDialog = true },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = DarkThemeColors.Primary
                            )
                        }
                    )
                    if (fileItem.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Properties", color = DarkThemeColors.TextPrimary) },
                            onClick = { showMenu = false },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = DarkThemeColors.Primary
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = DarkThemeColors.Error) },
                        onClick = { showMenu = false; onDelete(fileItem) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = DarkThemeColors.Error
                            )
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = DarkThemeColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name", color = DarkThemeColors.TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkThemeColors.Primary,
                        unfocusedBorderColor = DarkThemeColors.Divider,
                        focusedTextColor = DarkThemeColors.TextPrimary,
                        unfocusedTextColor = DarkThemeColors.TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) onRename(fileItem, renameInput)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkThemeColors.Primary)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false },
                    colors = TextButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = DarkThemeColors.TextSecondary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = DarkThemeColors.TextTertiary
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = DarkThemeColors.SurfaceLight,
            titleContentColor = DarkThemeColors.TextPrimary
        )
    }
}