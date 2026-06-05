package net.spartanb312.grunteon.obfuscator.process.transformers.other

import kotlinx.serialization.Serializable

import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotations
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isBridge
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import org.objectweb.asm.Opcodes

@Transformer.Description(
    "process.other.fake_synthetic_bridge.desc",
    "Insert fake synthetic bridge flag"
)
class FakeSyntheticBridge : Transformer<FakeSyntheticBridge.Config>(
    "FakeSyntheticBridge",
    Category.Other,
) {
    init {
        after(MethodRenamer::class.java, "FakeSyntheticBridge should run after MethodRenamer")
        before(ReferenceObfuscate::class.java, "FakeSyntheticBridge should run before ReferenceObfuscate")
    }

    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig()
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > FakeSyntheticBridge:Inserting fake synthetic bridge flags")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            // Synthetic
            if (!classNode.access.isSynthetic && !classNode.hasAnnotations) {
                classNode.access = classNode.access or Opcodes.ACC_SYNTHETIC
            }
            classNode.methods.asSequence()
                .filter { !it.access.isSynthetic }
                .forEach {
                    it.access = it.access or Opcodes.ACC_SYNTHETIC
                    counter.add()
                }
            classNode.fields.asSequence()
                .filter { !it.access.isSynthetic && !it.hasAnnotations }
                .forEach {
                    it.access = it.access or Opcodes.ACC_SYNTHETIC
                    counter.add()
                }
            // Bridge
            classNode.methods.asSequence()
                .filter { !it.isInitializer && !it.isAbstract && !it.isBridge }
                .forEach {
                    if (Opcodes.ACC_BRIDGE and it.access == 0) {
                        it.access = it.access or Opcodes.ACC_BRIDGE
                        counter.add()
                    }
                }
        }
        post {
            Logger.info(" - FakeSyntheticBridge:")
            Logger.info("    Inserted ${counter.global.get()} flags")
        }
    }

}
