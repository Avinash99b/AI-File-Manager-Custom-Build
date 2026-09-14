package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class BreadcrumbSegment(
    val name: String,
    val path: String
)

@Composable
fun PathHeader(
    currentPath: String,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (String) -> Unit = {}
) {
    val segments = buildBreadcrumbs(currentPath)
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate Up",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                segments.forEachIndexed { index, segment ->
                    AssistChip(
                        onClick = { onNavigateToPath(segment.path) },
                        label = {
                            if (index == 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = "Home Storage",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(segment.name, fontSize = 12.sp)
                                }
                            } else {
                                Text(segment.name, fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (index == segments.lastIndex)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (index == segments.lastIndex)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    if (index < segments.lastIndex) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp).padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildBreadcrumbs(path: String): List<BreadcrumbSegment> {
    val clean = File(path).absolutePath
    val rootPath = "/storage/emulated/0"
    val result = mutableListOf<BreadcrumbSegment>()

    if (clean.startsWith(rootPath)) {
        result.add(BreadcrumbSegment("Internal", rootPath))
        val subParts = clean.removePrefix(rootPath).split("/").filter { it.isNotBlank() }
        var currentAcc = rootPath
        subParts.forEach { part ->
            currentAcc = "$currentAcc/$part"
            result.add(BreadcrumbSegment(part, currentAcc))
        }
    } else {
        val parts = clean.split("/").filter { it.isNotBlank() }
        var currentAcc = ""
        parts.forEach { part ->
            currentAcc = "$currentAcc/$part"
            result.add(BreadcrumbSegment(part, currentAcc))
        }
    }
    return if (result.isEmpty()) listOf(BreadcrumbSegment("Root", "/")) else result
}
