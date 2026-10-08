package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.attribute.FileTime
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeCompileCacheTest {
    @Test
    fun identicalBuildHitsAndOneChangedTranslationUnitMissesWithoutReadingDiagnosticSource() = fixture { f ->
        assertTrue(f.compile().success)
        val times = f.bundle.sourceFiles.associate { source ->
            val time = FileTime.fromMillis(1_234_000L)
            Files.setLastModifiedTime(source.path, time)
            source.path to Files.getLastModifiedTime(source.path)
        }
        val warm = f.compile()
        assertTrue(warm.success, warm.output)
        assertEquals(2, f.count("COMPILE"))
        assertEquals(4, f.count("PREPROCESS"))
        assertEquals(2, f.count("LINK")) // Linking is deliberately not cached.
        times.forEach { (path, time) -> assertEquals(time, Files.getLastModifiedTime(path)) }
        val changed = f.bundle.copy(sourceFiles = f.bundle.sourceFiles.mapIndexed { index, source ->
            if (index == 0) source.copy(text = source.text + "\nint changed = 2;\n") else source
        })
        assertTrue(f.compile(changed).success)
        assertEquals(3, f.count("COMPILE"))
        assertEquals(times.getValue(f.secondSource), Files.getLastModifiedTime(f.secondSource))
    }

    @Test
    fun transitiveHeaderContentNotMtimeInvalidatesBothObjects() = fixture { f ->
        assertTrue(f.compile().success)
        val header = f.include.resolve("transitive.h")
        val time = Files.getLastModifiedTime(header)
        header.writeText("int headerValue = 99;\n")
        Files.setLastModifiedTime(header, time)
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE"))
        Files.delete(header)
        val failed = f.compile()
        assertFalse(failed.success)
        assertFalse(Files.exists(f.bundle.libraryPath))
        assertTrue(failed.libraries.isEmpty())
    }

    @Test
    fun newlyShadowingHeaderAndOptionalIncludeInvalidatePreprocessedFingerprint() = fixture { f ->
        assertTrue(f.compile().success)
        f.bundle.sourcePath.parent.resolve("jni.h").writeText("int shadowed = 7;\n")
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE"))
        f.bundle.sourcePath.parent.resolve("optional.h").writeText("int optional = 8;\n")
        assertTrue(f.compile().success)
        assertEquals(6, f.count("COMPILE"))
    }

    @Test
    fun compilerVersionBinaryToolchainIdentityAndEffectiveFlagsAllInvalidate() = fixture { f ->
        assertTrue(f.compile().success)
        f.root.resolve("version").writeText("fake-2")
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE"))
        Files.writeString(f.executable, "\n# driver changed\n", APPEND)
        assertTrue(f.compile().success)
        assertEquals(6, f.count("COMPILE"))
        System.setProperty(NativeCompileCache.ToolchainProperty, "immutable-toolchain-2")
        assertTrue(f.compile().success)
        assertEquals(8, f.count("COMPILE"))
        assertTrue(f.compile(config = f.config.copy(compilerArgs = listOf("-DVALUE=2"))).success)
        assertEquals(10, f.count("COMPILE"))
        assertTrue(f.compile(config = f.config.copy(optimizationLevel = NativeOptimizationLevel.O2)).success)
        assertEquals(12, f.count("COMPILE"))
        f.root.resolve("target").writeText("fake-aarch64")
        assertTrue(f.compile().success)
        assertEquals(14, f.count("COMPILE"))
    }

    @Test
    fun environmentAndExplicitTargetArePartOfObjectIdentity() = fixture { f ->
        assertTrue(f.compile().success)
        val output = f.root.resolve("direct.o")
        val command = NativeCompiler.buildGnuLikeObjectCommand(
            f.firstSource, output, f.executable.toString(), f.include, f.include.resolve("linux"), f.config, f.platform
        )
        fun compile(env: String, target: String) {
            val cache = assertNotNull(NativeCompileCache.create(
                NativeCompiler.NativeCompilerExecutable(f.executable.toString(), NativeCompiler.NativeCompilerKind.GnuLike),
                Path.of(f.config.workDir), f.firstSource.parent, System.getenv() + ("FAKE_INPUT" to env)
            ))
            assertEquals(0, assertNotNull(cache.compileObject(command, f.firstSource, output, f.firstSource.parent, target)).exitCode)
        }
        compile("one", "target-one")
        compile("one", "target-one")
        assertEquals(3, f.count("COMPILE"))
        compile("two", "target-one")
        compile("two", "target-two")
        assertEquals(5, f.count("COMPILE"))
    }

    @Test
    fun compilesTheHashedSnapshotNotASecondReadOfHeaders() = fixture { f ->
        assertTrue(f.compile().success)
        val output = f.root.resolve("snapshot.o")
        val cache = assertNotNull(NativeCompileCache.create(
            NativeCompiler.NativeCompilerExecutable(f.executable.toString(), NativeCompiler.NativeCompilerKind.GnuLike),
            Path.of(f.config.workDir), f.firstSource.parent
        ))
        val command = NativeCompiler.buildGnuLikeObjectCommand(
            f.firstSource, output, f.executable.toString(), f.include, f.include.resolve("linux"), f.config, f.platform
        )
        f.root.resolve("mutate-header-after-preprocess").writeText("yes")
        assertEquals(0, assertNotNull(cache.compileObject(
            command, f.firstSource, output, f.firstSource.parent, "snapshot-target"
        )).exitCode)
        assertTrue("headerValue = 1" in output.readText())
        assertTrue("headerValue = 200" in f.include.resolve("transitive.h").readText())
        assertEquals(0, assertNotNull(cache.compileObject(
            command, f.firstSource, output, f.firstSource.parent, "snapshot-target"
        )).exitCode)
        assertTrue("headerValue = 200" in output.readText())
        assertEquals(4, f.count("COMPILE"))
    }

    @Test
    fun sideOutputAndDriverOverrideEnvironmentDisableCache() = fixture { f ->
        listOf("DEPENDENCIES_OUTPUT", "CCC_OVERRIDE_OPTIONS", "CC_LOG_DIAGNOSTICS").forEach { name ->
            assertNull(NativeCompileCache.create(
                NativeCompiler.NativeCompilerExecutable(f.executable.toString(), NativeCompiler.NativeCompilerKind.GnuLike),
                Path.of(f.config.workDir), f.firstSource.parent, System.getenv() + (name to "value")
            ))
        }
        assertEquals(0, f.count("VERSION"))
    }

    @Test
    fun cacheRequiresBothOptInAndToolchainIdentityButAlwaysPreservesSourceMtime() = fixture { f ->
        System.clearProperty(NativeCompileCache.EnabledProperty)
        assertTrue(f.compile().success)
        val time = FileTime.fromMillis(1_234_000L)
        Files.setLastModifiedTime(f.firstSource, time)
        assertTrue(f.compile().success)
        assertEquals(time, Files.getLastModifiedTime(f.firstSource))
        System.setProperty(NativeCompileCache.EnabledProperty, "true")
        System.clearProperty(NativeCompileCache.ToolchainProperty)
        assertTrue(f.compile().success)
        assertEquals(6, f.count("COMPILE"))
        assertEquals(0, f.count("PREPROCESS"))
        assertEquals(0, f.count("VERSION"))
    }

    @Test
    fun unsupportedFlagsAndMsvcConservativelyBypassCache() = fixture { f ->
        listOf("-fplugin=plugin.so", "@options.rsp", "-march=native", "-g", "-MMD", "-fmodules", "-flto").forEach { flag ->
            val config = f.config.copy(compilerArgs = listOf(flag))
            repeat(2) { assertTrue(f.compile(config = config).success) }
            if (flag == "-MMD") assertTrue(Files.exists(f.firstSource.parent.resolve("obj/grunteon_native_a_0000.d")))
        }
        assertEquals(28, f.count("COMPILE"))
        assertEquals(0, f.count("PREPROCESS"))
        val before = f.count("VERSION")
        repeat(2) { assertTrue(f.compile(config = f.config.copy(compilerMode = NativeCompilerMode.Msvc)).success) }
        assertEquals(before, f.count("VERSION"))
    }

    @Test
    fun failedObjectIsNotPublishedAndPreviousLibraryCannotSurvive() = fixture { f ->
        assertTrue(f.compile().success)
        val entriesBefore = f.entries().size
        val failing = f.bundle.copy(sourceFiles = f.bundle.sourceFiles.mapIndexed { index, source ->
            if (index == 0) source.copy(text = source.text + "\n// FAIL_COMPILE\n") else source
        })
        repeat(2) {
            val result = f.compile(failing)
            assertFalse(result.success)
            assertTrue(result.libraries.isEmpty())
            assertFalse(Files.exists(f.bundle.libraryPath))
            assertFalse(Files.exists(f.firstSource.parent.resolve("obj/grunteon_native_a_0000.o")))
            assertEquals(entriesBefore, f.entries().size)
        }
        assertEquals(1, f.count("LINK"))
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE")) // Failed attempts never became hits.
        f.assertNoTemporaryOutputs()
    }

    @Test
    fun failedLinkOrMissingOutputDoesNotExposeOldOrPartialLibrary() = fixture { f ->
        assertTrue(f.compile().success)
        f.root.resolve("fail-link").writeText("yes")
        assertFalse(f.compile().success)
        assertFalse(Files.exists(f.bundle.libraryPath))
        Files.delete(f.root.resolve("fail-link"))
        assertTrue(f.compile().success)
        f.root.resolve("missing-output").writeText("yes")
        assertFalse(f.compile().success)
        assertFalse(Files.exists(f.bundle.libraryPath))
        f.assertNoTemporaryOutputs()
    }

    @Test
    fun missingObjectOutputAndMissingCompilerCannotReuseOldOutputs() = fixture { f ->
        assertTrue(f.compile().success)
        f.root.resolve("missing-output").writeText("yes")
        val config = f.config.copy(compilerArgs = listOf("-DNEW=1"))
        assertFalse(f.compile(config = config).success)
        assertFalse(Files.exists(f.bundle.libraryPath))
        assertEquals(2, f.entries().size)
        f.bundle.libraryPath.writeText("stale library")
        assertFalse(f.compile(config = f.config.copy(compilerExecutable = f.root.resolve("absent").toString())).success)
        assertFalse(Files.exists(f.bundle.libraryPath))
    }

    @Test
    fun corruptEntriesAreMissesAndReplacedAtomically() = fixture { f ->
        assertTrue(f.compile().success)
        f.entries().forEach { path ->
            val bytes = Files.readAllBytes(path)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            Files.write(path, bytes)
        }
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE"))
        assertTrue(f.compile().success)
        assertEquals(4, f.count("COMPILE"))
        f.assertNoTemporaryOutputs()
    }

    @Test
    fun zigTargetFlagsSeparateObjectsAndLaterFailureRemovesEveryLibrary() = fixture { f ->
        val platforms = NativePlatform.zigBuiltInTargets.filter { it.os == "linux" }
        val bundle = f.bundle.copy(libraryTargets = platforms.map { platform ->
            NativeLibraryTarget(platform, platform.resourceDirectory, "libtest.so", f.root.resolve(platform.resourceDirectory + ".so"))
        })
        val config = f.config.copy(compilerMode = NativeCompilerMode.Zig)
        assertTrue(f.compile(bundle, config).success)
        assertTrue(f.compile(bundle, config).success)
        assertEquals(4, f.count("COMPILE"))
        val changed = config.copy(targetCompilerArgs = mapOf(platforms.first().resourceDirectory to listOf("-DTARGET=2")))
        assertTrue(f.compile(bundle, changed).success)
        assertEquals(6, f.count("COMPILE"))
        f.root.resolve("fail-link-target").writeText(platforms.last().zigTarget!!)
        val result = f.compile(bundle, changed)
        assertFalse(result.success)
        assertTrue(result.libraries.isEmpty())
        bundle.resolvedLibraryTargets.forEach { assertFalse(Files.exists(it.libraryPath)) }
    }

    @Test
    fun preprocessingFailureAndExternalInputDirectivesFallBackToUncachedCompile() = fixture { f ->
        f.root.resolve("fail-preprocess").writeText("yes")
        repeat(2) { assertTrue(f.compile().success) }
        assertEquals(4, f.count("COMPILE"))
        assertTrue(f.entries().isEmpty())
        Files.delete(f.root.resolve("fail-preprocess"))
        val assembly = f.bundle.copy(sourceFiles = f.bundle.sourceFiles.map {
            it.copy(text = it.text + "\nasm(\".incbin external.bin\");\n")
        })
        repeat(2) { assertTrue(f.compile(assembly).success) }
        assertEquals(8, f.count("COMPILE"))
        val pch = f.bundle.copy(sourceFiles = f.bundle.sourceFiles.map {
            it.copy(text = it.text + "\n#pragma GCC pch_preprocess \"precompiled.gch\"\n")
        })
        repeat(2) { assertTrue(f.compile(pch).success) }
        assertEquals(12, f.count("COMPILE"))
        assertTrue(f.entries().isEmpty())
    }

    private fun fixture(block: (Fixture) -> Unit) = synchronized(NativeCompileCacheTest::class.java) {
        if (File.separatorChar == '\\' || !Files.isExecutable(Path.of("/bin/sh"))) {
            println("Skipping NativeCompileCacheTest: POSIX shell required for fake compiler launcher")
            return@synchronized
        }
        val enabled = System.getProperty(NativeCompileCache.EnabledProperty)
        val toolchain = System.getProperty(NativeCompileCache.ToolchainProperty)
        val root = Files.createTempDirectory("native-cache-test-")
        try {
            System.setProperty(NativeCompileCache.EnabledProperty, "true")
            System.setProperty(NativeCompileCache.ToolchainProperty, "immutable-toolchain-1")
            block(Fixture(root))
        } finally {
            if (enabled == null) System.clearProperty(NativeCompileCache.EnabledProperty)
            else System.setProperty(NativeCompileCache.EnabledProperty, enabled)
            if (toolchain == null) System.clearProperty(NativeCompileCache.ToolchainProperty)
            else System.setProperty(NativeCompileCache.ToolchainProperty, toolchain)
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private class Fixture(val root: Path) {
        val platform = NativePlatform("linux", "x86_64", "lib", ".so", "linux")
        val include = Files.createDirectories(root.resolve("include"))
        val executable = root.resolve("fake-c++")
        val firstSource = root.resolve("src/grunteon_native_a.cpp")
        val secondSource = root.resolve("src/grunteon_native_b.cpp")
        val config = NativePipelineConfig(
            compilerExecutable = executable.toString(), compilerMode = NativeCompilerMode.GnuLike,
            workDir = root.resolve("work").toString(), jniIncludeRoot = include.toString(), parallelCompileJobs = 1
        )
        val bundle = NativeSourceBundle(
            NativeBuildPlan("test/Loader", "test/lib.so", "lib.so", platform, emptyList()),
            "", firstSource, root.resolve("lib/lib.so"),
            listOf(firstSource, secondSource).map { NativeSourceFile(it, "#include <jni.h>\n// OPTIONAL_INCLUDE\nint value = 1;\n") },
            sourceTextFactory = { error("Split compiler must not read the lazy diagnostic source") }
        )

        init {
            Files.createDirectories(include.resolve("linux")).resolve("jni_md.h").writeText("// platform\n")
            include.resolve("jni.h").writeText("#include <transitive.h>\n")
            include.resolve("transitive.h").writeText("int headerValue = 1;\n")
            root.resolve("version").writeText("fake-1")
            root.resolve("target").writeText("fake-x86_64")
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
            val classpath = listOf(NativeCacheFakeCompiler::class.java, Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
                .joinToString(File.pathSeparator)
            fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
            executable.writeText("#!/bin/sh\nexec " + quote(java) + " -cp " + quote(classpath) + " " +
                NativeCacheFakeCompiler::class.java.name + " " + quote(root.toString()) + " \"\$@\"\n")
            assertTrue(executable.toFile().setExecutable(true))
        }

        fun compile(bundle: NativeSourceBundle = this.bundle, config: NativePipelineConfig = this.config) =
            NativeCompiler.compile(bundle, config)

        fun count(kind: String): Int = root.resolve("calls").let { path ->
            if (Files.exists(path)) path.readText().lineSequence().count { it.substringBefore('\t') == kind } else 0
        }

        fun entries(): List<Path> {
            val cache = Path.of(config.workDir).resolve("object-cache-v1")
            if (!Files.exists(cache)) return emptyList()
            return Files.list(cache).use { it.filter { path -> path.toString().endsWith(".entry") }.toList() }
        }

        fun assertNoTemporaryOutputs() {
            Files.walk(root).use { paths ->
                assertFalse(paths.anyMatch { it.fileName.toString().let { name ->
                    name.startsWith(".native-") || name.startsWith(".publish-")
                } })
            }
        }
    }
}

/** Separate JVM fake: no native compiler or Python installation is required by the cache tests. */
internal object NativeCacheFakeCompiler {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val root = Path.of(arguments.first())
        val args = arguments.drop(1)
        fun log(kind: String) { Files.writeString(root.resolve("calls"), kind + "\t" + args.joinToString("\t") + "\n", CREATE, APPEND) }
        if ("--version" in args) { log("VERSION"); println(root.resolve("version").readText()); return }
        if ("-dumpmachine" in args) { println(root.resolve("target").readText()); return }
        val output = args.indexOf("-o").takeIf { it >= 0 }?.let { Path.of(args[it + 1]) }
            ?: args.firstOrNull { it.startsWith("/Fe:") }?.let { Path.of(it.removePrefix("/Fe:")) }
            ?: error("Missing output argument")
        val includes = args.indices.filter { args[it] == "-I" }.map { Path.of(args[it + 1]) }
        fun expand(path: Path): String = path.readText().lineSequence().joinToString("\n") { line ->
            val include = Regex("#include [<\"]([^>\"]+)[>\"]").matchEntire(line)?.groupValues?.get(1)
            when {
                include != null -> expand((listOf(path.parent) + includes).map { it.resolve(include) }
                    .firstOrNull(Files::isRegularFile) ?: error("Missing include: $include"))
                line == "// OPTIONAL_INCLUDE" -> path.parent.resolve("optional.h").let {
                    if (Files.exists(it)) expand(it) else "// absent optional include"
                }
                else -> line
            }
        }
        val source = args.firstOrNull { it.endsWith(".cpp") || it.endsWith(".ii") }?.let(Path::of)
        if ("-E" in args) {
            log("PREPROCESS")
            if (Files.exists(root.resolve("fail-preprocess"))) exitProcess(2)
            output.writeText(expand(source!!) + "\n// env=" + System.getenv("FAKE_INPUT"))
            if (Files.deleteIfExists(root.resolve("mutate-header-after-preprocess"))) {
                root.resolve("include/transitive.h").writeText("int headerValue = 200;\n")
            }
            return
        }
        val compiling = "-c" in args
        log(if (compiling) "COMPILE" else "LINK")
        if (compiling && "-MMD" in args) output.resolveSibling(output.fileName.toString().removeSuffix(".o") + ".d")
            .writeText("dependency side output")
        if (Files.exists(root.resolve("missing-output"))) return
        val text = if (source != null) {
            if (source.toString().endsWith(".ii")) source.readText() else expand(source)
        } else {
            args.filter { it.startsWith("@") }.map { Path.of(it.removePrefix("@")) }.filter(Files::exists)
                .flatMap { it.readText().lineSequence().filter(String::isNotBlank).toList() }
                .joinToString("\n") { Path.of(it.trim('"')).readText() }
        }
        output.writeText("object-or-library\n$text") // Deliberately writes partial output even on failure.
        if (compiling && "FAIL_COMPILE" in text) exitProcess(3)
        if (!compiling && Files.exists(root.resolve("fail-link"))) exitProcess(4)
        if (!compiling && Files.exists(root.resolve("fail-link-target")) &&
            root.resolve("fail-link-target").readText() in args) exitProcess(5)
    }
}
