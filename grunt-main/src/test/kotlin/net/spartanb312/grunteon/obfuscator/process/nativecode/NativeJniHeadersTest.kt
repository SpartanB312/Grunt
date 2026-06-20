package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeJniHeadersTest {

    @Test
    fun configuredPlatformIncludeWins() {
        val root = createTempDirectory("grunteon-jni-root")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val configured = root.resolve("configured-linux")
        configured.createDirectories()
        configured.resolve("jni_md.h").writeText("/* configured */")
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" }

        val result = NativeJniHeaders.resolve(
            platform = platform,
            config = NativePipelineConfig(
                jniIncludeRoot = common.toString(),
                targetJniIncludeDirs = mapOf("linux-x86_64" to configured.toString())
            ),
            workDir = root.resolve("work"),
            allowBuiltInPlatformHeader = true
        )

        assertEquals(common, result.includeRoot)
        assertEquals("configured", result.platformIncludeSource)
        assertEquals(configured, result.platformInclude)
    }

    @Test
    fun localScanPlatformIncludeFallsBackBeforeBuiltInHeader() {
        val root = createTempDirectory("grunteon-jni-local")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val scannedRoot = root.resolve("other-jdk").resolve("include")
        val scannedPlatform = scannedRoot.resolve("darwin")
        scannedPlatform.createDirectories()
        scannedPlatform.resolve("jni_md.h").writeText("/* scanned */")
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "macos-aarch64" }

        val result = NativeJniHeaders.resolvePlatformInclude(
            platform = platform,
            config = NativePipelineConfig(),
            commonIncludeRoot = common,
            workDir = root.resolve("work"),
            allowBuiltInPlatformHeader = true,
            localIncludeRoots = listOf(scannedRoot)
        )

        assertEquals("local-scan", result.source)
        assertEquals(scannedPlatform, result.path)
    }

    @Test
    fun builtInPlatformHeaderIsGeneratedWhenNoPlatformHeaderExists() {
        val root = createTempDirectory("grunteon-jni-builtin")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-aarch64" }

        val result = NativeJniHeaders.resolvePlatformInclude(
            platform = platform,
            config = NativePipelineConfig(),
            commonIncludeRoot = common,
            workDir = root.resolve("work"),
            allowBuiltInPlatformHeader = true,
            localIncludeRoots = emptyList()
        )

        val header = result.path.resolve("jni_md.h")
        assertEquals("built-in", result.source)
        assertTrue(header.exists())
        assertTrue("JNIEXPORT" in header.readText())
    }

    @Test
    fun strictPolicyRequiresConfiguredHeaderForCrossTarget() {
        val root = createTempDirectory("grunteon-jni-strict-cross")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val scannedRoot = root.resolve("scanned-jdk").resolve("include")
        val scannedPlatform = scannedRoot.resolve("linux")
        scannedPlatform.createDirectories()
        scannedPlatform.resolve("jni_md.h").writeText("/* scanned */")
        val current = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "windows-x86_64" }
        val cross = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" }

        val failure = assertFailsWith<NativeJniHeaderException> {
            NativeJniHeaders.resolvePlatformInclude(
                platform = cross,
                config = NativePipelineConfig(jniHeaderPolicy = NativeJniHeaderPolicy.RequireConfiguredTargetHeaders),
                commonIncludeRoot = common,
                workDir = root.resolve("work"),
                allowBuiltInPlatformHeader = true,
                localIncludeRoots = listOf(scannedRoot),
                currentPlatform = current
            )
        }

        assertTrue("linux-x86_64" in failure.message.orEmpty())
        assertTrue("targetJniIncludeDirs" in failure.message.orEmpty())
    }

    @Test
    fun strictPolicyAllowsConfiguredHeaderForCrossTarget() {
        val root = createTempDirectory("grunteon-jni-strict-configured")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val configured = root.resolve("configured-linux")
        configured.createDirectories()
        configured.resolve("jni_md.h").writeText("/* configured */")
        val current = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "windows-x86_64" }
        val cross = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" }

        val result = NativeJniHeaders.resolvePlatformInclude(
            platform = cross,
            config = NativePipelineConfig(
                jniHeaderPolicy = NativeJniHeaderPolicy.RequireConfiguredTargetHeaders,
                targetJniIncludeDirs = mapOf("linux-x86_64" to configured.toString())
            ),
            commonIncludeRoot = common,
            workDir = root.resolve("work"),
            allowBuiltInPlatformHeader = true,
            localIncludeRoots = emptyList(),
            currentPlatform = current
        )

        assertEquals("configured", result.source)
        assertEquals(configured, result.path)
    }

    @Test
    fun strictPolicyDoesNotGenerateBuiltInHeaderForCurrentTarget() {
        val root = createTempDirectory("grunteon-jni-strict-current")
        val common = root.resolve("jdk").resolve("include")
        common.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        val current = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "windows-x86_64" }

        assertFailsWith<NativeJniHeaderException> {
            NativeJniHeaders.resolvePlatformInclude(
                platform = current,
                config = NativePipelineConfig(jniHeaderPolicy = NativeJniHeaderPolicy.RequireConfiguredTargetHeaders),
                commonIncludeRoot = common,
                workDir = root.resolve("work"),
                allowBuiltInPlatformHeader = true,
                localIncludeRoots = emptyList(),
                currentPlatform = current
            )
        }
    }

    @Test
    fun strictPolicyAllowsCurrentTargetJdkHeader() {
        val root = createTempDirectory("grunteon-jni-strict-current-jdk")
        val common = root.resolve("jdk").resolve("include")
        val platformInclude = common.resolve("win32")
        platformInclude.createDirectories()
        common.resolve("jni.h").writeText("/* jni */")
        platformInclude.resolve("jni_md.h").writeText("/* current */")
        val current = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "windows-x86_64" }

        val result = NativeJniHeaders.resolvePlatformInclude(
            platform = current,
            config = NativePipelineConfig(jniHeaderPolicy = NativeJniHeaderPolicy.RequireConfiguredTargetHeaders),
            commonIncludeRoot = common,
            workDir = root.resolve("work"),
            allowBuiltInPlatformHeader = true,
            localIncludeRoots = emptyList(),
            currentPlatform = current
        )

        assertEquals("local-scan", result.source)
        assertEquals(platformInclude, result.path)
    }

    @Test
    fun missingCommonJniHeaderFailsWithoutLocalFallback() {
        val root = createTempDirectory("grunteon-jni-missing")
        val missing = root.resolve("missing")
        val result = NativeJniHeaders.resolveCommonIncludeRoot(
            config = NativePipelineConfig(jniIncludeRoot = missing.toString()),
            javaHome = root.resolve("java-home"),
            javaHomeEnv = null,
            localIncludeRoots = emptyList()
        )

        assertEquals(null, result)
        assertFailsWith<NativeJniHeaderException> {
            NativeJniHeaders.resolve(
                platform = NativePlatform.zigBuiltInTargets.single { it.resourceDirectory == "linux-x86_64" },
                config = NativePipelineConfig(jniIncludeRoot = missing.toString()),
                workDir = root.resolve("work"),
                allowBuiltInPlatformHeader = true,
                javaHome = root.resolve("java-home"),
                javaHomeEnv = null,
                localIncludeRoots = emptyList()
            )
        }
    }
}
