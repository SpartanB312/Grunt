package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal object NativeCompiler {

    fun compile(bundle: NativeSourceBundle): NativeCompileResult {
        bundle.sourcePath.parent.createDirectories()
        bundle.libraryPath.parent.createDirectories()
        bundle.sourcePath.writeText(bundle.sourceText)

        val compiler = findCompiler() ?: return NativeCompileResult(
            success = false,
            output = "No C++ compiler found on PATH. Tried clang++ and g++."
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

        val command = mutableListOf(
            compiler.absolutePath,
            "-std=c++17",
            "-O2",
            "-fPIC",
            "-shared",
            "-I", includeRoot.absolutePathString(),
            "-I", includeOs.absolutePathString(),
            "-o", bundle.libraryPath.absolutePathString(),
            bundle.sourcePath.absolutePathString()
        )
        if (bundle.plan.platform.os == "macos") {
            command[command.indexOf("-shared")] = "-dynamiclib"
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return if (exitCode == 0 && Files.exists(bundle.libraryPath)) {
            NativeCompileResult(true, bundle.libraryPath, output)
        } else {
            NativeCompileResult(false, null, "Native compiler exited with code $exitCode\n$output")
        }
    }

    private fun resolveJniIncludeRoot(javaHome: Path): Path {
        val direct = javaHome.resolve("include")
        if (direct.exists()) return direct
        return javaHome.parent?.resolve("include") ?: direct
    }

    private fun findCompiler(): File? {
        return sequenceOf("clang++", "g++")
            .mapNotNull { findExecutable(it) }
            .firstOrNull()
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
}
