package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    }

    private fun sourceBundle(platform: NativePlatform): NativeSourceBundle {
        val root = Path.of("build/native-compiler-test")
        return NativeSourceBundle(
            plan = NativeBuildPlan(
                loaderInternalName = "test/NativeLoader",
                resourceName = "grunteon/native/${platform.resourceDirectory}/${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                libraryFileName = "${platform.libraryPrefix}grunteon_native${platform.librarySuffix}",
                platform = platform,
                classes = emptyList()
            ),
            sourceText = "extern \"C\" int grt_dummy() { return 1; }\n",
            sourcePath = root.resolve("src").resolve("grunteon_native.cpp"),
            libraryPath = root.resolve("lib").resolve(platform.resourceDirectory)
                .resolve("${platform.libraryPrefix}grunteon_native${platform.librarySuffix}")
        )
    }
}
