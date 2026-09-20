package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.security.FileAccessPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * FileAccessPolicy is the only thing standing between the agent and the user's filesystem now
 * that the Python sandbox no longer gates file access, so its rules are pinned here.
 */
class FileAccessPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun policyRootedAt(root: File, forbidden: List<File> = emptyList()) =
        FileAccessPolicy(allowedRoots = listOf(root), forbiddenRoots = forbidden)

    @Test
    fun pathsInsideTheAllowedRootAreAccepted() {
        val root = tempFolder.newFolder("storage")
        val target = File(root, "Download/report.pdf")
        val resolved = policyRootedAt(root).resolve(target.absolutePath)

        assertTrue(resolved.isSuccess)
        assertEquals(target.canonicalFile.absolutePath, resolved.getOrThrow().absolutePath)
    }

    @Test
    fun theAllowedRootItselfIsAccepted() {
        val root = tempFolder.newFolder("storage")
        assertTrue(policyRootedAt(root).resolve(root.absolutePath).isSuccess)
    }

    @Test
    fun pathsOutsideTheAllowedRootAreRejected() {
        val root = tempFolder.newFolder("storage")
        val outside = tempFolder.newFolder("elsewhere")

        val result = policyRootedAt(root).resolve(File(outside, "secret.txt").absolutePath)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("outside your shared storage")
        )
    }

    @Test
    fun siblingDirectoriesWithASharedPrefixAreNotTreatedAsChildren() {
        val root = tempFolder.newFolder("storage")
        val sibling = File(root.parentFile, "${root.name}-backup").apply { mkdirs() }

        assertTrue(policyRootedAt(root).resolve(sibling.absolutePath).isFailure)
    }

    @Test
    fun traversalSegmentsAreRejectedOutright() {
        val root = tempFolder.newFolder("storage")
        val result = policyRootedAt(root).resolve("${root.absolutePath}/../etc/passwd")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("traversal"))
    }

    @Test
    fun systemDirectoriesAreAlwaysOffLimits() {
        val policy = FileAccessPolicy(allowedRoots = emptyList(), forbiddenRoots = emptyList())

        listOf("/proc/version", "/sys/kernel", "/dev/null", "/system/build.prop", "/data/app/x")
            .forEach { path ->
                val result = policy.resolve(path)
                assertTrue("$path must be rejected", result.isFailure)
                assertTrue(
                    result.exceptionOrNull()?.message.orEmpty().contains("System directories")
                )
            }
    }

    @Test
    fun appPrivateStorageIsOffLimitsEvenInsideAnAllowedRoot() {
        val root = tempFolder.newFolder("storage")
        val privateDir = File(root, "app_private").apply { mkdirs() }
        val policy = policyRootedAt(root, forbidden = listOf(privateDir))

        val result = policy.resolve(File(privateDir, "secrets.json").absolutePath)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("private storage"))
    }

    @Test
    fun blankPathsAreRejected() {
        assertTrue(FileAccessPolicy(emptyList(), emptyList()).resolve("   ").isFailure)
    }

    @Test
    fun isAncestorOfDetectsCopyingAFolderIntoItself() {
        val policy = FileAccessPolicy(emptyList(), emptyList())
        val parent = tempFolder.newFolder("photos")
        val child = File(parent, "2024").apply { mkdirs() }
        val unrelated = tempFolder.newFolder("videos")

        assertTrue(policy.isAncestorOf(parent, child))
        assertTrue("A folder is its own ancestor for move purposes", policy.isAncestorOf(parent, parent))
        assertFalse(policy.isAncestorOf(parent, unrelated))
    }
}
