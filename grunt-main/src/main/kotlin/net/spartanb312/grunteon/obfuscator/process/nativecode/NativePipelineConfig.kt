package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
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
    val workDir: String = "build/grunteon/native"
)
