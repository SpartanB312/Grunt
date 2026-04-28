package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.ObfConfig
import java.nio.file.Path as NioPath
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

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

private fun locateProjectRoot(): NioPath {
    var current = Path("").absolute().normalize()
    repeat(8) {
        if (current.resolve("settings.gradle.kts").exists()) return current
        current = current.parent ?: return@repeat
    }
    return Path("").absolute().normalize()
}
