package net.spartanb312.grunt.process.transformers

import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.dot
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.splash

/**
 * Post process for resource files
 * Last update on 2024/06/26
 * Improvements:
 * 1.PluginMain
 * 2.BungeeMain
 * 3.FabricMain
 */
object PostProcessTransformer : Transformer("PostProcess", Category.Miscellaneous) {

    private val manifest by value("Manifest", true)
    private val pluginMain by value("Plugin YML", true)
    private val bungeeMain by value("Bungee YML", true)
    private val fabricMain by value("Fabric JSON", true)
    private val manifestReplace by value("ManifestPrefix", listOf("Main-Class:"))

    override fun ResourceCache.transform() {
        if (manifest) processManifest()
        if (pluginMain) processPluginMain()
        if (bungeeMain) processBungeeMain()
        if (fabricMain) processFabricMain()
    }

    private fun ResourceCache.processManifest() {
        val manifestFile = resources["META-INF/MANIFEST.MF"] ?: return
        Logger.info(" - Processing MANIFEST.MF...")
        val manifest = mutableListOf<String>()
        manifestFile.decodeToString().split("\n").forEach { line ->
            var final = line
            manifestReplace.forEach { prefixRaw ->
                val prefix = prefixRaw.removeSuffix(" ")
                if (line.startsWith(prefix)) {
                    val remaining = line.substringAfter(prefix)
                        .substringAfter(" ")
                        .replace("\r", "")
                        .splash
                    val obfName = classMappings.getOrDefault(remaining, null)
                    if (obfName != null) {
                        final = "$prefix ${obfName.dot}"
                        Logger.info("    Replaced manifest $final")
                    }
                }
            }
            manifest.add(final)
        }
        resources["META-INF/MANIFEST.MF"] = manifest.joinToString("\n").toByteArray()
    }

    private fun ResourceCache.processPluginMain() {
    }

    private fun ResourceCache.processBungeeMain() {
    }

    private fun ResourceCache.processFabricMain() {
    }

}