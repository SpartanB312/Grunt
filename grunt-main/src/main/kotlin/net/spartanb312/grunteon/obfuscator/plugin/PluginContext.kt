package net.spartanb312.grunteon.obfuscator.plugin

import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistryEntry
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.ILogger

interface PluginContext {
    val metadata: PluginMetadata
    val classLoader: ClassLoader

    fun registerTransformer(entry: TransformerRegistryEntry)

    fun logger(): ILogger = Logger
}

inline fun <reified C : TransformerConfig> PluginContext.registerTransformer(
    noinline createTransformer: () -> Transformer<*>,
    noinline createConfig: () -> C,
) {
    registerTransformer(
        TransformerRegistry.entry(
            createTransformer = createTransformer,
            createConfig = createConfig,
            owner = metadata.id,
        )
    )
}
