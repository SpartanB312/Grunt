package net.spartanb312.grunt.utils

object Platform {

    val platform: Platforms = figure(System.getProperty("os.name"), System.getProperty("os.arch"))

    private fun figure(name: String, arch: String): Platforms {
        val linux = arrayOf("Linux", "FreeBSD", "SunOS", "Unit")
        val macos = arrayOf("Mac OS X", "Darwin")
        val arm = arrayOf("arm", "aarch64")
        if (linux.any { name.contains(it, true) }) {
            return if (arm.any { arch.contains(it, true) }) {
                if (arch.contains("64") || arch.contains("armv8")) Platforms.LinuxARM64
                else Platforms.LinuxARM32
            } else Platforms.Linux
        } else if (macos.any { name.contains(it, true) }) {
            return if (arm.any { arch.contains(it) }) Platforms.MacARM64
            else Platforms.Mac
        } else if (name.contains("Windows")) {
            return if (arch.contains("64")) {
                if (arch.contains("aarch64")) Platforms.WindowsARM64
                else Platforms.Windows64
            } else Platforms.Windows32
        }
        throw Error("Unsupported platform!")
    }

    enum class OS {
        Linux,
        MacOS,
        Windows
    }

    enum class Platforms(val platformName: String, val classifier: String, val os: OS) {
        Linux("Linux 64 Bit", "linux", OS.Linux),
        LinuxARM32("Linux 32 Bit ARM", "linux-arm32", OS.Linux),
        LinuxARM64("Linux 64 Bit ARM", "linux-arm64", OS.Linux),
        Windows32("Windows 32 Bit", "windows-x86", OS.Windows),
        Windows64("Windows 64 Bit", "windows", OS.Windows),
        WindowsARM64("Windows 64 Bit ARM", "windows-arm64", OS.Windows),
        Mac("MacOS 64 Bit", "macos", OS.MacOS),
        MacARM64("MacOS 64 Bit ARM", "macos-arm64", OS.MacOS)
    }

}
