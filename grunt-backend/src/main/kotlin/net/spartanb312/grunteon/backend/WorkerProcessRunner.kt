package net.spartanb312.grunteon.backend

import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Component
class WorkerProcessRunner(private val properties: BackendProperties) {
    internal fun run(jobDir: Path, attempt: String, heartbeat: () -> Unit): Int {
        val builder = ProcessBuilder(buildCommand(jobDir, attempt))
            .directory(File(System.getProperty("user.dir")))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(jobDir.resolve("worker-process.log").toFile()))
        builder.environment()["OMP_NUM_THREADS"] = properties.effectiveWorkerCpuCount().toString()
        builder.environment()["CMAKE_BUILD_PARALLEL_LEVEL"] = properties.nativeCompileJobs.toString()
        val process = builder.start()
        val exit = awaitProcess(process, properties.workerTimeoutSeconds, properties.heartbeatSeconds,
            properties.workerTerminationGraceSeconds, properties.workerReapTimeoutSeconds, heartbeat)
        return checkAttemptExit(exit, jobDir.resolve("attempts").resolve(attempt).resolve(".started"))
    }

    internal fun buildCommand(jobDir: Path, attempt: String): List<String> {
        properties.effectiveWorkerConcurrency()
        val executable = if (System.getProperty("os.name").startsWith("Windows", true)) "java.exe" else "java"
        val java = properties.javaExecutable.ifBlank {
            Path.of(System.getProperty("java.home"), "bin", executable).toString()
        }
        val options = buildList {
            addAll(properties.workerJavaOptions)
            if (properties.legacyHeapOption() == null) add("-Xmx${properties.workerHeapMiB}m")
            if (properties.legacyCpuOption() == null) add("-XX:ActiveProcessorCount=${properties.effectiveWorkerCpuCount()}")
            add("-Dgrunteon.worker.nativeCompileJobs=${properties.nativeCompileJobs}")
        }
        val classpath = System.getProperty("java.class.path")
        val launch = if (!classpath.contains(File.pathSeparator) && classpath.endsWith(".jar", true)) {
            listOf("-jar", classpath)
        } else listOf("-cp", classpath, "net.spartanb312.grunteon.backend.BackendApplicationKt")
        return listOf(java) + options + launch + listOf("worker", jobDir.toString(), attempt)
    }
}

internal class WorkerAttemptNotStartedException : RuntimeException("Worker timed out before acquiring its job lock")

internal fun checkAttemptExit(exit: Int, startedMarker: Path): Int {
    if (exit == -1 && !Files.exists(startedMarker)) throw WorkerAttemptNotStartedException()
    return exit
}

internal fun awaitProcess(process: Process, timeoutSeconds: Long, heartbeatSeconds: Long,
                          graceSeconds: Long, reapTimeoutSeconds: Long = 5, heartbeat: () -> Unit): Int {
    val descendants = linkedSetOf<ProcessHandle>()
    val started = System.nanoTime()
    var nextHeartbeat = started
    try {
        while (true) {
            descendants.removeIf { !it.isAlive }
            process.descendants().use { it.forEach(descendants::add) }
            val now = System.nanoTime()
            if (now >= nextHeartbeat) {
                heartbeat() // Failure or a lost lease terminates/reaps before releasing this worker slot.
                nextHeartbeat = now + TimeUnit.SECONDS.toNanos(heartbeatSeconds)
            }
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) return process.exitValue()
            if (timeoutSeconds > 0 && System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(timeoutSeconds)) return -1
        }
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } finally {
        terminateAndReap(process, descendants, graceSeconds, reapTimeoutSeconds)
    }
}

internal class UnreapedWorkerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Bounded cleanup. Failure to confirm death is fail-closed: the dispatcher quarantines, never reuses, this slot. */
internal fun terminateAndReap(process: Process, known: Set<ProcessHandle> = emptySet(),
                             graceSeconds: Long = 0, reapTimeoutSeconds: Long = 5) {
    var interrupted = Thread.interrupted()
    val descendants = known.toMutableSet()
    fun alive() = process.isAlive || descendants.any { it.isAlive }
    fun awaitExit(seconds: Long) {
        val started = System.nanoTime()
        val budget = TimeUnit.SECONDS.toNanos(seconds)
        while (alive() && System.nanoTime() - started < budget) {
            try {
                if (process.isAlive) process.waitFor(50, TimeUnit.MILLISECONDS) else Thread.sleep(50)
            } catch (_: InterruptedException) { interrupted = true }
        }
    }
    try {
        process.descendants().use { it.forEach(descendants::add) }
        descendants.filter { it.isAlive }.forEach { it.destroy() }
        if (process.isAlive) process.destroy()
        awaitExit(graceSeconds)
        descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
        if (process.isAlive) process.destroyForcibly()
        awaitExit(reapTimeoutSeconds)
        if (alive()) throw UnreapedWorkerException("Worker/native process termination could not be confirmed; slot quarantined")
        // A timed wait reaps the direct child without an unbounded wait on an unkillable process.
        try { process.waitFor(0, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { interrupted = true }
    } catch (error: UnreapedWorkerException) {
        throw error
    } catch (error: Exception) {
        throw UnreapedWorkerException("Worker cleanup failed; slot quarantined", error)
    } finally {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
