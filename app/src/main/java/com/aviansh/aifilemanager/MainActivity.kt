package com.aviansh.aifilemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.ui.theme.AIFileManagerTheme
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        AppPaths.init(this)

        setContent {

            AIFileManagerTheme {

                PermissionGate()

            }

        }

        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }

    }

}