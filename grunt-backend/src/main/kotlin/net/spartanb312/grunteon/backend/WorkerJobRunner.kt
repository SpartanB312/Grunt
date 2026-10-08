package net.spartanb312.grunteon.backend

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.process.resource.ObfuscationIO
import net.spartanb312.grunteon.obfuscator.process.resource.PathResourceInput
import net.spartanb312.grunteon.obfuscator.process.resource.PathResourceOutput
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

object WorkerJobRunner {
    fun run(jobDir: Path, attempt: String? = null) {
        val normalizedJobDir = jobDir.toAbsolutePath().normalize()
        if (attempt == null) normalizedJobDir.createDirectories()
        else require(Files.isDirectory(normalizedJobDir)) { "Job directory is no longer available" }
        if (attempt != null) {
            require(UUID.fromString(attempt).toString() == attempt) { "Invalid worker attempt id" }
            // The dispatcher alone holds the write end. Backend crash/kill closes it even before lease expiry.
            Thread({
                try { while (System.`in`.read() != -1) { /* no application input */ } } finally {
                    ProcessHandle.current().descendants().use { children -> children.forEach { it.destroyForcibly() } }
                    Runtime.getRuntime().halt(1)
                }
            }, "grunt-worker-parent-watch").apply { isDaemon = true; start() }
        }
        requireNotNull(exclusiveLease(normalizedJobDir, ".worker.lock", wait = true)).use {
            val attemptDir = if (attempt == null) normalizedJobDir else normalizedJobDir.resolve("attempts").resolve(attempt)
            attemptDir.createDirectories()
            if (attempt != null) Files.writeString(attemptDir.resolve(".started"), "")
            execute(normalizedJobDir, attemptDir)
        }
    }

    private fun execute(jobDir: Path, outputDir: Path) {
        val configPath = jobDir.resolve("config.json")
        val inputPath = jobDir.resolve("input.jar")
        val libsDir = jobDir.resolve("libs")
        val outputPath = outputDir.resolve("output.jar")
        val mappingsPath = outputDir.resolve("mappings.json")
        val logPath = outputDir.resolve("log.txt")
        val normalizedConfigPath = outputDir.resolve("normalized-config.json")
        try {
            PluginManager.loadPlugins()
            val libPaths = if (libsDir.exists()) {
                Files.list(libsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", true) }
                        .sorted().toList()
                }
            } else emptyList()
            val original = ObfConfig.read(configPath)
            val nativeBudget = System.getProperty("grunteon.worker.nativeCompileJobs")?.toInt()?.also { require(it > 0) }
            val requestedNativeJobs = original.nativePipeline.parallelCompileJobs
            val config = original.copy(
                globalConfig = original.globalConfig.copy(
                    input = inputPath.toString(), output = outputPath.toString(), libs = libPaths.map { it.toString() },
                ),
                nativePipeline = original.nativePipeline.copy(
                    workDir = outputDir.resolve("native").toString(),
                    parallelCompileJobs = if (nativeBudget == null) requestedNativeJobs
                        else if (requestedNativeJobs <= 0) nativeBudget else minOf(requestedNativeJobs, nativeBudget),
                ),
            )
            ObfConfig.write(config, normalizedConfigPath)
            val previousLogger = Logger
            try {
                SimpleLogger("Grunteon-${jobDir.fileName}", logPath.toString(),
                    debug = { config.globalConfig.profiler }, console = false).use { logger ->
                    Logger = logger
                    val io = ObfuscationIO(
                        input = PathResourceInput(inputPath),
                        libraries = libPaths.map { PathResourceInput(it) },
                        output = PathResourceOutput(outputPath),
                        mappingsOutput = PathResourceOutput(mappingsPath),
                    )
                    Grunteon.create(config, io).use { it.run() }
                } // Ordered logger drain/flush/close must complete before reading log.txt into the ZIP.
            } finally { Logger = previousLogger }
            createResultZip(outputDir.resolve("result.zip"), outputPath, mappingsPath, logPath, normalizedConfigPath)
        } catch (error: Throwable) {
            runCatching {
                Files.writeString(logPath, "\n${error.stackTraceToString()}",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
            }
            throw error
        }
    }

    internal fun createResultZip(zipPath: Path, vararg files: Path) {
        val temporary = Files.createTempFile(zipPath.parent, ".result-", ".zip.tmp")
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                files.filter { it.exists() }.forEach { file ->
                    // Keep DEFLATED headers/data descriptors without wasting CPU recompressing a nested JAR.
                    zip.setLevel(if (file.name.endsWith(".jar", true)) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            Files.move(temporary, zipPath, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
