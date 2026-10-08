package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeCompileCacheGnuIntegrationTest {
    external fun nativeValue(): Int

    @Test
    fun compilesExactPreprocessedRuntimeHeadersAndLoadsChangedLibrary() = synchronized(NativeCompileCacheTest::class.java) {
        val executable = System.getProperty("grunteon.gnu.executable")
            ?: System.getenv("GRUNTEON_GNU_EXECUTABLE")
            ?: run {
                println("Skipping native cache GNU integration: set GRUNTEON_GNU_EXECUTABLE")
                return@synchronized
            }
        val enabled = System.getProperty(NativeCompileCache.EnabledProperty)
        val toolchain = System.getProperty(NativeCompileCache.ToolchainProperty)
        val root = Files.createTempDirectory("native-cache-gnu-")
        try {
            System.setProperty(NativeCompileCache.EnabledProperty, "true")
            // The private cache only lives during this test; the installed toolchain must stay immutable.
            System.setProperty(NativeCompileCache.ToolchainProperty, "isolated-gnu-integration-test")
            val platform = NativePlatform.current()
            val source = root.resolve("src/grunteon_native_main.cpp")
            val other = root.resolve("src/grunteon_native_other.cpp")
            val header = root.resolve("src/grunteon_native_value.hpp")
            val headers = listOf("jni.h", "cmath", "cstdint", "cstring", "limits", "mutex", "string", "vector", "unordered_set", "algorithm")
                .joinToString("\n") { "#include <$it>" }
            val text = headers + """

                #include "grunteon_native_value.hpp"
                extern int other();
                extern "C" JNIEXPORT jint JNICALL
                Java_net_spartanb312_grunteon_obfuscator_process_nativecode_NativeCompileCacheGnuIntegrationTest_nativeValue(JNIEnv*, jobject) {
                    return other() + VALUE;
                }
            """.trimIndent()
            val library = root.resolve("lib/" + platform.libraryPrefix + "cache_test" + platform.librarySuffix)
            val plan = NativeBuildPlan("test/Loader", "test/library", library.fileName.toString(), platform, emptyList())
            val bundle = NativeSourceBundle(
                plan, "", source, library,
                listOf(NativeSourceFile(source, text), NativeSourceFile(other, "int other() { return 41; }\n"),
                    NativeSourceFile(header, "#define VALUE 1\n")),
                sourceTextFactory = { error("Diagnostic source must remain lazy") }
            )
            val config = NativePipelineConfig(
                compilerExecutable = executable, compilerMode = NativeCompilerMode.GnuLike,
                workDir = root.resolve("work").toString(), parallelCompileJobs = 1
            )
            val cold = NativeCompiler.compile(bundle, config)
            assertTrue(cold.success, cold.output)
            val warm = NativeCompiler.compile(bundle, config)
            assertTrue(warm.success, warm.output)
            assertEquals(2, warm.output.lineSequence().count { it.startsWith("Native object cache hit:") }, warm.output)
            val changed = NativeCompiler.compile(bundle.copy(sourceFiles = bundle.sourceFiles.map {
                if (it.path == other) it.copy(text = "int other() { return 42; }\n") else it
            }), config)
            assertTrue(changed.success, changed.output)
            assertEquals(1, changed.output.lineSequence().count { it.startsWith("Native object cache hit:") }, changed.output)
            println("GNU cache smoke ms: cold=" + cold.compileTimeMillis + ", identical=" + warm.compileTimeMillis +
                ", one-TU-change=" + changed.compileTimeMillis)
            System.load(library.toAbsolutePath().toString())
            assertEquals(43, nativeValue())
        } finally {
            if (enabled == null) System.clearProperty(NativeCompileCache.EnabledProperty)
            else System.setProperty(NativeCompileCache.EnabledProperty, enabled)
            if (toolchain == null) System.clearProperty(NativeCompileCache.ToolchainProperty)
            else System.setProperty(NativeCompileCache.ToolchainProperty, toolchain)
            // Loaded DLLs cannot be removed on Windows until the test JVM exits.
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach {
                try { Files.deleteIfExists(it) } catch (_: java.io.IOException) { it.toFile().deleteOnExit() }
            } }
        }
    }
}
