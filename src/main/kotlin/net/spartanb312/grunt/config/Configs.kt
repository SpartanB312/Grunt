package net.spartanb312.grunt.config

import com.google.gson.*
import net.spartanb312.grunt.process.Transformers
import java.io.*

object Configs {

    private val configs = mutableListOf<Configurable>()
    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()

    object Settings : Configurable("Settings") {
        val input by value("Input", "input.jar")
        val output by value("Output", "output.jar")
        val libraries by value("Libraries", listOf())
        val exclusions by value("Exclusions", listOf())
        val generateRemap by value("GenerateRemap", true)
        val remapOutput by value("RemapOutput", "mappings.json")
        val parallel by value("ParallelProcessing", false)
        val customDictionary by value("CustomDictionary", listOf())
        val dictionaryStartIndex by value("DictionaryStartIndex", 0)
        val corruptOutput by value("CorruptOutput", false)
        val fileRemovePrefix by value("FileRemovePrefix", listOf())
        val fileRemoveSuffix by value("FileRemoveSuffix", listOf())
    }

    init {
        configs.add(Settings)
        Transformers.forEach {
            configs.add(it)
        }
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

}