package net.spartanb312.grunteon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.github.composefluent.*
import io.github.composefluent.component.Text
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.launch
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.ObfConfig
import net.spartanb312.grunteon.obfuscator.SUBTITLE
import net.spartanb312.grunteon.obfuscator.VERSION
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.util.Logger
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    FileKit.init(appId = "Grunteon")
    if (!args.contains("--disablePlugin")) {
        PluginManager.loadPlugins()
    } else {
        PluginManager.freeze()
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Grunteon",
            state = WindowState(width = 1600.dp, height = 900.dp),
        ) {
            App()
        }
    }
}

@Composable
fun App() {
    val plugins = remember { PluginManager.plugins }
    val uiSettingsPath = remember { defaultUiSettingsPath() }
    val initialUiSettings = remember { loadUiSettings(uiSettingsPath) }
    var editorReady by remember { mutableStateOf(true) }
    var configPath by remember { mutableStateOf(defaultConfigPath()) }
    var status by remember { mutableStateOf("Choose a config to begin") }
    var page by remember { mutableStateOf(AppPage.Editor) }
    var fontScale by remember { mutableStateOf(initialUiSettings.fontScale) }
    var themeMode by remember { mutableStateOf(initialUiSettings.themeMode) }
    var uiLogLevel by remember { mutableStateOf(initialUiSettings.uiLogLevel) }
    var obfuscationRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val baseDensity = LocalDensity.current
    val fluentColors =
        if (themeMode == ThemeMode.Dark) darkColors(Color(0xFF0078D4)) else lightColors(Color(0xFF0078D4))

    val obfuscationLogs = remember { mutableStateListOf<String>() }
    val uiState = remember { UIState() }

    val obfConfigState = remember { mutableStateOf(ObfConfig()) }
    var obfConfig by obfConfigState

    fun openWorkspace(config: ObfConfig, path: java.nio.file.Path, message: String) {
        obfConfig = config
        configPath = path
        status = message
        page = AppPage.General
        editorReady = true
    }

    fun reloadConfig() {
        val loaded = loadConfig(configPath)
        if (loaded.success) {
            openWorkspace(loaded.config, loaded.path, loaded.message)
        } else {
            status = loaded.message
        }
    }

    fun saveConfig() {
        ObfConfig.write(obfConfig, configPath)
        status = "Saved ${obfConfig.transformers.size} transformer nodes to ${configPath.toAbsolutePath().normalize()}"
    }

    fun persistUiSettings(settings: UiSettings) {
        saveUiSettings(settings, uiSettingsPath).onFailure {
            status = "Failed to save UI settings to ${uiSettingsPath.toAbsolutePath().normalize()}: ${it.message}"
        }
    }

    fun updateFontScale(value: Float) {
        val next = UiSettings(
            fontScale = value.coerceIn(MinFontScale, MaxFontScale),
            themeMode = themeMode,
            uiLogLevel = uiLogLevel,
        )
        fontScale = next.fontScale
        persistUiSettings(next)
    }

    fun updateThemeMode(value: ThemeMode) {
        val next = UiSettings(
            fontScale = fontScale,
            themeMode = value,
            uiLogLevel = uiLogLevel,
        )
        themeMode = next.themeMode
        persistUiSettings(next)
    }

    fun updateUiLogLevel(value: UiLogLevel) {
        val next = UiSettings(
            fontScale = fontScale,
            themeMode = themeMode,
            uiLogLevel = value,
        )
        uiLogLevel = next.uiLogLevel
        persistUiSettings(next)
    }

    fun appendObfuscationLog(line: String) {
        SwingUtilities.invokeLater {
            obfuscationLogs.add(line)
        }
    }

    fun runObfuscation() {
        if (obfuscationRunning) return
        val runConfig = obfConfig
        obfuscationLogs.clear()
        obfuscationRunning = true
        status = "Obfuscation started"
        Thread(
            {
                val previousLogger = Logger
                Logger = UiLogger("Grunteon", uiLogLevel, ::appendObfuscationLog)
                try {
                    Logger.info("Starting obfuscation with ${runConfig.transformers.count { it.enabled }} enabled transformer nodes")
                    val instance = Grunteon.create(runConfig)
                    instance.execute()
                    Logger.info("Obfuscation finished")
                    SwingUtilities.invokeLater {
                        status = "Obfuscation finished"
                    }
                } catch (t: Throwable) {
                    Logger.error("Obfuscation failed: ${t.message ?: t::class.qualifiedName}")
                    t.stackTraceToString().lines().forEach { Logger.error(it) }
                    SwingUtilities.invokeLater {
                        status = "Obfuscation failed"
                    }
                } finally {
                    Logger = previousLogger
                    SwingUtilities.invokeLater {
                        obfuscationRunning = false
                    }
                }
            },
            "Obf-Main"
        ).apply {
            isDaemon = true
            start()
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, BaseFontScale * fontScale),
    ) {
        FluentTheme(
            colors = fluentColors,
            typography = Typography(
                caption = FluentTheme.typography.caption.copy(fluentColors.text.text.tertiary),
                body = FluentTheme.typography.body.copy(fluentColors.text.text.primary),
                bodyStrong = FluentTheme.typography.bodyStrong.copy(fluentColors.text.text.primary),
                bodyLarge = FluentTheme.typography.bodyLarge.copy(fluentColors.text.text.primary),
                subtitle = FluentTheme.typography.subtitle.copy(fluentColors.text.text.primary),
                title = FluentTheme.typography.title.copy(fluentColors.text.text.primary),
                titleLarge = FluentTheme.typography.titleLarge.copy(fluentColors.text.text.primary),
                display = FluentTheme.typography.display.copy(fluentColors.text.text.primary)
            )
        ) {
            ProvideTextStyle(FluentTheme.typography.body) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(FluentTheme.colors.background.mica.base)
                        .padding(start = 10.dp, top = 8.dp, end = 10.dp)
                ) {
                    if (!editorReady) {
                        WelcomeScreen(
                            status = status,
                            onOpenConfig = {
                                coroutineScope.launch {
                                    val path = chooseConfigPath()
                                    if (path != null) {
                                        val loaded = loadConfig(path)
                                        if (loaded.success) {
                                            openWorkspace(loaded.config, loaded.path, loaded.message)
                                        } else {
                                            status = loaded.message
                                        }
                                    }
                                }
                            },
                            onNewConfig = {
                                coroutineScope.launch {
                                    val path = chooseNewConfigPath()
                                    if (path != null) {
                                        openWorkspace(
                                            config = ObfConfig(),
                                            path = path,
                                            message = "New config. Save will write to ${
                                                path.toAbsolutePath().normalize()
                                            }"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        return@ProvideTextStyle
                    }

                    TopToolbar(
                        page = page,
                        onPageChange = { page = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (page) {
                            AppPage.General -> GeneralPage(
                                config = obfConfig,
                                status = status,
                                onConfigChange = { obfConfig = it },
                                onReload = ::reloadConfig,
                                onSave = ::saveConfig,
                                modifier = Modifier.fillMaxSize()
                            )

                            AppPage.Editor -> PipelineEditorPage(uiState, obfConfigState)
                            AppPage.Obfuscation -> ObfuscationPage(
                                logs = obfuscationLogs,
                                running = obfuscationRunning,
                                onObfuscate = ::runObfuscation,
                                modifier = Modifier.fillMaxSize()
                            )

                            AppPage.Settings -> SettingsPage(
                                fontScale = fontScale,
                                onFontScaleChange = ::updateFontScale,
                                themeMode = themeMode,
                                onThemeModeChange = ::updateThemeMode,
                                uiLogLevel = uiLogLevel,
                                onUiLogLevelChange = ::updateUiLogLevel,
                                configPath = configPath,
                                uiSettingsPath = uiSettingsPath,
                                status = status,
                                plugins = plugins,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    BottomStatusBar(uiState)
                }
            }
        }
    }
}

@Composable
private fun BottomStatusBar(uiState: UIState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            uiState.globalStatus,
            modifier = Modifier.weight(1f),
        )
        Text(
            uiState.pageStatus,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text(
            "$VERSION [$SUBTITLE]",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}
