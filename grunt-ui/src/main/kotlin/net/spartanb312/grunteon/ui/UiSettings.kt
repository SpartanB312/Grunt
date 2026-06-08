package net.spartanb312.grunteon.ui

import java.nio.file.Path as NioPath
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

data class UiSettings(
    val fontScale: Float = DefaultFontScale,
    val themeMode: ThemeMode = ThemeMode.Dark,
    val uiLogLevel: UiLogLevel = UiLogLevel.Info,
)

fun defaultUiSettingsPath(): NioPath {
    return defaultConfigPath().parent.resolve("ui.ini")
}

fun loadUiSettings(path: NioPath = defaultUiSettingsPath()): UiSettings {
    return runCatching {
        if (!path.exists()) {
            UiSettings()
        } else {
            val values = path.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator == -1) return@mapNotNull null
                    line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                }
                .toMap()

            UiSettings(
                fontScale = values["fontScale"]?.toFloatOrNull()?.coerceIn(MinFontScale, MaxFontScale)
                    ?: DefaultFontScale,
                themeMode = values["themeMode"]?.toEnumOrNull<ThemeMode>() ?: ThemeMode.Dark,
                uiLogLevel = values["uiLogLevel"]?.toEnumOrNull<UiLogLevel>() ?: UiLogLevel.Info,
            )
        }
    }.getOrDefault(UiSettings())
}

fun saveUiSettings(settings: UiSettings, path: NioPath = defaultUiSettingsPath()): Result<Unit> {
    return runCatching {
        path.parent?.createDirectories()
        path.writeText(
            buildString {
                appendLine("fontScale=${settings.fontScale.coerceIn(MinFontScale, MaxFontScale)}")
                appendLine("themeMode=${settings.themeMode.name}")
                appendLine("uiLogLevel=${settings.uiLogLevel.name}")
            }
        )
    }
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
    return enumValues<T>().firstOrNull { it.name.equals(this, ignoreCase = true) }
}
