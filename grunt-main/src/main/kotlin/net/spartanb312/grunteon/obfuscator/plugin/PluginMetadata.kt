package net.spartanb312.grunteon.obfuscator.plugin

import java.nio.file.Path
import java.util.jar.Manifest

data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val entryClass: String,
    val file: Path,
    val depends: List<String> = emptyList(),
    val softDepends: List<String> = emptyList(),
    val loadBefore: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
) {
    companion object {
        fun fromManifest(file: Path, manifest: Manifest): PluginMetadata? {
            val attributes = manifest.mainAttributes
            val entryClass = attributes.getValue("Grunt-Plugin-Main")
                ?: attributes.getValue("Entry-Class")
                ?: return null
            val fallbackId = file.fileName.toString().removeSuffix(".jar")
            val id = attributes.getValue("Grunt-Plugin-Id") ?: fallbackId
            return PluginMetadata(
                id = id,
                name = attributes.getValue("Grunt-Plugin-Name") ?: id,
                version = attributes.getValue("Grunt-Plugin-Version") ?: "unknown",
                apiVersion = attributes.getValue("Grunt-Plugin-Api") ?: "1",
                entryClass = entryClass,
                file = file,
                depends = attributes.getList("Grunt-Plugin-Depends"),
                softDepends = attributes.getList("Grunt-Plugin-SoftDepends"),
                loadBefore = attributes.getList("Grunt-Plugin-LoadBefore"),
                conflicts = attributes.getList("Grunt-Plugin-Conflicts"),
            )
        }

        fun fromPlugin(plugin: GruntPlugin): PluginMetadata {
            val pluginClass = plugin.javaClass
            return PluginMetadata(
                id = plugin.pluginID,
                name = pluginClass.simpleName,
                version = plugin.version,
                apiVersion = "1",
                entryClass = pluginClass.name,
                file = Path.of(""),
            )
        }

        private fun java.util.jar.Attributes.getList(name: String): List<String> {
            return getValue(name)
                ?.split(',', ';')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }
    }
}
