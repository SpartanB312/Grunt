package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_OPTIMIZER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.collection.toListFast
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.matchAnyOp
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode

@Transformer.Stability(StableLevel.RockSolid)
@Transformer.Description(
    "process.optimize.dead_code_remove.desc",
    "Remove useless instruction sequences"
)
class DeadCodeRemove : Transformer<DeadCodeRemove.Config>(
    "DeadCodeRemove",
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
        @SettingDesc("Remove redundant load and pop")
        @SettingName("Pop")
        val pop: Boolean = true,
        @SettingDesc("Remove redundant load and pop2")
        @SettingName("Pop2")
        val pop2: Boolean = true,
        @SettingDesc("Remove fall through goto")
        @SettingName("Fallthrough")
        val fallthrough: Boolean = true
    ) : TransformerConfig()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > DeadCodeRemove: Removing dead codes...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val instListCache = localScopeValue { FastObjectArrayList<AbstractInsnNode>() }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_OPTIMIZER)) return@parForEachClassesFiltered
            val instListCache = instListCache.local
            classNode.methods.asSequence()
                .filter { !it.isNative && !it.isAbstract }
                .forEach { methodNode ->
                    if (methodNode.isExcluded(DISABLE_OPTIMIZER)) return@forEach
                    for (it in methodNode.instructions.toListFast(instListCache)) {
                        val counter = counter.local
                        when {
                            config.pop && it.opcode == Opcodes.POP -> {
                                val pre = it.previous ?: continue
                                if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                    methodNode.instructions.remove(pre)
                                    methodNode.instructions.remove(it)
                                    counter.add(2)
                                }
                            }

                            config.pop2 && it.opcode == Opcodes.POP2 -> {
                                val pre = it.previous ?: continue
                                if (pre.matchAnyOp(Opcodes.DLOAD, Opcodes.LLOAD)) {
                                    methodNode.instructions.remove(pre)
                                    methodNode.instructions.remove(it)
                                    counter.add(2)
                                } else if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                    val prePre = pre.previous ?: continue
                                    if (prePre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                        methodNode.instructions.remove(prePre)
                                        methodNode.instructions.remove(pre)
                                        methodNode.instructions.remove(it)
                                        counter.add(3)
                                    }
                                }
                            }

                            config.fallthrough && it.opcode == Opcodes.GOTO -> {
                                val next = it.next
                                if (next == (it as JumpInsnNode).label) {
                                    methodNode.instructions.remove(it)
                                    counter.add(1)
                                }
                            }
                        }
                    }
                }
        }
        post {
            Logger.info(" - DeadCodeRemove:")
            Logger.info("    Removed ${counter.global.get()} dead codes")
        }
    }
}

