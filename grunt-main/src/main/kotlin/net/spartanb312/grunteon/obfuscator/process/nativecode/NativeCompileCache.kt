package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * Opt-in, local, trusted object cache; never a dependency-mtime cache.
 *
 * Enable with -Dgrunteon.native.cache.enabled=true AND
 * -Dgrunteon.native.cache.toolchainId=<immutable-toolchain-content-id>.
 * The ID must cover the ENTIRE toolchain (driver helpers, assembler, dynamic libraries, specs,
 * implicit configuration and wrapper state), not just its version. Change it whenever any of those
 * change. Inputs/toolchains must not be mutated concurrently with a build. The driver itself is
 * additionally hashed and queried on every build. No config/serialization contract is changed.
 *
 * Every lookup preprocesses again, so transitive/system headers, include shadowing, __has_include,
 * and volatile predefined macros are resolved afresh. On a miss we compile that exact snapshot,
 * NOT the original source again. Unknown flags, debug info, PCH/modules, plugins, response files,
 * native CPU tuning and assembly fall back to an ordinary uncached build. MSVC and single-TU
 * compile+link are not cached. A full automatic toolchain/include-search identity is not claimed.
 *
 * Entries live in workDir/object-cache-v1, have an embedded key/checksum, and are published as one
 * atomic file. Environment is hashed, not serialized into a manifest. Objects/diagnostics may still
 * contain sensitive source data. Linking is never cached. There is no eviction; callers may remove
 * the cache between builds. Do not share it with untrusted users. Build workspaces are not shared
 * concurrently; only complete cache entries may be shared. Unknown flags/MSVC use the unchanged
 * command/output locations to preserve side artifacts, with failed primary outputs removed.
 */
