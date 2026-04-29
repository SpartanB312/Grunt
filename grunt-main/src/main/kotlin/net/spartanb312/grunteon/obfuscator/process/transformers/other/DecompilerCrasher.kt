package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.massiveBlankString
import net.spartanb312.grunteon.obfuscator.util.massiveString

@Transformer.Description(
    "process.other.decompiler_crasher.desc",
    "Crash decompilers"
)
class DecompilerCrasher : Transformer<DecompilerCrasher.Config>(
    "DecompilerCrasher",
    Category.Other,
) {
    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        val blankString: Boolean = false
    ) : TransformerConfig()

    context(config: Config)
    private val String?.bigBrainSignature
        get() = if (isNullOrEmpty()) {
            if (config.blankString) massiveBlankString else massiveString
        } else this

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > DecompilerCrasher: Insert crashers to classes...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            context(config) {
                val counter = counter.local
                classNode.methods.forEach { methodNode ->
                    methodNode.signature = methodNode.signature.bigBrainSignature
                }
                classNode.fields.forEach { fieldNode ->
                    fieldNode.signature = fieldNode.signature.bigBrainSignature
                }
                classNode.signature = classNode.signature.bigBrainSignature
                counter.add()
            }
        }
        post {
            Logger.info(" - DecompilerCrasher:")
            Logger.info("    Added ${counter.global.get()} crashers")
        }
    }

}
