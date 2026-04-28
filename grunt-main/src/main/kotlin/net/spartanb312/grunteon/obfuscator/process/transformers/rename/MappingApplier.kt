package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode

class MappingApplier : Transformer<MappingApplier.Config>(
    name = enText("process.rename.mapping_applier", "MappingApplier"),
    category = Category.Renaming,
    description = enText(
        "process.rename.mapping_applier.desc",
        "Applying mappings"
    )
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
        parForEachClasses {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, instance.nameMapping)
            it.accept(adapter)
            newClasses.local.add(copy)
        }
        seq {
            val instance = contextOf<Grunteon>()
            instance.workRes.inputClassMap.clear()
            newClasses.global.forEach { instance.workRes.inputClassMap[it.name] = it }
        }
    }
}
