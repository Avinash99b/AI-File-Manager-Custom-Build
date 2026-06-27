package com.aviansh.aifilemanager.domain

import android.content.Context
import java.io.File

object AppPaths {
    lateinit var filesDir: File
        private set

    fun init(context: Context) {
        filesDir = context.filesDir
    }
}