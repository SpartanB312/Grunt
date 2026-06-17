package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.shuffle
import net.spartanb312.grunteon.obfuscator.util.collection.shuffled
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed

@Transformer.CreditMultiplier(0.8)
@Transformer.Stability(StableLevel.RockSolid)
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
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingName("Methods")
        val methods: Boolean = true,
        @SettingName("Fields")
        val fields: Boolean = true,
        @SettingName("Annotations")
        val annotations: Boolean = true,
        @SettingName("Exceptions")
        val exceptions: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > ShuffleMembers: Shuffling members...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(
            instance.globalExclusion
                .and(instance.mixinExclusion)
                .and(config.classFilter.toClassPredicate())
        ) { classNode ->
            val counter = counter.local
            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
            if (config.methods) classNode.methods?.let {
                classNode.methods = it.shuffled(randomGen)
                counter.add(it.size)
                it.forEach { method ->
                    if (config.exceptions) {
                        method.exceptions?.shuffle(randomGen)
                        counter.add(method.exceptions.size)
                    }
                }
            }
            if (config.fields) classNode.fields?.let {
                classNode.fields = it.shuffled(randomGen)
                counter.add(it.size)
            }
            if (config.annotations) {
                classNode.visibleAnnotations?.let {
                    classNode.visibleAnnotations = it.shuffled(randomGen)
                    counter.add(it.size)
                }
                classNode.invisibleAnnotations?.let {
                    classNode.invisibleAnnotations = it.shuffled(randomGen)
                    counter.add(it.size)
                }
                classNode.methods?.forEach { methodNode ->
                    methodNode.visibleAnnotations?.let {
                        methodNode.visibleAnnotations = it.shuffled(randomGen)
                        counter.add(it.size)
                    }
                    methodNode.invisibleAnnotations?.let {
                        methodNode.invisibleAnnotations = it.shuffled(randomGen)
                        counter.add(it.size)
                    }
                }
            }
        }
        post {
            Logger.info(" - ShuffleMembers:")
            credit.add(counter.global.get() * 10L)
            Logger.info("    Shuffled ${counter.global.get()} members")
        }
    }

}
