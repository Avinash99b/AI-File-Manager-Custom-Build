package com.aviansh.aifilemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviansh.aifilemanager.ui.screens.DarkThemeColors


@Composable
fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = DarkThemeColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Loading files…",
                color = DarkThemeColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp),
                fontSize = 14.sp
            )
        }
    }
}