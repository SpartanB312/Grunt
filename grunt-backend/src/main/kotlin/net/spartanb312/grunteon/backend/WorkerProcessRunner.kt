package net.spartanb312.grunteon.backend

import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Component
class WorkerProcessRunner(
    private val properties: BackendProperties,
) {
    fun run(jobDir: Path): Int {
        val command = buildCommand(jobDir)
        val process = ProcessBuilder(command)
            .directory(File(System.getProperty("user.dir")))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(jobDir.resolve("worker-process.log").toFile()))
            .start()
        if (properties.workerTimeoutSeconds > 0) {
            val finished = process.waitFor(properties.workerTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return -1
            }
        }
        return process.waitFor()
    }

    private fun buildCommand(jobDir: Path): List<String> {
        val java = properties.javaExecutable.ifBlank {
            val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                "java.exe"
            } else {
                "java"
            }
            Path(System.getProperty("java.home")).resolve("bin").resolve(executable).absolutePathString()
        }
        val classpath = System.getProperty("java.class.path")
        val isSingleJar = !classpath.contains(File.pathSeparator) && classpath.endsWith(".jar", ignoreCase = true)
        return if (isSingleJar) {
            listOf(java) + properties.workerJavaOptions + listOf("-jar", classpath, "worker", jobDir.toString())
        } else {
            listOf(java) + properties.workerJavaOptions + listOf(
                "-cp",
                classpath,
                "net.spartanb312.grunteon.backend.BackendApplicationKt",
                "worker",
                jobDir.toString(),
            )
        }
    }
}
