package net.spartanb312.grunteon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.github.composefluent.*
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
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
    var editorReady by remember { mutableStateOf(true) }
    var configPath by remember { mutableStateOf(defaultConfigPath()) }
    var status by remember { mutableStateOf("Choose a config to begin") }

    val appConfigState = remember { mutableStateOf(AppConfig()) }
    var appConfig by appConfigState

    var obfuscationRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    val darkMode = when (appConfig.themeMode) {
        Auto -> isSystemInDarkTheme()
        Light -> false
        Dark -> true
    }

    val obfuscationLogs = remember { mutableStateListOf<String>() }
    val uiState = remember { UIState() }

    val obfConfigState = remember { mutableStateOf(ObfConfig()) }
    var obfConfig by obfConfigState

    fun openWorkspace(config: ObfConfig, path: java.nio.file.Path, message: String) {
        obfConfig = config
        configPath = path
        status = message
        uiState.currentPage = AppPage.General
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
                Logger = UiLogger("Grunteon", appConfig.uiLogLevel, ::appendObfuscationLog)
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

    val pipelineEditorState = remember { PipelineEditorState(uiState, obfConfigState) }

    CompositionLocalProvider(
        LocalDensity provides Density(
            LocalDensity.current.density * appConfig.uiScale.toFloat(),
            appConfig.fontScale.toFloat()
        ),
    ) {
        val fluentColors = if (darkMode) darkColors() else lightColors()
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
                        .padding(top = 4.dp)
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
                    TopToolbar(uiState)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(FluentTheme.colors.background.solid.tertiary),
                    ) {
                        Box(
                            Modifier.weight(1f)
                                .padding(8.dp)
                        ) {
                            when (uiState.currentPage) {
                                AppPage.General -> GeneralPage(
                                    config = obfConfig,
                                    status = status,
                                    onConfigChange = { obfConfig = it },
                                    onReload = ::reloadConfig,
                                    onSave = ::saveConfig,
                                    modifier = Modifier.fillMaxSize()
                                )

                                AppPage.Editor -> PipelineEditorPage(pipelineEditorState)
                                AppPage.Obfuscation -> ObfuscationPage(
                                    logs = obfuscationLogs,
                                    running = obfuscationRunning,
                                    onObfuscate = ::runObfuscation,
                                    modifier = Modifier.fillMaxSize()
                                )
                                AppPage.Settings -> SettingsPage(
                                    appConfigState,
                                    plugins
                                )
                            }
                        }
                        BottomStatusBar(uiState)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomStatusBar(uiState: UIState) {
    Card(Modifier.fillMaxWidth(), shape = FluentTheme.shapes.intersectionEdge) {
        Row(
            modifier = Modifier
                .padding(8.dp),
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
}
