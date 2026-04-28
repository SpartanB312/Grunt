package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
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
class ClassRenamer : Transformer<ClassRenamer.Config>(
    name = enText("process.rename.class_renamer", "ClassRenamer"),
    category = Category.Renaming,
    description = enText(
        "process.rename.class_renamer.desc",
        "Renaming classes"
    )
), MappingSource {

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.Controlflow, "Renamer should run after controlflow category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
    }

    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Dictionary for renamer")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc(enText = "Parent package for target name")
        val parent: String = "net/spartanb312/obf/",
        @SettingDesc(enText = "Prefix for target name")
        val prefix: String = "",
        @SettingDesc(enText = "Append special char to reverse name")
        val reversed: Boolean = false,
        @SettingDesc(enText = "Shuffled mappings for classes")
        val shuffled: Boolean = false,
        @SettingDesc(enText = "Corrupted name for class in zip")
        val corruptedName: Boolean = false,
        @SettingDesc(enText = "Class exclusion for corrupted name")
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
            val classes = if (config.shuffled) instance.workRes.inputClassCollection.shuffled(randomGen)
            else instance.workRes.inputClassCollection
            var counter = 0
            classes.asSequence()
                .filter { strategy.testClass(it) }
                .forEach { clazz ->
                    instance.nameMapping.putClassMapping(
                        clazz.name,
                        config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + nameGenerator.nextName()
                    )
                    counter++
                }
            Logger.info("    Generated mapping for ${counter} classes")
        }
    }
}
