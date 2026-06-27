package com.aviansh.aifilemanager.domain.engines

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
class ToyBoxExecutionResult(
    val exitCode: Int,
    val stdout: String?,
    stderr: String?
) {

    companion object {
        private val IGNORED_STDERR_PATTERNS = listOf(
            Regex("^toybox: warning:.*", RegexOption.IGNORE_CASE),
            Regex("^warning:.*", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * stderr with harmless warnings removed.
     */
    val stderr: String? = stderr
        ?.lineSequence()
        ?.filterNot { line ->
            IGNORED_STDERR_PATTERNS.any { it.matches(line.trim()) }
        }
        ?.joinToString("\n")
        ?.takeIf { it.isNotBlank() }

    val isSuccess: Boolean
        get() = exitCode == 0

    val hasErrors: Boolean
        get() = !stderr.isNullOrBlank()

    /**
     * Returns the full command output.
     *
     * If both stdout and stderr contain text, they are separated by a newline.
     * Harmless stderr warnings are already filtered out.
     */
    fun getOutput(): String =
        buildString {
            stdout?.trim()?.takeIf { it.isNotEmpty() }?.let { append(it) }

            stderr?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append('\n')
                append(it)
            }
        }

    override fun toString(): String = buildString {
        append("ExitCode=").append(exitCode)

        if (!stdout.isNullOrBlank()) {
            append("\nstdout:\n").append(stdout)
        }

        if (!stderr.isNullOrBlank()) {
            append("\nstderr:\n").append(stderr)
        }
    }
}

object ToyboxEngine {
    /**
     * Executes a command using toybox sh.
     * 
     * Example:
     * execute("ls /sdcard");
     * execute("id");
     * execute("echo hello");
     */
    @Throws(IOException::class, InterruptedException::class)
    fun execute(command: String?): ToyBoxExecutionResult {
        val process = ProcessBuilder(
            "/system/bin/toybox",
            "sh",
            "-c",
            command
        ).start()

        val stdout = read(process.inputStream)
        val stderr = read(process.errorStream)

        val exitCode = process.waitFor()

        return ToyBoxExecutionResult(exitCode, stdout, stderr)
    }

    @Throws(IOException::class)
    private fun read(`is`: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()

        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            sb.append(line).append('\n')
        }

        return sb.toString()
    }
}