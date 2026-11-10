package com.example.domain.tools

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thin, dependency-free wrapper around [ProcessBuilder].
 *
 * Used for the real `shell_exec`, `git_status` and `git_diff` tools. Output is captured with a
 * byte cap, the child is killed when it overruns [timeoutMs], and a missing binary is reported as a
 * launch error instead of throwing. Written against API 24-safe APIs only (no
 * `Process.waitFor(timeout, unit)`).
 */
object ProcessRunner {

    data class Result(
        val argv: List<String>,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val durationMs: Long,
        val launchError: String? = null
    ) {
        val commandLine: String get() = argv.joinToString(" ")
        val isSuccess: Boolean get() = launchError == null && !timedOut && exitCode == 0
    }

    fun run(
        argv: List<String>,
        workingDir: File? = null,
        timeoutMs: Long = 30_000L,
        maxOutputBytes: Int = 64_000,
        extraEnv: Map<String, String> = emptyMap()
    ): Result {
        if (argv.isEmpty()) {
            return Result(argv, -1, "", "", false, 0L, "No command given")
        }
        val startedAt = System.currentTimeMillis()
        val builder = ProcessBuilder(argv)
        if (workingDir != null) builder.directory(workingDir)
        builder.redirectErrorStream(false)
        for ((key, value) in extraEnv) builder.environment()[key] = value

        val process = try {
            builder.start()
        } catch (e: IOException) {
            return Result(
                argv = argv,
                exitCode = -1,
                stdout = "",
                stderr = "",
                timedOut = false,
                durationMs = System.currentTimeMillis() - startedAt,
                launchError = e.message ?: "Failed to start process"
            )
        }

        try {
            process.outputStream.close()
        } catch (_: IOException) {
            // stdin is unused; a closed pipe here is harmless
        }

        val outBuffer = StringBuilder()
        val errBuffer = StringBuilder()
        val stdoutThread = startReader(process.inputStream, outBuffer, maxOutputBytes)
        val stderrThread = startReader(process.errorStream, errBuffer, maxOutputBytes)

        val exitCode = AtomicInteger(Int.MIN_VALUE)
        val waiter = Thread {
            try {
                exitCode.set(process.waitFor())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        waiter.isDaemon = true
        waiter.start()

        waiter.join(timeoutMs)
        val timedOut = waiter.isAlive
        if (timedOut) {
            process.destroy()
            waiter.join(1_500L)
            if (waiter.isAlive) process.destroyForcibly()
        }

        stdoutThread.join(1_000L)
        stderrThread.join(1_000L)

        return Result(
            argv = argv,
            exitCode = if (timedOut) -1 else exitCode.get().let { if (it == Int.MIN_VALUE) -1 else it },
            stdout = outBuffer.toString(),
            stderr = errBuffer.toString(),
            timedOut = timedOut,
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun startReader(stream: InputStream, sink: StringBuilder, cap: Int): Thread {
        val thread = Thread {
            try {
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val chunk = CharArray(4096)
                    while (true) {
                        val read = reader.read(chunk)
                        if (read < 0) break
                        synchronized(sink) {
                            if (sink.length < cap) {
                                sink.append(chunk, 0, read)
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                // process died or was killed; keep what we already captured
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }
}