internal class NativeCompileCache private constructor(
    private val root: Path,
    private val identity: List<String>,
    private val environment: Map<String, String>,
    private val prefixSize: Int
) {
    fun compileObject(
        command: List<String>,
        source: Path,
        objectPath: Path,
        directory: Path,
        target: String
    ): NativeCompiler.CommandResult? {
        // These commands are built internally; do not attempt to interpret arbitrary compiler CLIs.
        val inputs = listOf("-c", source.toAbsolutePath().toString(), "-o", objectPath.toAbsolutePath().toString())
        if (command.takeLast(4) != inputs) {
            return null
        }
        val options = command.drop(prefixSize).dropLast(4)
        val codegenOptions = codegenOptions(options) ?: return null
        val snapshot = try {
            Files.createTempFile(directory, ".native-preprocessed-", ".ii")
        } catch (_: IOException) {
            return null
        }
        try {
            val prefix = command.take(prefixSize)
            // GCC otherwise silently ignores automatic .gch files under -E. Make their use visible
            // as a pch_preprocess pragma, which is rejected below. Unsupported drivers fall back.
            val preprocess = prefix + options + listOf(
                "-E", "-fpch-preprocess", source.toAbsolutePath().toString(), "-o", snapshot.toAbsolutePath().toString()
            )
            val preprocessed = NativeCompiler.runCommand(preprocess, directory, environment)
            if (preprocessed.exitCode != 0 || !Files.isRegularFile(snapshot) || Files.size(snapshot) == 0L) return null
            if (!isSelfContained(snapshot)) return null
            val key = fingerprint(buildList {
                addAll(identity)
                add(directory.toRealPath().toString())
                add(target)
                add(command.size.toString())
                addAll(command)
                add(hashFile(source))
                add(hashFile(snapshot))
                add(environment.size.toString())
                environment.toSortedMap().forEach { (key, value) -> add(key); add(value) }
            })
            val cachedOutput = restore(key, objectPath)
            if (cachedOutput != null) {
                val output = preprocessed.output + cachedOutput + "Native object cache hit: " + source.fileName + "\n"
                return NativeCompiler.CommandResult(0, output, command)
            }
            val compile = prefix + codegenOptions + listOf(
                "-x", "c++-cpp-output", "-c", snapshot.toAbsolutePath().toString(),
                "-o", objectPath.toAbsolutePath().toString()
            )
            val result = NativeCompiler.runOutputCommand(compile, objectPath, directory, environment)
            if (result.exitCode == 0) publish(key, objectPath, result.output)
            return result.copy(output = preprocessed.output + result.output)
        } catch (_: IOException) {
            // Cache availability is not a requirement for compilation. Never reuse an unchecked entry.
            return null
        } finally {
            Files.deleteIfExists(snapshot)
        }
    }

    private fun restore(key: String, objectPath: Path): String? {
        val entry = root.resolve("$key.entry")
        if (!Files.isRegularFile(entry, NOFOLLOW_LINKS)) return null
        val temporary = Files.createTempFile(objectPath.parent, ".native-cache-hit-", ".o")
        try {
            val output = DataInputStream(Files.newInputStream(entry).buffered()).use { input ->
                if (input.readUTF() != Format || input.readUTF() != key) return null
                val expectedHash = input.readUTF()
                val diagnostics = input.readUTF()
                val size = input.readLong()
                if (size <= 0L || size > Files.size(entry)) return null
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newOutputStream(temporary).buffered().use { output ->
                    var remaining = size
                    val buffer = ByteArray(64 * 1024)
                    while (remaining > 0L) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) return null
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                }
                if (input.read() != -1 || hex(digest.digest()) != expectedHash) return null
                diagnostics
            }
            Files.move(temporary, objectPath, ATOMIC_MOVE, REPLACE_EXISTING)
            return output
        } catch (_: IOException) {
            return null
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun publish(key: String, objectPath: Path, diagnostics: String) {
        var temporary: Path? = null
        try {
            Files.createDirectories(root)
            temporary = Files.createTempFile(root, ".publish-", ".tmp")
            DataOutputStream(Files.newOutputStream(temporary).buffered()).use { output ->
                output.writeUTF(Format)
                output.writeUTF(key)
                output.writeUTF(hashFile(objectPath))
                output.writeUTF(diagnostics)
                output.writeLong(Files.size(objectPath))
                Files.newInputStream(objectPath).use { it.copyTo(output) }
            }
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            Files.move(temporary, root.resolve("$key.entry"), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: IOException) {
            // Including filesystems without atomic rename and overlong diagnostics: do not cache.
        } finally {
            temporary?.let { Files.deleteIfExists(it) }
        }
    }

    companion object {
        private const val Format = "grunteon-native-object-v1"
        internal const val EnabledProperty = "grunteon.native.cache.enabled"
        internal const val ToolchainProperty = "grunteon.native.cache.toolchainId"

        fun create(
            compiler: NativeCompiler.NativeCompilerExecutable,
            workDir: Path,
            directory: Path,
            environment: Map<String, String> = System.getenv().toMap()
        ): NativeCompileCache? {
            if (System.getProperty(EnabledProperty) != "true") return null
            val toolchainId = System.getProperty(ToolchainProperty)?.takeIf { it.isNotBlank() } ?: return null
            if (compiler.kind == NativeCompiler.NativeCompilerKind.Msvc) return null
            if (!supportsEnvironment(environment)) return null
            return try {
                val executable = Path.of(compiler.command).toRealPath()
                val prefix = listOf(compiler.command) +
                    if (compiler.kind == NativeCompiler.NativeCompilerKind.Zig) listOf("c++") else emptyList()
                val version = NativeCompiler.runCommand(prefix + "--version", directory, environment)
                val target = NativeCompiler.runCommand(prefix + "-dumpmachine", directory, environment)
                if (version.exitCode != 0 || target.exitCode != 0) return null
                if (version.output.isBlank() || target.output.isBlank()) return null
                NativeCompileCache(
                    workDir.resolve("object-cache-v1"),
                    listOf(Format, toolchainId, compiler.kind.name, executable.toString(), hashFile(executable),
                        version.output, target.output, System.getProperty("os.name"), System.getProperty("os.arch")),
                    environment.toMap(), prefix.size
                )
            } catch (_: IOException) {
                null
            }
        }

        fun supportsOptions(options: List<String>): Boolean = codegenOptions(options) != null

        fun supportsEnvironment(environment: Map<String, String>): Boolean = environment.keys.none {
            it in setOf("DEPENDENCIES_OUTPUT", "SUNPRO_DEPENDENCIES", "CCC_OVERRIDE_OPTIONS", "GCC_COMPARE_DEBUG",
                "GCC_COMPARE_DEBUG_OUTPUT", "CC_PRINT_OPTIONS", "CC_PRINT_HEADERS", "CC_LOG_DIAGNOSTICS")
        }

        // A whitelist, not a blacklist: files affecting codegen/side outputs must never slip through.
        private fun codegenOptions(options: List<String>): List<String>? {
            val result = mutableListOf<String>()
            var index = 0
            while (index < options.size) {
                val option = options[index++]
                when {
                    option in setOf("-I", "-isystem", "-iquote", "-idirafter", "-D", "-U", "-include", "-imacros") -> {
                        val value = options.getOrNull(index++) ?: return null
                        if (value.endsWith(".pch") || value.endsWith(".gch")) return null
                    }
                    listOf("-I", "-D", "-U").any { option.startsWith(it) && option.length > it.length } -> Unit
                    option == "-target" || option == "--target" -> {
                        val value = options.getOrNull(index++) ?: return null
                        if (!value.matches(Regex("[A-Za-z0-9_.+-]+")) || "native" in value) return null
                        result += option
                        result += value
                    }
                    option in setOf(
                        "-std=c++17", "-O0", "-O1", "-O2", "-O3", "-Os", "-Oz", "-g0", "-fPIC", "-pthread",
                        "-fno-exceptions", "-fexceptions", "-fno-rtti", "-frtti", "-fno-strict-aliasing",
                        "-fstrict-aliasing", "-fwrapv", "-fno-wrapv", "-fvisibility=hidden", "-fvisibility=default",
                        "-fvisibility-inlines-hidden"
                    ) -> result += option
                    else -> return null
                }
            }
            return result
        }

        private val ExternalInputToken = Regex("""\b(?:asm|__asm|__asm__|_Pragma|import|module)\b""")
        // glibc declares simple symbol aliases with __asm__("" "name"); these cannot read files.
        private val SymbolAlias = Regex("""\b__asm(?:__)?\s*\(\s*(?:""\s*)?"[A-Za-z_][A-Za-z0-9_]*"\s*\)""")
        private val SafeDirective = Regex(
            """\s*#\s*(?:[0-9]+\s+.*|pragma GCC visibility (?:push\([^)]*\)|pop)|""" +
                """pragma (?:GCC|clang) diagnostic (?:push|pop|(?:ignored|warning|error) "-W[A-Za-z0-9=+-]+"))\s*"""
        )

        private fun isSelfContained(snapshot: Path): Boolean = Files.newBufferedReader(snapshot).use { reader ->
            reader.lineSequence().all { line ->
                // Assembly can read .incbin files. PCH/modules/pragmas can reference non-text inputs.
                if (line.trimStart().startsWith("#")) SafeDirective.matches(line)
                else !ExternalInputToken.containsMatchIn(SymbolAlias.replace(line, ""))
            }
        }

        private fun hashFile(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return hex(digest.digest())
        }

        private fun fingerprint(values: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
