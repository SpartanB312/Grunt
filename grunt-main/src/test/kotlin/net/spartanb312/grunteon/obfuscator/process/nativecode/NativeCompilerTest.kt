package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeCompilerTest {

    @Test
    fun buildsGnuLikeSharedLibraryCommand() {
        val bundle = sourceBundle(NativePlatform("linux", "x86_64", "lib", ".so", "linux"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("linux")

        val command = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("/usr/bin/clang++", NativeCompiler.NativeCompilerKind.GnuLike),
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(compilerArgs = listOf("-DGRUNTEON_TEST=1"))
        )

        assertEquals("/usr/bin/clang++", command.first())
        assertTrue("-std=c++17" in command)
        assertTrue("-O1" in command)
        assertTrue("-shared" in command)
        assertTrue("-fPIC" in command)
        assertTrue("-DGRUNTEON_TEST=1" in command)
        assertTrue("-o" in command)
        assertEquals(bundle.sourcePath.toAbsolutePath().toString(), command.last())
    }

    @Test
    fun buildsMacDynamicLibraryCommandForGnuLikeCompiler() {
        val bundle = sourceBundle(NativePlatform("macos", "aarch64", "lib", ".dylib", "darwin"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("darwin")

        val command = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("/usr/bin/clang++", NativeCompiler.NativeCompilerKind.GnuLike),
            includeRoot = includeRoot,
            includeOs = includeOs
        )

        assertTrue("-dynamiclib" in command)
        assertFalse("-shared" in command)
    }

    @Test
    fun staticallyLinksWindowsRuntimeForGnuLikeCompiler() {
        val bundle = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("win32")

        val command = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("g++.exe", NativeCompiler.NativeCompilerKind.GnuLike),
            includeRoot = includeRoot,
            includeOs = includeOs
        )

        assertTrue("-shared" in command)
        assertTrue("-static" in command)
        assertTrue("-static-libgcc" in command)
        assertTrue("-static-libstdc++" in command)
    }

    @Test
    fun buildsGnuLikeObjectAndLinkCommandsForSplitSources() {
        val bundle = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("win32")
        val source = bundle.sourcePath.parent.resolve("grunteon_native_chunk_0000.cpp")
        val objectPath = bundle.sourcePath.parent.resolve("obj").resolve("grunteon_native_chunk_0000.o")
        val config = NativePipelineConfig(
            compilerArgs = listOf("-DGRUNTEON_TEST=1"),
            optimizationLevel = NativeOptimizationLevel.O0
        )

        val objectCommand = NativeCompiler.buildGnuLikeObjectCommand(
            sourcePath = source,
            objectPath = objectPath,
            compiler = "g++.exe",
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = config
        )
        val linkCommand = NativeCompiler.buildGnuLikeLinkCommand(
            bundle = bundle,
            compiler = "g++.exe",
            objectPaths = listOf(objectPath),
            config = config
        )

        assertTrue("-c" in objectCommand)
        assertTrue("-O0" in objectCommand)
        assertTrue("-DGRUNTEON_TEST=1" in objectCommand)
        assertTrue(objectPath.toAbsolutePath().toString() in objectCommand)
        assertTrue("-shared" in linkCommand)
        assertTrue("-static" in linkCommand)
        assertTrue(bundle.libraryPath.toAbsolutePath().toString() in linkCommand)
        assertEquals(objectPath.toAbsolutePath().toString(), linkCommand.last())
    }

    @Test
    fun stripDebugInfoAddsGnuLikeCompileAndLinkArgsWithoutLeakingLinkArgsIntoObjectCompile() {
        val bundle = sourceBundle(NativePlatform("linux", "x86_64", "lib", ".so", "linux"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("linux")
        val source = bundle.sourcePath.parent.resolve("grunteon_native_chunk_0000.cpp")
        val objectPath = bundle.sourcePath.parent.resolve("obj").resolve("grunteon_native_chunk_0000.o")
        val config = NativePipelineConfig(
            stripDebugInfo = true,
            compilerArgs = listOf(
                "-g",
                "-gline-tables-only",
                "-fdebug-prefix-map=a=b",
                "-fdata-sections",
                "-DGRUNTEON_TEST=1"
            )
        )

        val singleCommand = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("/usr/bin/clang++", NativeCompiler.NativeCompilerKind.GnuLike),
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = config
        )
        val objectCommand = NativeCompiler.buildGnuLikeObjectCommand(
            sourcePath = source,
            objectPath = objectPath,
            compiler = "/usr/bin/clang++",
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = config,
            platform = bundle.plan.platform
        )
        val linkCommand = NativeCompiler.buildGnuLikeLinkCommand(
            bundle = bundle,
            compiler = "/usr/bin/clang++",
            objectPaths = listOf(objectPath),
            config = config
        )

        assertTrue("-g0" in singleCommand)
        assertTrue("-Wl,--strip-all" in singleCommand)
        assertTrue("-DGRUNTEON_TEST=1" in singleCommand)
        assertTrue("-fdata-sections" in singleCommand)
        assertFalse("-g" in singleCommand)
        assertFalse("-gline-tables-only" in singleCommand)
        assertFalse("-fdebug-prefix-map=a=b" in singleCommand)

        assertTrue("-g0" in objectCommand)
        assertFalse("-Wl,--strip-all" in objectCommand)
        assertTrue("-Wl,--strip-all" in linkCommand)
    }

    @Test
    fun stripDebugInfoUsesMacosGnuLikeLinkerStripArgs() {
        val bundle = sourceBundle(NativePlatform("macos", "aarch64", "lib", ".dylib", "darwin"))
        val objectPath = bundle.sourcePath.parent.resolve("obj").resolve("chunk.o")

        val command = NativeCompiler.buildGnuLikeLinkCommand(
            bundle = bundle,
            compiler = "/usr/bin/clang++",
            objectPaths = listOf(objectPath),
            config = NativePipelineConfig(stripDebugInfo = true)
        )

        assertTrue("-Wl,-S" in command)
        assertTrue("-Wl,-x" in command)
        assertFalse("-Wl,--strip-all" in command)
    }

    @Test
    fun buildsGnuLikeLinkCommandWithObjectResponseFile() {
        val bundle = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val objectPaths = listOf(
            bundle.sourcePath.parent.resolve("obj").resolve("chunk one.o"),
            bundle.sourcePath.parent.resolve("obj").resolve("chunk_two.o")
        )

        val command = NativeCompiler.buildGnuLikeLinkCommandWithObjectResponseFile(
            bundle = bundle,
            compiler = "g++.exe",
            objectPaths = objectPaths,
            config = NativePipelineConfig()
        )

        assertTrue(command.last().startsWith("@"))
        assertFalse(command.any { it.endsWith("chunk one.o") || it.endsWith("chunk_two.o") })
        val responseText = Path.of(command.last().removePrefix("@")).readText()
        assertTrue("chunk one.o" in responseText)
        assertTrue("chunk_two.o" in responseText)
        assertTrue("\"" in responseText)
    }

    @Test
    fun stripDebugInfoFiltersMsvcDebugArgsAndAddsLinkReleaseArgs() {
        val bundle = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("win32")

        val command = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("cl.exe", NativeCompiler.NativeCompilerKind.Msvc),
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(
                stripDebugInfo = true,
                compilerArgs = listOf("/MT", "/Zi", "/Fdgrunteon.pdb", "-Fdalt.pdb", "/DEBUG:FULL")
            )
        )

        assertTrue("/MT" in command)
        assertFalse("/Zi" in command)
        assertFalse("/Fdgrunteon.pdb" in command)
        assertFalse("-Fdalt.pdb" in command)
        assertFalse("/DEBUG:FULL" in command)
        assertTrue("/link" in command)
        assertTrue("/INCREMENTAL:NO" in command)
        assertTrue("/OPT:REF" in command)
        assertTrue("/OPT:ICF" in command)
    }

    @Test
    fun buildsMsvcCommandWithSourceResponseFile() {
        val base = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val bundle = base.copy(
            sourceFiles = listOf(
                NativeSourceFile(base.sourcePath.parent.resolve("chunk one.cpp"), ""),
                NativeSourceFile(base.sourcePath.parent.resolve("chunk_two.cpp"), "")
            )
        )
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("win32")

        val command = NativeCompiler.buildMsvcCommandWithSourceResponseFile(
            bundle = bundle,
            compiler = "cl.exe",
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(compilerArgs = listOf("/MT"))
        )

        assertEquals("cl.exe", command.first())
        assertTrue("/LD" in command)
        assertTrue("/MT" in command)
        assertTrue(command.last().startsWith("@"))
        assertFalse(command.any { it.endsWith("chunk one.cpp") || it.endsWith("chunk_two.cpp") })
        val responseText = Path.of(command.last().removePrefix("@")).readText()
        assertTrue("chunk one.cpp" in responseText)
        assertTrue("chunk_two.cpp" in responseText)
        assertTrue("\"" in responseText)
    }

    @Test
    fun buildsZigCommandWithTargetAndSourceResponseFile() {
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" }
        val base = sourceBundle(platform)
        val bundle = base.copy(
            sourceFiles = listOf(
                NativeSourceFile(base.sourcePath.parent.resolve("chunk one.cpp"), ""),
                NativeSourceFile(base.sourcePath.parent.resolve("chunk_two.cpp"), "")
            )
        )
        val target = bundle.resolvedLibraryTargets.single()
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("linux")

        val command = NativeCompiler.buildZigCommand(
            bundle = bundle,
            target = target,
            compiler = "zig",
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(
                compilerMode = NativeCompilerMode.Zig,
                compilerArgs = listOf("-DALL=1"),
                targetCompilerArgs = mapOf("linux-x86_64" to listOf("-DTARGET=1"))
            )
        )

        assertEquals("zig", command[0])
        assertEquals("c++", command[1])
        assertTrue("-target" in command)
        assertTrue("x86_64-linux-gnu" in command)
        assertTrue("-shared" in command)
        assertTrue("-fPIC" in command)
        assertTrue("-DALL=1" in command)
        assertTrue("-DTARGET=1" in command)
        assertFalse("-static-libgcc" in command)
        assertTrue(command.last().startsWith("@"))
        val responseText = Path.of(command.last().removePrefix("@")).readText()
        assertTrue("chunk one.cpp" in responseText)
        assertTrue("chunk_two.cpp" in responseText)
    }

    @Test
    fun stripDebugInfoFiltersZigTargetDebugArgsAndUsesTargetPlatformLinkArgs() {
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "macos-aarch64" }
        val bundle = sourceBundle(platform)
        val target = bundle.resolvedLibraryTargets.single()
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("darwin")

        val command = NativeCompiler.buildZigCommand(
            bundle = bundle,
            target = target,
            compiler = "zig",
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(
                compilerMode = NativeCompilerMode.Zig,
                stripDebugInfo = true,
                compilerArgs = listOf("-DALL=1", "-ggdb3"),
                targetCompilerArgs = mapOf("macos-aarch64" to listOf("-DTARGET=1", "-gdwarf-5"))
            )
        )

        assertTrue("-DALL=1" in command)
        assertTrue("-DTARGET=1" in command)
        assertTrue("-g0" in command)
        assertTrue("-Wl,-S" in command)
        assertTrue("-Wl,-x" in command)
        assertFalse("-ggdb3" in command)
        assertFalse("-gdwarf-5" in command)
        assertFalse("-Wl,--strip-all" in command)
    }

    @Test
    fun buildsZigLinkCommandWithTargetSpecificObjectResponseFile() {
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "windows-x86_64" }
        val bundle = sourceBundle(platform)
        val target = bundle.resolvedLibraryTargets.single()
        val objectPaths = listOf(
            bundle.sourcePath.parent.resolve("obj").resolve("windows-x86_64").resolve("chunk one.o"),
            bundle.sourcePath.parent.resolve("obj").resolve("windows-x86_64").resolve("chunk_two.o")
        )

        val command = NativeCompiler.buildZigLinkCommandWithObjectResponseFile(
            bundle = bundle,
            target = target,
            compiler = "zig.exe",
            objectPaths = objectPaths,
            config = NativePipelineConfig(compilerMode = NativeCompilerMode.Zig)
        )

        assertEquals("zig.exe", command[0])
        assertEquals("c++", command[1])
        assertTrue("x86_64-windows-gnu" in command)
        assertTrue("-shared" in command)
        assertFalse("-static-libgcc" in command)
        assertFalse("-static-libstdc++" in command)
        assertTrue(command.last().startsWith("@"))
        val responseText = Path.of(command.last().removePrefix("@")).readText()
        assertTrue("windows-x86_64" in responseText)
        assertTrue("chunk one.o" in responseText)
    }

    @Test
    fun buildsMandatoryZigTargetLogLines() {
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" }
        val bundle = sourceBundle(platform)
        val target = bundle.resolvedLibraryTargets.single()

        val lines = NativeCompiler.zigTargetLogLines(
            target = target,
            compiler = "C:/Tools/zig/zig.exe",
            includes = NativeJniIncludeResolution(
                includeRoot = Path.of("C:/Program Files/Java/jdk-25/include"),
                includeRootSource = "java.home",
                platformInclude = Path.of("C:/CrossJdk/linux/include/linux"),
                platformIncludeSource = "configured"
            )
        )

        assertTrue(lines.any { it.contains("linux-x86_64") })
        assertTrue(lines.any { it.contains("x86_64-linux-gnu") })
        assertTrue(lines.any { it.contains("grunteon/native/linux-x86_64/libgrunteon_native.so") })
        assertTrue(lines.any { it.contains("java.home") })
        assertTrue(lines.any { it.contains("configured") })
        assertTrue(lines.any { it.contains("C:/Tools/zig/zig.exe") })
    }

    @Test
    fun compilesBuiltInZigTargetsWhenExecutableIsProvided() {
        val zigExecutable = (System.getProperty("grunteon.zig.executable")
            ?: System.getenv("GRUNTEON_ZIG_EXECUTABLE"))
            ?.takeIf { it.isNotBlank() }
            ?: return
        val bundle = sourceBundleForTargets(NativePlatform.zigBuiltInTargets)

        val result = NativeCompiler.compile(
            bundle = bundle,
            config = NativePipelineConfig(
                compilerMode = NativeCompilerMode.Zig,
                compilerExecutable = zigExecutable,
                workDir = "build/native-compiler-zig-smoke/work",
                optimizationLevel = NativeOptimizationLevel.O0
            )
        )

        assertTrue(result.success, result.output)
        assertEquals(NativePlatform.zigBuiltInTargets.size, result.libraries.size, result.output)
        NativePlatform.zigBuiltInTargets.forEach { platform ->
            val library = assertNotNull(
                result.libraries.singleOrNull { it.platform.resourceDirectory == platform.resourceDirectory },
                "Missing compiled Zig target ${platform.resourceDirectory}\n${result.output}"
            )
            assertTrue(
                library.libraryPath.toFile().isFile,
                "Missing library file for ${platform.resourceDirectory}: ${library.libraryPath}\n${result.output}"
            )
        }
    }

    @Test
    fun choosesConservativeAutomaticParallelCompileJobs() {
        val jobs = NativeCompiler.effectiveParallelCompileJobs(
            config = NativePipelineConfig(parallelCompileJobs = 0),
            sourceCount = 20,
            availableProcessors = 16,
            freePhysicalMemoryBytes = 64L * 1024L * 1024L * 1024L
        )

        assertEquals(2, jobs)
    }

    @Test
    fun automaticParallelCompileJobsRespectMemoryPressure() {
        val jobs = NativeCompiler.effectiveParallelCompileJobs(
            config = NativePipelineConfig(parallelCompileJobs = 0),
            sourceCount = 20,
            availableProcessors = 16,
            freePhysicalMemoryBytes = 1024L * 1024L * 1024L
        )

        assertEquals(1, jobs)
    }

    @Test
    fun explicitParallelCompileJobsOverrideAutomaticCap() {
        val jobs = NativeCompiler.effectiveParallelCompileJobs(
            config = NativePipelineConfig(parallelCompileJobs = 8),
            sourceCount = 3,
            availableProcessors = 16,
            freePhysicalMemoryBytes = 1024L * 1024L * 1024L
        )

        assertEquals(3, jobs)
    }

    @Test
    fun buildsMsvcDllCommand() {
        val bundle = sourceBundle(NativePlatform("windows", "x86_64", "", ".dll", "win32"))
        val includeRoot = Path.of("build/test-jdk/include")
        val includeOs = includeRoot.resolve("win32")

        val command = NativeCompiler.buildCompileCommand(
            bundle = bundle,
            compiler = NativeCompiler.NativeCompilerExecutable("cl.exe", NativeCompiler.NativeCompilerKind.Msvc),
            includeRoot = includeRoot,
            includeOs = includeOs,
            config = NativePipelineConfig(compilerArgs = listOf("/MT"))
        )

        assertEquals("cl.exe", command.first())
        assertTrue("/LD" in command)
        assertTrue("/EHsc" in command)
        assertTrue("/MT" in command)
        assertTrue(command.any { it.startsWith("/I") && it.contains("include") })
        assertTrue(command.any { it.startsWith("/Fe:") && it.endsWith(".dll") })
        assertEquals(bundle.sourcePath.toAbsolutePath().toString(), command.last())
    }

    @Test
    fun detectsCompilerCommandStyle() {
        assertEquals(NativeCompiler.NativeCompilerKind.Msvc, NativeCompiler.compilerKind("cl.exe"))
        assertEquals(NativeCompiler.NativeCompilerKind.Msvc, NativeCompiler.compilerKind("C:/LLVM/bin/clang-cl.exe"))
        assertEquals(NativeCompiler.NativeCompilerKind.GnuLike, NativeCompiler.compilerKind("clang++"))
        assertEquals(NativeCompiler.NativeCompilerKind.GnuLike, NativeCompiler.compilerKind("/usr/bin/g++"))
        assertEquals(NativeCompiler.NativeCompilerKind.Zig, NativeCompiler.compilerKind("zig"))
        assertEquals(NativeCompiler.NativeCompilerKind.Zig, NativeCompiler.compilerKind("anything", NativeCompilerMode.Zig))
    }

    private fun sourceBundle(platform: NativePlatform): NativeSourceBundle {
        val root = Path.of("build/native-compiler-test")
        val sourceText = """
            #include <jni.h>
            extern "C" JNIEXPORT jint JNICALL grt_dummy(JNIEnv*, jclass) { return 1; }
        """.trimIndent() + "\n"
        return NativeSourceBundle(
            plan = NativeBuildPlan(
                loaderInternalName = "test/NativeLoader",
                resourceName = "grunteon/native/${platform.resourceDirectory}/${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                libraryFileName = "${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                platform = platform,
                classes = emptyList()
            ),
            sourceText = sourceText,
            sourcePath = root.resolve("src").resolve("grunteon_native.cpp"),
            libraryPath = root.resolve("lib").resolve(platform.resourceDirectory)
                .resolve("${platform.libraryPrefix}grunteon_native${platform.librarySuffix}")
        )
    }

    private fun sourceBundleForTargets(platforms: List<NativePlatform>): NativeSourceBundle {
        val first = platforms.first()
        val root = Path.of("build/native-compiler-zig-smoke")
        val sourcePath = root.resolve("src").resolve("grunteon_native.cpp")
        val base = sourceBundle(first)
        return base.copy(
            sourcePath = sourcePath,
            libraryPath = root.resolve("lib").resolve(first.resourceDirectory)
                .resolve("${first.libraryPrefix}grunteon_native${first.librarySuffix}"),
            sourceFiles = listOf(NativeSourceFile(sourcePath, base.sourceText)),
            libraryTargets = platforms.map { platform ->
                NativeLibraryTarget(
                    platform = platform,
                    resourceName = "grunteon/native/${platform.resourceDirectory}/${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                    libraryFileName = "${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                    libraryPath = root.resolve("lib").resolve(platform.resourceDirectory)
                        .resolve("${platform.libraryPrefix}grunteon_native${platform.librarySuffix}")
                )
            }
        )
    }
}
