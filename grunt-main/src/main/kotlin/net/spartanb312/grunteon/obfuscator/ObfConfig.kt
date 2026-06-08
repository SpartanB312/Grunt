package net.spartanb312.grunteon.obfuscator

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

@Serializable
data class ObfConfig(
    // General configs
    @SettingDesc("The input jar that will be obfuscated")
    val input: String = "input.jar",
    @SettingDesc("The output obfuscated jar")
    val output: String? = "output.jar",
    @SettingDesc("Dependencies of the input jar")
    val libs: List<String> = listOf(),
    @SettingDesc("Global hard exclusions")
    val exclusions: List<String> = listOf(
        "net/example/package/**",
        "net/example/Class"
    ),
    @SettingDesc("Minecraft mixin exclusions. For mods or plugins")
    val mixinExclusions: List<String> = listOf(
        "net/spartanb312/client/mixins/**",
        "net/spartanb312/common/MixinExampleClass"
    ),
    @SettingDesc("Use your specified random seed")
    val controllableRandom: Boolean = true,
    @SettingDesc("Base seed for controllable random")
    val inputSeed: String = "I love XJP",
    @SettingDesc("Dump class/method/field mappings")
    val dumpMappings: Boolean = true,
    @SettingDesc("Enable profiler for performance analysis")
    val profiler: Boolean = false,
    @SettingDesc("Enable debug mode for more verbose logging")
    val forceComputeMax: Boolean = false,
    @SettingDesc("Dependency missing check")
    val missingCheck: Boolean = true,
    @SettingDesc("Show hidden experimental transformers in UI")
    val showHiddenTransformers: Boolean = false,
    // Features
    @SettingDesc("Corrupt file headers")
    val corruptHeaders: Boolean = false,
    @SettingDesc("Corrupt zip CRC32")
    val corruptCRC32: Boolean = false,
    @SettingDesc("Remove time stamps")
    val removeTimeStamps: Boolean = false,
    @SettingDesc("Output jar compression level")
    val compressionLevel: Int = Deflater.BEST_COMPRESSION,
    @SettingDesc("Output jar file archive comment")
    val archiveComment: String = "",
    @SettingDesc("File with specified prefix will be removed")
    val fileRemovePrefix: List<String> = listOf(),
    @SettingDesc("File with specified suffix will be removed")
    val fileRemoveSuffix: List<String> = listOf(),
    // Custom dictionary
    @SettingDesc("Custom dictionary file. Each line is a name")
    val customDictionary: String = "customDictionary.txt",
    @SettingDesc("Custom incremental elements for dictionary")
    val customIncrementalDictionary: List<String> = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n"
    ),
    // Transformers
    val transformerConfigs: List<TransformerConfig> = listOf()
) {
    fun baseSeed(): String = if (controllableRandom) inputSeed else Random.nextInt().toString()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private fun json() = Json {
            serializersModule = TransformerConfig.serializersModule()
            prettyPrint = true
            encodeDefaults = true
            prettyPrintIndent = "    "
            ignoreUnknownKeys = true
        }

        fun read(path: Path): ObfConfig {
            return json().decodeFromString(serializer(), path.readText())
        }

        fun write(config: ObfConfig, path: Path) {
            val jsonString = json().encodeToString(serializer(), config)
            path.writeText(jsonString)
        }
    }
}
