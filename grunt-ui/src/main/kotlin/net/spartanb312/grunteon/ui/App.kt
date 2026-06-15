package net.spartanb312.grunteon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatDarkLaf
import io.github.composefluent.*
import io.github.composefluent.component.Text
import io.github.composefluent.surface.Card
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.launch
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.SUBTITLE
import net.spartanb312.grunteon.obfuscator.VERSION
import net.spartanb312.grunteon.obfuscator.plugin.PluginManager
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.awt.Frame
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

private val TitleBarCaptionHeight = 48.dp
private val TitleBarCaptionStartInset = 228.dp
private val TitleBarCaptionEndInset = 132.dp

fun main(args: Array<String>) {
    configureWindowDecorations()
    FileKit.init(appId = "Grunteon")
    if (!args.contains("--disablePlugin")) {
        PluginManager.loadPlugins()
    } else {
        PluginManager.freeze()
    }
    application {
        val windowState = rememberWindowState(width = 1600.dp, height = 900.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Grunteon",
            state = windowState,
            icon = painterResource("logo.svg")
        ) {
            App(
                isMaximized = windowState.placement == WindowPlacement.Maximized,
                onMinimize = { window.extendedState = window.extendedState or Frame.ICONIFIED },
                onToggleMaximize = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                onExit = ::exitApplication,
            )
        }
    }
}

private fun configureWindowDecorations() {
    System.setProperty("flatlaf.useWindowDecorations", "true")
    System.setProperty("flatlaf.menuBarEmbedded", "true")
    JFrame.setDefaultLookAndFeelDecorated(true)
    JDialog.setDefaultLookAndFeelDecorated(true)
    FlatDarkLaf.setup()
}

@Composable
fun FrameWindowScope.App(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onExit: () -> Unit,
) {
    FullWindowContentEffect()

    val plugins = remember { PluginManager.plugins }
    var editorReady by remember { mutableStateOf(true) }
    var configPath by remember { mutableStateOf(defaultConfigPath()) }
    var status by remember { mutableStateOf("Choose a config to begin") }
    val coroutineScope = rememberCoroutineScope()

    val appConfigState = remember { mutableStateOf(AppConfig()) }
    var appConfig by appConfigState

    var obfuscationRunning by remember { mutableStateOf(false) }

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

    fun newConfig(path: java.nio.file.Path) {
        openWorkspace(
            config = ObfConfig(),
            path = path,
            message = "New config. Save will write to ${path.toAbsolutePath().normalize()}"
        )
    }

    fun reloadConfig() {
        val loaded = loadConfig(configPath)
        if (loaded.success) {
            openWorkspace(loaded.config, loaded.path, loaded.message)
        } else {
            status = loaded.message
        }
    }

    fun saveConfigTo(path: java.nio.file.Path): Boolean {
        return runCatching {
            ObfConfig.write(obfConfig, path)
        }.onSuccess {
            configPath = path
            status = "Saved ${obfConfig.transformers.size} transformer nodes to ${path.toAbsolutePath().normalize()}"
        }.onFailure {
            status = "Failed to save ${path.toAbsolutePath().normalize()}: ${it.message}"
        }.isSuccess
    }

    fun saveConfig(): Boolean {
        return saveConfigTo(configPath)
    }

    fun openConfigFrom(path: java.nio.file.Path) {
        val loaded = loadConfig(path)
        if (loaded.success) {
            openWorkspace(loaded.config, loaded.path, loaded.message)
        } else {
            status = loaded.message
        }
    }

    fun requestNewConfig() {
        coroutineScope.launch {
            chooseNewConfigPath()?.let(::newConfig)
        }
    }

    fun requestOpenConfig() {
        coroutineScope.launch {
            chooseConfigPath()?.let(::openConfigFrom)
        }
    }

    fun requestSaveConfigAs() {
        coroutineScope.launch {
            chooseSaveConfigPath(configPath)?.let(::saveConfigTo)
        }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) return@onPreviewKeyEvent false
                            when (it.key) {
                                Key.N -> {
                                    requestNewConfig()
                                    true
                                }

                                Key.O -> {
                                    requestOpenConfig()
                                    true
                                }

                                Key.S -> {
                                    if (it.isShiftPressed) requestSaveConfigAs() else saveConfig()
                                    true
                                }

                                else -> false
                            }
                        }
                        .background(FluentTheme.colors.background.mica.base)
                ) {
                    Column(Modifier.fillMaxSize()) {
//                    if (!editorReady) {
//                        WelcomeScreen(
//                            status = status,
//                            onOpenConfig = {
//                                coroutineScope.launch {
//                                    val path = chooseConfigPath()
//                                    if (path != null) {
//                                        val loaded = loadConfig(path)
//                                        if (loaded.success) {
//                                            openWorkspace(loaded.config, loaded.path, loaded.message)
//                                        } else {
//                                            status = loaded.message
//                                        }
//                                    }
//                                }
//                            },
//                            onNewConfig = {
//                                coroutineScope.launch {
//                                    val path = chooseNewConfigPath()
//                                    if (path != null) {
//                                        openWorkspace(
//                                            config = ObfConfig(),
//                                            path = path,
//                                            message = "New config. Save will write to ${
//                                                path.toAbsolutePath().normalize()
//                                            }"
//                                        )
//                                    }
//                                }
//                            },
//                            modifier = Modifier.fillMaxSize()
//                        )
//                        return@ProvideTextStyle
//                    }
                        TopToolbar(
                            uiState = uiState,
                            onNewConfig = ::requestNewConfig,
                            onOpenConfig = ::requestOpenConfig,
                            onSaveConfig = ::saveConfig,
                            onSaveConfigAs = ::requestSaveConfigAs,
                            isMaximized = isMaximized,
                            showWindowControls = true,
                            onMinimize = onMinimize,
                            onToggleMaximize = onToggleMaximize,
                            onExit = onExit,
                        )
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
                                AppPage.General -> GeneralPage(obfConfigState)
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
}

@Composable
private fun FrameWindowScope.FullWindowContentEffect() {
    val captionHeight = with(LocalDensity.current) { TitleBarCaptionHeight.roundToPx() }
    val captionStartInset = with(LocalDensity.current) { TitleBarCaptionStartInset.roundToPx() }
    val captionEndInset = with(LocalDensity.current) { TitleBarCaptionEndInset.roundToPx() }

    DisposableEffect(window, captionHeight, captionStartInset, captionEndInset) {
        var installedHwnd = 0L
        var attempts = 0

        fun applyRootPaneProperties() {
            val rootPane = window.rootPane
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, captionHeight)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, false)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, false)
            rootPane.revalidate()
            rootPane.repaint()
        }

        val installTimer = Timer(50, null).apply {
            initialDelay = 0
            addActionListener {
                applyRootPaneProperties()
                installedHwnd = WindowsCaptionHitTest.installOrUpdate(
                    window = window,
                    captionHeight = captionHeight,
                    captionStartInset = captionStartInset,
                    captionEndInset = captionEndInset,
                )
                if (installedHwnd != 0L) {
                    println("WindowsCaptionHitTest: installed hwnd=0x${installedHwnd.toString(16)}")
                    stop()
                } else if (++attempts >= 40) {
                    println("WindowsCaptionHitTest: failed to install")
                    stop()
                }
            }
        }

        SwingUtilities.invokeLater {
            installTimer.start()
        }

        onDispose {
            installTimer.stop()
            SwingUtilities.invokeLater {
                WindowsCaptionHitTest.uninstall(installedHwnd)
                window.rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICONIFFY, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_MAXIMIZE, null)
                window.rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_CLOSE, null)
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
