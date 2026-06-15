package net.spartanb312.grunteon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spartanb312.grunteon.obfuscator.process.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import kotlin.reflect.KClass
import java.nio.file.Path as NioPath

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

class UIState {
    var globalStatus by mutableStateOf("Ready")
    var pageStatus by mutableStateOf("")
    var currentPage by mutableStateOf(AppPage.General)
}