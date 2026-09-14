package com.aviansh.aifilemanager

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aviansh.aifilemanager.ui.Routes
import com.aviansh.aifilemanager.ui.screens.FileManagerScreen
import com.aviansh.aifilemanager.ui.screens.GeminiSettingsRoute
import com.aviansh.aifilemanager.ui.screens.PermissionScreen

@Composable
fun PermissionGate() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            PermissionUtils.hasManageStoragePermission()
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasManageStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (granted) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                FileManagerScreen(context) {
                    navController.navigate(Routes.GEMINI_SETTINGS)
                }
            }

            composable(Routes.GEMINI_SETTINGS) {
                GeminiSettingsRoute {
                    navController.popBackStack()
                }
            }
        }
    } else {
        PermissionScreen {
            PermissionUtils.openPermissionSettings(context)
        }
    }
}
