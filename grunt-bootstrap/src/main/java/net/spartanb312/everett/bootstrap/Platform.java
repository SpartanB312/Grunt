package net.spartanb312.everett.bootstrap;

import java.util.function.Predicate;

public final class Platform {

    public enum OS {
        Linux,
        MacOS,
        Windows
    }

    public enum Platforms {
        Linux("Linux 64 Bit", "linux", OS.Linux),
        LinuxARM32("Linux 32 Bit ARM", "linux-arm32", OS.Linux),
        LinuxARM64("Linux 64 Bit ARM", "linux-arm64", OS.Linux),
        Windows32("Windows 32 Bit", "windows-x86", OS.Windows),
        Windows64("Windows 64 Bit", "windows", OS.Windows),
        WindowsARM64("Windows 64 Bit ARM", "windows-arm64", OS.Windows),
        Mac("MacOS 64 Bit", "macos", OS.MacOS),
        MacARM64("MacOS 64 Bit ARM", "macos-arm64", OS.MacOS);

        private final String platformName;
        private final String classifier;
        private final OS os;

        Platforms(String name, String classifier, OS os) {
            this.platformName = name;
            this.classifier = classifier;
            this.os = os;
        }

        public String getPlatformName() {
            return platformName;
        }

        public String getClassifier() {
            return classifier;
        }

        public OS getOS() {
            return os;
        }
    }

    private static final Platforms platform = figure(System.getProperty("os.name"), System.getProperty("os.arch"));

    public static Platforms getPlatform() {
        return platform;
    }

    private static Platforms figure(String name, String arch) {
        String[] linux = {"Linux", "FreeBSD", "SunOS", "Unit"};
        String[] macos = {"Mac OS X", "Darwin"};
        String[] arm = {"arm", "aarch64"};

        if (any(linux, name::contains)) {
            if (any(arm, arch::contains)) {
                if (arch.contains("64") || arch.contains("armv8")) {
                    return Platforms.LinuxARM64;
                } else return Platforms.LinuxARM32;
            } else return Platforms.Linux;
        } else if (any(macos, name::contains)) {
            if (any(arm, arch::contains)) return Platforms.MacARM64;
            else return Platforms.Mac;
        } else if (name.contains("Windows")) {
            if (arch.contains("64")) {
                if (arch.contains("aarch64")) return Platforms.WindowsARM64;
                else return Platforms.Windows64;
            } else return Platforms.Windows32;
        }

        throw new Error("Unsupported platform!");
    }

    private static <T> boolean any(T[] array, Predicate<T> predicate) {
        for (T it : array) {
            if (predicate.test(it)) return true;
        }
        return false;
    }

}
