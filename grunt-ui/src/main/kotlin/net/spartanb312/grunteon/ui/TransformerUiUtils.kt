package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.process.*

fun findDefinition(
    config: TransformerConfig,
    definitions: List<TransformerDefinition>,
): TransformerDefinition? = definitions.find { it.configClass == config::class }

fun validateOrder(
    nodes: List<TransformerEntry>,
    definitions: List<TransformerDefinition>,
): Map<Int, List<String>> {
    val enabled = nodes.filter { it.enabled }
    val transformers = enabled.map { entry ->
        findDefinition(entry.config, definitions)?.transformerPrototype
    }
    val typedTransformers = transformers.filterNotNull()
    return enabled.indices
        .map { index ->
            index to (transformers[index]?.orderRules?.filter { (rule, message) ->
                !rule(typedTransformers, index)
            }?.map {
                it.second
            } ?: emptyList())
        }
        .filter { it.second.isNotEmpty() }
        .toMap()
}

fun transformerDefinitions(): List<TransformerDefinition> {
    return TransformerRegistry.entries.map { entry ->
        val transformer = entry.transformerPrototype
        TransformerDefinition(
            label = transformer.engName,
            typeName = entry.configClass.qualifiedName.orEmpty(),
            category = transformer.category,
            description = transformer.descriptionText(),
            owner = entry.owner,
            isHidden = transformer.isHiddenTransformer(),
            configClass = entry.configClass,
            configFactory = entry.createConfig,
            transformerPrototype = transformer,
        )
    }.sortedWith(compareBy<TransformerDefinition> { it.category.ordinal }.thenBy { it.label })
}

private fun Transformer<*>.descriptionText(): String {
    return this::class.java
        .getAnnotation(Transformer.Description::class.java)
        ?.enText
        .orEmpty()
}

private fun Transformer<*>.isHiddenTransformer(): Boolean {
    return this::class.java.isAnnotationPresent(HiddenTransformer::class.java)
}
