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
            )
        }
    }
}
