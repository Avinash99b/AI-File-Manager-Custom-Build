package com.aviansh.aifilemanager.domain

import android.content.Context
import java.io.File

object AppPaths {
    const val BASE_DIR       = "/sdcard/AIFileManager"
    const val SNAPSHOTS_DIR  = "$BASE_DIR/.snapshots"
    const val DEFAULT_START  = "/sdcard/"
    var filesDir: String = ""

    /** Max snapshots retained on disk before the oldest is pruned on commit. */
    const val MAX_SNAPSHOTS  = 10

    fun init(context: Context){
        filesDir=context.filesDir.absolutePath
    }
}