package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.ToolResult
import com.aviansh.aifilemanager.domain.agent.tools.CopyTool
import com.aviansh.aifilemanager.domain.agent.tools.CreateFolderTool
import com.aviansh.aifilemanager.domain.agent.tools.DeleteTool
import com.aviansh.aifilemanager.domain.agent.tools.FileInfoTool
import com.aviansh.aifilemanager.domain.agent.tools.ListDirectoryTool
import com.aviansh.aifilemanager.domain.agent.tools.MoveTool
import com.aviansh.aifilemanager.domain.agent.tools.ReadFileTool
import com.aviansh.aifilemanager.domain.agent.tools.SearchFilesTool
import com.aviansh.aifilemanager.domain.agent.tools.WriteFileTool
import com.aviansh.aifilemanager.domain.engines.TrashEngine
import com.aviansh.aifilemanager.domain.security.FileAccessPolicy
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Behaviour of the native tools the agent drives, including which ones are gated behind
 * user confirmation and which ones route through the trash.
 */
@RunWith(RobolectricTestRunner::class)
class FileToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var policy: FileAccessPolicy

    private fun args(vararg pairs: Pair<String, Any>) = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    @Before
    fun setup() = runBlocking {
        AppPaths.filesDir = tempFolder.newFolder("files_dir").absolutePath
        AppPaths.cacheDir = tempFolder.newFolder("cache_dir").absolutePath
        root = tempFolder.newFolder("storage")
        policy = FileAccessPolicy(allowedRoots = listOf(root), forbiddenRoots = emptyList())
        TrashEngine.empty()
        Unit
    }

    // --- confirmation gating ---

    @Test
    fun onlyMutatingToolsRequireConfirmation() {
        val safe = listOf(
            ListDirectoryTool(policy), ReadFileTool(policy), FileInfoTool(policy),
            SearchFilesTool(policy), CreateFolderTool(policy), CopyTool(policy)
        )
        val gated = listOf(WriteFileTool(policy), MoveTool(policy), DeleteTool(policy))

        safe.forEach { assertFalse("${it.name} must run automatically", it.spec.destructive) }
        gated.forEach { assertTrue("${it.name} must ask the user", it.spec.destructive) }
    }

    @Test
    fun previewsReadAsPlainEnglishForTheConfirmationCard() {
        val existing = File(root, "old.txt").apply { writeText("x") }

        assertEquals(
            "Delete ${existing.absolutePath}",
            DeleteTool(policy).previewFor(args("path" to existing.absolutePath))
        )
        assertTrue(
            WriteFileTool(policy).previewFor(args("path" to existing.absolutePath))
                .startsWith("Overwrite")
        )
        assertTrue(
            WriteFileTool(policy).previewFor(args("path" to File(root, "new.txt").absolutePath))
                .startsWith("Write new file")
        )
    }

    // --- read-only tools ---

    @Test
    fun listDirectoryReportsEntries() = runBlocking {
        File(root, "a.txt").writeText("a")
        File(root, "sub").mkdirs()

        val result = ListDirectoryTool(policy).execute(args("path" to root.absolutePath))

        assertTrue(result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("a.txt"))
        assertTrue(output.contains("sub"))
    }

    @Test
    fun readFileRejectsDirectoriesWithAUsefulHint() = runBlocking {
        val result = ReadFileTool(policy).execute(args("path" to root.absolutePath))

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("list_directory"))
    }

    @Test
    fun searchFilesMatchesBySubstring() = runBlocking {
        File(root, "invoice-2024.pdf").writeText("x")
        File(root, "cat.jpg").writeText("x")

        val result = SearchFilesTool(policy).execute(
            args("path" to root.absolutePath, "query" to "invoice")
        )

        assertTrue(result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("invoice-2024.pdf"))
        assertFalse(output.contains("cat.jpg"))
    }

    @Test
    fun toolsRefusePathsOutsideThePolicyRoot() = runBlocking {
        val outside = tempFolder.newFolder("outside")
        File(outside, "secret.txt").writeText("nope")

        val result = ReadFileTool(policy).execute(args("path" to File(outside, "secret.txt").absolutePath))

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("outside your shared storage"))
    }

    @Test
    fun missingArgumentsAreReportedClearly() = runBlocking {
        val result = ReadFileTool(policy).execute(JSONObject())
        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("Missing required argument 'path'"))
    }

    // --- mutations ---

    @Test
    fun writeFileRefusesToClobberWithoutOverwrite() = runBlocking {
        val file = File(root, "notes.txt").apply { writeText("original") }

        val result = WriteFileTool(policy).execute(
            args("path" to file.absolutePath, "content" to "replacement")
        )

        assertTrue(result is ToolResult.Failure)
        assertTrue((result as ToolResult.Failure).error.contains("overwrite=true"))
        assertEquals("original", file.readText())
    }

    @Test
    fun overwritingKeepsThePreviousVersionInTheTrash() = runBlocking {
        val file = File(root, "notes.txt").apply { writeText("original") }

        val result = WriteFileTool(policy).execute(
            args("path" to file.absolutePath, "content" to "replacement", "overwrite" to true)
        )

        assertTrue(result is ToolResult.Success)
        assertEquals("replacement", file.readText())
        assertTrue(
            "The overwritten version must be recoverable",
            TrashEngine.list().any { it.originalPath == file.absolutePath }
        )
    }

    @Test
    fun deleteRoutesThroughTheTrashInsteadOfUnlinking() = runBlocking {
        val file = File(root, "bye.txt").apply { writeText("content") }

        val result = DeleteTool(policy).execute(args("path" to file.absolutePath))

        assertTrue(result is ToolResult.Success)
        assertFalse(file.exists())

        val entry = TrashEngine.list().single { it.originalPath == file.absolutePath }
        TrashEngine.restore(entry.id).getOrThrow()
        assertEquals("content", file.readText())
    }

    @Test
    fun deletingAMissingPathFails() = runBlocking {
        val result = DeleteTool(policy).execute(args("path" to File(root, "ghost.txt").absolutePath))
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun moveRenamesAndRefusesToNestAFolderInsideItself() = runBlocking {
        val file = File(root, "a.txt").apply { writeText("a") }
        val dest = File(root, "b.txt")

        assertTrue(
            MoveTool(policy).execute(
                args("source" to file.absolutePath, "destination" to dest.absolutePath)
            ) is ToolResult.Success
        )
        assertFalse(file.exists())
        assertEquals("a", dest.readText())

        val folder = File(root, "photos").apply { mkdirs() }
        val nested = File(folder, "inner")
        val bad = MoveTool(policy).execute(
            args("source" to folder.absolutePath, "destination" to nested.absolutePath)
        )
        assertTrue(bad is ToolResult.Failure)
        assertTrue((bad as ToolResult.Failure).error.contains("into itself"))
    }

    @Test
    fun copyDuplicatesWithoutRemovingTheSource() = runBlocking {
        val file = File(root, "a.txt").apply { writeText("a") }
        val dest = File(root, "copy.txt")

        val result = CopyTool(policy).execute(
            args("source" to file.absolutePath, "destination" to dest.absolutePath)
        )

        assertTrue(result is ToolResult.Success)
        assertTrue(file.exists())
        assertEquals("a", dest.readText())
    }

    @Test
    fun createFolderIsIdempotent() = runBlocking {
        val dir = File(root, "New/Nested")
        val tool = CreateFolderTool(policy)

        assertTrue(tool.execute(args("path" to dir.absolutePath)) is ToolResult.Success)
        assertTrue(dir.isDirectory)
        assertTrue("Re-creating must not fail", tool.execute(args("path" to dir.absolutePath)) is ToolResult.Success)
    }

    @Test
    fun fileInfoDoesNotReadContents() = runBlocking {
        val file = File(root, "big.bin").apply { writeText("secret payload") }

        val result = FileInfoTool(policy).execute(args("path" to file.absolutePath))

        assertTrue(result is ToolResult.Success)
        val output = (result as ToolResult.Success).output
        assertTrue(output.contains("big.bin"))
        assertFalse(output.contains("secret payload"))
    }
}
