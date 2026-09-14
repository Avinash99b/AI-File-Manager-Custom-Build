package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.ui.screens.DarkThemeColors

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(verticalArrangement = Arrangement.Center) {
            Icon(
                Icons.Default.FolderOpen,
                null,
                modifier = Modifier.size(56.dp),
                tint = DarkThemeColors.TextTertiary
            )
            Text(
                "Empty folder",
                color = DarkThemeColors.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 14.sp
            )
        }
    }
}

