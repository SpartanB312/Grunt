package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.HiddenTransformer
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.SettingName
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.shuffled
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.dot
import net.spartanb312.grunteon.obfuscator.util.extensions.isMixinClass
import net.spartanb312.grunteon.obfuscator.util.splash
import java.nio.charset.StandardCharsets

@HiddenTransformer
@Transformer.Description(
    "process.rename.mixin_class_renamer.desc",
    "Renaming mixin classes"
)
class MixinClassRenamer : Transformer<MixinClassRenamer.Config>(
    "MixinClassRenamer",
    Category.Renaming,
), MappingSource {

    init {
        after(Category.Encryption, "Mixin renamer should run after encryption category")
        after(Category.AntiDebug, "Mixin renamer should run after anti debug category")
        after(Category.Authentication, "Mixin renamer should run after authentication category")
        after(Category.Exploit, "Mixin renamer should run after exploit category")
        after(Category.Miscellaneous, "Mixin renamer should run after miscellaneous category")
        after(Category.Optimization, "Mixin renamer should run after optimization category")
        after(Category.Redirect, "Mixin renamer should run after redirect category")
        after(ControlflowJump::class.java, "Mixin renamer should run after ControlflowJump")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules inside mixin range")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Dictionary for mixin class renamer")
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc("Parent package for target mixin classes")
        @SettingName("Target package")
        val targetPackage: String = "net/spartanb312/obf/mixins/",
        @SettingDesc("Prefix for target mixin class names")
        @SettingName("Prefix")
        val prefix: String = "",
        @SettingDesc("Mixin config JSON files to remap")
        @SettingName("Mixin files")
        val mixinFiles: List<String> = listOf("mixins.example.json"),
        @SettingDesc("Mixin refmap JSON files to remap")
        @SettingName("Refmap files")
        val refmapFiles: List<String> = listOf("mixins.example.refmap.json"),
        @SettingDesc("Shuffled mappings for mixin classes")
        @SettingName("Shuffled")
        val shuffled: Boolean = false
    ) : TransformerConfig() {
        fun normalizedTargetPackage(): String {
            val normalized = targetPackage.splash.trim('/')
            return if (normalized.isEmpty()) "" else "$normalized/"
        }
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            Logger.info(" > MixinClassRenamer: Generating mixin class mappings...")
            val strategy = config.classFilter.buildFilterStrategy()
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            val nameGenerator = NameGenerator(dictionary)
            val targetPackage = config.normalizedTargetPackage()
            val randomGen = Xoshiro256PPRandom(getSeed("MixinClassRenamer"))
            val mappings = linkedMapOf<String, String>()
            val classes = instance.workRes.inputClassCollection.asSequence()
                .filter { it.isMixinClass }
                .filter { strategy.testClass(it, excludeMixins = false) }
                .sortedBy { it.name }
                .run {
                    if (config.shuffled) shuffled(randomGen) else this
                }

            classes.forEach { classNode ->
                val newName = targetPackage + config.prefix + nameGenerator.nextName()
                mappings[classNode.name] = newName
                instance.nameMapping.putClassMapping(classNode.name, newName)
            }

            Logger.info("    Generated mapping for ${mappings.size} mixin classes")
            if (mappings.isNotEmpty()) {
                remapMixinFiles(config, mappings)
                remapRefmapFiles(config, mappings)
            }
        }
    }

    context(instance: Grunteon)
    private fun remapMixinFiles(config: Config, mappings: Map<String, String>) {
        config.mixinFiles.asSequence()
            .filter { it.isNotBlank() }
            .forEach { fileName ->
                val mixinFile = instance.workRes.getInputResource(fileName) ?: return@forEach
                Logger.info("    Processing mixin file $fileName...")
                val source = Gson().fromJson(
                    String(mixinFile.content, StandardCharsets.UTF_8),
                    JsonObject::class.java
                ) ?: return@forEach

                val packagePrefix = source["package"]?.asString ?: ""
                val hasPackage = packagePrefix.isNotBlank()
                val targetPackage = config.normalizedTargetPackage()
                val targetPackageDot = targetPackage.removeSuffix("/").dot
                val output = JsonObject()
                var changed = false

                source.asMap().forEach { (name, value) ->
                    when (name) {
                        "package" -> {
                            if (targetPackageDot.isNotEmpty()) {
                                output.addProperty("package", targetPackageDot)
                            } else {
                                output.add(name, value)
                            }
                        }

                        "mixins", "client", "server" -> {
                            val (arrayValue, remapped) = remapMixinArray(value, packagePrefix, hasPackage, mappings)
                            output.add(name, arrayValue)
                            changed = changed || remapped
                        }

                        else -> output.add(name, value)
                    }
                }

                if (changed) {
                    mixinFile.content = Gson().toJson(output).toByteArray(StandardCharsets.UTF_8)
                }
            }
    }

    private fun remapMixinArray(
        value: JsonElement,
        packagePrefix: String,
        hasPackage: Boolean,
        mappings: Map<String, String>
    ): Pair<JsonElement, Boolean> {
        if (!value.isJsonArray) return value.deepCopy() to false

        val array = JsonArray()
        var changed = false
        value.asJsonArray.forEach { element ->
            val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            if (primitive == null || !primitive.isString) {
                array.add(element.deepCopy())
                return@forEach
            }

            val mixinName = primitive.asString
            val previousName = mixinClassName(packagePrefix, mixinName)
            val mappedName = mappings[previousName]
            if (mappedName != null) {
                array.add(if (hasPackage) mappedName.substringAfterLast("/") else mappedName.dot)
                changed = true
            } else {
                array.add(if (hasPackage) previousName.dot else mixinName)
            }
        }
        return array to changed
    }

    context(instance: Grunteon)
    private fun remapRefmapFiles(config: Config, mappings: Map<String, String>) {
        config.refmapFiles.asSequence()
            .filter { it.isNotBlank() }
            .forEach { fileName ->
                val refmapFile = instance.workRes.getInputResource(fileName) ?: return@forEach
                Logger.info("    Processing mixin refmap $fileName...")
                val source = Gson().fromJson(
                    String(refmapFile.content, StandardCharsets.UTF_8),
                    JsonObject::class.java
                ) ?: return@forEach

                val output = JsonObject()
                var changed = false
                source.asMap().forEach { (name, value) ->
                    when (name) {
                        "mappings" -> {
                            val (objectValue, remapped) = remapClassKeyedObject(value, mappings)
                            output.add(name, objectValue)
                            changed = changed || remapped
                        }

                        "data" -> {
                            val dataObject = JsonObject()
                            if (value.isJsonObject) {
                                value.asJsonObject.asMap().forEach { (type, typeValue) ->
                                    val (objectValue, remapped) = remapClassKeyedObject(typeValue, mappings)
                                    dataObject.add(type, objectValue)
                                    changed = changed || remapped
                                }
                                output.add(name, dataObject)
                            } else {
                                output.add(name, value.deepCopy())
                            }
                        }

                        else -> output.add(name, value)
                    }
                }

                if (changed) {
                    refmapFile.content = Gson().toJson(output).toByteArray(StandardCharsets.UTF_8)
                }
            }
    }

    private fun remapClassKeyedObject(
        value: JsonElement,
        mappings: Map<String, String>
    ): Pair<JsonElement, Boolean> {
        val output = JsonObject()
        var changed = false
        if (!value.isJsonObject) return value.deepCopy() to false

        value.asJsonObject.asMap().forEach { (name, element) ->
            val remappedName = remapClassKey(name, mappings)
            output.add(remappedName, element)
            changed = changed || remappedName != name
        }
        return output to changed
    }

    private fun remapClassKey(name: String, mappings: Map<String, String>): String {
        mappings[name]?.let { return it }
        val internalName = name.splash
        val remappedName = mappings[internalName] ?: return name
        return if (name.contains('.')) remappedName.dot else remappedName
    }

    private fun mixinClassName(packagePrefix: String, mixinName: String): String {
        return if (packagePrefix.isBlank()) {
            mixinName.splash
        } else {
            "$packagePrefix.$mixinName".splash
        }
    }
}
