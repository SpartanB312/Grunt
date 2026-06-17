package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_OPTIMIZER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.random
import net.spartanb312.grunteon.obfuscator.util.collection.toListFast
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import org.objectweb.asm.tree.LineNumberNode

@Transformer.CreditMultiplier(0.8)
@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.optimize.source_debug_info_hide.desc",
    "Remove source file information and line numbers"
)
class SourceDebugInfoHide : Transformer<SourceDebugInfoHide.Config>(
    "SourceDebugInfoHide",
    Category.Optimization,
) {

    init {
        before(Category.Encryption, "Optimizer should run before encryption category")
        before(Category.Controlflow, "Optimizer should run before controlflow category")
        before(Category.AntiDebug, "Optimizer should run before anti debug category")
        before(Category.Authentication, "Optimizer should run before authentication category")
        before(Category.Exploit, "Optimizer should run before exploit category")
        before(Category.Miscellaneous, "Optimizer should run before miscellaneous category")
        before(Category.Redirect, "Optimizer should run before redirect category")
        before(Category.Renaming, "Optimizer should run before renaming category")
    }

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc("Remove or edit source file information")
        @SettingName("Source files")
        val sourceFiles: SourceFileAction = SourceFileAction.Remove,
        @SettingDesc("Remove line numbers")
        @SettingName("Line numbers")
        val lineNumbers: Boolean = true,
        @SettingDesc("Customize source names")
        @SettingName("Source names")
        val sourceNames: List<String> = listOf("")
    ) : TransformerConfig()

    enum class SourceFileAction(override val displayName: CharSequence) : DisplayEnum {
        Off("No operation"),
        Remove("Remove"),
        Replace("Replace"),
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > SourceDebugInfoHide: Removing/Editing debug information...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            if (classNode.isExcluded(DISABLE_OPTIMIZER)) return@parForEachClassesFiltered
            val counter = counter.local
            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
            if (config.sourceFiles != SourceFileAction.Off) {
                if (config.sourceFiles == SourceFileAction.Replace) {
                    classNode.sourceDebug = config.sourceNames.random(randomGen)
                    classNode.sourceFile = config.sourceNames.random(randomGen)
                } else {
                    classNode.sourceDebug = null
                    classNode.sourceFile = null
                }
                counter.add()
            }
            if (config.lineNumbers) classNode.methods.forEach { methodNode ->
                if (methodNode.isExcluded(DISABLE_OPTIMIZER)) return@forEach
                methodNode.instructions.toListFast().forEach {
                    if (it is LineNumberNode) {
                        methodNode.instructions.remove(it)
                        counter.add()
                    }
                }
            }
        }
        post {
            Logger.info(" - SourceDebugInfoHide:")
            credit.add(counter.global.get() * 50L)
            Logger.info("    Removed/Edited ${counter.global.get()} debug information")
        }
    }
}
