package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal object NativeCompiler {

    fun compile(
        bundle: NativeSourceBundle,
        config: NativePipelineConfig = NativePipelineConfig()
    ): NativeCompileResult {
        bundle.sourcePath.parent.createDirectories()
        bundle.libraryPath.parent.createDirectories()
        bundle.sourcePath.writeText(bundle.sourceText)

        val compiler = findCompiler(config) ?: return NativeCompileResult(
            success = false,
            output = if (config.compilerExecutable.isNullOrBlank()) {
                "No C++ compiler found on PATH. Tried clang++, g++, clang-cl, and cl."
            } else {
                "Configured C++ compiler not found: ${config.compilerExecutable}"
            }
        )
        val javaHome = Path.of(System.getProperty("java.home"))
        val includeRoot = resolveJniIncludeRoot(javaHome)
        val includeOs = includeRoot.resolve(bundle.plan.platform.jniIncludeOs)
        if (!includeRoot.exists() || !includeOs.exists()) {
            return NativeCompileResult(
                success = false,
                output = "JNI headers not found under ${includeRoot.absolutePathString()} and ${includeOs.absolutePathString()}"
            )
        }

        val command = buildCompileCommand(bundle, compiler, includeRoot, includeOs, config)

        val process = try {
            ProcessBuilder(command)
                .directory(bundle.sourcePath.parent.toFile())
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            return NativeCompileResult(
                success = false,
                output = "Failed to start native compiler ${compiler.command}: ${exception.message}"
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return if (exitCode == 0 && Files.exists(bundle.libraryPath)) {
            NativeCompileResult(true, bundle.libraryPath, output)
        } else {
            NativeCompileResult(false, null, "Native compiler exited with code $exitCode\n$output")
        }
    }

    internal fun buildCompileCommand(
        bundle: NativeSourceBundle,
        compiler: NativeCompilerExecutable,
        includeRoot: Path,
        includeOs: Path,
        config: NativePipelineConfig = NativePipelineConfig()
    ): List<String> {
        return when (compiler.kind) {
            NativeCompilerKind.GnuLike -> buildGnuLikeCommand(bundle, compiler.command, includeRoot, includeOs, config)
            NativeCompilerKind.Msvc -> buildMsvcCommand(bundle, compiler.command, includeRoot, includeOs, config)
        }
    }

    private fun buildGnuLikeCommand(
        bundle: NativeSourceBundle,
        compiler: String,
        includeRoot: Path,
        includeOs: Path,
        config: NativePipelineConfig
    ): List<String> {
        val sharedFlag = if (bundle.plan.platform.os == "macos") "-dynamiclib" else "-shared"
        return buildList {
            add(compiler)
            add("-std=c++17")
            add("-O2")
            add("-fPIC")
            add(sharedFlag)
            addAll(defaultGnuLikeCompilerArgs(bundle.plan.platform))
            addAll(config.compilerArgs)
            add("-I")
            add(includeRoot.absolutePathString())
            add("-I")
            add(includeOs.absolutePathString())
            add("-o")
            add(bundle.libraryPath.absolutePathString())
            add(bundle.sourcePath.absolutePathString())
        }
    }

    private fun defaultGnuLikeCompilerArgs(platform: NativePlatform): List<String> {
        return if (platform.os == "windows") {
            listOf("-static", "-static-libgcc", "-static-libstdc++")
        } else {
            emptyList()
        }
    }

    private fun buildMsvcCommand(
        bundle: NativeSourceBundle,
        compiler: String,
        includeRoot: Path,
        includeOs: Path,
        config: NativePipelineConfig
    ): List<String> {
        return buildList {
            add(compiler)
            add("/nologo")
            add("/std:c++17")
            add("/EHsc")
            add("/O2")
            add("/LD")
            add("/I${includeRoot.absolutePathString()}")
            add("/I${includeOs.absolutePathString()}")
            addAll(config.compilerArgs)
            add("/Fe:${bundle.libraryPath.absolutePathString()}")
            add(bundle.sourcePath.absolutePathString())
        }
    }

    private fun resolveJniIncludeRoot(javaHome: Path): Path {
        val direct = javaHome.resolve("include")
        if (direct.exists()) return direct
        return javaHome.parent?.resolve("include") ?: direct
    }

    private fun findCompiler(config: NativePipelineConfig): NativeCompilerExecutable? {
        val configured = config.compilerExecutable?.takeIf { it.isNotBlank() }
        if (configured != null) {
            return resolveConfiguredCompiler(configured)
        }
        return sequenceOf("clang++", "g++", "clang-cl", "cl")
            .mapNotNull { name ->
                findExecutable(name)?.let {
                    NativeCompilerExecutable(it.absolutePath, compilerKind(name))
                }
            }
            .firstOrNull()
    }

    private fun resolveConfiguredCompiler(value: String): NativeCompilerExecutable? {
        val configuredFile = File(value)
        val resolved = when {
            configuredFile.isFile -> configuredFile.absolutePath
            value.contains('/') || value.contains('\\') -> return null
            else -> findExecutable(value)?.absolutePath ?: return null
        }
        return NativeCompilerExecutable(resolved, compilerKind(value))
    }

    internal fun compilerKind(command: String): NativeCompilerKind {
        val name = File(command).name.lowercase()
        return if (name == "cl" ||
            name == "cl.exe" ||
            name == "clang-cl" ||
            name == "clang-cl.exe"
        ) {
            NativeCompilerKind.Msvc
        } else {
            NativeCompilerKind.GnuLike
        }
    }

    private fun findExecutable(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        val extensions = if (File.separatorChar == '\\') {
            val pathext = System.getenv("PATHEXT")
                ?.split(File.pathSeparatorChar)
                ?.filter { it.isNotBlank() }
                ?: listOf(".COM", ".EXE", ".BAT", ".CMD")
            listOf("") + pathext
        } else {
            listOf("")
        }
        return path
            .split(File.pathSeparatorChar)
            .asSequence()
            .flatMap { dir -> extensions.asSequence().map { ext -> File(dir, name + ext) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    internal data class NativeCompilerExecutable(
        val command: String,
        val kind: NativeCompilerKind
    )

    internal enum class NativeCompilerKind {
        GnuLike,
        Msvc
    }
}
