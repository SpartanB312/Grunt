package net.spartanb312.grunteon.obfuscator.process

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import net.spartanb312.grunteon.obfuscator.lang.I18NDescriptorPath
import net.spartanb312.grunteon.obfuscator.util.filters.ClassPredicate
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class SettingSection(val enText: String)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class SettingDesc(val enText: String)

// Optional, will fallback to property name if not present
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class SettingName(val enText: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRangeVal(val min: Int, val max: Int, val step: Int = 1)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DecimalRangeVal(val min: Double, val max: Double, val step: Double)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiddenFromAutoParameter

@Serializable
abstract class TransformerConfig {
    companion object {
        @OptIn(InternalSerializationApi::class)
        fun serializersModule(): SerializersModule {
            return SerializersModule {
                polymorphic(TransformerConfig::class) {
                    TransformerRegistry.entries.forEach { entry ->
                        @Suppress("UNCHECKED_CAST")
                        subclass(
                            entry.configClass as KClass<TransformerConfig>,
                            entry.configClass.serializer()
                        )
                    }
                }
            }
        }
    }
}

@Serializable
@I18NDescriptorPath("common.classFilter")
@SettingDesc("Specify class include/exclude rules")
@SettingName("Class filter")
data class ClassFilterConfig(
    @SettingDesc("Specify class exclusions")
    @SettingName("Exclude strategy")
    val excludeStrategy: List<String> = listOf(
        "net/dummy/**", // Exclude package
        "net/dummy/Class", // Exclude class
        "net/dummy/Event**" // Exclude prefix
    ),
    @SettingDesc("Specify class includes")
    @SettingName("Include strategy")
    val includeStrategy: List<String> = listOf(
        "**" // Include all
    )
) {
    fun toClassPredicate(): ClassPredicate {
        return ClassPredicate.IncludeExclude(
            buildClassNamePredicates(includeStrategy),
            buildClassNamePredicates(excludeStrategy)
        )
    }
}
