package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.aviansh.aifilemanager.domain.AppPaths
import com.aviansh.aifilemanager.domain.sandbox.PythonExecutionRequest
import com.chaquo.python.PyObject
import com.chaquo.python.Python
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
    fun executeArbitraryCode(
        code: String,
        workspaceDir: String? = null,
        allowedReadRoots: List<File> = emptyList(),
        timeoutMillis: Long = 60_000L
    ): String {
        val normalizedCode = cleanAndNormalizeCode(code)
        val request = PythonExecutionRequest(
            code = normalizedCode,
            workspaceDir = workspaceDir,
            allowedReadRoots = allowedReadRoots,
            timeoutMillis = timeoutMillis
        )
        return execute(request)
    }

    /**
     * Directories the CPython runtime and installed wheels legitimately need to write to for
     * imports to succeed (Chaquopy extracts requirements/assets lazily on first import, and many
     * libraries touch temp dirs). Blocking these makes `from pypdf import PdfReader` fail with a
     * misleading "Write denied outside workspace" error, so they are always allowed.
     */
    private fun defaultInfraWriteRoots(): List<File> {
        val roots = mutableListOf<File>()
        if (AppPaths.filesDir.isNotBlank()) roots.add(File(AppPaths.filesDir, "chaquopy"))
        if (AppPaths.cacheDir.isNotBlank()) roots.add(File(AppPaths.cacheDir))
        System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() }?.let { roots.add(File(it)) }
        return roots
    }

    private fun canonicalOf(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }

    internal fun buildHardenedPythonSetup(request: PythonExecutionRequest): String {
        val workspaceDirStr = request.workspaceDir?.let { path ->
            if (path.isBlank()) "" else canonicalOf(File(path))
        } ?: ""

        val allowedRootsStr = request.allowedReadRoots.map { canonicalOf(it) }

        val infraRoots = (request.infraWriteRoots + defaultInfraWriteRoots())
            .map { canonicalOf(it) }
            .filter { it.isNotBlank() }
            .distinct()

        return buildString {
            if (workspaceDirStr.isNotBlank()) {
                appendLine("import os")
                appendLine("os.makedirs(r'$workspaceDirStr', exist_ok=True)")
                appendLine("os.chdir(r'$workspaceDirStr')")
            }

            if (request.denyNetwork) {
                appendLine("""
                    # Network policy is enforced at the socket layer rather than by banning
                    # imports. Banning module names broke ordinary offline work, because large
                    # chunks of the standard library (email, zipfile, xml, and therefore pypdf,
                    # pandas and openpyxl) import socket/urllib transitively and would fail with
                    # a misleading "denied by network policy" error. Neutralising the socket
                    # itself is both stricter and invisible to code that never goes online.
                    import socket as _socket_mod

                    def _denied(*args, **kwargs):
                        raise PermissionError("Network access is denied by policy")

                    class _BlockedSocket(_socket_mod.socket):
                        def connect(self, *a, **k): _denied()
                        def connect_ex(self, *a, **k): _denied()
                        def sendto(self, *a, **k): _denied()
                        def bind(self, *a, **k): _denied()

                    _socket_mod.socket = _BlockedSocket
                    _socket_mod.create_connection = _denied
                    _socket_mod.create_server = _denied

                    import sys, builtins
                    _orig_import = builtins.__import__
                    # These are pure HTTP clients: nothing offline needs them, and failing fast
                    # on import gives the agent a much clearer signal than a socket error.
                    _blocked_net_modules = {
                        'requests', 'httpx', 'urllib3', 'aiohttp', 'websocket', 'websockets'
                    }

                    def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
                        if name.split('.')[0] in _blocked_net_modules:
                            raise PermissionError(
                                f"Network module '{name}' is denied by network policy"
                            )
                        return _orig_import(name, globals, locals, fromlist, level)

                    builtins.__import__ = _safe_import
                """.trimIndent())
            }

            appendLine("""
                import os, builtins, sys
                _orig_open = builtins.open
                _workspace_dir = r'$workspaceDirStr'
                _allowed_roots = ${allowedRootsStr.map { "r'$it'" }}
                _infra_write_roots = ${infraRoots.map { "r'$it'" }}

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

                def _is_infra_path(filepath):
                    # Paths owned by the Python runtime itself (Chaquopy asset extraction,
                    # __pycache__, temp files). Writing here is an implementation detail of
                    # importing a library, never a user-visible file operation.
                    for root in _infra_write_roots:
                        if root and _is_subpath(filepath, root):
                            return True
                    text = str(filepath)
                    base = os.path.basename(text)
                    if base.endswith('.pyc') or '__pycache__' in text:
                        return True
                    # Chaquopy extracts wheels lazily on first import; the exact data dir varies
                    # (/data/data/... vs /data/user/0/...), so match the marker directory too.
                    if '/chaquopy/' in text or text.endswith('/chaquopy'):
                        return True
                    return False

                def _safe_open(file, mode='r', *args, **kwargs):
                    if isinstance(file, int):
                        return _orig_open(file, mode, *args, **kwargs)

                    try:
                        filepath = os.fspath(file)
                    except TypeError:
                        filepath = str(file)
                    if isinstance(filepath, bytes):
                        filepath = os.fsdecode(filepath)

                    mode_str = str(mode)
                    is_write = any(flag in mode_str for flag in ('w', 'a', '+', 'x'))

                    if _is_infra_path(filepath):
                        return _orig_open(file, mode, *args, **kwargs)

                    if is_write:
                        if _workspace_dir and not _is_subpath(filepath, _workspace_dir):
                            raise PermissionError(
                                "Write denied outside workspace: " + str(filepath) +
                                ". run_python may only write inside its scratch workspace (" + _workspace_dir +
                                "). To change a real file, use the write_file, move, copy or delete tool instead."
                            )
                    else:
                        allowed = False
                        if _workspace_dir and _is_subpath(filepath, _workspace_dir):
                            allowed = True
                        if not allowed:
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

                # builtins.open only covers file content. Mutating calls made straight through
                # the os module (and therefore shutil, pathlib, tempfile, ...) must respect the
                # same workspace boundary, otherwise run_python could delete user files without
                # passing through the confirmed, trash-backed file tools.
                def _guard_write_path(func_name, filepath):
                    if _is_infra_path(filepath):
                        return
                    if _workspace_dir and not _is_subpath(filepath, _workspace_dir):
                        raise PermissionError(
                            func_name + " denied outside workspace: " + str(filepath) +
                            ". run_python may only modify its scratch workspace (" + _workspace_dir +
                            "). Use the write_file, move, copy or delete tool for real files."
                        )

                def _wrap_os_write(func_name, arg_count=1):
                    original = getattr(os, func_name, None)
                    if original is None:
                        return

                    def wrapper(*args, **kwargs):
                        for path_arg in args[:arg_count]:
                            if isinstance(path_arg, int):
                                continue
                            try:
                                candidate = os.fspath(path_arg)
                            except TypeError:
                                continue
                            if isinstance(candidate, bytes):
                                candidate = os.fsdecode(candidate)
                            _guard_write_path(func_name, candidate)
                        return original(*args, **kwargs)

                    wrapper.__name__ = func_name
                    setattr(os, func_name, wrapper)

                for _fn in ('remove', 'unlink', 'rmdir', 'removedirs', 'mkdir', 'makedirs',
                            'truncate', 'chmod', 'symlink'):
                    _wrap_os_write(_fn, 1)
                for _fn in ('rename', 'renames', 'replace', 'link'):
                    _wrap_os_write(_fn, 2)
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

}
