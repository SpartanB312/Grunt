package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_OPTIMIZER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.toListFast
import net.spartanb312.grunteon.obfuscator.util.extensions.findMethod
import net.spartanb312.grunteon.obfuscator.util.extensions.isEnum
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode

@Transformer.CreditMultiplier(0.8)
@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.optimize.enum_optimize.desc",
    "Optimize enum values() calls"
)
class EnumOptimize : Transformer<EnumOptimize.Config>(
    "EnumOptimize",
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
        @SettingName("Class filter")
        val classFilter: ClassFilterConfig = ClassFilterConfig()
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > EnumOptimize: Optimizing enums...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_OPTIMIZER)) return@parForEachClassesFiltered
            if (!classNode.isEnum) return@parForEachClassesFiltered
            val counter = counter.local
            val desc = "[L${classNode.name};"
            val valuesMethod = classNode.findMethod("values", "()$desc") {
                it.instructions.size() >= 4
            }
            if (valuesMethod != null) {
                for (instruction in valuesMethod.instructions.toListFast()) {
                    if (instruction is MethodInsnNode) {
                        if (instruction.opcode == Opcodes.INVOKEVIRTUAL && instruction.name == "clone") {
                            if (instruction.next.opcode == Opcodes.CHECKCAST) {
                                valuesMethod.instructions.remove(instruction.next)
                            }
                            valuesMethod.instructions.remove(instruction)
                            counter.add(1)
                        }
                    }
                }
            }
        }
        post {
            Logger.info(" - EnumOptimize:")
            credit.add(counter.global.get() * 75L)
            Logger.info("    Optimized ${counter.global.get()} enums")
        }
    }
}
