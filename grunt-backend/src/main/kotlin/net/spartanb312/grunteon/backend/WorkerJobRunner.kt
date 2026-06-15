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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

object WorkerJobRunner {
    fun run(jobDir: Path) {
        val normalizedJobDir = jobDir.toAbsolutePath().normalize()
        val configPath = normalizedJobDir.resolve("config.json")
        val inputPath = normalizedJobDir.resolve("input.jar")
        val libsDir = normalizedJobDir.resolve("libs")
        val outputPath = normalizedJobDir.resolve("output.jar")
        val mappingsPath = normalizedJobDir.resolve("mappings.json")
        val logPath = normalizedJobDir.resolve("log.txt")
        val normalizedConfigPath = normalizedJobDir.resolve("normalized-config.json")

        normalizedJobDir.createDirectories()

        try {
            PluginManager.loadPlugins()

            val libPaths = if (libsDir.exists()) {
                Files.list(libsDir).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                        .sorted()
                        .toList()
                }
            } else {
                emptyList()
            }

            val original = ObfConfig.read(configPath)
            val config = original.copy(
                globalConfig = original.globalConfig.copy(
                    input = inputPath.toString(),
                    output = outputPath.toString(),
                    libs = libPaths.map { it.toString() },
                )
            )
            ObfConfig.write(config, normalizedConfigPath)

            Logger = SimpleLogger(
                "Grunteon-${normalizedJobDir.fileName}",
                logPath.toString()
            ) { config.globalConfig.profiler }

            val io = ObfuscationIO(
                input = PathResourceInput(inputPath),
                libraries = libPaths.map { PathResourceInput(it) },
                output = PathResourceOutput(outputPath),
                mappingsOutput = PathResourceOutput(mappingsPath),
            )

            Grunteon.create(config, io).execute()
            createResultZip(normalizedJobDir.resolve("result.zip"), outputPath, mappingsPath, logPath, normalizedConfigPath)
        } catch (error: Throwable) {
            runCatching {
                Files.writeString(
                    logPath,
                    "\n${error.stackTraceToString()}",
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND,
                )
            }
            throw error
        }
    }

    private fun createResultZip(zipPath: Path, vararg files: Path) {
        ZipOutputStream(zipPath.outputStream()).use { zip ->
            files.filter { it.exists() }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
