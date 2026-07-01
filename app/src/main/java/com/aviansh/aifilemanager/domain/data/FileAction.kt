package com.aviansh.aifilemanager.domain.data

import com.aviansh.aifilemanager.domain.AppPaths
import java.io.File

enum class FileActionType { MOVE, DELETE, COPY, CREATE }
data class FileAction(
    val type: FileActionType,
    val sourcePath: String,
    val destinationPath: String? = null,
    val overwrite: Boolean = false,
    val comment: String = ""
)

data class FileActionsPreviewResult(
    val filesAdded: Int, //create as add
    val filesDeleted: Int, //delete as delete
    val filesUpdated: Int, //Copy or move is categorized as updated
)
fun getSnapshotsDir(): File {
    return File(AppPaths.filesDir, "snapshots")
}


//Assuming this is only called after snapshot is created.
fun FileAction.generateInverseAction(transactionId: Long): FileAction? {
    val snapshotFilesDir = getSnapshotsDir()
    val snapshotDir = File(snapshotFilesDir, transactionId.toString())
    var action: FileAction? = null

    when (this.type) {
        FileActionType.MOVE -> {
            action = FileAction(
                FileActionType.MOVE,
                this.destinationPath!!,
                this.sourcePath,
                comment="Rollback for ${this.comment}"
            )
        }


        FileActionType.DELETE -> {
            action = FileAction(
                FileActionType.CREATE,
                File(snapshotDir, File(sourcePath).name).absolutePath,
                this.sourcePath,
                comment="Rollback for ${this.comment}"
            )
        }

        FileActionType.COPY -> {
            action = FileAction(
                FileActionType.COPY,
                File(snapshotDir, File(destinationPath!!).name).absolutePath,
                this.destinationPath,
                comment="Rollback for ${this.comment}"
            )
        }

        FileActionType.CREATE -> {
            action = FileAction(
                FileActionType.DELETE,
                this.sourcePath,
                comment="Rollback for ${this.comment}"
            )
        }
    }
    return action
}
