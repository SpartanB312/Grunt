package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import java.nio.file.Path
import kotlin.io.path.exists

/** Injectable disk boundary so model tests never touch the real user configuration directory. */
internal open class UiConfigStorage {
    open fun readAppConfig(path: Path): AppConfig = if (path.exists()) AppConfig.read(path) else AppConfig()
    open fun readAppState(path: Path): AppState = if (path.exists()) AppState.read(path) else AppState()
    open fun readConfig(path: Path): ObfConfig = ObfConfig.read(path)
    open fun writeAppConfig(config: AppConfig, path: Path) = AppConfig.write(config, path)
    open fun writeAppState(state: AppState, path: Path) = AppState.write(state, path)
    open fun writeConfig(config: ObfConfig, path: Path) = atomicConfigWrite(path) { ObfConfig.write(config, it) }
}