package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

internal object NativeCompiler {
    private const val AutoCompileJobCap = 2
    private const val EstimatedBytesPerCompileJob = 1536L * 1024L * 1024L

    fun compile(
        bundle: NativeSourceBundle,
        config: NativePipelineConfig = NativePipelineConfig()
    ): NativeCompileResult {
        bundle.libraryPath.parent.createDirectories()
        writeSourceFiles(bundle)

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

        val compileStartNanos = System.nanoTime()
        val result = if (compiler.kind == NativeCompilerKind.GnuLike && bundle.compilableSourcePaths().size > 1) {
            compileGnuLikeSplit(bundle, compiler.command, includeRoot, includeOs, config)
        } else {
            runCommand(
                buildCompileCommand(bundle, compiler, includeRoot, includeOs, config),
                bundle.sourcePath.parent
            )
        }
        val compileTimeMillis = (System.nanoTime() - compileStartNanos) / 1_000_000L
        return if (result.exitCode == 0 && Files.exists(bundle.libraryPath)) {
            NativeCompileResult(true, bundle.libraryPath, result.output, compileTimeMillis)
        } else {
            NativeCompileResult(
                success = false,
                libraryPath = null,
                output = "Native compiler exited with code ${result.exitCode}\n${result.output}",
                compileTimeMillis = compileTimeMillis
            )
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
            add(gnuOptimizationFlag(config))
            addAll(positionIndependentCodeArgs(bundle.plan.platform))
            add(sharedFlag)
            addAll(defaultGnuLikeCompilerArgs(bundle.plan.platform))
            addAll(config.compilerArgs)
            add("-I")
            add(includeRoot.absolutePathString())
            add("-I")
            add(includeOs.absolutePathString())
            add("-o")
            add(bundle.libraryPath.absolutePathString())
            addAll(bundle.compilableSourcePaths().map { it.absolutePathString() })
        }
    }

    internal fun buildGnuLikeObjectCommand(
        sourcePath: Path,
        objectPath: Path,
        compiler: String,
        includeRoot: Path,
        includeOs: Path,
        config: NativePipelineConfig
    ): List<String> {
        return buildList {
            add(compiler)
            add("-std=c++17")
            add(gnuOptimizationFlag(config))
            addAll(positionIndependentCodeArgs(NativePlatform.current()))
            addAll(config.compilerArgs)
            add("-I")
            add(includeRoot.absolutePathString())
            add("-I")
            add(includeOs.absolutePathString())
            add("-c")
            add(sourcePath.absolutePathString())
            add("-o")
            add(objectPath.absolutePathString())
        }
    }

    internal fun buildGnuLikeLinkCommand(
        bundle: NativeSourceBundle,
        compiler: String,
        objectPaths: List<Path>,
        config: NativePipelineConfig
    ): List<String> {
        val sharedFlag = if (bundle.plan.platform.os == "macos") "-dynamiclib" else "-shared"
        return buildList {
            add(compiler)
            add(sharedFlag)
            addAll(defaultGnuLikeCompilerArgs(bundle.plan.platform))
            addAll(config.compilerArgs)
            add("-o")
            add(bundle.libraryPath.absolutePathString())
            addAll(objectPaths.map { it.absolutePathString() })
        }
    }

    private fun defaultGnuLikeCompilerArgs(platform: NativePlatform): List<String> {
        return if (platform.os == "windows") {
            listOf("-static", "-static-libgcc", "-static-libstdc++")
        } else {
            emptyList()
        }
    }

