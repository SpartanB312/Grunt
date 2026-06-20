package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.IntRangeVal
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.util.NATIVE_EXCLUDED
import net.spartanb312.grunteon.obfuscator.util.NATIVE_INCLUDED

@Serializable
enum class NativeBackend {
    Cpp,
    Llvm
}

@Serializable
enum class NativeOptimizationLevel {
    O0,
    O1,
    O2,
    O3,
    Os,
    Oz
}

@Serializable
enum class NativeCompilerMode {
    Auto,
    GnuLike,
    Msvc,
    Zig
}

@Serializable
enum class NativeTargetPreset {
    WindowsX86_64,
    WindowsAarch64,
    LinuxX86_64,
    LinuxAarch64,
    MacosX86_64,
    MacosAarch64
}

@Serializable
enum class NativeJniHeaderPolicy {
    AutoFallback,
    RequireConfiguredTargetHeaders
}

@Serializable
data class NativePipelineConfig(
    @SettingDesc("Enable native code generation after the transformer pipeline and before jar dumping")
    @SettingName("Enabled")
    val enabled: Boolean = false,
    @SettingDesc("Annotations that mark classes or methods as native candidates")
    @SettingName("Included annotations")
    val includedAnnotationList: List<String> = listOf(NATIVE_INCLUDED),
    @SettingDesc("Annotations that exclude classes or methods from native code generation")
    @SettingName("Excluded annotations")
    val excludedAnnotationList: List<String> = listOf(NATIVE_EXCLUDED),
    @SettingDesc("Class filter used by the native pipeline candidate scanner")
    @SettingName("Class filter")
    val classFilter: ClassFilterConfig = ClassFilterConfig(),
    @SettingDesc("Fail the obfuscation job when a marked candidate is rejected by validation")
    @SettingName("Fail on validation error")
    val failOnValidationError: Boolean = false,
    @SettingDesc("Fail the obfuscation job when native compilation fails")
    @SettingName("Fail on compile error")
    val failOnCompileError: Boolean = false,
    @SettingDesc("Remove Grunteon native marker annotations after native pipeline processing")
    @SettingName("Clean native annotations")
    val cleanNativeAnnotations: Boolean = true,
    @SettingDesc("Log each native candidate skip reason")
    @SettingName("Log skips")
    val logSkips: Boolean = true,
    @SettingDesc("Native code generation backend")
    @SettingName("Backend")
    val backend: NativeBackend = NativeBackend.Cpp,
    @SettingDesc("Working directory for generated native sources and compiled libraries")
    @SettingName("Work directory")
    val workDir: String = "build/grunteon/native",
    @SettingDesc("Split large generated C++ output into multiple source files before compiling")
    @SettingName("Split source files")
    val splitSourceFiles: Boolean = true,
    @SettingDesc("Maximum native methods emitted into one generated C++ source file when source splitting is enabled")
    @IntRangeVal(min = 1, max = 4096)
    @SettingName("Max methods per source file")
    val maxMethodsPerSourceFile: Int = 16,
    @SettingDesc("Maximum parallel C++ compile jobs for split native sources. 0 uses a conservative memory-aware automatic value.")
    @IntRangeVal(min = 0, max = 128)
    @SettingName("Parallel compile jobs")
    val parallelCompileJobs: Int = 0,
    @SettingDesc("C++ compiler optimization level for generated native sources")
    @SettingName("Optimization level")
    val optimizationLevel: NativeOptimizationLevel = NativeOptimizationLevel.O1,
    @SettingDesc("Native C++ compiler command style")
    @SettingName("Compiler mode")
    val compilerMode: NativeCompilerMode = NativeCompilerMode.Auto,
    @SettingDesc("Target platforms for Zig cross compilation. Empty means all built-in Zig targets.")
    @SettingName("Target platforms")
    val targetPlatforms: List<NativeTargetPreset> = emptyList(),
    @SettingDesc("Optional JDK include root containing jni.h. Empty uses auto-detection.")
    @SettingName("JNI include root")
    val jniIncludeRoot: String? = null,
    @SettingDesc("JNI platform header fallback policy. Strict mode requires configured headers for cross targets.")
    @SettingName("JNI header policy")
    val jniHeaderPolicy: NativeJniHeaderPolicy = NativeJniHeaderPolicy.AutoFallback,
    @SettingDesc("Optional per-target directories containing jni_md.h, keyed by resource id such as linux-x86_64")
    @SettingName("Target JNI include dirs")
    val targetJniIncludeDirs: Map<String, String> = emptyMap(),
    @SettingDesc("Optional C++ compiler executable path or command name. Empty uses auto-detection.")
    @SettingName("Compiler executable")
    val compilerExecutable: String? = null,
    @SettingDesc("Additional arguments passed to the native C++ compiler")
    @SettingName("Compiler args")
    val compilerArgs: List<String> = emptyList(),
    @SettingDesc("Additional compiler arguments per target resource id")
    @SettingName("Target compiler args")
    val targetCompilerArgs: Map<String, List<String>> = emptyMap()
)
