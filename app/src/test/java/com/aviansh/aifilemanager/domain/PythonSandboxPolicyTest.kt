package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.sandbox.PythonExecutionRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the sandbox policy that produced the reported "PermissionError: Write denied outside
 * workspace: /data/data/.../chaquopy/AssetFinder/requirements/pypdf" failures.
 */
class PythonSandboxPolicyTest {

    private fun setup(
        workspace: String = "/data/user/0/com.aviansh.aifilemanager/files/workspace_test",
        readRoots: List<File> = listOf(File("/storage/emulated/0")),
        infraRoots: List<File> = emptyList(),
        denyNetwork: Boolean = true
    ): String = PythonEngine.buildHardenedPythonSetup(
        PythonExecutionRequest(
            code = "print('hi')",
            workspaceDir = workspace,
            allowedReadRoots = readRoots,
            infraWriteRoots = infraRoots,
            denyNetwork = denyNetwork
        )
    )

    @Test
    fun runtimeInfrastructurePathsAreExemptFromWorkspaceWriteBlock() {
        val setup = setup()
        assertTrue(
            "Chaquopy asset extraction must bypass the workspace write check",
            setup.contains("def _is_infra_path")
        )
        assertTrue(
            "Chaquopy requirement dirs must be recognised as infrastructure",
            setup.contains("'/chaquopy/' in text")
        )
        assertTrue(
            "__pycache__/.pyc writes are import machinery, not user writes",
            setup.contains("__pycache__")
        )
        assertTrue(
            "The infra exemption must be consulted before denying a write",
            setup.indexOf("if _is_infra_path(filepath):") < setup.indexOf("Write denied outside workspace")
        )
    }

    @Test
    fun explicitInfraWriteRootsArePropagated() {
        val setup = setup(infraRoots = listOf(File("/tmp/custom-infra")))
        assertTrue(setup.contains("/tmp/custom-infra"))
    }

    @Test
    fun offlineStandardLibraryImportsAreNotBlocked() {
        val setup = setup()
        // These used to be banned by module-name prefix, which broke pypdf/openpyxl/email
        // because they import urllib.parse, http and zipfile transitively.
        listOf("'urllib'", "'http'", "'socket'", "'ssl'").forEach { module ->
            assertFalse(
                "Blocking $module by name breaks offline stdlib usage",
                setup.contains("_blocked_net_modules = {\n                        $module")
            )
        }
        assertTrue(
            "Network must be blocked at the socket layer instead",
            setup.contains("class _BlockedSocket")
        )
        assertTrue(setup.contains("Network access is denied by policy"))
    }

    @Test
    fun pureHttpClientsAreStillBlockedOnImport() {
        val setup = setup()
        listOf("requests", "httpx", "urllib3", "aiohttp").forEach {
            assertTrue("$it should stay blocked", setup.contains("'$it'"))
        }
    }

    @Test
    fun networkHardeningIsSkippedWhenPolicyAllowsIt() {
        val setup = setup(denyNetwork = false)
        assertFalse(setup.contains("class _BlockedSocket"))
    }

    @Test
    fun mutatingOsCallsAreGuardedNotJustOpen() {
        val setup = setup()
        assertTrue(
            "os-level writes must respect the workspace boundary too",
            setup.contains("_wrap_os_write")
        )
        listOf("remove", "unlink", "rmdir", "rename", "replace", "mkdir", "makedirs").forEach {
            assertTrue("os.$it should be guarded", setup.contains("'$it'"))
        }
    }

    @Test
    fun denialMessagesTellTheAgentWhatToDoInstead() {
        val setup = setup()
        assertTrue(
            "Errors must steer the agent to the workspace + plan-action pattern",
            setup.contains("declare a 'create' action with the real destination path")
        )
        assertTrue(setup.contains("express changes to real paths as plan actions instead"))
    }

    @Test
    fun workspaceDirectoryIsCreatedBeforeChdir() {
        val setup = setup()
        val makedirs = setup.indexOf("os.makedirs(")
        val chdir = setup.indexOf("os.chdir(")
        assertTrue("Workspace must exist before chdir", makedirs in 0 until chdir)
    }

    @Test
    fun readRootsStillConstrainReads() {
        val setup = setup()
        assertTrue(setup.contains("/storage/emulated/0"))
        assertTrue(setup.contains("Read denied outside allowed roots"))
    }

    @Test
    fun defaultTimeoutAllowsRealisticWork() {
        // 10s was not enough for a first pypdf import (Chaquopy extracts the wheel on demand).
        assertTrue(PythonExecutionRequest(code = "pass").timeoutMillis >= 30_000L)
    }
}
