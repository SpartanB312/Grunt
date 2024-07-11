package net.spartanb312.grunt.process.transformers.minecraft

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunt.config.setting
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.NameGenerator
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.notInList
import net.spartanb312.grunt.utils.logging.Logger
import java.nio.charset.StandardCharsets

/**
 * Renaming mixin classes
 * Last update on 2024/06/28
 */
object MixinClassRenameTransformer : Transformer("MixinClassRename", Category.Minecraft) {

    private val mixinDictionary by setting("MixinDictionary", "Alphabet")
    private val targetMixinPackage by setting("TargetMixinPackage", "net/spartanb312/obf/mixins/")
    private val mixinFile by setting("MixinFile", "mixins.example.json")
    private val refmapFile by setting("RefmapFile", "mixins.example.refmap.json")
    private val exclusion by setting("Exclusion", listOf())

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming mixin classes...")
        if (mixinClasses.isEmpty()) {
            Logger.info("    No mixin classes found")
            return
        }

        Logger.info("    Generating mappings for mixin classes...")
        val targetMixinPackage = targetMixinPackage.removeSuffix("/") + "/"
        val dictionary = NameGenerator.getByName(mixinDictionary)
        val mappings: MutableMap<String, String> = HashMap()
        val count = count {
            mixinClasses.forEach {
                if (it.name.notInList(exclusion)) {
                    mappings[it.name] = targetMixinPackage + dictionary.nextName()
                    add()
                }
            }
        }.get()

        Logger.info("    Applying mappings for mixin classes...")
        applyRemap("mixin classes", mappings, true)

        Logger.info("    Remapping mixin files...")
        val refmapReplaces = mutableMapOf<String, String>()
        val newMixinFile = resources[mixinFile]?.let { bytes ->
            val mainObject = JsonObject()
            Gson().fromJson(
                String(bytes, StandardCharsets.UTF_8),
                JsonObject::class.java
            ).apply {
                var packagePrefix = ""
                asMap().forEach { (name, value) ->
                    when (name) {
                        "required" -> mainObject.addProperty("required", value.asBoolean)
                        "minVersion" -> mainObject.addProperty("minVersion", value.asString)
                        "compatibilityLevel" -> mainObject.addProperty("compatibilityLevel", value.asString)
                        "refmap" -> mainObject.addProperty("refmap", value.asString)
                        "package" -> {
                            mainObject.addProperty(
                                "package",
                                targetMixinPackage.substringBeforeLast("/").replace("/", ".")
                            )
                            packagePrefix = value.asString
                        }

                        "mixins" -> {
                            val mixins = JsonArray()
                            value.asJsonArray.forEach { mixin ->
                                val clazz = packagePrefix + "." + mixin.asString
                                val newName = clazz.replace(".", "/")
                                val mapping = mappings[newName]
                                if (mapping != null) {
                                    refmapReplaces[newName] = mapping
                                    mixins.add(mapping.substringAfterLast("/"))
                                } else mixins.add(clazz)
                            }
                            mainObject.add("mixins", mixins)
                        }

                        "client" -> {
                            val client = JsonArray()
                            value.asJsonArray.forEach { mixin ->
                                val clazz = packagePrefix + "." + mixin.asString
                                val newName = clazz.replace(".", "/")
                                val mapping = mappings[newName]
                                if (mapping != null) {
                                    refmapReplaces[newName] = mapping
                                    client.add(mapping.substringAfterLast("/"))
                                } else client.add(clazz)
                            }
                            mainObject.add("client", client)
                        }

                        "server" -> {
                            val server = JsonArray()
                            value.asJsonArray.forEach { mixin ->
                                val clazz = packagePrefix + "." + mixin.asString
                                val newName = clazz.replace(".", "/")
                                val mapping = mappings[newName]
                                if (mapping != null) {
                                    refmapReplaces[newName] = mapping
                                    server.add(mapping.substringAfterLast("/"))
                                } else server.add(clazz)
                            }
                            mainObject.add("server", server)
                        }

                        else -> mainObject.add(name, value)
                    }
                }
            }
            Gson().toJson(mainObject).toByteArray(Charsets.UTF_8)
        }
        if (newMixinFile != null) resources[mixinFile] = newMixinFile
        val refmapBytes = resources[refmapFile]?.let { bytes ->
            val mainObject = JsonObject()
            Gson().fromJson(
                String(bytes, StandardCharsets.UTF_8),
                JsonObject::class.java
            ).apply {
                asMap().forEach { (name, value) ->
                    when (name) {
                        "mappings" -> {
                            val mappingsObject = JsonObject()
                            value.asJsonObject.asMap().forEach { (clazz, it) ->
                                val mapping = mappings[clazz]
                                if (mapping != null) {
                                    mappingsObject.add(mapping, it)
                                } else mappingsObject.add(clazz, it)
                            }
                            mainObject.add("mappings", mappingsObject)
                        }

                        "data" -> {
                            val dataObject = JsonObject()
                            value.asJsonObject.asMap().forEach { (type, typeObj) ->
                                val newTypeObj = JsonObject()
                                typeObj.asJsonObject.asMap().forEach { (clazz, data) ->
                                    val mapping = mappings[clazz]
                                    if (mapping != null) {
                                        newTypeObj.add(mapping, data)
                                    } else newTypeObj.add(clazz, data)
                                }
                                dataObject.add(type, newTypeObj)
                            }
                            mainObject.add("data", dataObject)
                        }
                    }
                }
            }
            Gson().toJson(mainObject).toByteArray(Charsets.UTF_8)
        }
        if (refmapBytes != null) resources[refmapFile] = refmapBytes

        Logger.info("    Renamed $count mixin classes")
    }

}