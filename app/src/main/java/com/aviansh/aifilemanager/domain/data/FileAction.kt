package com.aviansh.aifilemanager.domain.data

import com.aviansh.aifilemanager.domain.AppPaths
import java.io.File
import java.io.InputStream

enum class FileActionType { MOVE, DELETE, COPY, CREATE }
data class FileAction(
    val type: FileActionType,
    val sourcePath: String,
    val destinationPath: String? = null,
    val content: InputStream? = null,
    val overwrite: Boolean = false
)

fun getSnapshotsDir(): File {
    return File(AppPaths.filesDir, "snapshots")
}


//Assuming this is only called after snapshot is created.
fun FileAction.generateInverseAction(transactionId: Long): FileAction {
    val snapshotFilesDir = getSnapshotsDir()
    val snapshotDir = File(snapshotFilesDir, transactionId.toString())
    var action: FileAction

    when (this.type) {
        FileActionType.MOVE -> {
            action = FileAction(
                FileActionType.MOVE,
                this.destinationPath!!,
                this.sourcePath
            )
        }

        FileActionType.DELETE -> {
            action = FileAction(
                FileActionType.CREATE,
                File(snapshotDir, File(sourcePath).name).absolutePath,
                this.sourcePath
            )
        }

        FileActionType.COPY -> {
            action = FileAction(
                FileActionType.COPY,
                File(snapshotDir, File(destinationPath!!).name).absolutePath,
                this.destinationPath
            )
        }

        FileActionType.CREATE -> {
            action = FileAction(
                FileActionType.DELETE,
                this.sourcePath
            )
        }
    }
    return action
}
