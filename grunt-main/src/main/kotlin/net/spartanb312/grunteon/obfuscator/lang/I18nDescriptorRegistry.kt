package net.spartanb312.grunteon.obfuscator.lang

import kotlinx.serialization.Transient
import net.spartanb312.grunteon.obfuscator.process.HiddenFromAutoParameter
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.SettingSection
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistryEntry
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

object I18nDescriptorRegistry {

    private val asciiTokenRegex = Regex("[A-Za-z0-9]+")
    private val wordRegex = Regex("[A-Z]+(?=[A-Z][a-z0-9]|$)|[A-Z]?[a-z]+|[0-9]+")

    fun normalizeSegment(value: String): String {
        val words = asciiTokenRegex.findAll(value)
            .flatMap { token -> wordRegex.findAll(token.value).map { it.value } }
            .toList()
        if (words.isEmpty()) return "value"
        return buildString {
            words.forEachIndexed { index, word ->
                val lower = word.lowercase()
                if (index == 0) {
                    append(lower.replaceFirstChar { it.lowercaseChar() })
                } else {
                    append(lower.replaceFirstChar { it.uppercaseChar() })
                }
            }
        }
    }

    fun transformerRoot(transformer: Transformer<*>): String {
        return "transformer.${normalizeSegment(transformer.engName)}"
    }

    fun configFieldPath(basePath: String, propertyName: String): String {
        return "$basePath.${normalizeSegment(propertyName)}"
    }

    fun descriptor(key: String, fallback: String): I18nDescriptor {
        return I18nDescriptor(key, fallback)
    }

    fun classOverrideConfigBase(clazz: KClass<*>): String? {
        return clazz.findAnnotation<I18NDescriptorPath>()?.value?.let { "$it.config" }
    }

    fun collectTransformers(
        builder: Builder,
        entries: Iterable<TransformerRegistryEntry>,
    ) {
        entries.forEach { entry ->
            val transformer = entry.transformerPrototype
            val root = transformerRoot(transformer)
            builder.add("$root.name", transformer.engName)
            builder.add("$root.desc", transformer.descriptionFallback())
            collectConfig(builder, "$root.config", entry.configClass)
        }
    }

    fun collectConfig(
        builder: Builder,
        basePath: String,
        clazz: KClass<*>,
        visited: MutableSet<Pair<String, KClass<*>>> = mutableSetOf(),
    ) {
        val visitKey = basePath to clazz
        if (!visited.add(visitKey)) return
        configProperties(clazz).forEach { property ->
            val fieldPath = configFieldPath(basePath, property.name)
            val valueClass = property.returnType.valueClass()
            property.findAnnotation<SettingSection>()?.enText?.let {
                builder.add("$fieldPath.section", it)
            }
            builder.add("$fieldPath.name", property.nameFallback(valueClass))
            property.descriptionFallback(valueClass)?.let {
                builder.add("$fieldPath.desc", it)
            }
            collectNestedConfig(builder, fieldPath, property.returnType, visited)
        }
    }

    private fun collectNestedConfig(
        builder: Builder,
        fieldPath: String,
        type: KType,
        visited: MutableSet<Pair<String, KClass<*>>>,
    ) {
        val classifier = type.classifier as? KClass<*> ?: return
        if (classifier == List::class) {
            val elementClass = type.arguments.firstOrNull()?.type?.valueClass() ?: return
            if (elementClass.isData) {
                val itemPath = "$fieldPath.item"
                builder.add("$itemPath.name", elementClass.nameFallback("Item"))
                elementClass.findAnnotation<SettingDesc>()?.enText?.let {
                    builder.add("$itemPath.desc", it)
                }
                collectConfig(builder, itemPath, elementClass, visited)
            }
            return
        }
        if (classifier.isData) {
            val nestedBase = classOverrideConfigBase(classifier) ?: fieldPath
            collectConfig(builder, nestedBase, classifier, visited)
        }
    }

    private fun configProperties(clazz: KClass<*>): List<KProperty1<out Any, *>> {
        val copyFunc = clazz.memberFunctions.find { it.name == "copy" }
        val copyOrder = copyFunc?.parameters
            ?.drop(1)
            ?.withIndex()
            ?.associate { it.value.name to it.index }
            .orEmpty()
        return clazz.memberProperties
            .filter { it.javaField != null }
            .filter { property -> property.annotations.none { it is HiddenFromAutoParameter || it is Transient } }
            .sortedBy { copyOrder[it.name] ?: Int.MAX_VALUE }
    }

    private fun KProperty1<out Any, *>.nameFallback(valueClass: KClass<*>?): String {
        return findAnnotation<SettingName>()?.enText
            ?: valueClass?.findAnnotation<SettingName>()?.enText
            ?: camelCaseToWords(name)
    }

    private fun KProperty1<out Any, *>.descriptionFallback(valueClass: KClass<*>?): String? {
        return findAnnotation<SettingDesc>()?.enText
            ?: valueClass?.findAnnotation<SettingDesc>()?.enText
    }

    private fun KClass<*>.nameFallback(fallback: String): String {
        return findAnnotation<SettingName>()?.enText
            ?: simpleName?.let(::camelCaseToWords)
            ?: fallback
    }

    private fun KType.valueClass(): KClass<*>? {
        return classifier as? KClass<*>
    }

    private fun Transformer<*>.descriptionFallback(): String {
        return this::class.java
            .getAnnotation(Transformer.Description::class.java)
            ?.enText
            .orEmpty()
    }

    class Builder {
        private val values = linkedMapOf<String, String>()

        fun add(key: String, fallback: String) {
            val previous = values.putIfAbsent(key, fallback)
            require(previous == null || previous == fallback) {
                "Duplicate i18n descriptor key '$key' has conflicting fallbacks: '$previous' vs '$fallback'"
            }
        }

        fun build(): Map<String, String> {
            return values.toSortedMap()
        }
    }
}

fun camelCaseToWords(name: String): String {
    return name
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("[_\\-]+"), " ")
        .replaceFirstChar { it.uppercaseChar() }
}
