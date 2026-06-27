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
     *
     * def generate():
     *      ...
     *
     * Returns the JSON string produced by generate().
     */
    fun executeCode(code: String): String {

        val py = Python.getInstance()

        val builtins = py.getModule("builtins")

        val globals = py.getModule("__main__").dict

        builtins.callAttr(
            "exec",
            code,
            globals
        )

        val generator = globals["generate"]
            ?: throw IllegalStateException("generate() not found")

        val result: PyObject = generator.call()

        return result.toString()
    }

    fun generateMessage(generatorCode: String): String {

        return executeCode(generatorCode)

    }

    fun generateActions(
        generatorCode: String
    ): List<FileAction> {

        val json = executeCode(generatorCode)

        val arr = JSONArray(json)

        val actions = mutableListOf<FileAction>()

        for (i in 0 until arr.length()) {

            val obj = arr.getJSONObject(i)

            val type = when (obj.getString("action").lowercase()) {

                "move" -> FileActionType.MOVE

                "copy" -> FileActionType.COPY

                "delete" -> FileActionType.DELETE

                "create" -> FileActionType.CREATE

                else -> throw IllegalArgumentException(
                    "Unknown action"
                )
            }

            actions.add(

                FileAction(

                    type = type,

                    sourcePath = obj.getString("source"),

                    destinationPath =
                        if (obj.has("destination"))
                            obj.getString("destination")
                        else null

                )

            )

        }

        return actions

    }

}