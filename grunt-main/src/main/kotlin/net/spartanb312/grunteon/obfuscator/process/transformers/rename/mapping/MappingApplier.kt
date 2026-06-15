package net.spartanb312.grunteon.obfuscator.process.transformers.rename.mapping

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ReflectionSupport
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode

@Transformer.Description(
    "process.rename.mapping_applier.desc",
    "Applying mappings"
)
class MappingApplier : Transformer<MappingApplier.Config>(
    "MappingApplier",
    Category.Renaming,
), MappingSource {
    // Dummy
    class Config : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MappingApplier: Applying mappings...")
        }
        val newClasses = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<ClassNode>())
        }
        val reflectionCounter = reducibleScopeValue { MergeableCounter() }
        parForEachClasses {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, instance.nameMapping)
            it.accept(adapter)
            // Reflection literals are plain strings, so ClassRemapper cannot
            // update them. Run this on the renamed copy while draft metadata is
            // still present.
            reflectionCounter.local.add(ReflectionSupport.remapReflectionStrings(copy, instance.nameMapping))
            newClasses.local.add(copy)
        }
        post {
            val count = reflectionCounter.global.get()
            if (count > 0) Logger.info("    Remapped $count reflection strings")
        }
        seq {
            val instance = contextOf<Grunteon>()
            instance.workRes.inputClassMap.clear()
            newClasses.global.forEach { instance.workRes.inputClassMap[it.name] = it }
        }
    }
}
