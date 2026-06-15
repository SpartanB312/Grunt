package net.spartanb312.grunteon.ui

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.*
import io.github.vinceglb.filekit.path
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import kotlin.io.path.*
import java.nio.file.Path as NioPath

fun loadConfig(path: NioPath): ConfigLoadResult {
    return runCatching {
        val config = ObfConfig.read(path)
        ConfigLoadResult(
            config = config,
            path = path,
            message = "Loaded ${config.transformers.size} transformer nodes from ${path.toAbsolutePath().normalize()}",
            success = true,
        )
    }.getOrElse { error ->
        ConfigLoadResult(
            config = ObfConfig(),
            path = path,
            message = "Failed to load ${path.toAbsolutePath().normalize()}: ${error.message}",
            success = false,
        )
    }
}

fun defaultConfigPath(): NioPath {
    return locateProjectRoot().resolve("config.json")
}

suspend fun chooseConfigPath(): NioPath? {
    return FileKit.openFilePicker(
        type = FileKitType.File("json"),
        directory = defaultConfigPath().parent.toPlatformFile(),
        dialogSettings = fileDialogSettings("Open existed config"),
    )?.toNioPath()
}

suspend fun chooseNewConfigPath(): NioPath? {
    val defaultPath = defaultConfigPath()
    return FileKit.openFileSaver(
        suggestedName = defaultPath.nameWithoutExtension,
        defaultExtension = "json",
        allowedExtensions = setOf("json"),
        directory = defaultPath.parent.toPlatformFile(),
        dialogSettings = fileDialogSettings("New config"),
    )?.toNioPath()?.ensureExtension("json")
}

suspend fun chooseSaveConfigPath(currentPath: NioPath): NioPath? {
    return FileKit.openFileSaver(
        suggestedName = currentPath.nameWithoutExtension,
        defaultExtension = "json",
        allowedExtensions = setOf("json"),
        directory = initialChooserDirectory(currentPath).toPlatformFile(),
        dialogSettings = fileDialogSettings("Save config as"),
    )?.toNioPath()?.ensureExtension("json")
}

suspend fun chooseInputPath(currentValue: String): NioPath? {
    val initialPath = resolveChooserPath(currentValue, Path("input.jar"))
    return FileKit.openFilePicker(
        type = FileKitType.File("jar", "zip"),
        directory = initialChooserDirectory(initialPath).toPlatformFile(),
        dialogSettings = fileDialogSettings("Select input jar"),
    )?.toNioPath()
}

suspend fun chooseInputDirectory(currentValue: String): NioPath? {
    val initialPath = resolveChooserPath(currentValue, Path("input.jar"))
    return FileKit.openDirectoryPicker(
        directory = initialChooserDirectory(initialPath).toPlatformFile(),
        dialogSettings = fileDialogSettings("Select input directory"),
    )?.toNioPath()
}

suspend fun chooseOutputPath(currentValue: String): NioPath? {
    val initialPath = resolveChooserPath(currentValue, Path("output.jar"))
    return FileKit.openFileSaver(
        suggestedName = initialPath.nameWithoutExtension,
        defaultExtension = "jar",
        allowedExtensions = setOf("jar"),
        directory = initialChooserDirectory(initialPath).toPlatformFile(),
        dialogSettings = fileDialogSettings("Select output jar"),
    )?.toNioPath()?.ensureExtension("jar")
}

private fun locateProjectRoot(): NioPath {
    var current = Path("").absolute().normalize()
    repeat(8) {
        if (current.resolve("settings.gradle.kts").exists()) return current
        current = current.parent ?: return@repeat
    }
    return Path("").absolute().normalize()
}

private fun resolveChooserPath(rawPath: String, fallbackFile: NioPath): NioPath {
    val sanitized = rawPath.trim()
    if (sanitized.isBlank()) return defaultConfigPath().parent.resolve(fallbackFile).normalize()
    return Path(sanitized)
}

private fun initialChooserDirectory(path: NioPath): NioPath {
    return when {
        path.exists() && path.isDirectory() -> path
        path.parent?.exists() == true -> path.parent
        else -> defaultConfigPath().parent
    }.normalize()
}

private fun NioPath.toPlatformFile(): PlatformFile = PlatformFile(toFile())

private fun PlatformFile.toNioPath(): NioPath = Path(path)

private fun fileDialogSettings(title: String): FileKitDialogSettings {
    return FileKitDialogSettings(title = title)
}

private fun NioPath.ensureExtension(extension: String): NioPath {
    return if (this.extension.isBlank()) resolveSibling("$name.$extension") else this
}
