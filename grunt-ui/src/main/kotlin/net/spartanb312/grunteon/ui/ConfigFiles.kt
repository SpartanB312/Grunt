package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.ObfConfig
import java.nio.file.Path as NioPath
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

fun loadConfig(path: NioPath): ConfigLoadResult {
    return runCatching {
        val config = ObfConfig.read(path)
        ConfigLoadResult(
            config = config,
            path = path,
            message = "Loaded ${config.transformerConfigs.size} transformer nodes from ${path.toAbsolutePath().normalize()}",
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

fun chooseConfigPath(): NioPath? {
    val chooser = JFileChooser(defaultConfigPath().parent.toFile()).apply {
        dialogTitle = "Open existed config"
        fileFilter = FileNameExtensionFilter("JSON config files", "json")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else {
        null
    }
}

fun chooseNewConfigPath(): NioPath? {
    val chooser = JFileChooser(defaultConfigPath().parent.toFile()).apply {
        dialogTitle = "New config"
        selectedFile = defaultConfigPath().toFile()
        fileFilter = FileNameExtensionFilter("JSON config files", "json")
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        val selected = chooser.selectedFile.toPath()
        if (selected.fileName.toString().contains(".")) selected else selected.resolveSibling("${selected.fileName}.json")
    } else {
        null
    }
}

fun chooseInputPath(currentValue: String): NioPath? {
    val initialPath = resolveChooserPath(currentValue, Path("input.jar"))
    val chooser = JFileChooser(initialChooserDirectory(initialPath).toFile()).apply {
        dialogTitle = "Select input jar or directory"
        selectedFile = initialPath.toFile()
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        fileFilter = FileNameExtensionFilter("Jar or Zip files", "jar", "zip")
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else {
        null
    }
}

fun chooseOutputPath(currentValue: String): NioPath? {
    val initialPath = resolveChooserPath(currentValue, Path("output.jar"))
    val chooser = JFileChooser(initialChooserDirectory(initialPath).toFile()).apply {
        dialogTitle = "Select output jar"
        selectedFile = initialPath.toFile()
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("Jar files", "jar")
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath().let { selected ->
            if (selected.extension.isBlank()) selected.resolveSibling("${selected.name}.jar") else selected
        }
    } else {
        null
    }
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
