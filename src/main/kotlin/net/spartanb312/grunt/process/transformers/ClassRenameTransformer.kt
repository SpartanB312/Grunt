package net.spartanb312.grunt.process.transformers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunt.config.value
import net.spartanb312.grunt.dictionary.NameGenerator
import net.spartanb312.grunt.process.Transformer
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.utils.count
import net.spartanb312.grunt.utils.isNotExcludedIn
import net.spartanb312.grunt.utils.logging.Logger
import org.objectweb.asm.tree.ClassNode
import java.nio.charset.StandardCharsets

object ClassRenameTransformer : Transformer("ClassRename") {

    private val dictionary by value("Dictionary", "Alphabet")
    private val parent by value("Parent", "net/spartanb312/obf/")
    private val prefix by value("Prefix", "")

    private val corruptedName by value("CorruptedName", false)
    private val corruptedNameExclusion by value("CorruptedNameExclusions", listOf())

    private val manifestReplace by value("ManifestReplace", listOf("Main-Class"))
    private val pluginMainReplace by value("PluginMainReplace", false)
    private val bungeeMainReplace by value("BungeeMainReplace", false)
    private val exclusion by value("Exclusion", listOf())

    private val mixinSupport by value("MixinSupport", false)
    private val mixinDictionary by value("MixinDictionary", "Alphabet")
    private val mixinPackage by value("MixinPackage", "net/spartanb312/client/mixins/")
    private val targetMixinPackage by value("TargetMixinPackage", "net/spartanb312/obf/mixins/")
    private val mixinFile by value("MixinFile", "mixins.example.json")
    private val refmapFile by value("RefmapFile", "mixins.example.refmap.json")

    private val ClassNode.malNamePrefix
        get() = if (corruptedName) {
            if (corruptedNameExclusion.any { ex -> name.startsWith(ex) }) {
                Logger.info("    MalName excluded for $name")
                ""
            } else "\u0000"
        } else ""

    override fun ResourceCache.transform() {
        Logger.info(" - Renaming classes...")
        val generator = NameGenerator.getByName(dictionary)
        val mixinDic = NameGenerator.getByName(mixinDictionary)
        val remap: MutableMap<String, String> = HashMap()
        val count = count {
            nonExcluded.forEach {
                if (it.name.isNotExcludedIn(exclusion)) {
                    if (mixinSupport && it.name.startsWith(mixinPackage)) {
                        remap[it.name] = targetMixinPackage + mixinDic.nextName()
                    } else remap[it.name] = parent + prefix + it.malNamePrefix + generator.nextName()
                    add(1)
                }
            }
        }.get()

        Logger.info("    Applying remapping for classes...")
        applyRemap("classes", remap, true)

        if (mixinSupport) {
            Logger.info("    Remapping mixins...")
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
                                    val mapping = remap[newName]
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
                                    val mapping = remap[newName]
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
                                    val mapping = remap[newName]
                                    if (mapping != null) {
                                        refmapReplaces[newName] = mapping
                                        server.add(mapping.substringAfterLast("/"))
                                    } else server.add(clazz)
                                }
                                mainObject.add("server", server)
                            }
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
                                value.asJsonObject.asMap().forEach { (clazz, mappings) ->
                                    val mapping = remap[clazz]
                                    if (mapping != null) {
                                        mappingsObject.add(mapping, mappings)
                                    } else mappingsObject.add(clazz, mappings)
                                }
                                mainObject.add("mappings", mappingsObject)
                            }

                            "data" -> {
                                val dataObject = JsonObject()
                                value.asJsonObject.asMap().forEach { (type, typeObj) ->
                                    val newTypeObj = JsonObject()
                                    typeObj.asJsonObject.asMap().forEach { (clazz, data) ->
                                        val mapping = remap[clazz]
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
        }

        manifestReplace.forEach {
            val clazz = manifest[it]
            if (clazz != null) {
                val replace = remap[clazz.replace(".", "/")]
                if (replace != null) {
                    manifest[it] = replace.replace("/", ".")
                    Logger.info("    Replaced $it to $replace")
                }
            }
        }

        val map = mutableMapOf<MutableMap<String, String>, String>()
        if (pluginMainReplace) map[pluginYml] = "main"
        if (bungeeMainReplace) map[bungeeYml] = "main"
        map.forEach { (file, entry) ->
            val main = file[entry]
            if (main != null) {
                val replace = remap[main.replace(".", "/")]
                if (replace != null) {
                    file[entry] = replace.replace("/", ".")
                    Logger.info("    Replaced $entry to $replace")
                }
            }
        }

        Logger.info("    Renamed $count classes")
    }

}