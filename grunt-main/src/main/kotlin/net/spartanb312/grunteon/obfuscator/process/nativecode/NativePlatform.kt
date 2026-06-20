package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.util.Locale

internal data class NativePlatform(
    val os: String,
    val arch: String,
    val libraryPrefix: String,
    val librarySuffix: String,
    val jniIncludeOs: String,
    val zigTarget: String? = null
) {
    val resourceDirectory: String
        get() = "$os-$arch"

    companion object {
        val zigBuiltInTargets: List<NativePlatform> = listOf(
            NativePlatform("windows", "x86_64", "", ".dll", "win32", "x86_64-windows-gnu"),
            NativePlatform("windows", "aarch64", "", ".dll", "win32", "aarch64-windows-gnu"),
            NativePlatform("linux", "x86_64", "lib", ".so", "linux", "x86_64-linux-gnu"),
            NativePlatform("linux", "aarch64", "lib", ".so", "linux", "aarch64-linux-gnu"),
            NativePlatform("macos", "x86_64", "lib", ".dylib", "darwin", "x86_64-macos"),
            NativePlatform("macos", "aarch64", "lib", ".dylib", "darwin", "aarch64-macos")
        )

        fun current(): NativePlatform {
            val osName = System.getProperty("os.name").lowercase(Locale.US)
            val archName = System.getProperty("os.arch").lowercase(Locale.US)
            val os = normalizeOs(osName)
            val arch = normalizeArch(archName)
            return when (os) {
                "windows" -> NativePlatform(os, arch, "", ".dll", "win32")
                "macos" -> NativePlatform(os, arch, "lib", ".dylib", "darwin")
                else -> NativePlatform(os, arch, "lib", ".so", "linux")
            }
        }

        fun zigTargets(config: NativePipelineConfig): List<NativePlatform> {
            if (config.targetPlatforms.isEmpty()) return zigBuiltInTargets
            return config.targetPlatforms.map { it.toNativePlatform() }
        }

        fun targetsFor(config: NativePipelineConfig): List<NativePlatform> {
            return if (config.compilerMode == NativeCompilerMode.Zig) {
                zigTargets(config)
            } else {
                listOf(current())
            }
        }

        fun normalizeOs(raw: String): String {
            val value = raw.lowercase(Locale.US)
            return when {
                value.contains("win") -> "windows"
                value.contains("mac") || value.contains("darwin") -> "macos"
                value.contains("linux") || value.contains("nix") || value.contains("nux") || value.contains("aix") -> "linux"
                else -> value.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "unknown" }
            }
        }

        fun normalizeArch(raw: String): String {
            return when (val value = raw.lowercase(Locale.US)) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "aarch64"
                "x86", "i386", "i686" -> "x86"
                else -> value.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "unknown" }
            }
        }

        fun fromResourceDirectory(resourceDirectory: String): NativePlatform? {
            return zigBuiltInTargets.firstOrNull { it.resourceDirectory == resourceDirectory }
        }
    }
}

internal fun NativeTargetPreset.toNativePlatform(): NativePlatform {
    return when (this) {
        NativeTargetPreset.WindowsX86_64 -> NativePlatform.zigBuiltInTargets[0]
        NativeTargetPreset.WindowsAarch64 -> NativePlatform.zigBuiltInTargets[1]
        NativeTargetPreset.LinuxX86_64 -> NativePlatform.zigBuiltInTargets[2]
        NativeTargetPreset.LinuxAarch64 -> NativePlatform.zigBuiltInTargets[3]
        NativeTargetPreset.MacosX86_64 -> NativePlatform.zigBuiltInTargets[4]
        NativeTargetPreset.MacosAarch64 -> NativePlatform.zigBuiltInTargets[5]
    }
}