    private fun positionIndependentCodeArgs(platform: NativePlatform): List<String> {
        return if (platform.os == "windows") emptyList() else listOf("-fPIC")
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
            add(msvcOptimizationFlag(config))
            add("/LD")
            add("/I${includeRoot.absolutePathString()}")
            add("/I${includeOs.absolutePathString()}")
            addAll(config.compilerArgs)
            add("/Fe:${bundle.libraryPath.absolutePathString()}")
            addAll(bundle.compilableSourcePaths().map { it.absolutePathString() })
        }
    }

    private fun compileGnuLikeSplit(
        bundle: NativeSourceBundle,
        compiler: String,
        includeRoot: Path,
        includeOs: Path,
        config: NativePipelineConfig
    ): CommandResult {
        val sourcePaths = bundle.compilableSourcePaths()
        val objectDir = bundle.sourcePath.parent.resolve("obj")
        objectDir.createDirectories()
        val objectPaths = sourcePaths.mapIndexed { index, source ->
            objectDir.resolve("${source.name.substringBeforeLast('.')}_${index.toString().padStart(4, '0')}.o")
        }
        val jobs = effectiveParallelCompileJobs(config, sourcePaths.size)
        val executor = Executors.newFixedThreadPool(jobs)
        val output = StringBuilder()
        try {
            val tasks = sourcePaths.zip(objectPaths).map { (source, objectPath) ->
                Callable {
                    val command = buildGnuLikeObjectCommand(source, objectPath, compiler, includeRoot, includeOs, config)
                    runCommand(command, bundle.sourcePath.parent)
                }
            }
            val results = executor.invokeAll(tasks).map { it.get() }
            results.forEach { result ->
                if (result.output.isNotBlank()) output.append(result.output).appendLine()
                if (result.exitCode != 0) {
                    output.append("Failed command: ")
                        .append(result.command.joinToString(" "))
                        .appendLine()
                    return CommandResult(result.exitCode, output.toString(), result.command)
                }
            }
        } finally {
            executor.shutdown()
        }

        val linkCommand = buildGnuLikeLinkCommand(bundle, compiler, objectPaths, config)
        val linkResult = runCommand(linkCommand, bundle.sourcePath.parent)
        if (linkResult.output.isNotBlank()) output.append(linkResult.output)
        return CommandResult(linkResult.exitCode, output.toString(), linkCommand)
    }

    private fun writeSourceFiles(bundle: NativeSourceBundle) {
        val sourceRoot = bundle.sourcePath.parent
        val expectedSources = bundle.sourceFiles.map { it.path.toAbsolutePath().normalize() }.toSet()
        if (sourceRoot.exists()) {
            listOf("grunteon_native*.cpp", "grunteon_native*.hpp").forEach { glob ->
                Files.newDirectoryStream(sourceRoot, glob).use { entries ->
                    entries.forEach { existing ->
                        if (existing.toAbsolutePath().normalize() !in expectedSources) {
                            Files.deleteIfExists(existing)
                        }
                    }
                }
            }
        }
        bundle.sourceFiles.forEach { source ->
            source.path.parent.createDirectories()
            source.path.writeText(source.text)
        }
    }

    private fun NativeSourceBundle.compilableSourcePaths(): List<Path> {
        return sourceFiles.map { it.path }.filter { it.isCppSource() }
    }

    private fun Path.isCppSource(): Boolean {
        val fileName = name.lowercase()
        return fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx")
    }

    private fun runCommand(command: List<String>, directory: Path): CommandResult {
        val process = try {
            ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start()
        } catch (exception: IOException) {
            return CommandResult(
                exitCode = -1,
                output = "Failed to start native compiler ${command.firstOrNull().orEmpty()}: ${exception.message}",
                command = command
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return CommandResult(exitCode, output, command)
    }

    internal fun effectiveParallelCompileJobs(config: NativePipelineConfig, sourceCount: Int): Int {
        return effectiveParallelCompileJobs(
            config = config,
            sourceCount = sourceCount,
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            freePhysicalMemoryBytes = freePhysicalMemoryBytes()
        )
    }

    internal fun effectiveParallelCompileJobs(
        config: NativePipelineConfig,
        sourceCount: Int,
        availableProcessors: Int,
        freePhysicalMemoryBytes: Long
    ): Int {
        if (sourceCount <= 0) return 1
        val configured = config.parallelCompileJobs
        val jobs = if (configured > 0) {
            configured
        } else {
            val cpuBound = maxOf(1, availableProcessors - 1)
            val memoryBound = if (freePhysicalMemoryBytes > 0L) {
                maxOf(1, (freePhysicalMemoryBytes / EstimatedBytesPerCompileJob).toInt())
            } else {
                AutoCompileJobCap
            }
            minOf(cpuBound, memoryBound, AutoCompileJobCap)
        }
        return jobs.coerceIn(1, sourceCount)
    }

    private fun freePhysicalMemoryBytes(): Long {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        val method = bean.javaClass.methods.firstOrNull {
            it.name == "getFreeMemorySize" || it.name == "getFreePhysicalMemorySize"
        } ?: return -1L
        return runCatching {
            (method.invoke(bean) as? Number)?.toLong() ?: -1L
        }.getOrDefault(-1L)
    }

    private fun gnuOptimizationFlag(config: NativePipelineConfig): String {
        return "-${config.optimizationLevel.name}"
    }

    private fun msvcOptimizationFlag(config: NativePipelineConfig): String {
        return when (config.optimizationLevel) {
            NativeOptimizationLevel.O0 -> "/Od"
            NativeOptimizationLevel.O1,
            NativeOptimizationLevel.Os,
            NativeOptimizationLevel.Oz -> "/O1"
            NativeOptimizationLevel.O2,
            NativeOptimizationLevel.O3 -> "/O2"
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

    internal data class CommandResult(
        val exitCode: Int,
        val output: String,
        val command: List<String>
    )
}
