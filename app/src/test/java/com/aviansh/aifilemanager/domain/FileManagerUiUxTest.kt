package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.ExecutionPlan
import com.aviansh.aifilemanager.domain.agent.ExecutionState
import com.aviansh.aifilemanager.domain.agent.TimelineEvent
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.prefs.GeminiPreferences
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.GeminiModelRepository
import com.aviansh.aifilemanager.domain.security.InMemorySecretStore
import com.aviansh.aifilemanager.ui.vm.FilterOption
import com.aviansh.aifilemanager.ui.vm.FileManagerViewModel
import com.aviansh.aifilemanager.ui.vm.SortOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileManagerUiUxTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var viewModel: FileManagerViewModel
    private lateinit var fileRepo: FileRepository

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("ui_ux_test_root")
        AppPaths.filesDir = tempFolder.newFolder("app_files_dir").absolutePath
        AppPaths.cacheDir = tempFolder.newFolder("app_cache_dir").absolutePath

        val context = RuntimeEnvironment.getApplication()
        val prefs = GeminiPreferences(context)
        val secretStore = InMemorySecretStore()
        val geminiRepo = GeminiModelRepository(prefs, secretStore)
        fileRepo = FileRepository(context)

        viewModel = FileManagerViewModel(fileRepo, geminiRepo)
    }

    @Test
    fun testMultiSelectAndSelectionToggle() = runBlocking {
        val file1 = File(rootDir, "file1.txt").apply { writeText("hello") }
        val file2 = File(rootDir, "file2.txt").apply { writeText("world") }

        viewModel.loadFiles(rootDir.absolutePath)
        delay(300)

        viewModel.toggleFileSelection(file1.absolutePath)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPaths.contains(file1.absolutePath))

        viewModel.toggleFileSelection(file2.absolutePath)
        assertEquals(2, viewModel.uiState.value.selectedPaths.size)

        viewModel.toggleFileSelection(file1.absolutePath)
        assertEquals(1, viewModel.uiState.value.selectedPaths.size)
        assertFalse(viewModel.uiState.value.selectedPaths.contains(file1.absolutePath))

        viewModel.clearSelection()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedPaths.isEmpty())
    }

    @Test
    fun testCreateFolderAndListUpdate() = runBlocking {
        viewModel.loadFiles(rootDir.absolutePath)
        delay(300)

        viewModel.createFolder("NewFolder")
        delay(300)

        val newFolder = File(rootDir, "NewFolder")
        assertTrue(newFolder.exists())
        assertTrue(newFolder.isDirectory)
    }

    @Test
    fun testCopyCutAndPasteFiles() = runBlocking {
        val srcDir = File(rootDir, "srcDir").apply { mkdirs() }
        val destDir = File(rootDir, "destDir").apply { mkdirs() }
        val testFile = File(srcDir, "data.txt").apply { writeText("sample content") }

        viewModel.loadFiles(srcDir.absolutePath)
        delay(300)

        viewModel.toggleFileSelection(testFile.absolutePath)
        viewModel.copySelectedFiles(isCut = false)

        assertNotNull(viewModel.uiState.value.clipboard)
        assertEquals(1, viewModel.uiState.value.clipboard!!.sourcePaths.size)

        viewModel.loadFiles(destDir.absolutePath)
        delay(300)

        viewModel.pasteFiles()
        delay(300)

        val pastedFile = File(destDir, "data.txt")
        assertTrue(pastedFile.exists())
        assertEquals("sample content", pastedFile.readText())
    }

    @Test
    fun testSortAndFilterOptions() = runBlocking {
        File(rootDir, "z_doc.txt").writeText("z")
        File(rootDir, "a_doc.pdf").writeText("a")
        File(rootDir, "photo.jpg").writeText("img")

        viewModel.loadFiles(rootDir.absolutePath)
        delay(300)

        viewModel.setSortOption(SortOption.NAME_ASC)

        val filesAsc = viewModel.uiState.value.files
        assertEquals(3, filesAsc.size)
        assertEquals("a_doc.pdf", filesAsc[0].name)
        assertEquals("photo.jpg", filesAsc[1].name)
        assertEquals("z_doc.txt", filesAsc[2].name)

        viewModel.setFilterOption(FilterOption.IMAGES)
        val imagesOnly = viewModel.uiState.value.files
        assertEquals(1, imagesOnly.size)
        assertEquals("photo.jpg", imagesOnly[0].name)
    }

    @Test
    fun testPruneSelectionOnSearchAndFilterChange() = runBlocking {
        val f1 = File(rootDir, "file1.txt").apply { writeText("text1") }
        val f2 = File(rootDir, "file2.txt").apply { writeText("text2") }
        val img = File(rootDir, "photo.png").apply { writeText("img") }

        viewModel.loadFiles(rootDir.absolutePath)
        delay(300)

        viewModel.toggleFileSelection(f1.absolutePath)
        viewModel.toggleFileSelection(img.absolutePath)
        assertEquals(2, viewModel.uiState.value.selectedPaths.size)

        // 1. Search for "photo" -> file1.txt is hidden, so selection should be pruned to photo.png
        viewModel.onSearchQueryChange("photo")
        assertEquals(1, viewModel.uiState.value.selectedPaths.size)
        assertTrue(viewModel.uiState.value.selectedPaths.contains(img.absolutePath))
        assertFalse(viewModel.uiState.value.selectedPaths.contains(f1.absolutePath))

        // Clear search
        viewModel.onSearchQueryChange("")
        viewModel.toggleFileSelection(f1.absolutePath)
        assertEquals(2, viewModel.uiState.value.selectedPaths.size)

        // 2. Filter to DOCUMENTS -> photo.png is hidden, selection should contain only file1.txt
        viewModel.setFilterOption(FilterOption.DOCUMENTS)
        assertEquals(1, viewModel.uiState.value.selectedPaths.size)
        assertTrue(viewModel.uiState.value.selectedPaths.contains(f1.absolutePath))
        assertFalse(viewModel.uiState.value.selectedPaths.contains(img.absolutePath))
    }

    @Test
    fun testSamePathCopyCutPasteSkippedSafely() = runBlocking {
        val testFile = File(rootDir, "original.txt").apply { writeText("important content") }

        viewModel.loadFiles(rootDir.absolutePath)
        delay(300)

        viewModel.toggleFileSelection(testFile.absolutePath)
        viewModel.copySelectedFiles(isCut = false)

        // Paste into same directory where original.txt resides
        viewModel.pasteFiles()
        delay(300)

        // File should remain intact and not truncated or corrupted
        assertTrue(testFile.exists())
        assertEquals("important content", testFile.readText())
    }

    @Test
    fun testPersistAndRestoreProposedPlanTimelineEvent() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = GeminiPreferences(context)
        val secretStore = InMemorySecretStore()
        val geminiRepo = GeminiModelRepository(prefs, secretStore)

        val plan = ExecutionPlan(
            actions = listOf(FileAction(FileActionType.DELETE, File(rootDir, "test.txt").absolutePath)),
            explanation = "Delete test file"
        )

        val persistMethod = FileManagerViewModel::class.java.getDeclaredMethod("persistTimeline", List::class.java).apply { isAccessible = true }
        persistMethod.invoke(viewModel, listOf(TimelineEvent.UserPrompt("delete test.txt"), TimelineEvent.ProposedPlan(plan)))

        delay(500)

        // Create a new ViewModel to simulate app process recreation
        val newViewModel = FileManagerViewModel(fileRepo, geminiRepo)
        delay(500)

        val restoredTimeline = newViewModel.timeline.value
        assertTrue("Timeline should be restored", restoredTimeline.isNotEmpty())
        assertTrue("ProposedPlan should be restored in timeline", restoredTimeline.any { it is TimelineEvent.ProposedPlan })

        val state = newViewModel.executionState.value
        assertTrue("ExecutionState should be restored to WaitingForApproval", state is ExecutionState.WaitingForApproval)
        assertEquals(plan.explanation, (state as ExecutionState.WaitingForApproval).plan.explanation)
    }
}
