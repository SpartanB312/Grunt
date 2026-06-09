package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ControlflowJump
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping.MappingSource
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.shuffled
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

/**
 * Last update on 2026/03/31 by FluixCarvin
 * TODO: Reflection remap
 * TODO: Resource remap
 */
@Transformer.Description(
    "process.rename.class_renamer.desc",
    "Renaming classes"
)
class ClassRenamer : Transformer<ClassRenamer.Config>(
    "ClassRenamer",
    Category.Renaming,
), MappingSource {

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
        after(ControlflowJump::class.java, "Renamer should run after ControlflowJump")
    }

    @Serializable
    data class Config(
        @SettingDesc("Specify class include/exclude rules")
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Dictionary for renamer")
        @SettingName("Dictionary")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc("Parent package for target name")
        @SettingName("Parent")
        val parent: String = "net/spartanb312/obf/",
        @SettingDesc("Prefix for target name")
        @SettingName("Prefix")
        val prefix: String = "",
        @SettingDesc("Append special char to reverse name")
        @SettingName("Reversed")
        val reversed: Boolean = false,
        @SettingDesc("Shuffled mappings for classes")
        @SettingName("Shuffled")
        val shuffled: Boolean = false,
        @SettingDesc("Corrupted name for class in zip")
        @SettingName("Corrupted name")
        val corruptedName: Boolean = false,
        @SettingDesc("Class exclusion for corrupted name")
        @SettingName("Corrupted exclusion")
        val corruptedExclusion: List<String> = listOf()
    ) : TransformerConfig() {

        val corruptExPredicate = buildClassNamePredicates(corruptedExclusion)

        fun malNamePrefix(name: String): String = if (corruptedName) {
            if (corruptExPredicate.matchedAnyBy(name)) {
                Logger.info("    MalName excluded for $name")
                ""
            } else "\u0000"
        } else ""

        val reversePrefix get() = if (reversed) "\u202E" else ""
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            val instance = contextOf<Grunteon>()
            Logger.info(" > ClassRenamer: Generating class mappings...")
            val strategy = config.classFilter.buildFilterStrategy()
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            val nameGenerator = NameGenerator(dictionary)
            val randomGen = Xoshiro256PPRandom(getSeed("Global"))
            val counter = instance.workRes.inputClassCollection.asSequence()
                .filter { strategy.testClass(it) }
                .run {
                    if (config.shuffled) this.shuffled(randomGen) else this
                }
                .onEach { clazz ->
                    instance.nameMapping.putClassMapping(
                        clazz.name,
                        config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + nameGenerator.nextName()
                    )
                }
                .count()
            Logger.info("    Generated mapping for ${counter} classes")
        }
    }
}
