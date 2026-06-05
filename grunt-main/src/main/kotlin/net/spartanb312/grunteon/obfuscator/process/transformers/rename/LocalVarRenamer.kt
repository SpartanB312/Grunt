package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

/**
 * Last update on 2026/03/31 by FluixCarvin
 */
@Transformer.Description(
    "process.rename.local_var_renamer.desc",
    "Renaming local variables"
)
class LocalVarRenamer : Transformer<LocalVarRenamer.Config>(
    "LocalVarRenamer",
    Category.Renaming,
) {

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        //after(Category.Controlflow, "Renamer should run after controlflow category")
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
        @SettingDesc(enText = "Prefix for new name")
        val prefix: String = "\u202E",
        @SettingDesc(enText = "Delete local vars and parameters info")
        val deleteASMInfo: Boolean = false,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig()

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > LocalVarRenamer: Transforming local variables...")
            // TODO: there is a better way to do this instead of lateinit var
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val dictionary = globalScopeValue { NameGenerator.getDictionary(config.dictionary) }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            val dictionary = dictionary.global
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    // if (excluded) println("Excluded method: ${methodFullDesc(classNode, method)}")
                    if (excluded) return@forEach
                    if (config.deleteASMInfo) {
                        val locals = method.localVariables?.size ?: 0
                        val params = method.parameters?.size ?: 0
                        method.parameters?.clear()
                        method.localVariables?.clear()
                        counter.add(locals + params)
                        return@forEach
                    }
                    val nameGenerator = NameGenerator(dictionary)
                    method.localVariables?.forEach { it.name = "${config.prefix}${nameGenerator.nextName()}" }
                    counter.add(method.localVariables?.size ?: 0)
                }
        }
        post {
            Logger.info(" - LocalVarRenamer:")
            Logger.info("    Transformed ${counter.global.get()} local variables")
        }
    }
}
