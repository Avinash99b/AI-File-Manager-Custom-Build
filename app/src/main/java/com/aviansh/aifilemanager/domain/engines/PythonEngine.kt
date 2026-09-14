package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import com.aviansh.aifilemanager.domain.sandbox.PythonExecutionRequest
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object PythonEngine {

    private const val TAG = "PythonEngine"
    private val executor = Executors.newCachedThreadPool()

    /**
     * Executes arbitrary python code based on [PythonExecutionRequest].
     * Enforces wall-clock timeout, bounded output, path restrictions, and network policy.
     */
    fun execute(request: PythonExecutionRequest): String {
        if (request.code.isBlank()) {
            return "Execution Error: Code request is empty"
        }

        val future: Future<String> = executor.submit<String> {
            runPythonCodeInternal(request)
        }

        return try {
            future.get(request.timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            "Execution Error: Timed out after ${request.timeoutMillis}ms (Native/JNI code interruption may be imperfect)."
        } catch (e: Exception) {
            "Execution Error: ${e.cause?.message ?: e.message}"
        }
    }

    /**
     * Cleans up code string from model responses: strips markdown fences
     * and normalizes common leading indentation.
     */
    internal fun cleanAndNormalizeCode(code: String): String {
        var text = code.lineSequence()
            .dropWhile(String::isBlank)
            .joinToString("\n")
            .trimEnd()

        val trimmedStart = text.trimStart()
        if (trimmedStart.startsWith("```")) {
            val firstLineEnd = text.indexOf('\n')
            if (firstLineEnd != -1) {
                text = text.substring(firstLineEnd + 1)
            } else {
                text = text.trimStart().removePrefix("```")
            }
            text = text.lineSequence()
                .dropWhile(String::isBlank)
                .joinToString("\n")
        }

        val trimmedEnd = text.trimEnd()
        if (trimmedEnd.endsWith("```")) {
            val lastFence = trimmedEnd.lastIndexOf("```")
            text = trimmedEnd.substring(0, lastFence).trimEnd()
        }

        return text.trimIndent()
    }

    /**
     * Executes arbitrary python code and returns bounded stdout/stderr.
     */
    fun executeArbitraryCode(code: String, workspaceDir: String? = null): String {
        val normalizedCode = cleanAndNormalizeCode(code)
        val request = PythonExecutionRequest(
            code = normalizedCode,
            workspaceDir = workspaceDir
        )
        return execute(request)
    }

    internal fun buildHardenedPythonSetup(request: PythonExecutionRequest): String {
        val workspaceDirStr = request.workspaceDir?.let { path ->
            if (path.isBlank()) "" else {
                try {
                    File(path).canonicalPath
                } catch (_: Exception) {
                    File(path).absolutePath
                }
            }
        } ?: ""

        val allowedRootsStr = request.allowedReadRoots.map { file ->
            try {
                file.canonicalPath
            } catch (_: Exception) {
                file.absolutePath
            }
        }

        return buildString {
            if (workspaceDirStr.isNotBlank()) {
                appendLine("import os")
                appendLine("os.chdir(r'$workspaceDirStr')")
            }

            if (request.denyNetwork) {
                appendLine("""
                    import sys, builtins
                    _orig_import = builtins.__import__
                    _blocked_net_mods = {'socket', 'urllib', 'requests', 'http', 'httplib2', 'ftplib'}

                    def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
                        mod_base = name.split('.')[0]
                        if mod_base in _blocked_net_mods:
                            raise PermissionError(f"Network module '{name}' is denied by network policy")
                        return _orig_import(name, globals, locals, fromlist, level)

                    builtins.__import__ = _safe_import
                    sys.modules['socket'] = None
                """.trimIndent())
            }

            appendLine("""
                import os, builtins, sys
                _orig_open = builtins.open
                _workspace_dir = r'$workspaceDirStr'
                _allowed_roots = ${allowedRootsStr.map { "r'$it'" }}

                def _is_subpath(target, root):
                    if not root: return False
                    try:
                        t_abs = os.path.abspath(str(target))
                        r_abs = os.path.abspath(str(root))
                        t_real = os.path.realpath(t_abs)
                        r_real = os.path.realpath(r_abs)

                        def _contains(child, parent):
                            if not child or not parent: return False
                            p_prefix = parent if parent.endswith(os.sep) else parent + os.sep
                            return child == parent or child.startswith(p_prefix)

                        return (_contains(t_abs, r_abs) or
                                _contains(t_real, r_real) or
                                _contains(t_real, r_abs) or
                                _contains(t_abs, r_real))
                    except Exception:
                        return False

                def _safe_open(file, mode='r', *args, **kwargs):
                    if isinstance(file, int):
                        return _orig_open(file, mode, *args, **kwargs)

                    filepath = os.fsdecode(file) if isinstance(file, (str, bytes)) else str(file)
                    mode_str = str(mode)
                    is_write = 'w' in mode_str or 'a' in mode_str or '+' in mode_str or 'x' in mode_str

                    if is_write:
                        if _workspace_dir and not _is_subpath(filepath, _workspace_dir):
                            raise PermissionError(f"Write denied outside workspace: {filepath}")
                    else:
                        allowed = False
                        if _workspace_dir and _is_subpath(filepath, _workspace_dir):
                            allowed = True
                        for root in _allowed_roots:
                            if root and _is_subpath(filepath, root):
                                allowed = True
                                break
                        if _allowed_roots and not allowed:
                            raise PermissionError(f"Read denied outside allowed roots: {filepath}")
                    return _orig_open(file, mode, *args, **kwargs)

                builtins.open = _safe_open
                if 'io' in sys.modules:
                    sys.modules['io'].open = _safe_open
            """.trimIndent())
        }
    }

    private fun runPythonCodeInternal(request: PythonExecutionRequest): String {
        if (!Python.isStarted()) {
            return "JVM Sandbox Stub: Python environment not started. Execution simulated safely."
        }

        val py = Python.getInstance()
        val builtins = py.getModule("builtins")
        val sys = py.getModule("sys")
        val io = py.getModule("io")

        val globalsDict: PyObject = builtins.callAttr("dict")
        val setupCode = buildHardenedPythonSetup(request)

        val stringIo = io.callAttr("StringIO")
        val oldStdout = sys.get("stdout")
        val oldStderr = sys.get("stderr")
        sys.put("stdout", stringIo)
        sys.put("stderr", stringIo)

        return try {
            builtins.callAttr("exec", setupCode + "\n" + request.code, globalsDict)
            val fullOutput = stringIo.callAttr("getvalue").toString()
            truncateOutput(fullOutput, request.maxOutputBytes)
        } catch (e: Exception) {
            val errOutput = stringIo.callAttr("getvalue").toString()
            truncateOutput("Execution Error: ${e.message}\n$errOutput", request.maxOutputBytes)
        } finally {
            sys.put("stdout", oldStdout)
            sys.put("stderr", oldStderr)
            stringIo.callAttr("close")
        }
    }

    private fun truncateOutput(output: String, maxBytes: Int): String {
        val bytes = output.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return output
        val truncated = String(bytes, 0, maxBytes, Charsets.UTF_8)
        return "$truncated\n[OUTPUT TRUNCATED to $maxBytes bytes]"
    }

    /**
     * Executes generator code expecting a generate() function that returns a JSON string.
     * Hardened through PythonExecutionRequest policy.
     */
    fun executeGeneratorCode(
        code: String,
        workspaceDir: String? = null,
        allowedReadRoots: List<File> = emptyList(),
        timeoutMillis: Long = 10_000L,
        denyNetwork: Boolean = true
    ): String {
        val normalizedCode = cleanAndNormalizeCode(code)
        val request = PythonExecutionRequest(
            code = "import json\n$normalizedCode\nprint(json.dumps(generate()))",
            workspaceDir = workspaceDir,
            allowedReadRoots = allowedReadRoots,
            timeoutMillis = timeoutMillis,
            denyNetwork = denyNetwork
        )
        val output = execute(request)
        if (output.startsWith("Execution Error:") || output.startsWith("JVM Sandbox Stub:")) {
            throw IllegalStateException(output)
        }

        return output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .lastOrNull()
            ?.also { JSONArray(it) }
            ?: throw IllegalStateException("generate() returned no JSON output")
    }

    fun generateMessage(generatorCode: String): String = executeGeneratorCode(generatorCode)

    fun generateActions(generatorCode: String, workspaceDir: String? = null): List<FileAction> {
        val json = executeGeneratorCode(generatorCode, workspaceDir)

        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val type = when (obj.getString("action").lowercase()) {
                "move"   -> FileActionType.MOVE
                "copy"   -> FileActionType.COPY
                "delete" -> FileActionType.DELETE
                "create" -> FileActionType.CREATE
                else     -> throw IllegalArgumentException(
                    "Unknown action: ${obj.getString("action")}"
                )
            }
            FileAction(
                type = type,
                sourcePath = obj.getString("source"),
                destinationPath = if (obj.has("destination")) obj.getString("destination") else null,
                overwrite = obj.optBoolean("overwrite", false)
            ).also { action ->
                if (action.destinationPath.isNullOrBlank() && type != FileActionType.DELETE) {
                    throw IllegalArgumentException(
                        "Action of type ${obj.getString("action")} requires a non-null 'destination' " +
                            "(got: ${obj.toString()})"
                    )
                }
            }
        }
    }
}
