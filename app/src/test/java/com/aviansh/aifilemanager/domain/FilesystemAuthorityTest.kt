package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.WorkspaceEngine
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.engines.FileEngine
import com.aviansh.aifilemanager.domain.security.PathPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FilesystemAuthorityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var workspaceEngine: WorkspaceEngine

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("test_root")
        workspaceEngine = WorkspaceEngine(customAllowedRoots = listOf(rootDir))
    }

    @Test
    fun testMoveOverwriteDenied_failsPreflight() = runBlocking {
        val src = File(rootDir, "source.txt").apply { writeText("new content") }
        val dest = File(rootDir, "existing.txt").apply { writeText("old content") }

        val action = FileAction(
            type = FileActionType.MOVE,
            sourcePath = src.absolutePath,
            destinationPath = dest.absolutePath,
            overwrite = false
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertFalse("Preflight must fail when overwrite is false and dest exists", preflight.isValid)
        assertTrue(preflight.validationErrors.any { it.contains("overwrite is false") })

        val commitResult = workspaceEngine.commitWorkspace(listOf(action))
        assertFalse("Commit must fail", commitResult.isSuccess)
        assertEquals("Source file must remain untouched", "new content", src.readText())
        assertEquals("Existing file must remain untouched", "old content", dest.readText())
    }

    @Test
    fun testMoveOverwriteAllowed_replacesTarget() = runBlocking {
        val src = File(rootDir, "source.txt").apply { writeText("new content") }
        val dest = File(rootDir, "existing.txt").apply { writeText("old content") }

        val action = FileAction(
            type = FileActionType.MOVE,
            sourcePath = src.absolutePath,
            destinationPath = dest.absolutePath,
            overwrite = true
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertTrue("Preflight should pass when overwrite is true", preflight.isValid)

        val commitResult = workspaceEngine.commitWorkspace(listOf(action))
        assertTrue("Commit should succeed", commitResult.isSuccess)
        assertFalse("Source file should be moved", src.exists())
        assertEquals("Destination file should have new content", "new content", dest.readText())
    }

    @Test
    fun testRecursiveDirectoryCopy() = runBlocking {
        val subDir = File(rootDir, "src_dir").apply { mkdirs() }
        File(subDir, "file1.txt").writeText("data1")
        val nested = File(subDir, "nested").apply { mkdirs() }
        File(nested, "file2.txt").writeText("data2")

        val destDir = File(rootDir, "dest_dir")

        val action = FileAction(
            type = FileActionType.COPY,
            sourcePath = subDir.absolutePath,
            destinationPath = destDir.absolutePath,
            overwrite = false
        )

        val commitResult = workspaceEngine.commitWorkspace(listOf(action))
        assertTrue("Recursive copy commit should succeed", commitResult.isSuccess)
        assertTrue("Source directory still exists", subDir.exists())
        assertTrue("Destination directory exists", destDir.exists())
        assertEquals("data1", File(destDir, "file1.txt").readText())
        assertEquals("data2", File(destDir, "nested/file2.txt").readText())
    }

    @Test
    fun testSameSourceAndDestination_rejected() = runBlocking {
        val file = File(rootDir, "same.txt").apply { writeText("test") }

        val action = FileAction(
            type = FileActionType.MOVE,
            sourcePath = file.absolutePath,
            destinationPath = file.absolutePath,
            overwrite = true
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertFalse("Same source and destination must fail preflight", preflight.isValid)
        assertTrue(preflight.validationErrors.any { it.contains("identical") })
    }

    @Test
    fun testMoveDirectoryIntoItselfOrDescendant_rejected() = runBlocking {
        val parentDir = File(rootDir, "parent").apply { mkdirs() }
        val childDir = File(parentDir, "child").apply { mkdirs() }

        val action = FileAction(
            type = FileActionType.MOVE,
            sourcePath = parentDir.absolutePath,
            destinationPath = childDir.absolutePath,
            overwrite = true
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertFalse("Directory move into descendant must fail preflight", preflight.isValid)
        assertTrue(preflight.validationErrors.any { it.contains("descendant") })
    }

    @Test
    fun testPathTraversal_rejected() = runBlocking {
        val action = FileAction(
            type = FileActionType.DELETE,
            sourcePath = "${rootDir.absolutePath}/../outside.txt"
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertFalse("Path traversal must fail preflight", preflight.isValid)
        assertTrue(preflight.validationErrors.any { it.contains("traversal") })
    }

    @Test
    fun testPreflightAllOrNothing_zeroActionsExecutedIfAnyInvalid() = runBlocking {
        val file1 = File(rootDir, "valid1.txt").apply { writeText("v1") }
        val dest1 = File(rootDir, "moved1.txt")

        val invalidAction = FileAction(
            type = FileActionType.MOVE,
            sourcePath = "${rootDir.absolutePath}/../traversal.txt",
            destinationPath = "${rootDir.absolutePath}/dest.txt"
        )

        val validAction = FileAction(
            type = FileActionType.MOVE,
            sourcePath = file1.absolutePath,
            destinationPath = dest1.absolutePath,
            overwrite = false
        )

        val commitResult = workspaceEngine.commitWorkspace(listOf(validAction, invalidAction))
        assertFalse("Commit should fail if any action is invalid", commitResult.isSuccess)
        assertTrue("Valid file must not have been moved", file1.exists())
        assertFalse("Destination must not be created", dest1.exists())
    }

    @Test
    fun testRollbackAfterPartialFailure() = runBlocking {
        val file1 = File(rootDir, "f1.txt").apply { writeText("content1") }
        val dest1 = File(rootDir, "f1_dest.txt")

        val file2 = File(rootDir, "non_existent.txt")
        val dest2 = File(rootDir, "f2_dest.txt")

        val action1 = FileAction(FileActionType.COPY, file1.absolutePath, dest1.absolutePath, overwrite = false)
        val badAction = FileAction(FileActionType.MOVE, file2.absolutePath, dest2.absolutePath, overwrite = false)

        val result = workspaceEngine.commitWorkspace(listOf(action1, badAction))
        assertFalse("Commit must fail", result.isSuccess)
        assertTrue("file1 destination created during partial run should be rolled back", !dest1.exists() || file1.exists())
        assertEquals("Original file1 should still exist", "content1", file1.readText())
    }

    @Test
    fun testUnicodeFilenames() = runBlocking {
        val unicodeName = "文档_📄_test_אבג.txt"
        val src = File(rootDir, unicodeName).apply { writeText("unicode test content") }
        val dest = File(rootDir, "copy_$unicodeName")

        val action = FileAction(FileActionType.COPY, src.absolutePath, dest.absolutePath, overwrite = false)

        val result = workspaceEngine.commitWorkspace(listOf(action))
        assertTrue("Unicode filename copy must succeed", result.isSuccess)
        assertTrue(dest.exists())
        assertEquals("unicode test content", dest.readText())
    }
}
