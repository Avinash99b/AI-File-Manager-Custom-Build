package com.aviansh.aifilemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    onGrantPermission: () -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                "This app requires Full File Access."
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onGrantPermission
            ) {

                Text("Grant Permission")

            }

        }

    }

}