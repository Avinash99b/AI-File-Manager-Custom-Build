package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.engines.TrashEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The trash replaces transactional rollback, so recoverability is entirely its responsibility.
 */
@RunWith(RobolectricTestRunner::class)
class TrashEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workDir: File

    @Before
    fun setup() = runBlocking {
        AppPaths.filesDir = tempFolder.newFolder("files_dir").absolutePath
        AppPaths.cacheDir = tempFolder.newFolder("cache_dir").absolutePath
        workDir = tempFolder.newFolder("storage")
        TrashEngine.empty()
        Unit
    }

    @Test
    fun deletedFileIsRemovedFromDiskButRecoverable() = runBlocking {
        val file = File(workDir, "notes.txt").apply { writeText("important") }

        val trashed = TrashEngine.moveToTrash(file).getOrThrow()

        assertFalse("Original must be gone", file.exists())
        assertEquals(file.absolutePath, trashed.originalPath)
        assertEquals("notes.txt", trashed.name)
        assertTrue(TrashEngine.list().any { it.id == trashed.id })

        val restored = TrashEngine.restore(trashed.id).getOrThrow()

        assertTrue("Restore must put the file back", restored.exists())
        assertEquals("important", restored.readText())
        assertEquals(file.absolutePath, restored.absolutePath)
        assertTrue("Restored entries leave the trash", TrashEngine.list().none { it.id == trashed.id })
    }

    @Test
    fun directoriesAreTrashedAndRestoredWithTheirContents() = runBlocking {
        val dir = File(workDir, "album").apply { mkdirs() }
        File(dir, "a.txt").writeText("one")
        File(dir, "nested").apply { mkdirs() }
        File(dir, "nested/b.txt").writeText("two")

        val trashed = TrashEngine.moveToTrash(dir).getOrThrow()
        assertFalse(dir.exists())

        TrashEngine.restore(trashed.id).getOrThrow()

        assertEquals("one", File(dir, "a.txt").readText())
        assertEquals("two", File(dir, "nested/b.txt").readText())
    }

    @Test
    fun restoringOntoAnOccupiedPathFailsRatherThanClobbering() = runBlocking {
        val file = File(workDir, "conflict.txt").apply { writeText("old") }
        val trashed = TrashEngine.moveToTrash(file).getOrThrow()

        File(workDir, "conflict.txt").writeText("new content")

        val result = TrashEngine.restore(trashed.id)

        assertTrue(result.isFailure)
        assertEquals("new content", File(workDir, "conflict.txt").readText())
        assertTrue("The entry stays recoverable", TrashEngine.list().any { it.id == trashed.id })
    }

    @Test
    fun trashingAMissingFileFails() = runBlocking {
        val result = TrashEngine.moveToTrash(File(workDir, "ghost.txt"))
        assertTrue(result.isFailure)
    }

    @Test
    fun restoringAnUnknownIdFails() = runBlocking {
        assertTrue(TrashEngine.restore("not-a-real-id").isFailure)
    }

    @Test
    fun listIsNewestFirst() = runBlocking {
        val first = TrashEngine.moveToTrash(File(workDir, "a.txt").apply { writeText("a") }).getOrThrow()
        Thread.sleep(5)
        val second = TrashEngine.moveToTrash(File(workDir, "b.txt").apply { writeText("b") }).getOrThrow()

        val ids = TrashEngine.list().map { it.id }
        assertEquals(listOf(second.id, first.id), ids)
    }

    @Test
    fun restoreSinceUndoesEverythingFromThisTurn() = runBlocking {
        val before = File(workDir, "older.txt").apply { writeText("older") }
        TrashEngine.moveToTrash(before).getOrThrow()

        Thread.sleep(10)
        val turnStart = System.currentTimeMillis()
        Thread.sleep(10)

        val a = File(workDir, "a.txt").apply { writeText("a") }
        val b = File(workDir, "b.txt").apply { writeText("b") }
        TrashEngine.moveToTrash(a).getOrThrow()
        TrashEngine.moveToTrash(b).getOrThrow()

        val results = TrashEngine.restoreSince(turnStart)

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertTrue(a.exists())
        assertTrue(b.exists())
        assertFalse("Earlier deletions are untouched", before.exists())
    }

    @Test
    fun emptyDiscardsEverythingAndReportsTheCount() = runBlocking {
        TrashEngine.moveToTrash(File(workDir, "a.txt").apply { writeText("a") }).getOrThrow()
        TrashEngine.moveToTrash(File(workDir, "b.txt").apply { writeText("b") }).getOrThrow()

        assertEquals(2, TrashEngine.empty())
        assertTrue(TrashEngine.list().isEmpty())
    }
}
