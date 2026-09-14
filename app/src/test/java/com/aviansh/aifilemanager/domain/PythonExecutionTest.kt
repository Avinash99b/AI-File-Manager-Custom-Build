package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.sandbox.PythonExecutionRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PythonExecutionTest {

    @Test
    fun testEmptyRequest_returnsError() {
        val request = PythonExecutionRequest(code = "")
        val result = PythonEngine.execute(request)
        assertTrue(result.contains("Execution Error: Code request is empty"))
    }

    @Test
    fun testOutputCapTruncation() {
        val longString = "A".repeat(1000)
        val request = PythonExecutionRequest(
            code = "print('$longString')",
            maxOutputBytes = 100
        )
        val result = PythonEngine.execute(request)
        assertTrue("Output should indicate truncation or error handling", result.contains("TRUNCATED") || result.contains("JVM Sandbox"))
    }

    @Test
    fun testInvalidExecutionRequest() {
        val request = PythonExecutionRequest(
            code = "   ",
            timeoutMillis = 1000
        )
        val result = PythonEngine.execute(request)
        assertTrue(result.contains("Execution Error"))
    }

    @Test
    fun testCleanAndNormalizeCode_ordinaryCode() {
        val input = """
            def generate():
                import os
                return []
        """.trimIndent()
        val cleaned = PythonEngine.cleanAndNormalizeCode(input)
        assertEquals("def generate():\n    import os\n    return []", cleaned)
    }

    @Test
    fun testCleanAndNormalizeCode_indentedCode() {
        val input = "    def generate():\n        import os\n        return []"
        val cleaned = PythonEngine.cleanAndNormalizeCode(input)
        assertEquals("def generate():\n    import os\n    return []", cleaned)
    }

    @Test
    fun testCleanAndNormalizeCode_markdownFences() {
        val input = "```python\ndef generate():\n    import os\n    return []\n```"
        val cleaned = PythonEngine.cleanAndNormalizeCode(input)
        assertEquals("def generate():\n    import os\n    return []", cleaned)
    }

    @Test
    fun testCleanAndNormalizeCode_fencesAndLeadingIndentation() {
        val input = "    ```python\n    def generate():\n        import os\n        return []\n    ```"
        val cleaned = PythonEngine.cleanAndNormalizeCode(input)
        assertEquals("def generate():\n    import os\n    return []", cleaned)
    }

    @Test
    fun testCleanAndNormalizeCode_multilineStringsAndComments() {
        val input = """
            # Setup generator
            def generate():
                msg = ""${'"'}
                line 1
                line 2
                ""${'"'}
                return []
        """.trimIndent()
        val cleaned = PythonEngine.cleanAndNormalizeCode(input)
        assertTrue(cleaned.startsWith("# Setup generator\ndef generate():"))
    }

    @Test
    fun testBuildHardenedPythonSetup_includesRealpathResolution() {
        val request = PythonExecutionRequest(
            code = "print('hello')",
            workspaceDir = "/data/user/0/com.aviansh.aifilemanager/files/workspace_test"
        )
        val setup = PythonEngine.buildHardenedPythonSetup(request)
        assertTrue("Setup should calculate realpath to handle symlinks", setup.contains("os.path.realpath"))
        assertTrue("Setup should check subpath with contains logic", setup.contains("def _is_subpath"))
        assertTrue("Setup should install safe open interceptor", setup.contains("builtins.open = _safe_open"))
    }
}
