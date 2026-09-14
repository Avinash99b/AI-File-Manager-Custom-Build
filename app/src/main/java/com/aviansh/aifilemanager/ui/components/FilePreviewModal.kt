package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aviansh.aifilemanager.domain.repository.FileItem
import com.aviansh.aifilemanager.ui.screens.getFileIcon
import com.aviansh.aifilemanager.ui.screens.getFileType
import com.aviansh.aifilemanager.ui.screens.getFormattedDateForPreview
import com.aviansh.aifilemanager.ui.screens.getFormattedSizeForPreview
import com.aviansh.aifilemanager.ui.screens.isTextFile
import java.io.File

@Composable
fun FilePreviewModal(
    fileItem: FileItem?,
    onDismiss: () -> Unit
) {
    if (fileItem == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileItem.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Preview Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFileImage(fileItem.name)) {
                        AsyncImage(
                            model = File(fileItem.path),
                            contentDescription = fileItem.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = getFileIcon(fileItem.name),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                PreviewDetailRow("Name", fileItem.name)
                PreviewDetailRow("Size", getFormattedSizeForPreview(fileItem.size))
                PreviewDetailRow("Type", getFileType(fileItem.name))
                PreviewDetailRow("Path", fileItem.path)
                PreviewDetailRow("Modified", getFormattedDateForPreview(fileItem.lastModified))

                Spacer(modifier = Modifier.height(16.dp))

                if (isTextFile(fileItem.name)) {
                    Text(
                        text = "Content Preview",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    PreviewTextContent(fileItem.path)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Close")
            }
        }
    )
}

fun isFileImage(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "")
        .lowercase() in setOf(
        "jpg",
        "jpeg",
        "png",
        "gif",
        "bmp",
        "webp",
        "heic",
        "heif",
        "avif"
    )
}
