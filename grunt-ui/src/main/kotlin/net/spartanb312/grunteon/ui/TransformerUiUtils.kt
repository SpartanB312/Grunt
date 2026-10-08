package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.lang.I18n
import net.spartanb312.grunteon.obfuscator.lang.I18nDescriptorRegistry
import net.spartanb312.grunteon.obfuscator.process.*

fun findDefinition(
    config: TransformerConfig,
    definitions: List<TransformerDefinition>,
): TransformerDefinition? = if (definitions is IndexedTransformerDefinitions) {
    definitions.byConfigClass[config::class]
} else definitions.find { it.configClass == config::class }

fun validateOrder(
    nodes: List<TransformerEntry>,
    definitions: List<TransformerDefinition>,
): Map<Int, List<String>> {
    val enabled = nodes.mapIndexedNotNull { index, entry ->
        if (!entry.enabled) null else findDefinition(entry.config, definitions)?.let {
            index to it.transformerPrototype
        }
    }
    val transformers = enabled.map { it.second }
    return buildMap {
        enabled.forEachIndexed { enabledIndex, (nodeIndex, transformer) ->
            val warnings = transformer.orderRules.filter { (rule, _) ->
                !rule(transformers, enabledIndex)
            }.map { it.second }
            // Warnings belong to the original stack row, not its index after filtering disabled rows.
            if (warnings.isNotEmpty()) put(nodeIndex, warnings)
        }
    }
}

private class IndexedTransformerDefinitions(private val values: List<TransformerDefinition>) :
    AbstractList<TransformerDefinition>() {
    val byConfigClass = values.associateBy { it.configClass }
    override val size: Int get() = values.size
    override fun get(index: Int): TransformerDefinition = values[index]
}

/** Created after plugin loading. The immutable schema is independent of the selected language. */
internal class TransformerCatalog(entries: List<TransformerRegistryEntry>) {
    private val schema = entries.map { entry ->
        val transformer = entry.transformerPrototype
        val descriptorRoot = uiDescriptorPath(I18nDescriptorRegistry.transformerRoot(transformer))
        val labelFallback = transformer.engName
        val descriptionFallback = transformer.descriptionText()
        TransformerDefinition(
            label = labelFallback,
            typeName = entry.configClass.qualifiedName.orEmpty(),
            category = transformer.category,
            description = descriptionFallback,
            owner = entry.owner,
            isHidden = transformer.isHiddenTransformer(),
            configClass = entry.configClass,
            configFactory = entry.createConfig,
            transformerPrototype = transformer,
            descriptorRoot = descriptorRoot,
        )
    }
    private var language: net.spartanb312.grunteon.obfuscator.lang.Language? = null
    private var catalog: Map<String, String>? = null
    private var fallbackCatalog: Map<String, String>? = null
    private var localized: List<TransformerDefinition> = emptyList()

    @Synchronized
    fun definitions(): List<TransformerDefinition> {
        val currentLanguage = I18n.currentLanguage
        val currentCatalog = I18n.catalog(currentLanguage)
        val currentFallback = I18n.catalog(net.spartanb312.grunteon.obfuscator.lang.Language.English)
        // Catalog identity also invalidates testing/reloaded translations in the same language.
        if (language != currentLanguage || catalog !== currentCatalog || fallbackCatalog !== currentFallback) {
            fun text(key: String, fallback: String) = currentCatalog[key] ?: currentFallback[key] ?: fallback
            localized = IndexedTransformerDefinitions(schema.map { definition ->
                TransformerDefinition(
                    label = text("${definition.descriptorRoot}.name", definition.label),
                    typeName = definition.typeName,
                    category = definition.category,
                    description = text("${definition.descriptorRoot}.desc", definition.description),
                    owner = definition.owner,
                    isHidden = definition.isHidden,
                    configClass = definition.configClass,
                    configFactory = definition.configFactory,
                    transformerPrototype = definition.transformerPrototype,
                    descriptorRoot = definition.descriptorRoot,
                )
            }.sortedWith(compareBy<TransformerDefinition> { it.category.ordinal }.thenBy { it.label }))
            language = currentLanguage
            catalog = currentCatalog
            fallbackCatalog = currentFallback
        }
        return localized
    }
}

private var definitionEntries: List<TransformerRegistryEntry>? = null
private var definitionCatalog: TransformerCatalog? = null

@Synchronized
fun transformerDefinitions(): List<TransformerDefinition> {
    val entries = TransformerRegistry.entries
    if (entries != definitionEntries) {
        definitionCatalog = TransformerCatalog(entries)
        definitionEntries = entries
    }
    return definitionCatalog!!.definitions()
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
