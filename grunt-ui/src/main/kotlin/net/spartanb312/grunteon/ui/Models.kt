package net.spartanb312.grunteon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Decimal
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

class AppModel(val coroutineScope: CoroutineScope) {
    val uiState = AppUIState()
    var appState by mutableStateOf(AppState())
    var appConfig by mutableStateOf(AppConfig())
    var obfConfig by mutableStateOf(ObfConfig())

    fun openConfig(config: ObfConfig, path: NioPath, message: String) {
        obfConfig = config
        uiState.globalStatus = message
        appState = appState.copy(configPath = path)
    }

    fun newConfig() {
        obfConfig = ObfConfig()
        uiState.globalStatus = "Created new empty config"
        appState = appState.copy(configPath = null)
    }

    fun saveConfig(path: NioPath): Boolean {
        return runCatching {
            ObfConfig.write(obfConfig, path)
        }.onSuccess {
            appState = appState.copy(configPath = path)
            uiState.globalStatus =
                "Saved ${obfConfig.transformers.size} transformer nodes to ${path.toAbsolutePath().normalize()}"
        }.onFailure {
            uiState.globalStatus = "Failed to save ${path.toAbsolutePath().normalize()}: ${it.message}"
        }.isSuccess
    }
}

@Serializable
data class AppState(
    val configPath: NioPath? = null,
)

class AppUIState {
    var globalStatus by mutableStateOf("Ready")
    var pageStatus by mutableStateOf("")
    var currentPage by mutableStateOf(AppPage.General)
}

@Serializable
data class AppConfig(
    val startupAction: StartupAction = StartupAction.Dialog,

    @SettingSection("UI")
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    @SettingName("UI Scale")
    val uiScale: Decimal = Decimal.ONE,
    @DecimalRangeVal(min = 0.5, max = 4.0, step = 0.1)
    val fontScale: Decimal = Decimal.ONE,
    val themeMode: ThemeMode = ThemeMode.Auto,
    val uiLogLevel: UiLogLevel = UiLogLevel.Info,
)

enum class StartupAction {
    Dialog,
    LoadLastConfig,
    NewConfig,
}