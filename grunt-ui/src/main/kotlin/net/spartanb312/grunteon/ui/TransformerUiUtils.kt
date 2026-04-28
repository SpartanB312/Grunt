package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

fun copyWith(value: Any, parameter: KParameter, newValue: Any?): Any {
    val constructor = value::class.primaryConstructor ?: return value
    val properties = value::class.memberProperties.associateBy { it.name }
    val args = constructor.parameters.associateWith { param ->
        if (param == parameter) newValue else properties[param.name]?.getter?.call(value)
    }
    return constructor.callBy(args).also { copied ->
        if (value is TransformerConfig && copied is TransformerConfig) {
            copied.enabled = value.enabled
        }
    }
}

fun cloneConfig(config: TransformerConfig): TransformerConfig {
    return copyWithAllConstructorValues(config) as TransformerConfig
}

fun TransformerConfig.withEnabled(enabled: Boolean): TransformerConfig {
    return cloneConfig(this).also { it.enabled = enabled }
}

private fun copyWithAllConstructorValues(value: Any): Any {
    val constructor = value::class.primaryConstructor ?: return value
    val properties = value::class.memberProperties.associateBy { it.name }
    val args = constructor.parameters.associateWith { param ->
        val current = properties[param.name]?.getter?.call(value)
        if (current is TransformerConfig || current is ClassFilterConfig) copyWithAllConstructorValues(current) else current
    }
    return constructor.callBy(args).also { copied ->
        if (value is TransformerConfig && copied is TransformerConfig) {
            copied.enabled = value.enabled
        }
    }
}

fun findDefinition(
    config: TransformerConfig,
    definitions: List<TransformerDefinition>,
): TransformerDefinition? = definitions.firstOrNull { it.configClass == config::class }

fun validateOrder(
    nodes: List<PipelineNode>,
    definitions: List<TransformerDefinition>,
): Map<Long, String> {
    val enabled = nodes.filter { it.config.enabled }
    val transformers = enabled.map { node ->
        findDefinition(node.config, definitions)?.transformerFactory?.invoke()
    }
    val warnings = mutableMapOf<Long, String>()
    enabled.forEachIndexed { index, node ->
        val transformer = transformers[index] ?: return@forEachIndexed
        val typedTransformers = transformers.filterNotNull()
        transformer.orderRules.forEach { (rule, message) ->
            if (!rule(typedTransformers, index)) {
                warnings[node.id] = message
                return@forEach
            }
        }
    }
    return warnings
}

fun isVirtualMappingApplierPosition(
    index: Int,
    nodes: List<PipelineNode>,
    definitions: List<TransformerDefinition>,
): Boolean {
    val renamerIndices = nodes.mapIndexedNotNull { nodeIndex, node ->
        val definition = findDefinition(node.config, definitions)
        if (node.config.enabled && definition?.category == Category.Renaming) nodeIndex else null
    }
    return renamerIndices.lastOrNull() == index
}

fun transformerDefinitions(): List<TransformerDefinition> {
    return TransformerRegistry.entries.map { entry ->
        val transformer = entry.createTransformer()
        val config = entry.createConfig()
        TransformerDefinition(
            label = transformer.engName,
            typeName = config::class.qualifiedName.orEmpty(),
            category = transformer.category,
            description = transformer.description.findOrDefault(Languages.English),
            configClass = entry.configClass,
            configFactory = entry.createConfig,
            transformerFactory = entry.createTransformer,
        )
    }.sortedWith(compareBy<TransformerDefinition> { it.category.ordinal }.thenBy { it.label })
}
