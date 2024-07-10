package net.spartanb312.grunt.config

import com.google.gson.*
import net.spartanb312.grunt.config.Configs.Settings.exclusions
import net.spartanb312.grunt.config.Configs.Settings.mixinPackages
import net.spartanb312.grunt.process.Transformers
import org.objectweb.asm.tree.ClassNode
import java.io.*

object Configs {

    private val configs = mutableListOf<Configurable>()
    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()

    object Settings : Configurable("Settings") {
        val input by setting("Input", "input.jar")
        val output by setting("Output", "output.jar")
        val libraries by setting("Libraries", listOf())
        val exclusions by setting("Exclusions", listOf())
        val mixinPackages by setting("MixinPackage", listOf("net/spartanb312/client/mixins/"))
        val generateRemap by setting("DumpMappings", true)
        val remapOutput by setting("MappingsOutput", "mappings.json")
        val useComputeMax by setting("UseComputeMax", false)
        val customDictionary by setting("CustomDictionary", listOf())
        val dictionaryStartIndex by setting("DictionaryStartIndex", 0)
        val corruptOutput by setting("CorruptOutput", false)
        val fileRemovePrefix by setting("FileRemovePrefix", listOf())
        val fileRemoveSuffix by setting("FileRemoveSuffix", listOf())
    }

    init {
        configs.add(Settings)
        Transformers.forEach {
            configs.add(it)
        }
    }

    fun resetConfig() {
        configs.forEach { it }
    }

    fun loadConfig(path: String) {
        val map = path.jsonMap
        configs.forEach {
            map[it.name]?.asJsonObject?.let { jo -> it.getValue(jo) }
        }
    }

    fun saveConfig(path: String) {
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