package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import java.nio.file.Path as NioPath
import kotlin.reflect.KClass

data class TransformerDefinition(
    val label: String,
    val typeName: String,
    val category: Category,
    val description: String,
    val configClass: KClass<out TransformerConfig>,
    val configFactory: () -> TransformerConfig,
    val transformerFactory: () -> Transformer<*>,
)

data class PipelineNode(
    val id: Long,
    val config: TransformerConfig,
    val collapsed: Boolean = false,
)

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
