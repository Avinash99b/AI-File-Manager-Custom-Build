package com.aviansh.aifilemanager.domain

import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.sandbox.PythonExecutionRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the reported crash:
 *
 *   "Generator code execution failed: Value [{"action": "delete", ...}] of type
 *    java.lang.String cannot be converted to JSONArray"
 *
 * The runner used to wrap generate() in json.dumps() unconditionally. When the model followed
 * the prompt's instruction to "return a JSON-encoded list", the result was double-encoded into a
 * quoted string that could not be parsed as a JSON array.
 */
class GeneratorCodeContractTest {

    private fun setup(): String = PythonEngine.buildHardenedPythonSetup(
        PythonExecutionRequest(code = "pass", workspaceDir = "/tmp/ws")
    )

    @Test
    fun runnerDoesNotBlindlyDoubleEncodeGeneratorOutput() {
        // The old implementation injected exactly this. Its presence means a generate() that
        // returns a JSON string gets encoded a second time.
        val legacyRunner = "print(json.dumps(generate()))"
        val engineSource = PythonEngineSource.text
        assertFalse(
            "Generator output must not be unconditionally json.dumps()'d",
            engineSource.contains(legacyRunner)
        )
    }

    @Test
    fun runnerNormalizesStringAndListReturns() {
        val src = PythonEngineSource.text
        assertTrue(
            "String returns must be decoded rather than re-encoded",
            src.contains("isinstance(_result, (str, bytes, bytearray))")
        )
        assertTrue(
            "Nested encoding should be unwrapped defensively",
            src.contains("while isinstance(_parsed, str)")
        )
        assertTrue("A bare dict should be accepted as a single action", src.contains("_parsed = [_parsed]"))
        assertTrue("None should degrade to an empty plan", src.contains("if _parsed is None"))
        assertTrue(
            "Non-list results must fail with an explanatory message",
            src.contains("must return a list of action objects")
        )
    }

    @Test
    fun resultIsMarkedSoStrayPrintsCannotCorruptThePlan() {
        val src = PythonEngineSource.text
        assertTrue(src.contains("__AIFM_ACTIONS__"))
        assertTrue(
            "Parser must select the marked line rather than the last line of output",
            src.contains("lastOrNull { it.startsWith(RESULT_MARKER) }")
        )
    }

    @Test
    fun jsonNullDestinationIsNotReadAsTheStringNull() {
        val src = PythonEngineSource.text
        assertTrue(
            "JSON null must be detected with isNull(), not getString()",
            src.contains("!obj.isNull(\"destination\")")
        )
    }

    @Test
    fun sandboxSetupStillAppliesToGeneratorRuns() {
        // The generator runs through the same hardened request, so the workspace guard applies.
        assertTrue(setup().contains("builtins.open = _safe_open"))
    }
}

/** Reads the engine source so the runner contract can be asserted without a Python runtime. */
private object PythonEngineSource {
    val text: String by lazy {
        val candidates = listOf(
            "app/src/main/java/com/aviansh/aifilemanager/domain/engines/PythonEngine.kt",
            "../app/src/main/java/com/aviansh/aifilemanager/domain/engines/PythonEngine.kt"
        )
        candidates.map { java.io.File(it) }
            .firstOrNull { it.exists() }
            ?.readText()
            ?: error("Unable to locate PythonEngine.kt from ${java.io.File(".").absolutePath}")
    }
}
