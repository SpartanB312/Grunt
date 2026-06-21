package net.spartanb312.grunteon.ui

import net.spartanb312.grunteon.obfuscator.lang.I18nDescriptorRegistry
import net.spartanb312.grunteon.obfuscator.process.GlobalConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerRegistry
import net.spartanb312.grunteon.obfuscator.process.nativecode.NativePipelineConfig

object UiI18nCatalog {
    fun buildEnglishCatalog(): Map<String, String> {
        val builder = I18nDescriptorRegistry.Builder()
        I18nDescriptorRegistry.collectConfig(builder, UiDescriptorPaths.AppConfig, AppConfig::class)
        I18nDescriptorRegistry.collectConfig(builder, UiDescriptorPaths.GlobalConfig, GlobalConfig::class)
        I18nDescriptorRegistry.collectConfig(builder, UiDescriptorPaths.NativePipelineConfig, NativePipelineConfig::class)
        I18nDescriptorRegistry.collectTransformers(builder, TransformerRegistry.entries)
        return (builder.build() + UiText.englishCatalog()).withUiDescriptorNamespace()
    }

}
