package net.spartanb312.grunteon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.launch
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.ObfConfig
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
            state = WindowState(width = 1440.dp, height = 860.dp),
        ) {
            App()
        }
    }
}

@Composable
fun App() {
    val definitions = remember { transformerDefinitions() }
    val plugins = remember { PluginManager.plugins }
    val uiSettingsPath = remember { defaultUiSettingsPath() }
    val initialUiSettings = remember { loadUiSettings(uiSettingsPath) }
    var editorReady by remember { mutableStateOf(false) }
    var baseConfig by remember { mutableStateOf(ObfConfig()) }
    var configPath by remember { mutableStateOf(defaultConfigPath()) }
    var selectedNodeId by remember { mutableStateOf<Long?>(null) }
    var nextNodeId by remember { mutableLongStateOf(1L) }
    var status by remember { mutableStateOf("Choose a config to begin") }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(AppPage.Editor) }
    var fontScale by remember { mutableStateOf(initialUiSettings.fontScale) }
    var themeMode by remember { mutableStateOf(initialUiSettings.themeMode) }
    var uiLogLevel by remember { mutableStateOf(initialUiSettings.uiLogLevel) }
    var obfuscationRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val baseDensity = LocalDensity.current
    val palette = if (themeMode == ThemeMode.Dark) DarkPalette else LightPalette
    val nodes = remember { mutableStateListOf<PipelineNode>() }
    val obfuscationLogs = remember { mutableStateListOf<String>() }

    fun openWorkspace(config: ObfConfig, path: java.nio.file.Path, message: String) {
        baseConfig = config
        configPath = path
        nodes.clear()
        config.transformerConfigs.forEach { transformerConfig ->
            nodes.add(PipelineNode(nextNodeId++, transformerConfig))
        }
        selectedNodeId = nodes.firstOrNull()?.id
        status = message
        page = AppPage.General
        editorReady = true
    }

    fun selectedIndex(): Int = nodes.indexOfFirst { it.id == selectedNodeId }

    fun selectFallback() {
        if (selectedNodeId == null || nodes.none { it.id == selectedNodeId }) {
            selectedNodeId = nodes.firstOrNull()?.id
        }
    }

    fun addNode(definition: TransformerDefinition) {
        val node = PipelineNode(nextNodeId++, definition.configFactory())
        val selectedIndex = selectedIndex()
        if (selectedIndex != -1) {
            nodes.add(selectedIndex + 1, node)
        } else {
            nodes.add(node)
        }
        selectedNodeId = node.id
        status = "Added ${definition.label}"
    }

    fun updateSelectedConfig(config: net.spartanb312.grunteon.obfuscator.process.TransformerConfig) {
        val index = selectedIndex()
        if (index != -1) {
            val node = nodes[index]
            nodes[index] = node.copy(config = config, revision = node.revision + 1)
        }
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
        val output = baseConfig.copy(
            transformerConfigs = nodes.map { it.config }
        )
        ObfConfig.write(output, configPath)
        baseConfig = output
        status =
            "Saved ${output.transformerConfigs.size} transformer nodes to ${configPath.toAbsolutePath().normalize()}"
    }

    fun currentConfig(): ObfConfig = baseConfig.copy(transformerConfigs = nodes.map { it.config })

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
        val runConfig = currentConfig()
        obfuscationLogs.clear()
        obfuscationRunning = true
        status = "Obfuscation started"
        Thread(
            {
                val previousLogger = Logger
                Logger = UiLogger("Grunteon", uiLogLevel, ::appendObfuscationLog)
                try {
                    Logger.info("Starting obfuscation with ${runConfig.transformerConfigs.count { it.enabled }} enabled transformer nodes")
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

    if (editorReady) selectFallback()

    val orderWarnings = remember(nodes.map { Triple(it.config::class, it.config.enabled, it.revision) }) {
        validateOrder(nodes, definitions)
    }

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, BaseFontScale * fontScale),
        LocalUiPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = if (themeMode == ThemeMode.Dark) {
                darkColorScheme(
                    background = palette.background,
                    surface = palette.panel,
                    surfaceVariant = palette.panelAlt,
                    onBackground = palette.text,
                    onSurface = palette.text,
                    onSurfaceVariant = palette.muted,
                    primary = palette.accent,
                )
            } else {
                lightColorScheme(
                    background = palette.background,
                    surface = palette.panel,
                    surfaceVariant = palette.panelAlt,
                    onBackground = palette.text,
                    onSurface = palette.text,
                    onSurfaceVariant = palette.muted,
                    primary = palette.accent,
                )
            },
            shapes = UiShapes,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .padding(16.dp)
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
                                        message = "New config. Save will write to ${path.toAbsolutePath().normalize()}"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    return@MaterialTheme
                }

                TopToolbar(
                    page = page,
                    onPageChange = { page = it },
                    fontScale = fontScale,
                )
                Spacer(Modifier.height(14.dp))
                when (page) {
                    AppPage.General -> GeneralPage(
                        config = baseConfig,
                        status = status,
                        onConfigChange = { baseConfig = it },
                        onReload = ::reloadConfig,
                        onSave = ::saveConfig,
                        modifier = Modifier.fillMaxSize()
                    )

                    AppPage.Editor -> {
                        Header(
                            nodeCount = nodes.size,
                            enabledCount = nodes.count { it.config.enabled },
                            warningCount = orderWarnings.size,
                            status = status,
                            onReload = ::reloadConfig,
                            onSave = ::saveConfig,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TransformerLibrary(
                                definitions = definitions,
                                showHiddenTransformers = baseConfig.showHiddenTransformers,
                                search = search,
                                onSearchChange = { search = it },
                                onAdd = ::addNode,
                                modifier = Modifier.width(290.dp).fillMaxHeight()
                            )
                            PipelineStack(
                                nodes = nodes,
                                definitions = definitions,
                                selectedNodeId = selectedNodeId,
                                orderWarnings = orderWarnings,
                                onSelect = { selectedNodeId = it },
                                onMove = { from, to ->
                                    if (from in nodes.indices && to in nodes.indices) {
                                        val node = nodes.removeAt(from)
                                        nodes.add(to, node)
                                        selectedNodeId = node.id
                                    }
                                },
                                onDuplicate = { index ->
                                    val current = nodes[index]
                                    val copy = current.copy(id = nextNodeId++, config = cloneConfig(current.config))
                                    nodes.add(index + 1, copy)
                                    selectedNodeId = copy.id
                                },
                                onDelete = { index ->
                                    val removed = nodes.removeAt(index)
                                    if (selectedNodeId == removed.id) {
                                        selectedNodeId = nodes.getOrNull(index)?.id ?: nodes.lastOrNull()?.id
                                    }
                                },
                                onEnabledChange = { nodeId, enabled ->
                                    val index = nodes.indexOfFirst { it.id == nodeId }
                                    if (index != -1) {
                                        val node = nodes[index]
                                        nodes[index] = node.copy(
                                            config = node.config.withEnabled(enabled),
                                            revision = node.revision + 1
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            Inspector(
                                node = nodes.firstOrNull { it.id == selectedNodeId },
                                definition = nodes.firstOrNull { it.id == selectedNodeId }
                                    ?.let { findDefinition(it.config, definitions) },
                                onConfigChange = ::updateSelectedConfig,
                                modifier = Modifier.width(390.dp).fillMaxHeight()
                            )
                        }
                    }

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
        }
    }
}
