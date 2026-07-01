package com.aviansh.aifilemanager.domain.engines

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.FileActionType
import org.json.JSONArray

object PythonEngine {

    /**
     * Executes arbitrary generator code.
     *
     * The code MUST define:
     *   def generate():
     *     ...
     *
     * Returns the JSON string produced by generate().
     */
    fun executeCode(code: String): String {
        val py = Python.getInstance()
        val builtins = py.getModule("builtins")

        // exec() requires a plain dict as globals, not a module object.
        // We create one via builtins.dict() so Chaquopy is happy.
        val globalsDict: PyObject = builtins.callAttr("dict")

        builtins.callAttr("exec", code, globalsDict)

        val generator = globalsDict.callAttr("get", "generate")
            ?: throw IllegalStateException("generate() not found in provided code")

        val result: PyObject = generator.call()
        return result.toString()
    }

    fun generateMessage(generatorCode: String): String = executeCode(generatorCode)

    /**
     * Runs [generatorCode] and parses the resulting JSON array into [FileAction]s.
     *
     * Expected JSON schema per element:
     * {
     *   "action": "move" | "copy" | "delete" | "create",
     *   "source": "<absolute path>",
     *   "destination": "<absolute path>"   // optional for delete
     * }
     */
    fun generateActions(generatorCode: String): List<FileAction> {
        val json = executeCode(generatorCode)
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
                destinationPath = if (obj.has("destination")) obj.getString("destination") else null
            )
        }
    }
}