package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText

internal object NativeJniHeaders {

    fun resolve(
        platform: NativePlatform,
        config: NativePipelineConfig,
        workDir: Path,
        allowBuiltInPlatformHeader: Boolean,
        javaHome: Path = Path.of(System.getProperty("java.home")),
        javaHomeEnv: String? = System.getenv("JAVA_HOME"),
        localIncludeRoots: List<Path> = localJdkIncludeRoots(),
        currentPlatform: NativePlatform = NativePlatform.current()
    ): NativeJniIncludeResolution {
        val root = resolveCommonIncludeRoot(config, javaHome, javaHomeEnv, localIncludeRoots)
            ?: throw NativeJniHeaderException("JNI header jni.h not found. Configure nativePipeline.jniIncludeRoot or JAVA_HOME.")
        val platformInclude = resolvePlatformInclude(
            platform,
            config,
            root.path,
            workDir,
            allowBuiltInPlatformHeader,
            localIncludeRoots,
            currentPlatform
        )
        return NativeJniIncludeResolution(
            includeRoot = root.path,
            includeRootSource = root.source,
            platformInclude = platformInclude.path,
            platformIncludeSource = platformInclude.source
        )
    }

    internal fun resolveCommonIncludeRoot(
        config: NativePipelineConfig,
        javaHome: Path = Path.of(System.getProperty("java.home")),
        javaHomeEnv: String? = System.getenv("JAVA_HOME"),
        localIncludeRoots: List<Path> = localJdkIncludeRoots()
    ): NativeResolvedPath? {
        val candidates = buildList {
            config.jniIncludeRoot?.takeIf { it.isNotBlank() }?.let {
                add(NativeResolvedPath(Path.of(it), "configured"))
            }
            add(NativeResolvedPath(resolveJniIncludeRoot(javaHome), "java.home"))
            javaHomeEnv?.takeIf { it.isNotBlank() }?.let {
                add(NativeResolvedPath(Path.of(it).resolve("include"), "JAVA_HOME"))
            }
            addAll(localIncludeRoots.map { NativeResolvedPath(it, "local-scan") })
        }.distinctBy { it.path.toAbsolutePath().normalize() }

        return candidates.firstOrNull { it.path.resolve("jni.h").exists() }
    }

    internal fun resolvePlatformInclude(
        platform: NativePlatform,
        config: NativePipelineConfig,
        commonIncludeRoot: Path,
        workDir: Path,
        allowBuiltInPlatformHeader: Boolean,
        localIncludeRoots: List<Path> = localJdkIncludeRoots(),
        currentPlatform: NativePlatform = NativePlatform.current()
    ): NativeResolvedPath {
        config.targetJniIncludeDirs[platform.resourceDirectory]
            ?.takeIf { it.isNotBlank() }
            ?.let { configured ->
                val path = Path.of(configured)
                if (path.resolve("jni_md.h").exists()) {
                    return NativeResolvedPath(path, "configured")
                }
            }

        if (config.jniHeaderPolicy == NativeJniHeaderPolicy.RequireConfiguredTargetHeaders &&
            platform.resourceDirectory != currentPlatform.resourceDirectory
        ) {
            throw NativeJniHeaderException(
                "JNI platform header jni_md.h for cross target ${platform.resourceDirectory} must be configured with " +
                    "nativePipeline.targetJniIncludeDirs[\"${platform.resourceDirectory}\"] when " +
                    "jniHeaderPolicy=RequireConfiguredTargetHeaders"
            )
        }

        platformIncludeCandidates(commonIncludeRoot, platform.jniIncludeOs, localIncludeRoots)
            .firstOrNull { it.resolve("jni_md.h").exists() }
            ?.let { return NativeResolvedPath(it, "local-scan") }

        val canUseBuiltIn = allowBuiltInPlatformHeader &&
            config.jniHeaderPolicy == NativeJniHeaderPolicy.AutoFallback
        if (!canUseBuiltIn) {
            throw NativeJniHeaderException(
                "JNI platform header jni_md.h not found for ${platform.resourceDirectory} " +
                    "under ${commonIncludeRoot.absolutePathString()}/${platform.jniIncludeOs}"
            )
        }

        val builtIn = workDir.resolve("generated-jni")
            .resolve(platform.resourceDirectory)
            .resolve(platform.jniIncludeOs)
        builtIn.createDirectories()
        builtIn.resolve("jni_md.h").writeText(builtInJniMd(platform))
        return NativeResolvedPath(builtIn, "built-in")
    }

    private fun resolveJniIncludeRoot(javaHome: Path): Path {
        val direct = javaHome.resolve("include")
        if (direct.exists()) return direct
        return javaHome.parent?.resolve("include") ?: direct
    }

    private fun platformIncludeCandidates(
        commonIncludeRoot: Path,
        jniIncludeOs: String,
        localIncludeRoots: List<Path>
    ): List<Path> {
        return buildList {
            add(commonIncludeRoot.resolve(jniIncludeOs))
            localIncludeRoots.forEach { add(it.resolve(jniIncludeOs)) }
        }.distinctBy { it.toAbsolutePath().normalize() }
    }

    private fun localJdkIncludeRoots(): List<Path> {
        val roots = buildList {
            add(Path.of("C:/Program Files/Java"))
            add(Path.of("C:/Program Files/Eclipse Adoptium"))
            add(Path.of("C:/Program Files/Microsoft"))
            add(Path.of("/usr/lib/jvm"))
            add(Path.of("/Library/Java/JavaVirtualMachines"))
        }
        return roots.flatMap(::jdkIncludesUnder).distinctBy { it.toAbsolutePath().normalize() }.sortedBy {
            it.absolutePathString().lowercase()
        }
    }

    private fun jdkIncludesUnder(root: Path): List<Path> {
        if (!root.exists() || !root.isDirectory()) return emptyList()
        return buildList {
            root.resolve("include").takeIf { it.resolve("jni.h").exists() }?.let(::add)
            Files.newDirectoryStream(root).use { entries ->
                entries.asSequence()
                    .filter { it.isDirectory() }
                    .sortedBy { it.name.lowercase() }
                    .forEach { child ->
                        child.resolve("include").takeIf { it.resolve("jni.h").exists() }?.let(::add)
                        child.resolve("Contents").resolve("Home").resolve("include")
                            .takeIf { it.resolve("jni.h").exists() }
                            ?.let(::add)
                    }
            }
        }
    }

    private fun builtInJniMd(platform: NativePlatform): String {
        return when (platform.os) {
            "windows" -> """
                #ifndef _JAVASOFT_JNI_MD_H_
                #define _JAVASOFT_JNI_MD_H_
                #define JNIEXPORT __declspec(dllexport)
                #define JNIIMPORT __declspec(dllimport)
                #define JNICALL __stdcall
                typedef long jint;
                typedef __int64 jlong;
                typedef signed char jbyte;
                #endif
            """.trimIndent() + System.lineSeparator()
            else -> """
                #ifndef _JAVASOFT_JNI_MD_H_
                #define _JAVASOFT_JNI_MD_H_
                #define JNIEXPORT __attribute__((visibility("default")))
                #define JNIIMPORT
                #define JNICALL
                typedef int jint;
                typedef long long jlong;
                typedef signed char jbyte;
                #endif
            """.trimIndent() + System.lineSeparator()
        }
    }
}

internal data class NativeJniIncludeResolution(
    val includeRoot: Path,
    val includeRootSource: String,
    val platformInclude: Path,
    val platformIncludeSource: String
)

internal data class NativeResolvedPath(
    val path: Path,
    val source: String
)

internal class NativeJniHeaderException(message: String) : IllegalStateException(message)
