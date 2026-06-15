package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

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
        fun fromConfig(globalConfig: GlobalConfig): ObfuscationIO {
            return ObfuscationIO(
                input = PathResourceInput(Path(globalConfig.input)),
                libraries = globalConfig.libs
                    .map { PathResourceInput(Path(it)) }
                    .flatMap { it.expandJarInputs() },
                output = globalConfig.output?.let { PathResourceOutput(Path(it)) },
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
