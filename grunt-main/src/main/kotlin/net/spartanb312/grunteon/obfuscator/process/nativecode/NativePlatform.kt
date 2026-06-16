package net.spartanb312.grunteon.obfuscator.process.nativecode

import java.util.Locale

internal data class NativePlatform(
    val os: String,
    val arch: String,
    val libraryPrefix: String,
    val librarySuffix: String,
    val jniIncludeOs: String
) {
    val resourceDirectory: String
        get() = "$os-$arch"

    companion object {
        fun current(): NativePlatform {
            val osName = System.getProperty("os.name").lowercase(Locale.US)
            val archName = System.getProperty("os.arch").lowercase(Locale.US)
            val os = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "macos"
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "linux"
                else -> osName.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "unknown" }
            }
            val arch = when (archName) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "aarch64"
                "x86", "i386", "i686" -> "x86"
                else -> archName.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "unknown" }
            }
            return when (os) {
                "windows" -> NativePlatform(os, arch, "", ".dll", "win32")
                "macos" -> NativePlatform(os, arch, "lib", ".dylib", "darwin")
                else -> NativePlatform(os, arch, "lib", ".so", "linux")
            }
        }
    }
}
