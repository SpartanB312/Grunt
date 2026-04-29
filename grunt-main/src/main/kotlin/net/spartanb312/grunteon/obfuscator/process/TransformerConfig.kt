package net.spartanb312.grunteon.obfuscator.process

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import net.spartanb312.grunteon.obfuscator.lang.I18NDescriptorPath
import net.spartanb312.grunteon.obfuscator.util.filters.FilterStrategy
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class SettingDesc(val enText: String)

// Optional, will fallback to property name if not present
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class SettingName(val enText: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRangeVal(val min: Int, val max: Int, val step: Int = 1)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DecimalRangeVal(val min: Double, val max: Double, val step: Double)

@Serializable
abstract class TransformerConfig {
    @SettingDesc("Enable this transformer config node")
    var enabled: Boolean = true

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
@I18NDescriptorPath("process.common")
data class ClassFilterConfig(
    @SettingDesc(enText = "Specify class exclusions")
    val excludeStrategy: List<String> = listOf(
        "net/dummy/**", // Exclude package
        "net/dummy/Class", // Exclude class
        "net/dummy/Event**" // Exclude prefix
    ),
    @SettingDesc(enText = "Specify class includes")
    val includeStrategy: List<String> = listOf(
        "**" // Include all
    )
) {
    fun buildFilterStrategy(): FilterStrategy {
        return FilterStrategy(
            buildClassNamePredicates(includeStrategy),
            buildClassNamePredicates(excludeStrategy)
        )
    }
}
