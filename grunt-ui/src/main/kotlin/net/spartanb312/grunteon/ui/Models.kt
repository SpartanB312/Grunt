package net.spartanb312.grunteon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass

class TransformerDefinition(
    val label: String,
    val typeName: String,
    val category: Category,
    val description: String,
    val owner: String,
    val isHidden: Boolean,
    val configClass: KClass<out TransformerConfig>,
    val configFactory: () -> TransformerConfig,
    val transformerPrototype: Transformer<*>,
) {
    val isPluginProvided: Boolean
        get() = owner != "grunteon"
}

data class ConfigLoadResult(
    val config: ObfConfig,
    val path: NioPath,
    val message: String,
    val success: Boolean,
)

enum class AppPage {
    General,
    Editor,
    Obfuscation,
    Settings,
}

class AppModel(private val appScope: ApplicationScope, val coroutineScope: CoroutineScope) {
    val uiState = AppUIState()
    var appState by mutableStateOf(AppState())
    var appConfig by mutableStateOf(AppConfig())
    var obfConfig by mutableStateOf(ObfConfig())

    private val lastSaved = AtomicInteger(Int.MIN_VALUE)
    private val changeCounter = AtomicInteger(Int.MIN_VALUE)
    val hasUnsavedChanges: Boolean
        get() = lastSaved.get() != changeCounter.get()

    class DiscardConfirmState(
        val onSave: () -> Unit,
        val onDiscard: () -> Unit,
        val onCancel: () -> Unit
    )

    var discardConfirmState by mutableStateOf<DiscardConfirmState?>(null)

    init {
        loadAppConfig()
        loadAppState()
        changeCounter.incrementAndGet()
        when (appConfig.startupAction) {
            StartupAction.LoadLastConfig -> {
                appState.configPath?.let {
                    openConfig(it)
                } ?: run {
                    newConfig()
                }
            }
            StartupAction.NewConfig -> {
                newConfig()
            }
        }
        var localChangeCounter = Int.MIN_VALUE
        coroutineScope.launch {
            snapshotFlow { obfConfig }
                .collect {
                    localChangeCounter = changeCounter.compareAndExchange(localChangeCounter, localChangeCounter + 1)
                }
        }
    }

    fun onExit() {
        checkUnsavedChanges {
            saveAppConfig()
            saveAppState()
            appScope.exitApplication()
        }
    }

    fun checkUnsavedChanges(proceed: () -> Unit) {
        if (hasUnsavedChanges) {
            discardConfirmState = DiscardConfirmState(
                onSave = proceed,
                onDiscard = proceed,
                onCancel = {}
            )
        } else {
            proceed()
        }
    }

    fun loadAppConfig() {
        runCatching {
            AppConfig.read(Path("app_config.json"))
        }.onSuccess {
            appConfig = it
        }.onFailure {
            uiState.globalStatus = "Failed to load app config: ${it.message}"
            println("Failed to load app config: ${it.message}")
        }
    }

    fun saveAppConfig() {
        runCatching {
            AppConfig.write(appConfig, Path("app_config.json"))
        }.onFailure {
            println("Failed to save app config: ${it.message}")
        }
    }

    fun loadAppState() {
        runCatching {
            AppState.read(Path("app_state.json"))
        }.onSuccess {
            appState = it
        }.onFailure {
            uiState.globalStatus = "Failed to load app state: ${it.message}"
            println("Failed to load app state: ${it.message}")
        }
    }

    fun saveAppState() {
        runCatching {
            AppState.write(appState, Path("app_state.json"))
        }.onFailure {
            uiState.globalStatus = "Failed to save app state: ${it.message}"
            println("Failed to save app state: ${it.message}")
        }
    }

    fun openConfig(path: NioPath): Boolean {
        val loaded = loadConfig(path)
        if (loaded.success) {
            obfConfig = loaded.config
            uiState.globalStatus = loaded.message
            appState = appState.copy(configPath = path)
            resetCounter()
        } else {
            uiState.globalStatus = loaded.message
        }
        return loaded.success
    }

    fun newConfig() {
        obfConfig = ObfConfig()
        uiState.globalStatus = "Created new empty config"
        appState = appState.copy(configPath = null)
        resetCounter()
    }

    fun saveConfig(path: NioPath): Boolean {
        return runCatching {
            ObfConfig.write(obfConfig, path)
        }.onSuccess {
            appState = appState.copy(configPath = path)
            uiState.globalStatus =
                "Saved ${obfConfig.transformers.size} transformer nodes to ${path.toAbsolutePath().normalize()}"
            resetCounter()
        }.onFailure {
            uiState.globalStatus = "Failed to save ${path.toAbsolutePath().normalize()}: ${it.message}"
        }.isSuccess
    }

    private fun resetCounter() {
        lastSaved.set(changeCounter.get())
    }
}

@Serializable
data class AppState(
    val configPath: NioPath? = null,
) {
    companion object {
        private fun json() = Json {
            prettyPrint = true
            encodeDefaults = true
            prettyPrintIndent = "    "
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun read(path: Path): AppState {
            return json().decodeFromString(path.readText())
        }

        fun write(state: AppState, path: Path) {
            val jsonString = json().encodeToString(state)
            path.writeText(jsonString)
        }
    }
}

class AppUIState {
    var globalStatus by mutableStateOf("Ready")
    var pageStatus by mutableStateOf("")
    var currentPage by mutableStateOf(AppPage.General)
}

@Serializable
data class AppConfig(
    val startupAction: StartupAction = StartupAction.LoadLastConfig,

    @SettingSection("UI")
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    @SettingName("UI Scale")
    val uiScale: Decimal = Decimal.ONE,
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    val fontScale: Decimal = Decimal.ONE,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val uiLogLevel: UiLogLevel = UiLogLevel.Info,
) {
    companion object {
        private fun json() = Json {
            prettyPrint = true
            encodeDefaults = true
            prettyPrintIndent = "    "
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun read(path: Path): AppConfig {
            return json().decodeFromString(path.readText())
        }

        fun write(config: AppConfig, path: Path) {
            val jsonString = json().encodeToString(config)
            path.writeText(jsonString)
        }
    }
}

enum class StartupAction {
    // Dialog, TODO: need to do a homepage
    LoadLastConfig,
    NewConfig,
}