package net.spartanb312.grunt.config

import com.google.gson.*
import net.spartanb312.grunt.config.Configs.Settings.exclusions
import net.spartanb312.grunt.config.Configs.Settings.mixinPackages
import net.spartanb312.grunt.event.events.ConfigEvent
import net.spartanb312.grunt.process.Transformers
import org.objectweb.asm.tree.ClassNode
import java.io.*

object Configs {

    private val configs = mutableListOf<Configurable>()
    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()

    object Settings : Configurable("Settings") {
        var input by setting("Input", "input.jar")
        var output by setting("Output", "output.jar")
        var libraries by setting("Libraries", listOf())
        var exclusions by setting("Exclusions", listOf())
        var mixinPackages by setting("MixinPackage", listOf("net/spartanb312/client/mixins/"))
        var generateRemap by setting("DumpMappings", true)
        var parallel by setting("Multithreading", false)
        var timeUsage by setting("PrintTimeUsage", true)
        var forceUseComputeMax by setting("ForceUseComputeMax", false)
        var missingCheck by setting("LibsMissingCheck", true)
        var customDictionary by setting("CustomDictionaryFile", File("customDictionary.txt"))
        var dictionaryStartIndex by setting("DictionaryStartIndex", 0)
        var corruptCRC32 by setting("CorruptCRC32", false)
        var corruptJarHeader by setting("CorruptJarHeader", false)
        var fileRemovePrefix by setting("FileRemovePrefix", listOf())
        var fileRemoveSuffix by setting("FileRemoveSuffix", listOf())
    }

    object UISetting : Configurable("UI") {
        var darkTheme by setting("DarkTheme", true)
    }

    init {
        configs.add(Settings)
        configs.add(UISetting)
        Transformers.forEach {
            configs.add(it)
        }
    }

    fun resetConfig() {
        configs.forEach { config ->
            config.getValues().forEach { value ->
                value.reset()
            }
        }
    }

    fun loadConfig(path: String) {
        ConfigEvent.Load(path).let {
            it.post()
            if (it.cancelled) return
        }
        val map = path.jsonMap
        configs.forEach {
            map[it.name]?.asJsonObject?.let { jo -> it.getValue(jo) }
        }
    }

    fun saveConfig(path: String) {
        ConfigEvent.Save(path).let {
            it.post()
            if (it.cancelled) return
        }
        val configFile = File(path)
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            configFile.createNewFile()
        }
        JsonObject().apply {
            configs.forEach {
                add(it.name, it.saveValue())
            }
        }.saveToFile(configFile)
    }

    private val String.jsonMap: Map<String, JsonElement>
        get() {
            val loadJson = BufferedReader(FileReader(this))
            val map = mutableMapOf<String, JsonElement>()
            JsonParser.parseReader(loadJson).asJsonObject.entrySet().forEach {
                map[it.key] = it.value
            }
            loadJson.close()
            return map
        }

    fun JsonObject.saveToFile(file: File) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val saveJSon = PrintWriter(FileWriter(file))
        saveJSon.println(gsonPretty.toJson(this))
        saveJSon.close()
    }

    inline val String.isExcluded get() = isMixinClass || isGlobalExcluded
    inline val ClassNode.isExcluded get() = isMixinClass || isGlobalExcluded
    inline val String.isMixinClass get() = mixinPackages.any { this.startsWith(it) }
    inline val ClassNode.isMixinClass get() = mixinPackages.any { name.startsWith(it) }
    inline val String.isGlobalExcluded get() = exclusions.any { this.startsWith(it) }
    inline val ClassNode.isGlobalExcluded get() = exclusions.any { name.startsWith(it) }
    inline val String.shouldRemove
        get() = Settings.fileRemovePrefix.any { startsWith(it) }
                || Settings.fileRemoveSuffix.any { endsWith(it) }

}