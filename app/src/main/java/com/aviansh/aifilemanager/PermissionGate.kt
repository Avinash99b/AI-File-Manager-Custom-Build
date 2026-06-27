package com.aviansh.aifilemanager

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.aviansh.aifilemanager.ui.screens.AIChatScreen
import com.aviansh.aifilemanager.ui.screens.PermissionScreen

@Composable
fun PermissionGate() {

    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            PermissionUtils.hasManageStoragePermission()
        )
    }

    LaunchedEffect(Unit) {
        granted = PermissionUtils.hasManageStoragePermission()
    }

    if (granted) {

        AIChatScreen()

    } else {

        PermissionScreen {

            PermissionUtils.openPermissionSettings(context)

        }

    }

}