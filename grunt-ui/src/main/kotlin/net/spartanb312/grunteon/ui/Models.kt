package net.spartanb312.grunteon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.Language
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass

private const val AppConfigSaveDebounceMillis = 500L
private const val AppRuntimeDirectoryName = "Grunteon"

private fun appRuntimeConfigDir(): Path {
    val userHome = System.getProperty("user.home", ".")
    val osName = System.getProperty("os.name", "").lowercase()
    val baseDir = when {
        osName.contains("win") -> System.getenv("APPDATA")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path(it) }
            ?: Path(userHome, "AppData", "Roaming")
        osName.contains("mac") -> Path(userHome, "Library", "Application Support")
        else -> System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path(it) }
            ?: Path(userHome, ".config")
    }
    return baseDir.resolve(AppRuntimeDirectoryName)
}

private fun appConfigPath(): Path = appRuntimeConfigDir().resolve("app_config.json")

private fun appStatePath(): Path = appRuntimeConfigDir().resolve("app_state.json")

private fun Path.ensureParentDirectory() {
    parent?.createDirectories()
}

private fun Path.displayPath(): String = toAbsolutePath().normalize().toString()

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
    val descriptorRoot: String,
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
    Native,
    Obfuscation,
    Settings,
}

class AppModel(private val appScope: ApplicationScope, val coroutineScope: CoroutineScope) {
    val uiState = AppUIState()
    var appState by mutableStateOf(AppState())
    private var _appConfig by mutableStateOf(AppConfig())
    var appConfig: AppConfig
        get() = _appConfig
        set(value) {
            if (_appConfig != value) {
                _appConfig = value
                I18n.setLanguage(value.language)
                appConfigDirty = true
                scheduleAppConfigSave()
            }
        }
    private var _obfConfig by mutableStateOf(ObfConfig())
    var obfConfig: ObfConfig
        get() = _obfConfig
        set(value) {
            if (_obfConfig != value) {
                _obfConfig = value
                obfConfigDirty = true
            }
        }

    private var appConfigDirty by mutableStateOf(false)
    private var appConfigSaveJob: Job? = null
    private var obfConfigDirty by mutableStateOf(false)
    val hasUnsavedChanges: Boolean
        get() = obfConfigDirty

    class DiscardConfirmState(
        val onSave: () -> Unit,
        val onDiscard: () -> Unit,
        val onCancel: () -> Unit
    )

    var discardConfirmState by mutableStateOf<DiscardConfirmState?>(null)

    init {
        loadAppConfig()
        loadAppState()
        when (appConfig.startupAction) {
            StartupAction.LoadLastConfig -> {
                appState.configPath?.let { path ->
                    if (!openConfig(path)) {
                        recoverFromLastConfigLoadFailure(path)
                    }
                } ?: run {
                    newConfig()
                }
            }
            StartupAction.NewConfig -> {
                newConfig()
            }
        }
    }

