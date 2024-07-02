package net.spartanb312.grunt.process.transformers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.dot
import net.spartanb312.grunt.utils.logging.Logger
import net.spartanb312.grunt.utils.splash
import java.nio.charset.StandardCharsets

/**
 * Post process for resource files
 * Last update on 2024/07/02
 */
object PostProcessTransformer : Transformer("PostProcess", Category.Miscellaneous) {

    override val enabled by setting("Enabled", true)
    private val manifest by setting("Manifest", true)
    private val pluginMain by setting("Plugin YML", true)
    private val bungeeMain by setting("Bungee YML", true)
    private val fabricMain by setting("Fabric JSON", true)
    private val manifestReplace by setting("ManifestPrefix", listOf("Main-Class:"))

    override fun ResourceCache.transform() {
        Logger.info(" - Post processing resources...")
        if (manifest) processManifest()
        if (pluginMain) processPluginMain()
        if (bungeeMain) processBungeeMain()
        if (fabricMain) processFabricMain()
    }

    private fun ResourceCache.processManifest() {
        val manifestFile = resources["META-INF/MANIFEST.MF"] ?: return
        Logger.info("    Processing MANIFEST.MF...")
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
        val pluginYMLFile = resources["plugin.yml"] ?: return
        Logger.info("    Processing plugin.yml...")
        resources["plugin.yml"] = processYMLMain("plugin main", pluginYMLFile)
    }

    private fun ResourceCache.processBungeeMain() {
        val pluginYMLFile = resources["bungee.yml"] ?: return
        Logger.info("    Processing bungee.yml...")
        resources["bungee.yml"] = processYMLMain("bungee main", pluginYMLFile)
    }

    private fun ResourceCache.processYMLMain(desc: String, file: ByteArray): ByteArray {
        val lines = mutableListOf<String>()
        file.decodeToString().split("\n").forEach { line ->
            var final = line
            if (line.startsWith("main: ")) {
                val remaining = line.substringAfter("main: ")
                    .replace("\r", "")
                    .splash
                val obfName = classMappings.getOrDefault(remaining, null)?.dot
                if (obfName != null) {
                    final = "main: $obfName"
                    Logger.info("    Replaced $desc $obfName")
                }
            }
            lines.add(final)
        }
        return lines.joinToString("\n").toByteArray()
    }

    private fun ResourceCache.processFabricMain() {
        val jsonFile = resources["fabric.mod.json"] ?: return
        Logger.info("    Processing fabric.mod.json...")
        val mainObject = JsonObject()
        Gson().fromJson(
            String(jsonFile, StandardCharsets.UTF_8),
            JsonObject::class.java
        ).apply {
            asMap().forEach { (name, value) ->
                when (name) {
                    "entrypoints" -> {
                        val entryPointObject = JsonObject()
                        //  "entrypoints": {
                        //    "main": [
                        //      "org.example.Example1",
                        //      {
                        //        "adapter": "kotlin",
                        //        "value": "org.example.Example2"
                        //      }
                        //    ]
                        //  }
                        value.asJsonObject.asMap().forEach { (pointName, classesObj) ->
                            val classes = JsonArray()
                            classesObj.asJsonArray.forEach {
                                if (it.isJsonObject) {
                                    // Example:
                                    // {
                                    //     "adapter": "kotlin",
                                    //     "value": "org.example.ExampleMod"
                                    // }
                                    val entryPointElem = it.asJsonObject
                                    val pre = entryPointElem["value"].asString
                                    val new = classMappings.getOrDefault(pre.splash, null)
                                    if (new != null) {
                                        Logger.info("    Replaced fabric entry point $pointName $new")
                                        val newElem = JsonObject()
                                        newElem.addProperty("adapter",  entryPointElem["adapter"].asString)
                                        newElem.addProperty("value", new)
                                        classes.add(newElem)
                                    } else classes.add(it.asJsonObject)
                                } else {
                                    // "org.example.ExampleMod"
                                    val pre = it.asString
                                    val new = classMappings.getOrDefault(pre.splash, null)
                                    if (new != null) {
                                        Logger.info("    Replaced fabric entry point $pointName $new")
                                        classes.add(new)
                                    } else classes.add(pre)
                                }
                            }
                            entryPointObject.add(pointName, classes)
                        }
                        mainObject.add("entrypoints", entryPointObject)
                    }

                    else -> mainObject.add(name, value)
                }
            }
        }
        resources["fabric.mod.json"] = Gson().toJson(mainObject).toByteArray(Charsets.UTF_8)
    }

}