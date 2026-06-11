package net.spartanb312.grunteon.obfuscator.process.transformers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.ResourceSet
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.extensions.removeAnnotations
import java.nio.charset.StandardCharsets

@Transformer.Description(
    "process.other.post_process.desc",
    "Post resource process. Manifest/YML/JSON remap"
)
class PostProcess : Transformer<PostProcess.Config>(
    "PostProcess",
    Category.PostProcess,
) {
    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Remap manifest")
        @SettingName("Manifest")
        val manifest: Boolean = true,
        @SettingDesc("Remap Plugin YML")
        @SettingName("Plugin main")
        val pluginMain: Boolean = true,
        @SettingDesc("Remap Bungee YML")
        @SettingName("Bungee main")
        val bungeeMain: Boolean = true,
        @SettingDesc("Remap Fabric JSON")
        @SettingName("Fabric main")
        val fabricMain: Boolean = true,
        @SettingDesc("Remap Velocity JSON")
        @SettingName("Velocity main")
        val velocityMain: Boolean = true,
        @SettingDesc("Main class manifest key")
        @SettingName("Manifest replace")
        val manifestReplace: List<String> = listOf(
            "Main-Class:",
            "Launch-Entry:"
        )
    ) : TransformerConfig()

    init {
        after(Category.Encryption, "Post process should run after encryption category")
        after(Category.Controlflow, "Post process should run after controlflow category")
        after(Category.AntiDebug, "Post process should run after anti debug category")
        after(Category.Authentication, "Post process should run after authentication category")
        after(Category.Exploit, "Post process should run after exploit category")
        after(Category.Miscellaneous, "Post process should run after miscellaneous category")
        after(Category.Optimization, "Post process should run after optimization category")
        after(Category.Redirect, "Post process should run after redirect category")
        after(Category.Renaming, "Post process should run after renaming category")
        after(Category.Other, "Post process should run after other category")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            Logger.info(" > Post processing resources...")
            if (config.manifest) processManifest(config)
            if (config.pluginMain) processPluginMain()
            if (config.bungeeMain) processBungeeMain()
            if (config.fabricMain) processFabricMain()
            if (config.velocityMain) processVelocityMain()
        }
        // Clean up
        val annotationList = DISABLER + IGNORE + INTERNAL
        val filter = config.classFilter.buildFilterStrategy()
        parForEachClasses { classNode ->
            val include = filter.testClass(classNode)
            // annotations
            if (include) {
                classNode.removeAnnotations(annotationList)
                classNode.fields.forEach { field -> field.removeAnnotations(annotationList) }
                classNode.methods.forEach { method -> method.removeAnnotations(annotationList) }
            }
            // dummy insns
            classNode.methods.forEach { method ->
                method.instructions.forEach { instr ->
                    if (instr.opcode in dummyOpcodes) method.instructions.remove(instr)
                }
            }
        }
    }

    context(instance: Grunteon)
    private fun processManifest(config: Config) {
        val manifestFile = instance.workRes.getInputResource("META-INF/MANIFEST.MF") ?: return
        Logger.info("    Processing MANIFEST.MF...")
        val manifest = mutableListOf<String>()
        manifestFile.content.decodeToString().split("\n").forEach { line ->
            var final = line
            config.manifestReplace.forEach { prefixRaw ->
                val prefix = prefixRaw.removeSuffix(" ")
                if (line.startsWith(prefix)) {
                    val remaining = line.substringAfter(prefix)
                        .substringAfter(" ")
                        .replace("\r", "")
                        .splash
                    val obfName = instance.nameMapping.getMapping(remaining)?.dot
                    if (obfName != null) {
                        final = "$prefix $obfName"
                        Logger.info("    Replaced manifest $final")
                    }
                }
            }
            manifest.add(final)
        }
        manifestFile.content = manifest.joinToString("\n").toByteArray()
    }

    context(instance: Grunteon)
    private fun processPluginMain() {
        val pluginYMLFile = instance.workRes.getInputResource("plugin.yml") ?: return
        Logger.info("    Processing plugin.yml...")
        pluginYMLFile.content = processYMLMain("plugin main", pluginYMLFile)
    }

    context(instance: Grunteon)
    private fun processBungeeMain() {
        val pluginYMLFile = instance.workRes.getInputResource("bungee.yml") ?: return
        Logger.info("    Processing bungee.yml...")
        pluginYMLFile.content = processYMLMain("bungee main", pluginYMLFile)
    }

    context(instance: Grunteon)
    private fun processYMLMain(desc: String, file: ResourceSet.ResourceEntry): ByteArray {
        val lines = mutableListOf<String>()
        file.content.decodeToString().split("\n").forEach { line ->
            var final = line
            if (line.startsWith("main: ")) {
                val remaining = line.substringAfter("main: ")
                    .replace("\r", "")
                    .splash
                val obfName = instance.nameMapping.getMapping(remaining)?.dot
                if (obfName != null) {
                    final = "main: $obfName"
                    Logger.info("    Replaced $desc $obfName")
                }
            }
            lines.add(final)
        }
        return lines.joinToString("\n").toByteArray()
    }

    context(instance: Grunteon)
    private fun processFabricMain() {
        val jsonFile = instance.workRes.getInputResource("fabric.mod.json") ?: return
        Logger.info("    Processing fabric.mod.json...")
        val mainObject = JsonObject()
        Gson().fromJson(
            String(jsonFile.content, StandardCharsets.UTF_8),
            JsonObject::class.java
        ).apply {
            asMap().forEach { (name, value) ->
                when (name) {
                    "entrypoints" -> {
                        val entryPointObject = JsonObject()
                        value.asJsonObject.asMap().forEach { (pointName, classesObj) ->
                            val classes = JsonArray()
                            classesObj.asJsonArray.forEach {
                                if (it.isJsonObject) {
                                    val entryPointElem = it.asJsonObject
                                    val pre = entryPointElem["value"].asString
                                    val new = instance.nameMapping.getMapping(pre.splash)?.dot
                                    if (new != null) {
                                        Logger.info("    Replaced fabric entry point $pointName $new")
                                        val newElem = JsonObject()
                                        newElem.addProperty("adapter", entryPointElem["adapter"].asString)
                                        newElem.addProperty("value", new)
                                        classes.add(newElem)
                                    } else classes.add(it.asJsonObject)
                                } else {
                                    val pre = it.asString
                                    val new = instance.nameMapping.getMapping(pre.splash)?.dot
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
        jsonFile.content = Gson().toJson(mainObject).toByteArray(Charsets.UTF_8)
    }

    context(instance: Grunteon)
    private fun processVelocityMain() {
        val jsonFile = instance.workRes.getInputResource("velocity-plugin.json") ?: return
        Logger.info("    Processing velocity-plugin.json...")
        val mainObject = JsonObject()
        Gson().fromJson(
            String(jsonFile.content, StandardCharsets.UTF_8),
            JsonObject::class.java
        ).apply {
            asMap()?.forEach { (name, value) ->
                val newValue = if (name == "main") {
                    val mapping = instance.nameMapping.getMapping(value.asString.splash)?.dot ?: return
                    JsonPrimitive(mapping)
                } else {
                    value
                }
                mainObject.add(name, newValue)
            }
        }
        jsonFile.content = Gson().toJson(mainObject).toByteArray(Charsets.UTF_8)
    }

}
