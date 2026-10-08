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

/** All public state access and operation entry points belong to the caller's UI dispatcher. */
class AppModel internal constructor(
    private val exitApplication: () -> Unit,
    val coroutineScope: CoroutineScope,
    private val configDirectory: Path,
    private val storage: UiConfigStorage = UiConfigStorage(),
    private val io: ConfigIoQueue = ConfigIoQueue(),
) {
    constructor(appScope: ApplicationScope, coroutineScope: CoroutineScope) :
        this(appScope::exitApplication, coroutineScope, appRuntimeConfigDir())

    // Initialize every revision before a setter or asynchronous startup operation can use it.
    private var appConfigRevision = 0L
    private var appStateRevision = 0L
    private var configRevision = 0L
    private var documentRevision = 0L
    private var loadRequest = 0L
    private var saveRequest = 0L
    private var initialized = false
    private val startupFinished = kotlinx.coroutines.CompletableDeferred<Unit>()
    var isInitializing by mutableStateOf(true)
        private set
    private var appConfigSaveJob: Job? = null
    private var appConfigDirty by mutableStateOf(false)
    private var obfConfigDirty by mutableStateOf(false)
    var languageRevision by mutableStateOf(0L)
        private set
    var isClosing by mutableStateOf(false)
        private set
    internal var beforeExitIo: () -> Unit = {}

    val uiState = AppUIState()
    private var _appState by mutableStateOf(AppState())
    var appState: AppState
        get() = _appState
        set(value) {
            if (_appState != value) {
                _appState = value
                appStateRevision++
            }
        }
    private var _appConfig by mutableStateOf(AppConfig())
    var appConfig: AppConfig
        get() = _appConfig
        set(value) {
            if (_appConfig != value) {
                _appConfig = value
                appConfigRevision++
                updateLanguage(value.language)
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
                configRevision++
                obfConfigDirty = true
            }
        }
    val hasUnsavedChanges: Boolean get() = obfConfigDirty
    val configCommandsEnabled: Boolean get() = !isInitializing && !isClosing

    internal class ConfigSaveIntent(
        val owner: AppModel,
        val request: Long,
        val loadRequest: Long,
        val revision: Long,
        val document: Long,
        val pathRevision: Long,
        val sourcePath: NioPath?,
        val sourceConfig: ObfConfig,
        val snapshot: ObfConfig,
    ) {
        var consumed = false
    }

    class DiscardConfirmState(
        val onSave: () -> Unit,
        val onDiscard: () -> Unit,
        val onCancel: () -> Unit,
    )
    var discardConfirmState by mutableStateOf<DiscardConfirmState?>(null)

    /** Called from LaunchedEffect, not init: first composition must not perform disk IO. */
    suspend fun initialize() {
        if (initialized) {
            startupFinished.await()
            return
        }
        initialized = true
        try {
            val initialRevision = configRevision
            val initialConfig = obfConfig
            val initialSnapshot = configSnapshot(initialConfig)
            val initialDocument = documentRevision
            val initialPathRevision = appStateRevision
            val initialLoadRequest = loadRequest
            val untouchedDocument = initialRevision == 0L && initialDocument == 0L &&
                initialPathRevision == 0L && initialLoadRequest == 0L
            if (appConfigRevision == 0L) loadAppConfig()
            if (!untouchedDocument || isClosing || initialRevision != configRevision || initialDocument != documentRevision ||
                initialPathRevision != appStateRevision || initialLoadRequest != loadRequest
            ) return
            if (!isUnchangedConfig(initialRevision, initialConfig, initialSnapshot)) return
            loadAppState()
            if (isClosing || initialRevision != configRevision || initialDocument != documentRevision ||
                initialLoadRequest != loadRequest
            ) return
            if (!isUnchangedConfig(initialRevision, initialConfig, initialSnapshot)) return
            when (appConfig.startupAction) {
                StartupAction.LoadLastConfig -> {
                    val path = appState.configPath
                    if (path == null) newConfig()
                    else {
                        val revision = configRevision
                        val document = documentRevision
                        val pathRevision = appStateRevision
                        val request = loadRequest + 1
                        if (!openConfig(path) && revision == configRevision && document == documentRevision &&
                            pathRevision == appStateRevision && request == loadRequest && !isClosing
                        ) recoverFromLastConfigLoadFailure(path)
                    }
                }
                StartupAction.NewConfig -> newConfig()
            }
        } finally {
            isInitializing = false
            startupFinished.complete(Unit)
        }
    }

    fun onExit() {
        if (isClosing) return
        if (isInitializing) {
            coroutineScope.launch {
                initialize()
                onExit()
            }
            return
        }
        checkUnsavedChanges {
            val exitingRevision = configRevision
            isClosing = true
            appConfigSaveJob?.cancel()
            coroutineScope.launch {
                // Check revisions after the final suspension too, including a slow full-log flush.
                while (true) {
                    val settingsRevision = appConfigRevision
                    val stateRevision = appStateRevision
                    val saved = saveAppConfig() && saveAppState()
                    io.flush()
                    val flushLog = beforeExitIo // Capture on UI; never read Compose state in the IO task.
                    val flushed = performIo { flushLog() }.onFailure {
                        showCriticalError(uiText(UiText.Dialog.SaveConfigFailedTitle), it.message.orEmpty())
                    }.isSuccess
                    if (!saved || !flushed) {
                        isClosing = false
                        return@launch
                    }
                    if (exitingRevision != configRevision) {
                        isClosing = false
                        onExit() // Ask again if the document changed during the flush.
                        return@launch
                    }
                    if (settingsRevision == appConfigRevision && stateRevision == appStateRevision &&
                        flushLog === beforeExitIo
                    ) {
                        io.stop()
                        exitApplication() // No suspension between the revision check and exit.
                        return@launch
                    }
                }
            }
        }
    }

    fun checkUnsavedChanges(proceed: () -> Unit) {
        if (isClosing) return
        if (hasUnsavedChanges) {
            discardConfirmState = DiscardConfirmState(proceed, proceed, {})
        } else proceed()
    }

    suspend fun loadAppConfig() {
        val revision = appConfigRevision
        val path = configDirectory.resolve("app_config.json")
        val result = performIo { storage.readAppConfig(path) }
        if (revision != appConfigRevision || isClosing) return
        result.onSuccess { replaceAppConfigWithoutDirty(it) }.onFailure {
            reportError(UiText.Status.FailedToLoadAppConfig, path, it)
        }
    }

    suspend fun saveAppConfig(): Boolean {
        val revision = appConfigRevision
        val snapshot = configSnapshot(appConfig)
        val path = configDirectory.resolve("app_config.json")
        return performIo { storage.writeAppConfig(snapshot, path) }.onSuccess {
            if (revision == appConfigRevision) appConfigDirty = false
        }.onFailure {
            reportError(UiText.Status.FailedToSaveAppConfig, path, it)
        }.isSuccess
    }

    suspend fun loadAppState() {
        val revision = appStateRevision
        val document = documentRevision
        val path = configDirectory.resolve("app_state.json")
        val result = performIo { storage.readAppState(path) }
        if (revision != appStateRevision || document != documentRevision || isClosing) return
        result.onSuccess { appState = it }.onFailure {
            reportError(UiText.Status.FailedToLoadAppState, path, it)
        }
    }

    suspend fun saveAppState(): Boolean {
        val snapshot = appState.copy()
        val path = configDirectory.resolve("app_state.json")
        return performIo { storage.writeAppState(snapshot, path) }.onFailure {
            reportError(UiText.Status.FailedToSaveAppState, path, it)
        }.isSuccess
    }

    suspend fun openConfig(path: NioPath): Boolean {
        if (isClosing) return false
        val request = ++loadRequest
        saveRequest++ // A late save may not reselect the document being replaced.
        val revision = configRevision
        val pathRevision = appStateRevision
        val document = documentRevision
        val source = obfConfig
        val snapshot = configSnapshot(source)
        val result = performIo { storage.readConfig(path) }
        if (request != loadRequest || revision != configRevision || document != documentRevision ||
            pathRevision != appStateRevision || isClosing
        ) return false
        if (!isUnchangedConfig(revision, source, snapshot)) return false
        return result.onSuccess { config ->
            documentRevision++
            replaceConfigWithoutDirty(config)
            appState = appState.copy(configPath = path)
            uiState.globalStatus = uiText(UiText.Status.LoadedConfig,
                "count" to config.transformers.size, "path" to path.displayPath())
        }.onFailure {
            reportError(UiText.Status.FailedToLoadConfig, path, it, UiText.Dialog.OpenConfigFailedTitle)
        }.isSuccess
    }

    fun newConfig() {
        if (isClosing) return
        documentRevision++
        loadRequest++
        replaceConfigWithoutDirty(ObfConfig())
        uiState.globalStatus = uiText(UiText.Status.CreatedNewConfig)
        appState = appState.copy(configPath = null)
    }

    private suspend fun recoverFromLastConfigLoadFailure(path: NioPath) {
        documentRevision++
        replaceConfigWithoutDirty(ObfConfig())
        appState = appState.copy(configPath = null)
        uiState.globalStatus = uiText(UiText.Status.FailedToLoadLastConfig, "path" to path.displayPath())
        if (!saveAppState()) println(uiText(UiText.Status.FailedToUpdateAppState, "path" to path.displayPath()))
    }

    /** Capture on UI before scheduling a coroutine or showing a suspendable file chooser. */
    internal fun beginConfigSave(): ConfigSaveIntent? {
        if (!configCommandsEnabled) return null
        val source = obfConfig
        return ConfigSaveIntent(this, ++saveRequest, loadRequest, configRevision, documentRevision,
            appStateRevision, appState.configPath, source, configSnapshot(source))
    }

    internal suspend fun saveConfig(
        intent: ConfigSaveIntent,
        onSaved: () -> Unit = {},
        choosePath: suspend (NioPath?) -> NioPath?,
    ): Boolean {
        if (intent.owner !== this) return false
        try {
            if (intent.consumed || !isCurrentSave(intent, intent.loadRequest) || !isUnchangedSaveContent(intent)) return false
            val path = choosePath(intent.sourcePath) ?: return false
            val saved = saveConfig(intent, path)
            // No suspension between the post-write guard and save-and-continue, before any Deferred completes.
            if (saved) onSaved()
            return saved
        } finally {
            intent.consumed = true
        }
    }

    suspend fun saveConfig(path: NioPath): Boolean {
        val intent = beginConfigSave() ?: return false
        return saveConfig(intent, path)
    }

    private suspend fun saveConfig(intent: ConfigSaveIntent, path: NioPath): Boolean {
        if (intent.consumed) return false
        intent.consumed = true
        // A load may have completed, or edits/new commands may have arrived, while the picker was open.
        // Reject before enqueueing IO: even a stale write to the chosen destination would lose user data.
        if (!isCurrentSave(intent, intent.loadRequest) || !isUnchangedSaveContent(intent)) return false
        val acceptedLoadRequest = ++loadRequest
        val snapshot = intent.snapshot
        val result = performIo { storage.writeConfig(snapshot, path) }
        if (!isCurrentSave(intent, acceptedLoadRequest)) return false
        val unchanged = isUnchangedSaveContent(intent)
        result.onSuccess {
            appState = appState.copy(configPath = path)
            uiState.globalStatus = uiText(UiText.Status.SavedConfig,
                "count" to snapshot.transformers.size, "path" to path.displayPath())
            if (unchanged) obfConfigDirty = false
        }.onFailure {
            reportError(UiText.Status.FailedToSaveConfig, path, it, UiText.Dialog.SaveConfigFailedTitle)
        }
        // Check both revision and content: public nested lists/arrays can change without using the setter.
        return result.isSuccess && unchanged
    }

    private fun isCurrentSave(intent: ConfigSaveIntent, expectedLoadRequest: Long): Boolean =
        intent.owner === this && configCommandsEnabled && intent.request == saveRequest &&
            expectedLoadRequest == loadRequest && intent.document == documentRevision &&
            intent.pathRevision == appStateRevision

    private fun isUnchangedSaveContent(intent: ConfigSaveIntent): Boolean =
        isUnchangedConfig(intent.revision, intent.sourceConfig, intent.snapshot)

    private fun isUnchangedConfig(revision: Long, source: ObfConfig, snapshot: ObfConfig): Boolean {
        val sameIdentity = revision == configRevision && source === obfConfig
        val unchanged = sameIdentity && configContentEquals(obfConfig, snapshot)
        if (!unchanged) {
            // Recognized in-place edits must also invalidate other pending revision-based operations.
            if (sameIdentity) configRevision++
            obfConfigDirty = true
        }
        return unchanged
    }

    private fun scheduleAppConfigSave() {
        appConfigSaveJob?.cancel()
        if (isClosing) return // onExit owns the final revision/flush loop.
        appConfigSaveJob = coroutineScope.launch {
            delay(AppConfigSaveDebounceMillis)
            saveAppConfig()
        }
    }

    private fun updateLanguage(language: Language) {
        if (I18n.currentLanguage != language) {
            I18n.setLanguage(language)
            languageRevision++
        }
    }

    private fun replaceAppConfigWithoutDirty(config: AppConfig) {
        _appConfig = config
        appConfigRevision++
        updateLanguage(config.language)
        appConfigDirty = false
    }

    private fun replaceConfigWithoutDirty(config: ObfConfig) {
        _obfConfig = config
        configRevision++
        obfConfigDirty = false
    }

    private suspend fun <T> performIo(operation: () -> T): Result<T> = try {
        Result.success(io.submit(operation).await())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun reportError(
        descriptor: net.spartanb312.grunteon.obfuscator.lang.I18nDescriptor,
        path: Path,
        error: Throwable,
        title: net.spartanb312.grunteon.obfuscator.lang.I18nDescriptor? = null,
    ) {
        val message = uiText(descriptor, "path" to path.displayPath(),
            "message" to (error.message ?: error::class.qualifiedName))
        uiState.globalStatus = message
        println(message)
        if (title != null || isClosing) showCriticalError(uiText(title ?: UiText.Dialog.SaveConfigFailedTitle), message)
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
            atomicConfigWrite(path) { it.writeText(jsonString) }
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
            atomicConfigWrite(path) { it.writeText(jsonString) }
        }
    }
}

enum class StartupAction {
    // Dialog, TODO: need to do a homepage
    LoadLastConfig,
    NewConfig,
}