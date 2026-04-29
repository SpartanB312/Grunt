package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter

@Transformer.Description(
    "process.other.shuffle_members.desc",
    "Shuffle members in classes"
)
class ShuffleMembers : Transformer<ShuffleMembers.Config>(
    "ShuffleMembers",
    Category.Other,
) {
    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        val methods: Boolean = true,
        val fields: Boolean = true,
        val annotations: Boolean = true,
        val exceptions: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > ShuffleMembers: Shuffling members...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            if (config.methods) classNode.methods?.let {
                classNode.methods = it.shuffled()
                counter.add(it.size)
                it.forEach { method ->
                    if (config.exceptions) {
                        method.exceptions?.shuffle()
                        counter.add(method.exceptions.size)
                    }
                }
            }
            if (config.fields) classNode.fields?.let {
                classNode.fields = it.shuffled()
                counter.add(it.size)
            }
            if (config.annotations) {
                classNode.visibleAnnotations?.let {
                    classNode.visibleAnnotations = it.shuffled()
                    counter.add(it.size)
                }
                classNode.invisibleAnnotations?.let {
                    classNode.invisibleAnnotations = it.shuffled()
                    counter.add(it.size)
                }
                classNode.methods?.forEach { methodNode ->
                    methodNode.visibleAnnotations?.let {
                        methodNode.visibleAnnotations = it.shuffled()
                        counter.add(it.size)
                    }
                    methodNode.invisibleAnnotations?.let {
                        methodNode.invisibleAnnotations = it.shuffled()
                        counter.add(it.size)
                    }
                }
            }
        }
        post {
            Logger.info(" - ShuffleMembers:")
            Logger.info("    Shuffled ${counter.global.get()} members")
        }
    }

}