    fun onExit() {
        checkUnsavedChanges {
            appConfigSaveJob?.cancel()
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
        val path = appConfigPath()
        if (!path.exists()) {
            replaceAppConfigWithoutDirty(AppConfig())
            return
        }
        runCatching {
            AppConfig.read(path)
        }.onSuccess {
            replaceAppConfigWithoutDirty(it)
        }.onFailure {
            val message = uiText(
                UiText.Status.FailedToLoadAppConfig,
                "path" to path.displayPath(),
                "message" to (it.message ?: it::class.qualifiedName)
            )
            uiState.globalStatus = message
            println(message)
        }
    }

    fun saveAppConfig(): Boolean {
        val path = appConfigPath()
        val configToSave = appConfig
        return runCatching {
            AppConfig.write(configToSave, path)
        }.onSuccess {
            if (_appConfig == configToSave) {
                appConfigDirty = false
            }
        }.onFailure {
            println(
                uiText(
                    UiText.Status.FailedToSaveAppConfig,
                    "path" to path.displayPath(),
                    "message" to (it.message ?: it::class.qualifiedName)
                )
            )
        }.isSuccess
    }

    fun loadAppState() {
        val path = appStatePath()
        if (!path.exists()) {
            appState = AppState()
            return
        }
        runCatching {
            AppState.read(path)
        }.onSuccess {
            appState = it
        }.onFailure {
            val message = uiText(
                UiText.Status.FailedToLoadAppState,
                "path" to path.displayPath(),
                "message" to (it.message ?: it::class.qualifiedName)
            )
            uiState.globalStatus = message
            println(message)
        }
    }

    fun saveAppState(): Boolean {
        val path = appStatePath()
        return runCatching {
            AppState.write(appState, path)
        }.onFailure {
            println(
                uiText(
                    UiText.Status.FailedToSaveAppState,
                    "path" to path.displayPath(),
                    "message" to (it.message ?: it::class.qualifiedName)
                )
            )
        }.isSuccess
    }

    fun openConfig(path: NioPath): Boolean {
        val loaded = loadConfig(path)
        if (loaded.success) {
            replaceConfigWithoutDirty(loaded.config)
            uiState.globalStatus = loaded.message
            appState = appState.copy(configPath = path)
        } else {
            uiState.globalStatus = loaded.message
            showCriticalError(uiText(UiText.Dialog.OpenConfigFailedTitle), loaded.message)
        }
        return loaded.success
    }

    fun newConfig() {
        replaceConfigWithoutDirty(ObfConfig())
        uiState.globalStatus = uiText(UiText.Status.CreatedNewConfig)
        appState = appState.copy(configPath = null)
    }

    private fun recoverFromLastConfigLoadFailure(path: NioPath) {
        val failedPath = path.toAbsolutePath().normalize()
        replaceConfigWithoutDirty(ObfConfig())
        appState = appState.copy(configPath = null)
        val stateSaved = saveAppState()
        uiState.globalStatus = buildString {
            append(uiText(UiText.Status.FailedToLoadLastConfig, "path" to failedPath))
            if (!stateSaved) {
                println(uiText(UiText.Status.FailedToUpdateAppState, "path" to failedPath))
            }
        }
    }

    fun saveConfig(path: NioPath): Boolean {
        val configToSave = obfConfig
        return runCatching {
            ObfConfig.write(configToSave, path)
        }.onSuccess {
            appState = appState.copy(configPath = path)
            uiState.globalStatus = uiText(
                UiText.Status.SavedConfig,
                "count" to configToSave.transformers.size,
                "path" to path.toAbsolutePath().normalize()
            )
            if (_obfConfig == configToSave) {
                obfConfigDirty = false
            }
        }.onFailure {
            val message = uiText(
                UiText.Status.FailedToSaveConfig,
                "path" to path.toAbsolutePath().normalize(),
                "message" to (it.message ?: it::class.qualifiedName)
            )
            uiState.globalStatus = message
            showCriticalError(uiText(UiText.Dialog.SaveConfigFailedTitle), message)
        }.isSuccess
    }

    private fun scheduleAppConfigSave() {
        appConfigSaveJob?.cancel()
        appConfigSaveJob = coroutineScope.launch {
            delay(AppConfigSaveDebounceMillis)
            saveAppConfig()
        }
    }

    private fun replaceAppConfigWithoutDirty(config: AppConfig) {
        _appConfig = config
        I18n.setLanguage(config.language)
        appConfigDirty = false
    }

    private fun replaceConfigWithoutDirty(config: ObfConfig) {
        _obfConfig = config
        obfConfigDirty = false
    }

    private fun showCriticalError(title: String, message: String) {
        uiState.errorDialog = AppErrorDialog(title, message)
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
            path.ensureParentDirectory()
            path.writeText(jsonString)
        }
    }
}

data class AppErrorDialog(
    val title: String,
    val message: String,
)

class AppUIState {
    var globalStatus by mutableStateOf(uiText(UiText.Status.Ready))
    var currentPage by mutableStateOf(AppPage.General)
    var errorDialog by mutableStateOf<AppErrorDialog?>(null)
}

@Serializable
data class AppConfig(
    @SettingDesc("Action performed when the UI starts")
    @SettingName("Startup Action")
    val startupAction: StartupAction = StartupAction.LoadLastConfig,

    @SettingSection("UI")
    @SettingDesc("Language used by the UI")
    @SettingName("Language")
    val language: Language = Language.English,
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    @SettingDesc("Scale factor applied to the whole UI layout")
    @SettingName("UI Scale")
    val uiScale: Decimal = Decimal.ONE,
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    @SettingDesc("Scale factor applied to UI text")
    @SettingName("Font Scale")
    val fontScale: Decimal = Decimal.ONE,
    @SettingDesc("Theme preference used by the UI")
    @SettingName("Theme Mode")
    val themeMode: ThemeMode = ThemeMode.Auto,
    @SettingDesc("Minimum log level shown in the obfuscation log panel")
    @SettingName("Ui Log Level")
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
            path.ensureParentDirectory()
            path.writeText(jsonString)
        }
    }
}

enum class StartupAction {
    // Dialog, TODO: need to do a homepage
    LoadLastConfig,
    NewConfig,
}
