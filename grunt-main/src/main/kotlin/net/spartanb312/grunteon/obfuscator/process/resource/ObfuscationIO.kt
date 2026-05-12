package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.ObfConfig
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.walk

interface ResourceInput {
    val description: String
    fun resolvePath(): Path
}

interface ResourceOutput {
    val description: String
    val fileName: String
    fun exists(): Boolean
    fun openOutputStream(): OutputStream
}

data class PathResourceInput(
    private val path: Path
) : ResourceInput {
    override val description: String get() = path.absolutePathString()
    override fun resolvePath(): Path = path
}

data class PathResourceOutput(
    private val path: Path
) : ResourceOutput {
    override val description: String get() = path.absolutePathString()
    override val fileName: String get() = path.name
    override fun exists(): Boolean = path.exists()

    override fun openOutputStream(): OutputStream {
        path.toAbsolutePath().parent?.createDirectories()
        return path.outputStream()
    }
}

data class ObfuscationIO(
    val input: ResourceInput,
    val libraries: List<ResourceInput> = emptyList(),
    val output: ResourceOutput? = null,
    val mappingsOutput: ResourceOutput? = null,
) {
    companion object {
        fun fromConfig(config: ObfConfig): ObfuscationIO {
            return ObfuscationIO(
                input = PathResourceInput(Path(config.input)),
                libraries = config.libs
                    .map { PathResourceInput(Path(it)) }
                    .flatMap { it.expandJarInputs() },
                output = config.output?.let { PathResourceOutput(Path(it)) },
            )
        }
    }
}

fun ResourceInput.expandJarInputs(): List<ResourceInput> {
    val path = resolvePath()
    return if (path.isDirectory()) {
        path.walk()
            .filter { !it.isDirectory() && it.extension.equals("jar", ignoreCase = true) }
            .map { PathResourceInput(it) }
            .toList()
    } else {
        listOf(this)
    }
}
