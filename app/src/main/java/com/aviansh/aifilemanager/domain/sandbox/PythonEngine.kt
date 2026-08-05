package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import org.json.JSONArray

object PythonEngine {

    /**
     * Executes arbitrary python code and returns its stdout.
     */
    fun executeArbitraryCode(code: String, workspaceDir: String? = null): String {
        val py = Python.getInstance()
        val builtins = py.getModule("builtins")
        val sys = py.getModule("sys")
        val io = py.getModule("io")

        val globalsDict: PyObject = builtins.callAttr("dict")

        val setupCode = if (workspaceDir != null) {
            """
import os
os.chdir("$workspaceDir")
            """.trimIndent()
        } else {
            ""
        }

        // Redirect stdout
        val stringIo = io.callAttr("StringIO")
        val oldStdout = sys.get("stdout")
        sys.put("stdout", stringIo)

        return try {
            builtins.callAttr("exec", setupCode + "\n" + code, globalsDict)
            stringIo.callAttr("getvalue").toString()
        } catch (e: Exception) {
            "Execution Error: ${e.message}\n" + stringIo.callAttr("getvalue").toString()
        } finally {
            sys.put("stdout", oldStdout)
            stringIo.callAttr("close")
        }
    }

    /**
     * Executes generator code expecting a generate() function that returns a JSON string.
     */
    fun executeGeneratorCode(code: String, workspaceDir: String? = null): String {
        val py = Python.getInstance()
        val builtins = py.getModule("builtins")

        val globalsDict: PyObject = builtins.callAttr("dict")

        val setupCode = if (workspaceDir != null) {
            """
import os
os.chdir("$workspaceDir")
            """.trimIndent()
        } else {
            ""
        }

        val fullCode = setupCode + "\n" + code

        builtins.callAttr("exec", fullCode, globalsDict)

        val generator = globalsDict.callAttr("get", "generate")
            ?: throw IllegalStateException("generate() not found in provided code")

        val result: PyObject = generator.call()
        return result.toString()
    }

    fun generateMessage(generatorCode: String): String = executeGeneratorCode(generatorCode)

    fun generateActions(generatorCode: String, workspaceDir: String? = null): List<FileAction> {
        val json = executeGeneratorCode(generatorCode, workspaceDir)

        Log.d("PythonEngine", "Actions JSON: $json")
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
            )
        }
    }
}