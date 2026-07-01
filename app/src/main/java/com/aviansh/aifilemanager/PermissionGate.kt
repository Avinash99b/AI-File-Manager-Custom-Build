package com.aviansh.aifilemanager

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.aviansh.aifilemanager.ui.screens.FileListScreen
import com.aviansh.aifilemanager.ui.screens.FileManagerScreen
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

        FileManagerScreen(context)

    } else {

        PermissionScreen {

            PermissionUtils.openPermissionSettings(context)

        }

    }

}