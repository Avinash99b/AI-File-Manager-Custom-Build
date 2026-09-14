package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.agent.ExecutionPlan
import com.aviansh.aifilemanager.domain.agent.ExecutionState
import com.aviansh.aifilemanager.domain.agent.WorkspaceEngine
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.prefs.GeminiPreferences
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.repository.GeminiModelRepository
import com.aviansh.aifilemanager.domain.security.InMemorySecretStore
import com.aviansh.aifilemanager.domain.security.PathPolicy
import com.aviansh.aifilemanager.domain.transactions.PlanBinding
import com.aviansh.aifilemanager.ui.vm.FileManagerViewModel
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
class SeniorReviewDefectsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("senior_review_root")
        AppPaths.filesDir = tempFolder.newFolder("app_files_dir").absolutePath
        AppPaths.cacheDir = tempFolder.newFolder("app_cache_dir").absolutePath
    }

    @Test
    fun testFinding1_SecretStoreMigrationFromLegacyPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = GeminiPreferences(context)
        val secretStore = InMemorySecretStore()

        // 1. Save legacy key in DataStore
        prefs.saveApiKey("legacy_secret_key_123")
        prefs.saveModelName("gemini-test")
        assertEquals("legacy_secret_key_123", prefs.getApiKey())

        // 2. Instantiate repository with SecretStore
        val repository = GeminiModelRepository(prefs, secretStore)

        // 3. Read key - should trigger migration into SecretStore and clear legacy key
        val key = repository.getApiKey()
        assertEquals("legacy_secret_key_123", key)

        // 4. Verify key is now in SecretStore and purged from DataStore
        assertEquals("legacy_secret_key_123", secretStore.getSecret("gemini_api_key"))
        assertNull("Legacy key in DataStore must be cleared", prefs.getApiKey())
    }

    @Test
    fun testFinding2_AppPrivatePathRejectedEvenInsideAllowedRoot() = runBlocking {
        val appPrivateDir = File(AppPaths.filesDir)
        val secretFile = File(appPrivateDir, "internal_secret.txt").apply { writeText("secret") }

        // Allowed roots includes appPrivateDir's parent or rootDir
        val workspaceEngine = WorkspaceEngine(customAllowedRoots = listOf(rootDir, appPrivateDir.parentFile))

        val action = FileAction(
            type = FileActionType.DELETE,
            sourcePath = secretFile.absolutePath
        )

        val preflight = workspaceEngine.preflight(listOf(action))
        assertFalse("App-private path deletion must fail preflight", preflight.isValid)
        assertTrue(preflight.validationErrors.any { it.contains("forbidden") })
    }

    @Test
    fun testFinding4_PathBoundaryHelper_rejectsEvilPrefix() {
        val policy = PathPolicy(
            allowedRoots = listOf(File("/workspace")),
            forbiddenAppPrivatePaths = emptyList()
        )

        val validRes = policy.validatePath("/workspace/file.txt")
        assertTrue(validRes.isSuccess)

        val evilRes = policy.validatePath("/workspace_evil/file.txt")
        assertFalse("/workspace_evil must be rejected as outside allowed root /workspace", evilRes.isSuccess)
    }

    @Test
    fun testFinding6_DuplicateFilenamesInDifferentDirs_snapshotNoCollision() = runBlocking {
        val dirA = File(rootDir, "dirA").apply { mkdirs() }
        val dirB = File(rootDir, "dirB").apply { mkdirs() }

        val fileA = File(dirA, "file.txt").apply { writeText("content A") }
        val fileB = File(dirB, "file.txt").apply { writeText("content B") }

        val destA = File(rootDir, "destA_file.txt").apply { writeText("dest A old") }
        val destB = File(rootDir, "destB_file.txt").apply { writeText("dest B old") }

        val workspaceEngine = WorkspaceEngine(customAllowedRoots = listOf(rootDir))

        val action1 = FileAction(FileActionType.COPY, fileA.absolutePath, destA.absolutePath, overwrite = true)
        val action2 = FileAction(FileActionType.COPY, fileB.absolutePath, destB.absolutePath, overwrite = true)

        val commitResult = workspaceEngine.commitWorkspace(listOf(action1, action2))
        assertTrue("Commit with same filenames in different dirs must succeed", commitResult.isSuccess)
        assertEquals("content A", destA.readText())
        assertEquals("content B", destB.readText())
    }

    @Test
    fun testFinding8_PlanBindingHashMismatch_rejectsApproval() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = GeminiPreferences(context)
        val secretStore = InMemorySecretStore()
        val repo = GeminiModelRepository(prefs, secretStore)
        val fileRepo = FileRepository(context)

        val viewModel = FileManagerViewModel(fileRepo, repo)

        val action = FileAction(FileActionType.DELETE, File(rootDir, "target.txt").absolutePath)
        val originalPlan = ExecutionPlan(listOf(action), "Delete file target.txt")

        // Manually simulate WaitingForApproval state
        val stateField = FileManagerViewModel::class.java.getDeclaredField("_executionState").apply { isAccessible = true }
        (stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ExecutionState>).value = ExecutionState.WaitingForApproval(originalPlan)

        // Modified plan with different explanation / hash
        val tamperedPlan = ExecutionPlan(listOf(action), "Tampered explanation")

        viewModel.onApprovePlan(tamperedPlan)

        val finalState = viewModel.executionState.value
        assertTrue("State should be Failed due to hash mismatch", finalState is ExecutionState.Failed)
        assertTrue((finalState as ExecutionState.Failed).error.contains("mismatch"))
    }
}
