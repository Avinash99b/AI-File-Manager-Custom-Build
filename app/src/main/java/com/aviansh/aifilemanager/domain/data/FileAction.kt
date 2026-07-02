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

fun getTmpDir(): File{
    return File(AppPaths.filesDir, "tmpFilesDir")
}

//Assuming this is only called after snapshot is created.
fun FileAction.generateInverseAction(): FileAction? {
    val tmpDir = getTmpDir()
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
                FileActionType.MOVE,
                File(tmpDir, File(sourcePath).name).absolutePath,
                this.sourcePath,
                comment="Rollback for ${this.comment}"
            )
        }

        FileActionType.COPY -> {
            action = FileAction(
                FileActionType.COPY,
                File(tmpDir, File(destinationPath!!).name).absolutePath,
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
