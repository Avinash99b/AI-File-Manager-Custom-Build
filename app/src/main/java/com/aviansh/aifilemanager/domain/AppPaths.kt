package com.aviansh.aifilemanager.domain

import android.content.Context
import java.io.File

object AppPaths {
    var filesDir: String = ""
    var cacheDir: String=""

    fun init(context: Context){
        filesDir=context.filesDir.absolutePath
        cacheDir=context.cacheDir.absolutePath
    }
}